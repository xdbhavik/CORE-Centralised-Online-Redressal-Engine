               # CORE — Centralised Online Redressal Engine

An AI-powered grievance (complaint) redressal system. Citizens lodge complaints; AI classifies, detects duplicates, and assigns to the right department; officers act; admins oversee. Includes an IVR call-back verification flow and a RAG knowledge base.

---
## Components

| Component | Stack | Port / Entry |
|-----------|-------|--------------|
| **Backend API** | Spring Boot 3.3 (Java 21) | `8080` |
| **Admin Web UI** | React 19 + Vite (nginx) | `80` |
| **Citizen App** | Flutter (Android/iOS/Web) | — |
| **Officers App** | Flutter (Android/iOS/Web) | — |
| **MySQL** | `mysql:8.0` | `3306` |
| **Qdrant** | Vector DB for RAG | `6333` (internal) |

AI services used here:

- **NVIDIA NIM** — chat classification, chatbot, summarisation
- **Sarvam AI (api.sarvam.ai)** — STT (saarika), STT-translate (saaras), TTS (bulbul), translation (mayura)
- **Sarvam AI Voice Agents (apps.sarvam.ai)** — outbound IVR verification calls
- **Qdrant** — vector search (RAG, complaint-history knowledge, duplicate detection via embeddings)


## Features

- **Complaint lifecycle** — citizen lodges → AI classifies/flags duplicate → auto-assigns to department/officer → officer actions → admin moderates
- **IVR call-back verification** (optional) — Sarvam AI Voice Agents call the citizen; only after confirmation can a department act on a complaint (fail-open on unreachable)
- **RAG knowledge base** — Qdrant holds department/SOP/FAQ/government-orders history; LiteLLM answer generator with provenance
- **Duplicate detection** — semantic + hash embeddings, admin review of probable duplicates
- **OTP + Firebase Phone Auth** — mock OTP dev endpoints; real Firebase Admin SDK verification
- **Notifications** — in-app + SMS fan-out (log-only gateway; DLT-compliance note in code)
- **File uploads** — complaint media (photos/videos/PDFs) served over `/media/**`
- **Health/diagnostics** — `/api/v1/ai/health`, Spring Actuator `/health`, `/info`

---

## Architecture

```
Citizen (Flutter) ─┐
Officers (Flutter) ─┼─▶ http://host:8080 ──▶ [ Spring Boot backend ]
Admin (React) ─────┘                                  │
                                                      ├─▶ MySQL 3306
                                                      ├─▶ Qdrant 6333
                                                      ├─▶ NVIDIA NIM (chat)
                                                      ├─▶ Sarvam STT/TTS (api.sarvam.ai)
                                                      └─▶ Sarvam Voice (apps.sarvam.ai) — IVR
                                                    +─▶ /media/** (uploads via volume)
```

Backend layout:
- `controller/` — REST endpoints
- `service/` — business logic (complaints, auth, OTP, notification, SLA, assignments)
- `ai/` — chatbot client, RAG, Qdrant, duplicate detection, embeddings
- `ivr/` — voice-agents (Sarvam), TTS/STT services, webhook handling
- `security/` — JWT, Spring Security filter chain, CORS
- `model/`, `repository/`, `scheduler/` — JPA, schedulers, misc.

Frontend Admin uses axios + token refresh interceptor to call API.

Flutter apps (Citizen, Officers) are independent mobile/web clients.

---

## Prerequisites

| Tool | Version | Used for |
|------|---------|----------|
| **Java (JDK)** | 21 | Backend (Spring Boot 3.3 requires Java 17+) |
| **Maven** | 3.9+ (or bundled `mvnw`) | Build/run backend |
| **Node.js** | 20+ | Admin frontend (Vite build) |
| **Docker Desktop** | 20.10+ (compose v2) | Containerised run (recommended) |
| **Flutter SDK** (optional) | 3.x | Citizen/Officers apps |
| **MySQL** | 8.0 (not needed if using Docker) | Local DB |

---

## Quick Start — Docker Compose (recommended)

From the repo root:

```bash
# 1. Create .env and fill in secrets (only JWT_SECRET is strictly required)
cp .env.example .env

# 2. Build + run all services (MySQL, Qdrant, backend, admin frontend)
docker compose up --build

# 3. When ready, open:
#    Admin UI  → http://localhost
#    Backend   → http://localhost:8080
#    MySQL     → localhost:3306 (root/<DB_PASSWORD>)
```

The compose stack:
- **mysql** — health-checked, volume `mysql_data` persists across restarts
- **qdrant** — health-checked, persistent volume `qdrant_data`
- **backend** — waits for both healthy, env vars injected from `.env`
- **admin-frontend** — build-arg sets `VITE_API_BASE_URL=http://localhost:8080/api/v1`

Everything is connected by the same docker network (name: `core-grievance`).

Logs:
```bash
docker compose logs -f backend
```

Stop & wipe volumes (clean DB + vectors):
```bash
docker compose down -v
```


## Local Run (no Docker)

Run each piece manually.

### 1. MySQL

Start MySQL locally and create the schema (or let JDBC auto-create with `createDatabaseIfNotExist=true`):

```sql
CREATE DATABASE IF NOT EXISTS grievance_system
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Qdrant

```bash
docker run -p 6333:6333 -v qdrant_data:/qdrant/storage qdrant/qdrant:latest
```

### 3. Backend

```bash
cd backend

# (Windows PowerShell)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# or package + run
.\mvnw.cmd clean package -DskipTests
java -jar target/ai-based-graviance-system-0.0.1-SNAPSHOT.jar
```

The backend reads env vars (see **Configuration** below). The `local` Spring profile also reads `backend/config/application-local.properties` (git-ignored) for your real keys.

Backend available at `http://localhost:8080`.

### 4. Admin Frontend (React)

```bash
cd frontend/Admin

npm install

# .env already contains VITE_API_BASE_URL=http://localhost:8080/api/v1
npm run dev        # dev with HMR (vite) on http://localhost:5173 (default)
# or
npm run build      # outputs to dist/ — serve via nginx/vite preview
```

### 5. Flutter Apps (Citizen, Officers)

```bash
cd frontend/Citizen        # or frontend/Officers
flutter pub get
flutter run -d chrome      # web
flutter run -d android     # Android
flutter run                # connected device
```

> **Note:** Flutter apps currently point at their own hard-coded/demo backend URL — check their `lib` config (often in `lib/config`, `lib/constants`, or a `.env`/dart-define). Point them to `http://<backend-host>:8080/...` wherever relevant.

---

## Configuration — Environment Variables

Backend settings pattern `${ENV_VAR:default}` in `backend/src/main/resources/application.properties`.

### Server & App

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Java HTTP port |
| `APP_PUBLIC_URL` | `http://localhost:8080` | Public URL (used as HTTP-Referer for LLM) |
| `APP_UPLOAD_DIR` | `uploads` | Media/IVR audio dir (inside backend) |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:mysql://localhost:3306/grievance_system?...` | JDBC URL |
| `DB_USERNAME` | `root` | user |
| `DB_PASSWORD` | `root` | password |

### JWT

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | (placeholder) | **REQUIRED for production** — HS256 needs ≥32 bytes |
| `JWT_ACCESS_TOKEN_EXPIRATION_MILLIS` | `900000` (15m) | access token lifetime |
| `JWT_REFRESH_TOKEN_EXPIRATION_MILLIS` | `1209600000` (14d) | refresh token lifetime |

### AI

| Variable | Default | Description |
|----------|---------|-------------|
| `NVIDIA_API_KEY` | (empty) | `integrate.api.nvidia.com` chat |
| `NVIDIA_MODEL` | `nvidia/nemotron-nano-8b-instruct` | model name |
| `AI_CHAT_BASE_URL` | NVIDIA base | override the chat endpoint |
| `SARVAM_API_KEY` | (empty) | STT/TTS/translation `api.sarvam.ai` |
| `SARVAM_BASE_URL` | `https://api.sarvam.ai` | REST base |
| `SARVAM_VOICE_API_KEY` | (empty) |hosted voice agents |
| `SARVAM_VOICE_BASE_URL` | `https://apps.sarvam.ai` | voice agents base |
| `SARVAM_VOICE_*` | empty | org / workspace / app / connection / phone / webhook-secret |

### IVR / SMS

| Variable | Default | Description |
|----------|---------|-------------|
| `IVR_ENABLED` | `false` | Ship disabled; true only when sarvam voice is configured |
| `IVR_BASE_PUBLIC_URL` | `http://localhost:8080` | Public callback URL (needs **ngrok** for real calls in dev) |
| `IVR_VALIDATE_SIGNATURE` | `true` | HMAC webhook security |
| `SMS_ENABLED` | `false` | Logs only — no real SMS gateway wired |

### Firebase

| Variable | Default | Description |
|----------|---------|-------------|
| `FIREBASE_ENABLED` | `true` | Toggle Firebase Admin SDK |
| `FIREBASE_WEB_API_KEY` | (empty) | Optional; used for frontend flow |
| `FIREBASE_DEV_MOCK_ENABLED` | `true` (DEV ONLY) | Accepts `idToken=mock-<mobile>` → **false in prod** |

### Qdrant

| Variable | Default | Description |
|----------|---------|-------------|
| `QDRANT_URL` | `http://localhost:6333` | Vector DB |
| `QDRANT_API_KEY` | (empty) | If cloud Qdrant |

---

## Ports Summary

| Service | Host | Container |
|---------|------|-----------|
| Admin UI | `80` | `80` |
| Backend | `8080` | `8080` |
| MySQL | `3306` | `3306` |
| Qdrant | — (internal) | `6333` |

---

## Complaint & Verification Flow

1. Citizen registers complaint → complaint saved (verification PENDING)
2. (Optional) backend calls citizen via Sarvam Voice; citizen answers verification prompt
3. Webhook delivers result → complaint verified/rejected/unreachable
   - **Fail-open rule:** unreachable calls still let the complaint proceed (only explicit denial blocks it)
4. AI classification & duplicate detection run → auto-assignment
5. Officers act on complaint; admin moderates status/history

SLA clock starts at complaint creation time (Option A rule).

---

## Key API Endpoints (backend)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/auth/login` | POST | Login → JWT access+refresh |
| `/api/v1/auth/refresh` | POST | Refresh access token |
| `/api/v1/complaints` | POST/GET | Create/fetch complaints |
| `/api/v1/complaints/{id}` | GET/PATCH | Detail / status |
| `/api/v1/complaints/{id}/media` | POST | Upload media (multipart) |
| `/api/v1/officers/*` | various | Officer operations |
| `/api/v1/admin/*` | various | Admin duplicate review, officer/department management |
| `/api/v1/notifications` | GET | User notifications |
| `/api/v1/ai/health` | GET | LLM + Qdrant diagnostic |
| `/actuator/health`, `/actuator/info` | GET | Spring health |

See also the auto-generated **Swagger UI** at `http://localhost:8080/swagger-ui/index.html`.

---
