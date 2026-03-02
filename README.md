# POS FAB Frontend Skeleton (Kotlin Multiplatform)

## Architecture overview
Modules:
- `:app-desktop` - Compose Desktop entrypoint, Koin bootstrap, file session persistence
- `:shared:core` - core models, app result/error model, base viewmodel, logging facade
- `:shared:config` - typed config loader for env vars (`POS_ENV`, `POS_API_BASE_URL`, etc.)
- `:shared:network` - Ktor client foundation, typed auth DTOs and `AuthApiClient`
- `:shared:auth` - auth/session domain, repository, startup restore, logout, 401 refresh executor
- `:shared:features:login` - login state/viewmodel/screen
- `:shared:features:shell` - post-login shell, role/terminal-aware route guard, placeholder screens
- `:shared:ui` - shared Compose theme and base components

Layering enforced:
- UI (`features/*`, `app-desktop`) -> UseCase/ViewModel -> Repository (`shared:auth`) -> API client (`shared:network`)
- Backend DTOs are isolated to `shared:network`
- Centralized error model in `shared:core` (`Unauthorized`, `Forbidden`, `Conflict`, `Validation`, `RateLimit`, `Network`)

## Env setup (dev/prod)
Environment variables:
- `POS_ENV=dev|prod`
- `POS_API_BASE_URL`
- `POS_LOG_LEVEL`
- `POS_REQUEST_TIMEOUT_MS`
- `POS_ENABLE_VERBOSE_LOGS`

Templates:
- `app-desktop/env/.env.dev.example`
- `app-desktop/env/.env.prod.example`

Example run with dev env:
```bash
set -a
source app-desktop/env/.env.dev.example
set +a
./gradlew :app-desktop:run
```

## Run instructions
Run desktop app:
```bash
./gradlew :app-desktop:run
```

Run all tests:
```bash
./gradlew test
```

Useful focused tests:
```bash
./gradlew :shared:config:allTests :shared:auth:allTests :shared:features:shell:allTests
```

## Current implemented flow
- Login form (`username`, `password`, terminal `POS1/POS2/ADMIN`)
- Login API call and session save (access/refresh tokens + user + terminal)
- Startup restore (loads cached session and refreshes token)
- Single-flight 401 refresh support (`AuthorizedApiExecutor`)
- Logout clears persisted session
- Role/terminal-aware shell route guards:
  - `POS`, `Cash`, `Credit` for cashier/manager/admin
  - `Reports` for manager/admin
  - `Admin` only for admin role and `ADMIN` terminal

## Next implementation steps
1. POS draft screen and line-item state machine (draft create/edit/resume)
2. Checkout flow (totals, payment intent orchestration, idempotency key support)
3. Cash open/close workflows linked to cashier terminal context
4. Reports filtering and export placeholders to real endpoints
5. Backend endpoint contract hardening (exact auth DTO fields/paths)
6. Add Android app module if needed for handheld device support

## Notes
- This is a skeleton for LAN terminals (POS1, POS2, ADMIN), not full sales UI.
- Printer/payment integrations are intentionally left as extension points.
