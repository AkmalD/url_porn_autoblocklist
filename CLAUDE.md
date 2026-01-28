# CLAUDE.md - URL AutoBlocklist

## Project Overview

URL AutoBlocklist adalah sistem untuk mendeteksi dan memblokir situs dewasa secara otomatis menggunakan Machine Learning. Terdiri dari dua komponen:

- **Android App** (`app/`): Aplikasi mobile untuk deteksi URL di browser
- **Backend API** (`api/`): Go/Fiber REST API dengan PostgreSQL untuk manajemen blocklist

Aplikasi ini merupakan ekstraksi fitur auto blocklist dari LawanPMO Premium untuk keperluan Tugas Akhir.

## Build Commands

### Android (from root directory)

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean
```

### Go API (from `api/` directory)

```bash
# Install dependencies
go mod download

# Run server
go run ./cmd/server

# Build binary
go build -o server ./cmd/server
```

## Architecture

```
app/
├── src/main/
│   ├── java/com/lawanpmo/autoblocklist/
│   │   ├── AutoBlocklistApp.kt          # Application class (Hilt)
│   │   ├── data/
│   │   │   └── classifier/
│   │   │       └── UrlClassifier.kt     # TFLite ML classifier
│   │   ├── di/
│   │   │   └── AppModule.kt             # Hilt dependency injection
│   │   ├── domain/
│   │   │   └── repository/
│   │   │       └── IUrlClassifier.kt    # Classifier interface
│   │   ├── presentation/
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt
│   │   │       ├── home/
│   │   │       │   └── HomeScreen.kt    # Landing page
│   │   │       └── theme/               # Compose theme
│   │   └── service/
│   │       └── UrlBlockerAccessibilityService.kt  # Core blocking service
│   ├── assets/
│   │   └── url_classifier.tflite        # ML model (280 KB)
│   └── res/
│       ├── xml/
│       │   └── accessibility_service_config.xml
│       └── values/
│           ├── strings.xml
│           ├── colors.xml
│           └── themes.xml
```

## Backend Architecture

```
api/
├── cmd/server/
│   └── main.go                    # Entry point
├── internal/
│   ├── config/
│   │   └── config.go              # Environment configuration
│   ├── database/
│   │   ├── database.go            # DB connection & migration
│   │   └── seeder.go              # Initial data seeder (80+ domains)
│   ├── handler/
│   │   └── blocklist_handler.go   # HTTP handlers
│   ├── middleware/
│   │   └── auth.go                # Admin authentication
│   ├── model/
│   │   └── domain.go              # GORM models
│   ├── repository/
│   │   ├── domain_repository.go   # Domain data access
│   │   └── report_repository.go   # Report data access
│   └── service/
│       └── blocklist_service.go   # Business logic
├── go.mod
├── go.sum
└── .env.example
```

## API Endpoints

### Public Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/blocklist/sync` | Get all active blocked domains for device sync |
| GET | `/api/blocklist/stats` | Get public statistics |
| POST | `/api/blocklist/check` | Check if domain is blocked |
| POST | `/api/blocklist/report` | Report suspicious domain |

### Admin Endpoints (requires API key)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/blocklist/stats` | Full statistics |
| GET | `/api/admin/blocklist/domains` | List domains (with filters) |
| POST | `/api/admin/blocklist/domains` | Add single domain |
| POST | `/api/admin/blocklist/domains/bulk` | Bulk add domains |
| PUT | `/api/admin/blocklist/domains/:id/status` | Update domain status |
| DELETE | `/api/admin/blocklist/domains/:id` | Delete domain |
| GET | `/api/admin/blocklist/reports` | List pending reports |
| POST | `/api/admin/blocklist/reports/:id/review` | Approve/reject report |

### Sync Response Format

```json
{
  "domains": ["pornhub.com", "xvideos.com", ...],
  "version": 1705401600,
  "last_updated": "2024-01-16T12:00:00Z",
  "count": 85
}
```

## Database Models

```go
// BlockedDomain - stored blocked domains
type BlockedDomain struct {
    ID              uuid.UUID
    Domain          string          // e.g., "pornhub.com"
    Category        DomainCategory  // pornography, gambling, etc.
    Source          DomainSource    // seeded, user_report, ml_detected, admin_added
    Status          DomainStatus    // active, pending_review, rejected
    ConfidenceScore float64
    TimesBlocked    int64
    TimesReported   int64
}

// DomainReport - user-submitted reports
type DomainReport struct {
    ID           uuid.UUID
    Domain       string
    ReporterIP   string
    Reason       string
    MLScore      float64
    Status       string  // pending, approved, rejected
}
```

## Key Technologies

### Android
- **Kotlin** - Programming language
- **Jetpack Compose** - UI framework
- **Hilt** - Dependency injection
- **TensorFlow Lite** - ML inference
- **Accessibility Service** - URL detection

### Backend
- **Go 1.21** - Programming language
- **Fiber v2** - Web framework
- **GORM** - ORM for PostgreSQL
- **PostgreSQL** - Database

## How It Works

### ML Classification Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    URL CLASSIFICATION FLOW                       │
├─────────────────────────────────────────────────────────────────┤
│  1. DOMAIN NAME EXTRACTION                                      │
│     play.google.com → "google"                                  │
│     xxx.pornhub.com → "pornhub"                                 │
│     bokep.co.id → "bokep"                                       │
│                                                                 │
│  2. ML CLASSIFICATION (CNN 1D)                                  │
│     Domain name → Tokenize → Model → Score (0.0 - 1.0)         │
│                                                                 │
│  3. DECISION                                                    │
│     Score > 0.7 → BLOCK                                         │
│     Score <= 0.7 → ALLOW                                        │
└─────────────────────────────────────────────────────────────────┘
```

### Model Specifications

| Property | Value |
|----------|-------|
| Architecture | CNN 1D |
| Input | Character-level tokenized domain (max 34 chars) |
| Output | Sigmoid (0.0 = normal, 1.0 = adult) |
| Accuracy | 96.69% |
| Precision | 99.20% |
| Size | 280 KB |
| Threshold | 0.7 |

### Detection Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| `URL_CHECK_THROTTLE_MS` | 300ms | Throttle URL checks |
| `BLOCK_DELAY_MS` | 1500ms | Delay before blocking |
| `OVERLAY_DURATION_MS` | 5000ms | Block overlay display time |

## Supported Browsers

- Google Chrome (stable, beta, dev, canary)
- Samsung Internet
- Mozilla Firefox
- Microsoft Edge
- Opera
- Brave
- Vivaldi
- Kiwi Browser
- DuckDuckGo

## Setup Instructions

### Backend Setup

1. Install PostgreSQL and create database:
   ```sql
   CREATE DATABASE url_autoblocklist;
   ```

2. Copy environment file:
   ```bash
   cd api
   cp .env.example .env
   # Edit .env with your database credentials
   ```

3. Run the server:
   ```bash
   go run ./cmd/server
   ```

4. Server will:
   - Auto-migrate database tables
   - Seed 80+ known adult domains
   - Start on port 8080

### Android Setup

1. Open project in Android Studio
2. Sync Gradle
3. (Optional) Update API base URL in code if using backend
4. Build and install on device
5. Open app and tap "Aktifkan Proteksi"
6. Enable "URL AutoBlocklist" in Accessibility Settings
7. Done! Browsing is now protected

## Testing

### Manual Test Steps

1. Enable accessibility service
2. Open Chrome
3. Navigate to a known adult domain (not in Google search)
4. Verify: Browser closes, block overlay appears after ~1.5 seconds

### Check Logcat

```
adb logcat -s UrlBlockerService UrlClassifier
```

Expected logs:
```
D/UrlClassifier: Domain extraction: 'xxx.pornhub.com' → 'pornhub'
D/UrlClassifier: Classified 'pornhub.com' (name='pornhub'): score=0.9512, isAdult=true
D/UrlBlockerService: Adult content detected: pornhub.com (score=0.9512)
D/UrlBlockerService: Executing block for: pornhub.com
```

## Differences from LawanPMO

| Feature | LawanPMO Premium | URL AutoBlocklist |
|---------|------------------|-------------------|
| Purpose | Parental monitoring | URL blocking only |
| Blocklist sync | API sync | API sync + ML |
| Manual blocklist | Yes | No (API only) |
| Screen monitoring | Yes | No |
| App limits | Yes | No |
| User accounts | Yes | No |
| Admin dashboard | No | API endpoints |
| Complexity | Full app | Focused/minimal |

## Future Enhancements

1. **Statistics Screen** - Show blocked domains count, detection history
2. **Whitelist** - User-defined safe domains
3. **Blocklist Sync** - Optional API sync for known domains
4. **Notification** - Show when blocking occurs
5. **Settings** - Adjust threshold, delay, etc.

---

## Branch Info

- **Main branch:** `main`
- **Created from:** LawanPMO Premium `ref/automation_blocklist`
- **Purpose:** Tugas Akhir - Standalone URL blocking dengan ML
