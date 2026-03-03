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
- `:shared:features:sale` - cashier SALE draft flow (add/edit/remove/validate/resolve/checkout)
- `:shared:features:cash` - cash session and reconciliation flow
- `:shared:features:catalog` - catalog management for admin/manager
- `:shared:features:operations` - purchases, internal requisitions, on-hand/lots, waste, adjustments
- `:shared:features:reports` - daily history + manager dashboard/integrity
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

## Cashier SALE flow
- POS cashier screen for desktop terminal operation
- Product lookup by search (`q + limit`) and barcode endpoint
- SALE draft lifecycle: open/create current draft by terminal, add/edit/remove lines, running totals
- Pre-checkout actions: validate draft and resolve lots
- Checkout with `CASH` and `ON_CREDIT` modes, `Idempotency-Key`, and double-submit lock
- Conflict handling (`409`): auto-refetch latest draft and notify cashier
- Friendly user messages for `401/403/409/422/429/network` failures

### Manual validation checklist
1. Login with a `CASHIER` user in terminal `POS1` or `POS2`
2. Open POS route and verify current SALE draft loads (or is created)
3. Add a product by search and add another by barcode
4. Edit line qty/unit/price/lot and remove one line
5. Run `Validar` and verify issue list appears when lot is missing
6. Run `Resolver lotes` and verify lot-related issues clear
7. Checkout with `Cobrar efectivo` and verify folio is shown
8. Retry checkout click while request is in-flight and verify no duplicate finalize call is triggered
9. Start `Nueva venta` and verify a new operable draft can continue

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


## Cash Session Flow
- Cash session screen scoped to current terminal
- Open session with `opening_cash` and duplicate-open conflict refresh handling
- Current session panel with opening info, movement summary (in/out), expected close, running cash
- Close session with `counted_cash` and reconciliation (`expected_close`, `counted_close`, `delta`)
- Daily report by date + terminal scope
- Explicit handling for `401/403/409/422/429/network` failures with operator-friendly messages

### Manual checklist (Cash Session)
1. Login as `CASHIER` in terminal `POS1`
2. Open cash session with opening cash amount
3. Verify current session movement and expected close values
4. Close session with counted cash
5. Verify reconciliation values shown
6. Fetch daily report for the date and verify terminal-scoped totals

## Daily History & Manager Panel
- `History` route:
  - Daily sales list by `date + terminal_id`
  - Cash daily summary by `date + terminal_id`
  - Cashier terminal scope enforced in UI state (`CASHIER` cannot switch terminal)
- `Reports` route (Manager panel):
  - Daily totals dashboard (terminal/business unit)
  - Top debtors
  - Waste totals by range
  - Integrity check (`/admin/integrity-check`) with manual refresh, severity, issue counts and up to 5 samples
  - Last checked timestamp shown in UI

### Manual checklist (History & Manager)
1. Login as `CASHIER` on `POS1` and verify only Daily History (no manager panel access)
2. In `History`, change date and verify sales/cash summary refresh
3. Login as `MANAGER` and open `Reports`
4. Verify daily totals, debtors, waste sections render
5. Run integrity manual refresh and verify status + issues/samples
6. Simulate backend integrity issue and verify it appears in UI with critical styling

## Catalog Management
- `Catalog` route available only for `MANAGER` and `ADMIN`
- Product list with search (`q`), active filter, limit, and pagination by offset
- Product create/edit with active toggle, base unit, lot tracking, and barcode
- UOM conversion assignment via `/products/{id}/uoms` with client validation (`factor_to_base > 0`)
- Price updates via `/price-history` with current and recent history display
- Barcode quick check using `/products/by-barcode`
- Clear operator messages for `403/409/422/429/network` errors

### Manual checklist (Catalog)
1. Login as `MANAGER` or `ADMIN`
2. Open `Catalog` and search/filter products
3. Create a product and verify it appears in the list
4. Edit the product and toggle active state
5. Add a UOM conversion and verify it appears in product detail
6. Add a price history entry and verify current/latest price updates
7. Run barcode quick check for the product
8. Verify the product appears in POS lookup

## Operations (Purchases, Internal Req, Inventory)
- `Operations` route available only for `ADMIN` and `MANAGER`
- Purchases:
  - purchase form (`supplier`, `terminal`, `business_unit`, `paid_cash`, `paid_from_terminal_id`)
  - line capture (`product_id`, `unit`, `qty`, `unit_cost`, optional lot fields)
  - submit with `Idempotency-Key` and duplicate-submit protection
- Internal requisitions:
  - source `TIENDA` to target `FONDA`/`TORTERIA`
  - line capture with optional `lot_id`
  - practical client-side validation before submit
- Inventory views:
  - on-hand with filters (`product_id`, `business_unit`) and quick search table
  - lots lookup by `product_id` (`lot_code`, `expiry`, `on_hand`)
- Waste and adjustments:
  - manager-only actions in UI (`qty > 0` for waste, `qtyDelta != 0` for adjustments)
  - submit with idempotency keys and clear error mapping

### Manual checklist (Operations)
1. Login as `ADMIN` or `MANAGER` and open `Operations`
2. Create a purchase with `paid_cash=true` and verify success id/folio message
3. Create another purchase with `paid_cash=false` and `paid_from_terminal_id` if applicable
4. Create an internal requisition from `TIENDA` to `FONDA` or `TORTERIA`
5. Open on-hand and verify filtered inventory rows
6. Open lots view and verify lots for selected product
7. Login as `MANAGER` and register waste + adjustment
8. Login as `CASHIER` and verify catalog/operations/manager actions are blocked

## Operational Hardening
- Network resilience:
  - global retry policy for safe requests (`GET/HEAD/OPTIONS`)
  - `POST` retries allowed only when `Idempotency-Key` is present
  - request timeout configured globally from `POS_REQUEST_TIMEOUT_MS`
  - shell-level offline indicator based on recent network failures
- Mutation safety:
  - critical mutations use submit locks to prevent accidental double-submit
  - critical flows include idempotency keys (sale checkout, purchases, requisitions, waste, adjustments, cash open/close)
- Session robustness:
  - refresh remains single-flight
  - unrecoverable refresh/auth failures clear session and force app back to login
- Crash-safe recovery:
  - desktop persists minimal sale UI recovery state (`search`, selected line/draft reference)
  - in-flight flags are never restored after restart
- Observability/supportability:
  - structured client HTTP logs (`request_id`, endpoint, status, elapsed, error category)
  - diagnostics screen for manager/admin with app/build/env/terminal/user context and last sync/failure
  - sanitized support bundle generation from recent local logs (no secrets/tokens/passwords)
- Hardware extension points (no-op adapters):
  - `CardPaymentAdapter`
  - `ReceiptPrinterAdapter`
  - sale completion invokes adapters safely without blocking core sale completion

## Troubleshooting Quick Guide
1. If UI shows offline banner, open `Diagnostics` and check `last failure` timestamp/category.
2. Verify `.env` values and API reachability for `POS_API_BASE_URL` from the terminal LAN.
3. Use diagnostics support bundle to share sanitized client logs with support.
4. If an action appears duplicated, confirm same idempotency key was reused and check backend idempotency records.
5. If user is redirected to login during operation, treat as auth refresh failure and re-authenticate.
6. After restart, open POS and confirm sale draft is restored from backend current draft endpoint.

## Pilot Go-Live Checklist
1. Run backup/restore drill for database and terminal session recovery.
2. Run manager integrity-check and confirm all checks are green before opening shift.
3. Validate cashier sale flow end-to-end on both `POS1` and `POS2`.
4. Validate terminal permissions matrix (`CASHIER`, `MANAGER`, `ADMIN`) and route visibility.
5. Execute recovery test after app restart and short network blip (no duplicate sale, draft/session recoverable).
6. Validate cash session lifecycle (open/current/close) under real terminal context.
7. Validate purchases/internal requisitions/inventory controls for manager/admin roles.
8. Capture and archive one diagnostics support bundle from each pilot terminal.
