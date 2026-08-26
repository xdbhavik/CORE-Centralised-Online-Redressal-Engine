# CORE Citizen App

**Centralised Online Redressal Engine** - The citizen-facing mobile and web application for filing, tracking, and managing public grievances.

Built with Flutter, targeting Android, iOS, Web, Windows, and Linux from a single codebase.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Screens Overview](#screens-overview)
- [API Integration](#api-integration)
- [Supported Languages](#supported-languages)

---

## Features

### Complaint Filing
- **Text complaints** with real-time AI analysis (category, priority, confidence score)
- **Voice complaints** with audio recording (2-min max), waveform visualization, and backend speech-to-text transcription
- **Image complaints** via camera capture or gallery upload
- **AI Assistant** chatbot for guided complaint drafting
- **AI Review** screen with reasoning cards, duplicate detection, and anticipated resolution timeline before submission
- GPS auto-detection with Google Maps location picker and reverse geocoding
- Multi-media attachments (camera, gallery, file picker - up to 5 per complaint)

### Complaint Tracking
- Dashboard with animated stats (total, assigned, resolved complaints)
- Searchable complaint list with status filters (Assigned, Resolved, etc.)
- Detailed complaint view with status timeline, assigned officer info, media gallery, and map
- Complaint actions: close, reopen, delete

### Authentication
- Mobile + password login
- Passwordless OTP login via Firebase Phone Auth
- Registration with Firebase OTP verification
- Forgot password with OTP-based reset
- Auto token refresh with 401 retry interceptor
- Graceful fallback to mock OTP mode when Firebase config is missing (dev-friendly)

### Notifications & Alerts
- Real-time notification feed with unread badges
- Notification types: status updates, officer assignments, resolutions, feedback requests
- Mark all as read

### Profile & Settings
- Editable user profile (name, email, preferred language)
- Language preference selection
- Logout with confirmation

### UI/UX
- Material 3 design system with custom color tokens, typography (Inter), spacing grid, and elevation system
- Responsive layouts (mobile, tablet, desktop) with adaptive navigation (bottom nav on mobile, sidebar on desktop)
- Smooth page transitions (slide + fade) and staggered list animations
- Shimmer loading placeholders
- Glassmorphic splash screen with CORE branding

---

## Tech Stack

| Category | Package | Version |
|----------|---------|---------|
| Framework | Flutter | SDK ^3.11.0 |
| Routing | go_router | ^17.4.0 |
| HTTP Client | dio | ^5.11.0 |
| Auth | firebase_core | ^3.15.2 |
| Auth | firebase_auth | ^5.7.0 |
| Storage | shared_preferences | ^2.5.5 |
| Maps | google_maps_flutter | ^2.18.0 |
| Location | geolocator | ^14.0.2 |
| Geocoding | geocoding | ^4.0.0 |
| Camera/Gallery | image_picker | ^1.0.7 |
| File Picker | file_picker | ^8.1.7 |
| Audio Recording | record | ^6.2.1 |
| Audio Playback | audioplayers | ^6.1.0 |
| Typography | google_fonts | ^8.2.1 |
| Utilities | path_provider, mime, url_launcher | — |

---

## Architecture

The app follows a **feature-based architecture** with clean separation of concerns:

```
lib/
├── main.dart                  # App entry point, Firebase init
├── core/                      # Shared foundation
│   ├── config/                # App config, Firebase options
│   ├── models/                # Data models / DTOs
│   ├── network/               # Dio HTTP client with auth interceptor
│   ├── router/                # go_router route definitions
│   ├── services/              # API service classes
│   ├── theme/                 # Design tokens (colors, typography, spacing, shadows, animations)
│   └── widgets/               # Reusable UI components
├── features/                  # Feature modules
│   ├── auth/                  # Login, registration, OTP, forgot password
│   ├── complaints/            # Create, list, detail, AI review, voice/image input
│   ├── home/                  # Dashboard with stats, search, map, alerts
│   ├── onboarding/            # Welcome, language selection, feature walkthrough
│   ├── profile/               # User profile management
│   ├── alerts/                # Notification feed
│   ├── settings/              # App settings
│   └── splash/                # Animated splash screen
├── services/                  # Platform-specific services (image, voice)
└── utils/                     # Platform detection utilities
```

### Key Design Decisions

- **Local state management** via `StatefulWidget` — no global state library needed for the citizen app's scope
- **Service-oriented API layer** — each domain (auth, complaints, AI, notifications) has its own service class
- **Singleton Dio client** with interceptor-based token management and automatic 401 retry
- **Platform-aware UI** — camera features hidden on Windows, native maps on Android/iOS with web fallback elsewhere
- **Graceful Firebase fallback** — if `google-services.json` is missing, the app automatically switches to mock OTP mode instead of crashing

---

## Project Structure

```
apps/Citizen/
├── android/                   # Android platform config (Google Maps key, permissions)
├── ios/                       # iOS platform config
├── linux/                     # Linux platform config
├── windows/                   # Windows platform config
├── lib/                       # Dart source code (see Architecture above)
├── test/                      # Widget tests
├── pubspec.yaml               # Dependencies and metadata
└── analysis_options.yaml      # Lint rules
```

---

## Getting Started

### Prerequisites

- [Flutter SDK](https://docs.flutter.dev/get-started/install) ^3.11.0
- Android Studio or Xcode (for mobile builds)
- Chrome (for web builds)
- A running instance of the CORE backend

### Setup

```bash
# Navigate to the citizen app
cd apps/Citizen

# Install dependencies
flutter pub get

# Run on connected device / emulator
flutter run

# Run on Chrome (web)
flutter run -d chrome

# Run on Windows
flutter run -d windows
```

### Firebase Setup (for real OTP)

1. **Android**: Place your `google-services.json` in `apps/Citizen/android/app/`
2. **Web**: Firebase options are pre-configured in `lib/core/config/firebase_options.dart`
3. **Without Firebase**: The app automatically falls back to mock OTP mode using backend endpoints (dev only)

---

## Configuration

### API Base URL

The app connects to `https://demo.yogeshghule.me/api/v1` by default. Override it at build/run time:

```bash
# Local backend (Android emulator)
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1

# Local backend (physical device / web)
flutter run --dart-define=API_BASE_URL=http://<your-ip>:8080/api/v1
```

### Google Maps

Add your Maps API key in `android/app/src/main/AndroidManifest.xml` via the `MAPS_API_KEY` manifest placeholder.

### Android Permissions

The app requests the following permissions (declared in AndroidManifest):
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — GPS for complaint location
- `CAMERA` — Photo capture for image complaints
- `RECORD_AUDIO` — Voice recording for voice complaints
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` — Gallery access for media attachments

---

## Screens Overview

| Screen | Route | Description |
|--------|-------|-------------|
| Splash | `/splash` | Animated CORE branding with glassmorphic logo |
| Welcome | `/welcome` | Landing page with Get Started and Sign In CTAs |
| Language Selection | `/language` | Searchable language picker |
| Onboarding | `/onboarding` | 5-page feature walkthrough (voice, AI, tracking, privacy, accessibility) |
| Login | `/login` | Mobile + password authentication |
| OTP Login | `/otp-login` | Passwordless Firebase phone OTP |
| Register | `/register` | Account creation with OTP verification |
| Forgot Password | `/forgot-password` | 3-step OTP-based password reset |
| Verify OTP | `/verify-otp` | 6-digit OTP input with animated UI |
| Home | `/home` | Dashboard with stats, search, map, nearby alerts, AI FAB |
| Create Complaint | `/create-complaint` | Unified complaint form with voice, location, media |
| Text Complaint | `/text-complaint` | Text input with real-time AI analysis |
| Image Complaint | `/image-complaint` | Camera/gallery capture |
| Voice Complaint | `/voice-complaint` | Audio recording with waveform and transcription |
| Gallery Upload | `/gallery-upload` | Gallery-only image selection |
| AI Assistant | `/ai-assistant` | Chat interface with AI for guided complaint drafting |
| AI Review | `/ai-review` | Pre-submission review with AI reasoning and duplicate check |
| My Complaints | `/my-complaints` | Searchable, filterable complaint list |
| Complaint Detail | `/complaint-detail/:id` | Full details, timeline, media, feedback |
| Profile | `/profile` | Edit name, email, language preference |
| Alerts | `/alerts` | Notification feed with unread indicators |
| Settings | `/settings` | App settings and logout |

---

## API Integration

The app communicates with the CORE Spring Boot backend via a singleton Dio client. Key API domains:

| Service | Endpoints | Purpose |
|---------|-----------|---------|
| AuthService | `/auth/login`, `/auth/register`, `/auth/firebase/*` | Authentication and profile management |
| ComplaintService | `/complaints/*` | CRUD, media upload, timeline, close/reopen |
| AiService | `/ai/analyze`, `/ai/chat`, `/ai/duplicate-check` | AI analysis, chatbot, duplicate detection |
| NotificationService | `/notifications/*` | Notification feed and read status |
| FeedbackService | `/feedback/*` | Star ratings and comments on resolved complaints |
| SpeechToTextService | `/speech-to-text` | Voice recording transcription |
| LocationService | Geolocator + Geocoding | GPS coordinates and reverse geocoding |

Token management is handled automatically — the Dio interceptor attaches Bearer tokens to every request and refreshes expired tokens transparently.

---

## Supported Languages

The app supports all 23 scheduled languages of India:

English, Hindi, Bengali, Telugu, Marathi, Tamil, Urdu, Gujarati, Kannada, Odia, Malayalam, Punjabi, Assamese, Maithili, Sanskrit, Kashmiri, Nepali, Konkani, Sindhi, Dogri, Manipuri, Bodo, Santali

---

## Reusable Widget Library

The app includes a set of shared components under `lib/core/widgets/`:

- **CoreButton** — 7 variants (primary, secondary, danger, ghost, text, FAB, AI processing) with animated press feedback
- **CoreTextField** — Custom input with label, prefix icon, password toggle, autofill hints
- **CoreCard**, **CoreModal**, **CoreToast** — Standard UI containers
- **CitizenBottomNavbar** — Floating pill-style navigation bar with animated active indicator
- **ResponsiveLayout** — Breakpoint-based layout wrapper (mobile < 600px, tablet 600-1024px, desktop > 1024px)
- **ComplaintMapWidget** — Google Maps integration for complaint locations
- **StatusBadge** — Colored status indicator chips
- **ShimmerLoading** — Skeleton loading placeholders
- **StaggeredEntrance** — Animated list item entrance
- **AiReasoningCard** — AI analysis display card
- **KpiCard** — Dashboard metric display

---

## License

This project is part of the CORE - Centralised Online Redressal Engine system.
