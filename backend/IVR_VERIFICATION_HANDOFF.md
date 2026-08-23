# Citizen Call-Back Verification (IVR) — Handoff Note

**Status:** ✅ **All gaps (1–6) complete.** `mvn -o test` → BUILD SUCCESS, 57 tests, 0 failures.

Session 2 closed out Gaps 3, 4 & 5, added `VerificationGateTest` (8 cases) and documented the
Option A SLA rule in `BACKEND_REPORT.md` §8.1. The "Remaining work" section below is kept for
its reasoning, with outcomes recorded inline — see §5 for what is genuinely still open.



---

## 1. What this feature does

When a complaint is lodged, the system places an **outbound verification call** to the
citizen and asks them to confirm they actually lodged it. Until they confirm, the
department is not allowed to **act** on the complaint. This stops fake / prank / mistaken
complaints from consuming officer time.

### Two decisions already locked in — do not silently change these

**SLA = "Option A": the SLA clock starts at complaint creation, not at verification.**
Verification latency is *our* problem, not the citizen's. If the clock only started after
we reached them, every hour our telephony took would quietly extend the department's
deadline and shrink the citizen's real redressal window. So the gate holds *action*, while
`slaDueAt` keeps counting from creation. Consequence to keep in mind: a complaint can breach
SLA while still waiting for verification — that is intentional and is a signal that our
call-back pipeline is too slow, which is exactly what we want surfaced.

**Fail-open, not fail-closed** (`ivr.verification.proceed-on-failed=true`).
If we exhaust all call attempts without reaching the citizen, the complaint **proceeds**.
A switched-off phone must never be the reason a real grievance is silently stranded. Only an
*explicit* denial by the citizen blocks the complaint.

---

## 2. Files already created / modified

### New — `com.SIH.mark1.ivr` package
| File | Role |
|---|---|
| `config/IvrProperties.java`, `SarvamVoiceProperties.java`, `IvrConfig.java` | config binding |
| `model/IvrSession.java`, `IvrCallLog.java`, `IvrCallState.java`, `IvrLanguage.java` | call state + audit trail |
| `repository/IvrSessionRepository.java`, `IvrCallLogRepository.java` | persistence |
| `service/SarvamWebhookAuthenticator.java` | mints/verifies the per-call HMAC token on the callback |
| `service/SarvamVoiceCallService.java` | places Sarvam voice-agent calls; `isAvailable()` gate |
| `dto/SarvamCallWebhookPayload.java` | the single post-call webhook body |
| `service/VerificationPrompts.java` | multilingual notification/SMS copy |
| `service/SarvamTtsService.java`, `SarvamSpeechTranslateService.java` | voice synthesis / speech→English (inbound IVR only) |
| `service/CitizenVerificationService.java` | **the core state machine** |
| `service/SmsService.java`, `PhoneNumberFormatter.java` | outbound SMS (E.164 normalisation) |
| `controller/IvrVerificationController.java` | Sarvam post-call webhook |

### Modified
- `model/Complaint.java` — verification fields (status, attempt count, timestamps). See the verification block in that file for exact field names before writing DTO mappers.
- `model/VerificationStatus.java`, `model/SourceChannel.java` — new enums.
- `model/NotificationType.java` — added `VERIFICATION_PENDING`, `VERIFICATION_CONFIRMED`, `VERIFICATION_REJECTED`, `VERIFICATION_UNREACHABLE`.
- `service/ComplaintService.java` — kicks off `startVerification(...)` on complaint creation.
- `scheduler/VerificationCallScheduler.java` — retry sweep for unanswered calls.
- `service/NotificationService.java` — **SMS fan-out** (see §3).
- `service/AssignmentManagementService.java` — **action gate enforcement** (see §3).
- `resources/application.properties` — `ivr.*`, `ivr.verification.*`, `sms.enabled`, `sarvam.voice.*`.

### `CitizenVerificationService` public API
```java
void    startVerification(Complaint)                       // enqueue first call
boolean attemptCall(Complaint)                             // place one attempt
void    handleCallResult(SarvamCallWebhookPayload)          // the single webhook entry point
int     runDueVerificationCalls()                          // scheduler entry point
void    confirmFromApp(Complaint)                          // in-app confirmation
boolean isActionAllowed(Complaint)                         // the gate
boolean isDenied(Complaint)
String  blockedReasonOrNull(Complaint)                     // citizen-facing message
```

One webhook method, not five. With Sarvam AI Voice Agents the agent conducts the whole
conversation and reports **once** when the call ends, so there is no per-prompt round trip for
us to handle — the answer, an unanswered ring and a provider-side failure all arrive through
`handleCallResult`.

---

## 3. Work completed this session (with the reasoning)

### Gap 6 — schema
No migration needed. `spring.jpa.hibernate.ddl-auto=update` creates the new `Complaint`
verification columns and the `ivr_session` / `ivr_call_log` tables on boot. Verified against
`application.properties`.

### Gap 2 — SMS delivery
Created `SmsService` + `PhoneNumberFormatter`, and added a fan-out in
`NotificationService.sendNotification(...)`.

Only **three** notification types go out over SMS:
```java
VERIFICATION_PENDING, VERIFICATION_REJECTED, VERIFICATION_UNREACHABLE
```
The reason is deliberate: the call-back exists *because* we could not reach the person in the
app, so an in-app-only notice would be invisible to exactly the people it is meant for.
Routine status updates stay in-app to avoid spamming citizens and burning SMS credit.

Two safety properties, both intentional:
- The fan-out runs **after** the in-app row is persisted, so a telephony outage can never cost a user their notification history.
- Failures are swallowed and logged (`log.warn`). A courtesy SMS must never roll back the grievance action that triggered it.
- `sms.enabled=false` by default → local/CI runs log instead of hitting the paid API.

### Gap 1 — the gate leak (the important fix)
Auto-assignment already respected the gate, but **manual admin assignment bypassed it
entirely** — an admin could push a complaint the citizen had explicitly *denied* straight to
an officer. Fixed in `AssignmentManagementService`:

- `assertActionAllowed(Complaint)` — throws `IllegalStateException(blockedReason)`. Placed at
  the **service layer** so every caller (admin UI, raw API client, scheduler) must pass it;
  a controller-level check would have been bypassable by the next new caller.
- `CitizenVerificationService` is injected as `ObjectProvider<...>` (lazy). The IVR module
  reaches back into assignment when a citizen confirms, so a hard constructor dependency in
  both directions fails context startup. `AutoAssignmentService` is already wired the same way
  inside `CitizenVerificationService` — follow that existing pattern.
- Gate applied in `assign(...)` and in a new `findAssignedToOfficerForAction(...)`, which all
  officer **write** paths now use: accept / start / resolve / addPublicUpdate / addInternalNote.

**Read paths are deliberately left open.** `findAssignedToOfficer(...)` (no gate) still backs
`officerComplaintDetail` and `getInternalNotes`, because an officer must be able to open a
flagged complaint to understand *why* it is on hold. It is *acting* that waits for the
citizen's confirmation, not *looking*.

> `requestReassignment(...)` also still uses the ungated variant. That was a judgement call —
> asking an admin to reassign is arguably an administrative escalation rather than work on the
> complaint. **Flag this for review**; if the team disagrees, switch it to
> `findAssignedToOfficerForAction`.

---

## 4. Remaining work → all closed in session 2

### Gap 3 — Citizen endpoints ✅ DONE
> Implemented in `ComplaintController` as `GET /{id}/verification` and
> `POST /{id}/verification/confirm`, returning `VerificationStatusResponse`. Ownership is
> enforced by reusing the controller's existing owner-scoped lookup, so the check cannot be
> forgotten. `confirmFromApp(...)` short-circuits when already `VERIFIED`, making the double
> tap idempotent.

The confirm endpoint is the escape hatch for a citizen who opens the app before we manage to
reach them by phone — without it, someone with a poor phone line waits on our retry schedule.

### Gap 4 — Admin endpoints ✅ DONE
> New `service/admin/AdminVerificationService.java` + four routes on `AdminController`:
> `GET /admin/verification/flagged`, `GET /admin/verification/summary`,
> `POST /admin/verification/{id}/approve`, `POST /admin/verification/{id}/reject`.
>
> `reason` is validated as mandatory on both overrides (`@NotBlank` on
> `VerificationOverrideRequest`, re-checked in the service so a non-HTTP caller cannot skip it),
> and each override writes a `status_history` row naming the acting admin. An override that
> contradicts a citizen's explicit denial is the action most in need of an audit trail.
>
> `flagged` uses `ComplaintRepository.findByVerificationStatusAndDeletedFalse(...)`; `summary`
> uses `countByVerificationStatusAndDeletedFalse(...)` — both added alongside the existing
> finders in that repository.

### Gap 5 — Expose verification state in DTOs ✅ DONE
> `ComplaintDetailsResponse`, `AssignmentQueueResponse` and `AssignmentComplaintDetailResponse`
> now carry `verificationStatus` + `verificationBlockedReason` (human-readable), populated in
> `AssignmentManagementService.detail(...)` / `toQueueResponse(...)`. Without this the gate
> could block an action while no UI could explain why.

### Finally ✅ DONE
> - Option A documented in `BACKEND_REPORT.md` **§8.1**, with an explicit "this is intentional,
>   not a bug" warning next to the breach-while-pending behaviour, plus the new enums in §7.
> - `mvn -o test` → **57 tests, 0 failures**. The feared `ObjectProvider` compilation break in
>   `SlaServiceTest` / `ComplaintCreationTest` did not materialise — neither constructs
>   `AssignmentManagementService`.
> - `VerificationGateTest` (8 cases) covers both directions of the gate: see §5.

---

## 5. Still open / for review


1. **`requestReassignment(...)` remains ungated** — the judgement call flagged in §3. Unchanged
   this session because it is a deliberate open question for the team, not an oversight.
2. **Client-side fields not yet wired.** The backend now emits `verificationStatus` /
   `verificationBlockedReason`, but `core/lib/models/officer/officer_models.dart`,
   `services/officer_service.dart` and the admin panel do not read them yet. Until they do, a
   blocked action still surfaces as a bare error string to the user — which was the whole point
   of Gap 5.
3. **No end-to-end telephony test.** Everything is unit-level with the Sarvam call service
   mocked. The real webhook round-trip (HMAC token check, agent-variable extraction) has not
   been exercised against a live agent; worth an integration test before this goes anywhere
   public.
4. **The agent script lives outside this repo.** The greeting, question and sign-off are
   authored in the Sarvam dashboard, so a prompt edit there changes what citizens hear with no
   code review and no diff. `sarvam.voice.app-version` is pinned to limit the blast radius, and
   `sarvam.voice.result-variable` / `confirmed-value` / `denied-value` must match the agent's
   configured output variable exactly — a mismatch makes every answer read as "unclear", which
   looks like a dead feature rather than a config error.

### What `VerificationGateTest` asserts
`src/test/java/com/SIH/mark1/service/VerificationGateTest.java` drives
`AssignmentManagementService.assign(...)` rather than the gate helper in isolation — the bug in
Gap 1 was a *caller that skipped the gate*, which a helper-only test would have missed:

| Case | Expected |
|---|---|
| `REJECTED` (citizen denied) | assignment throws, message mentions the denial |
| `PENDING` | assignment throws, reads as a wait not an accusation |
| `FAILED` + `proceed-on-failed=true` | assignment proceeds (fail-open) |
| `FAILED` + `proceed-on-failed=false` | blocked (keeps the flag meaningful) |
| `VERIFIED` | assignment proceeds |
| `gate-enabled=false` | even a denied complaint proceeds |
| IVR module absent (`getIfAvailable() == null`) | no gate, not "block everything" |

The allowed cases assert the call reaches the *officer lookup* and fails there, which proves the
gate was passed without dragging the status/SLA/notification pipeline into the test.

---


## 6. Local run notes


Everything ships **disabled** so a fresh clone never places calls or sends SMS:
```properties
ivr.enabled=false          # no outbound calls; webhooks return "service unavailable"
sms.enabled=false          # messages logged, not sent
ivr.validate-signature=true    # keep true anywhere internet-reachable
ivr.verification.gate-enabled=true
ivr.verification.proceed-on-failed=true
```
To exercise the real flow: fill the `SARVAM_VOICE_*` variables (API key, org / workspace / app
ids, connection id, agent phone number, webhook secret), expose the backend via ngrok, set
`IVR_BASE_PUBLIC_URL` to that HTTPS URL, then `SARVAM_VOICE_ENABLED=true` and
`IVR_ENABLED=true`. For curl-based webhook testing only, temporarily set
`IVR_VALIDATE_SIGNATURE=false`.

Note that `sarvam.voice.*` (apps.sarvam.ai, hosted voice agents) and `ai.sarvam.*`
(api.sarvam.ai, TTS/STT REST) are **separate products with separate keys**. Swapping one for
the other fails silently in whichever half you broke.

Compile check used this session (PowerShell truncates piped Maven output, so redirect to a file):
```powershell
cd "c:\ai-based graviance system\ai-based-graviance-system"
mvn -o compile -DskipTests > compile-out.txt 2>&1
Select-String -Path compile-out.txt -Pattern "ERROR|BUILD"
```
