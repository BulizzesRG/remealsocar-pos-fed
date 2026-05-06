# Architect Reviewer Memory — fed/ (Frontend)

## Architecture Style
Kotlin Multiplatform modular monolith (desktop/JVM target only currently).
Layer order: ViewModel <- UseCase (via UseCases holder) <- Repository (interface) <- RepositoryImpl <- ApiClient <- Ktor HttpClient

## Module Layout
- `shared/core/` — AppResult<T>, AppError, UserRole, TerminalCode, BaseViewModel
- `shared/config/` — PosConfig
- `shared/network/` — HttpClientFactory, per-feature Dtos + ApiClient impls
- `shared/auth/` — SessionManager, AuthorizedApiExecutor, AuthRepository, UseCases
- `shared/features/<name>/` — domain models, repository interfaces, use cases, ViewModels, UI
- `app-desktop/` — Koin DI wiring (DesktopModules.kt), Main.kt, App.kt/AppStateController

## Established Patterns
- **3-layer DTO separation**: network DTOs (shared/network/) -> domain models (features/.../domain/ or inline) -> ViewModel state
- **Repository as interface in feature module**: interface lives in features/, impl in features/common/ implementing multiple repo interfaces
- **AuthorizedApiExecutor**: all API calls go through executor.execute { ... } for transparent token refresh
- **safeCall inline**: wraps Ktor response into AppResult<T>; already has NetworkHealthTracker integration
- **HttpClientFactory**: `ignoreUnknownKeys = true`, `explicitNulls = false`, `isLenient = true` — already set globally
- **UseCases holder class** (not individual use case classes): thin data class grouping repository references; passed to ViewModels
- **String IDs in domain models**: Long IDs from backend are .toString()-mapped in RepositoryImpl; domain/UI layer uses String throughout
- **No @SerialName in DTOs when JSON field already matches camelCase**: backend uses camelCase in JSON (kotlinx default)
- **@SerialName used when backend field is snake_case**: e.g. `@SerialName("payment_status")`

## Key Backend Contract Facts
- Backend reports: `WasteTotalsReportResponse` has ONLY `wasteDate`, `productId: Long`, `totalWasteQtyBase: BigDecimal` — NO productName, NO unit
- Backend integrity: `IntegrityIssueResponse.samples` is `List<IntegrityIssueSampleResponse>` (complex: entityType, entityId, details:JsonElement?) — NOT List<String>
- Backend `IntegrityCheckResponse` has NO `checkedAt` field — timestamp must be client-generated
- Backend `DailySalesTotalReportResponse` uses camelCase field names; does NOT have terminalId as String (it's Long)
- Backend reports URL paths: `/reports/sales/daily-totals`, `/reports/cash/reconciliation`, `/reports/customers/top-debtors`, `/reports/inventory/waste`, `/admin/integrity-check`

## Frontend DTO Mismatches Confirmed (reports module)
1. `WasteItemDto` has `productName` and `unit` — backend sends neither; will crash on deserialization
2. `IntegrityIssueDto.samples: List<String>` — backend sends `List<IntegrityIssueSampleResponse>`; will crash
3. `IntegrityCheckDto` has `checkedAt` — backend sends none (safe with default null)
4. `DailySalesTotalsDto` field names partially mismatched with backend camelCase fields

## Common Anti-Patterns Observed
- Frontend DTOs written to match a hypothetical/desired API rather than actual backend response shape
- Domain models including fields that backend doesn't provide (productName, unit in WasteItem)

## Links
- patterns.md — (not yet created; extend this file or create as needed)
