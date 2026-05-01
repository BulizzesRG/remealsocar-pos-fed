# UI/UX Designer Memory — POS Fonda/Tortería (remealsocar/fed)

## Proyecto
- App de punto de venta (POS) de escritorio para fonda/tortería mexicana
- Stack: Compose Multiplatform 1.7.3 + Material Design 3 + Koin DI
- Target: JVM Desktop (no móvil)
- Roles: CASHIER, MANAGER, ADMIN — rutas controladas por RouteGuard

## Estado del sistema de diseño (auditado 2026-04-30)
- PosTheme.kt usa `darkColorScheme()` sin parametros — SIN paleta propia
- PosComponents.kt tiene solo 2 componentes: PosTextField y PosPrimaryButton
- Sin tokens de espaciado — valores hardcoded (4/6/8/10/12/16.dp) en todas las pantallas
- Sin tipografia custom — solo FontWeight inline y fontSize hardcoded (22.sp, 28.sp)
- Sin Scaffold en ninguna pantalla — sin TopAppBar, sin estructura estandar M3

## Archivos clave de UI
- Tema: `shared/ui/src/commonMain/kotlin/com/posfab/shared/ui/theme/PosTheme.kt`
- Componentes: `shared/ui/src/commonMain/kotlin/com/posfab/shared/ui/components/PosComponents.kt`
- Pantallas en: `shared/features/*/src/commonMain/kotlin/**/*Screen.kt`
- Entry point: `app-desktop/src/jvmMain/kotlin/com/posfab/app/App.kt`

## Paleta propuesta (ver UI-IMPROVEMENT-PLAN.md seccion 2.1)
- Primario: terracota/adobe — PosRed500 = #D94A2B
- Secundario: ambar/maiz — PosAmber200 = #FFDFA0
- Terciario: verde serrano — PosGreen200 = #A5D6A7
- Background dark: PosNeutral950 = #0F0D0C
- NO usar morados genericos de M3 default

## Tokens de espaciado propuestos (PosSpacing object)
- xs=4, sm=8, md=12, lg=16, xl=24, xxl=32
- PosLayout.navWidth=240.dp, touchTarget=48.dp, loginFormWidth=420.dp

## Convencion de idioma
- La UI debe ser 100% en ESPANOL para el usuario final
- El codigo (variables, funciones, clases) puede ser en ingles
- Problema actual: mezcla ES/EN en labels (opening_cash, product_id, lot_code expuestos al usuario)

## Plan de mejora
- Documento completo en `fed/UI-IMPROVEMENT-PLAN.md`
- 15 archivos a crear/modificar, 4 sprints estimados, 5-7 dias
- P1 (critico): PosTheme+Colors+Typography+Spacing+Components, LoginScreen, ShellScreen, CashierSaleScreen
- P2 (alto): CashSessionScreen, OperationsScreen, CatalogScreen
- P3 (medio): ManagerPanelScreen, DailyHistoryScreen, DiagnosticsScreen, App.kt

## Patrones a NO cambiar
- Arquitectura ViewModel + StateFlow — correcta
- RouteGuard para control de acceso por rol
- onPreviewKeyEvent con atajos F5/F9/Enter en CashierSaleScreen
- Patron SaleDraft con versioning
