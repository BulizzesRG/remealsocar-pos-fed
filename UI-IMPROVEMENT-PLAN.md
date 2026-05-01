# Plan de Mejora UI/UX — POS Fonda/Tortería Mexicana
## Compose Multiplatform 1.7.3 + Material Design 3

**Fecha de análisis:** 2026-04-30
**Analista:** UI/UX Designer Agent
**Alcance:** Código en `fed/shared/` y `fed/app-desktop/`

---

## 0. Contexto del proyecto y criterios de ejecución

### 0.1 Estado del proyecto

- **Fase:** construcción pre-MVP. El sistema **aún no esta en produccion** y todavia no ha entregado un MVP funcional. Esto significa que los cambios de este plan se aplican sobre codigo en evolucion, sin usuarios reales todavia, y sin necesidad de feature flags, rollback gradual ni convivencia entre UI vieja y nueva.
- **Idioma:** **100% espanol**, in-house, sin internacionalizacion. Toda la UI se hardcodea en espanol. No se introduce `stringResource` ni infraestructura de i18n. Si en el futuro hace falta otro idioma, sera un proyecto separado.
- **Despliegue:** uso interno (un solo negocio / una sola fonda). No hay clientes externos ni tickets de soporte que dependan de retrocompatibilidad visual.

### 0.2 Criterios para que este plan sea ejecutable

1. No romper contratos, RBAC, restricciones por terminal ni flujos operativos definidos por `bed` (backend fuente de verdad).
2. Priorizar primero senales visuales, jerarquia, legibilidad y prevencion de errores antes que embellecimiento cosmetico.
3. Mantener intactos los atajos de teclado y la velocidad operativa del cajero, salvo que el cambio mejore claramente ambos.
4. Evitar migraciones estructurales grandes sin retorno operativo claro. Si un cambio puede resolverse con componentes compartidos y ajuste de layout, eso tiene prioridad sobre rehacer arquitectura de pantallas.
5. Considerar los snippets de este documento como base de implementacion, no como copia literal garantizada. Antes de aplicar, verificar imports, disponibilidad real de componentes M3 en Compose Multiplatform `1.7.3`, y nombres exactos del estado/viewmodel existente.
6. Por estar en pre-MVP, **no se introducen feature flags, dual-UI ni rollback**. Cada fase deja la app compilable y operable end-to-end antes de pasar a la siguiente.

### 0.3 Prerrequisitos tecnicos bloqueantes (resolver antes de la Fase 1)

Estos puntos deben estar listos antes de tocar cualquier pantalla. Si no se hacen primero, los snippets del plan no compilan.

**0.3.1 Iconos extendidos de Material**

El plan usa muchos iconos fuera del set core de M3 (`QrCodeScanner`, `Search`, `Payments`, `CreditCard`, `Inventory2`, `Warehouse`, `BarChart`, `BugReport`, `AdminPanelSettings`, `History`, `ContentCopy`, `Logout`, `Delete`, `Person`, `Lock`, `CheckCircle`, `Add`, `Inventory`). Hoy ningun modulo declara la dependencia.

Usar el accessor idiomatico de Compose Multiplatform `compose.materialIconsExtended` en cada modulo consumidor que renderice esos iconos en sus snippets:

```kotlin
// build.gradle.kts del modulo consumidor (commonMain)
implementation(compose.materialIconsExtended)
```

Forma idiomatica de Compose Multiplatform — no usar aqui la coordenada Maven `org.jetbrains.compose.material:material-icons-extended:...`, y no hace falta agregar una entrada nueva en `libs.versions.toml` si el proyecto ya expone el accessor `compose.materialIconsExtended`.

**Modulos donde debe agregarse la dependencia (lista exhaustiva segun los snippets del plan):**

| Modulo | Iconos que renderiza | Fase donde se requiere |
|---|---|---|
| `shared/ui` | `Warning` (banner offline), iconos genericos via `ImageVector` en componentes reutilizables | Fase 0 |
| `shared/features/login` | `Person`, `Lock` | Fase 4 |
| `shared/features/shell` | `ShoppingCart`, `Payments`, `History`, `Inventory2`, `Warehouse`, `BarChart`, `BugReport`, `CreditCard`, `AdminPanelSettings`, `AutoMirrored.Filled.Logout` | Fase 5 |
| `shared/features/sale` | `QrCodeScanner`, `Search`, `Delete`, `CheckCircle`, `Inventory`, `Add`, `Payments`, `CreditCard` | Fases 6-7 |
| `shared/features/cash` | iconos opcionales en confirmacion de cierre | Fase 8 |
| `shared/features/operations` | iconos opcionales por tab; agregar si se introducen | Fase 9 |
| `shared/features/catalog` | sin iconos en los snippets actuales — agregar solo si se introducen | Fase 10 |
| `shared/features/reports` | `ContentCopy` (Diagnostics), iconos opcionales en cards de manager/historial | Fases 11-12 |

Recomendacion: agregar la dependencia **en todos los modulos listados durante la Fase 0**, aunque algunos aun no rendericen iconos. Es mas barato hacerlo de una sola vez que descubrir errores de compilacion fase por fase.

**0.3.2 Iconos con direccionalidad → `Icons.AutoMirrored`**

En M3 1.3.x, `ExitToApp`, `Logout`, `ArrowBack`, `List`, `Send` etc. estan deprecados en `Icons.Filled.*` a favor de `Icons.AutoMirrored.Filled.*`. Para los snippets de este plan aplica al menos:

| Antes | Despues |
|---|---|
| `Icons.Filled.ExitToApp` | `Icons.AutoMirrored.Filled.Logout` |
| `Icons.Filled.Logout` | `Icons.AutoMirrored.Filled.Logout` |

**0.3.3 Imports explicitos en `PosComponents.kt`**

Al implementar los 12 componentes en un solo archivo, los snippets requieren al menos:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
// Importar EXPLICITAMENTE el HorizontalDivider de M3 — el wildcard
// material3.* lo resuelve, pero ojo: si en otros archivos se mezcla con
// `import androidx.compose.material.Divider` (M2), el compilador puede
// elegir el deprecado silenciosamente. Mejor importar el simbolo directo.
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.posfab.shared.ui.theme.PosLayout
import com.posfab.shared.ui.theme.PosSpacing
import kotlin.math.abs
import kotlin.math.roundToLong
```

> Nota: el wildcard `import androidx.compose.material3.*` ya cubre `HorizontalDivider`. Se importa explicitamente arriba para dejar la intencion clara y evitar resoluciones ambiguas si en algun archivo coexiste con un `import androidx.compose.material.Divider` (M2 deprecado). Misma logica aplica a `Icon`, `Text`, `OutlinedTextField`: si hay duda de mezcla M2/M3, importarlos explicitamente desde `material3`.

**0.3.4 Tokens M3 1.3.x adicionales**

Para evitar trazas purpura residuales del default de M3 al usar componentes que dependen de `surfaceContainer*`, poblar explicitamente esos tokens en `PosDarkColorScheme` (ver seccion 2.1 actualizada).

---

## 1. Diagnóstico del Estado Actual

### 1.1 Sistema de Diseño — Hallazgos Críticos

**PosTheme.kt — El peor problema de todo el proyecto:**

```kotlin
// ESTADO ACTUAL — línea 7 de PosTheme.kt
private val PosColorScheme = darkColorScheme()
```

Este es `darkColorScheme()` sin ningún parámetro. Eso significa que la aplicación usa los colores por defecto de Material 3: morado/violeta genérico de Google. No tiene identidad visual alguna. En una POS de cocina mexicana donde el cajero trabaja bajo presión y con ruido, los colores no solo son estética — son señales de estado (sesión abierta/cerrada, errores, totales). Con defaults de M3 no hay esas señales diferenciadas.

**PosComponents.kt — Solo 2 componentes, ambos incompletos:**

```kotlin
// PosTextField: sin soporte para error, isError, supportingText, leadingIcon
// PosPrimaryButton: padding interno hardcodeado (top = 8.dp) que produce
//   resultados inconsistentes cuando se usa con modifier externo
```

Solo existen `PosTextField` y `PosPrimaryButton`. El resto de las 8 pantallas usan componentes M3 directamente sin encapsulamiento, lo que genera inconsistencias masivas (OutlinedTextField en unos lados, distintos tamaños, sin tokens de espaciado).

### 1.2 Problemas por Pantalla

**LoginScreen.kt:**
- La columna ocupa `fillMaxSize()` con `Arrangement.Center` — en una pantalla grande de desktop (1920×1080), el formulario queda flotando en el centro de un espacio enorme, sin límite de ancho máximo. Un cajero ve un campo de texto de 1800px de ancho. Ridículo y difícil de apuntar.
- El selector de terminal usa `RadioButton` puro sin label accesible. El texto del label está en un `Text` separado sin `Modifier.toggleable` asociado, por lo que la región clickeable es solo el círculo del radio (aprox. 20×20px). Fitts's Law: objetivo demasiado pequeño.
- El error de login no tiene color — `Text(text = error)` sin `color = MaterialTheme.colorScheme.error`. El usuario no puede distinguir visualmente un error de un mensaje informativo.
- "Iniciar Sesión" en el título pero "Sign In" en el botón — mezcla de idiomas en la misma pantalla.

**ShellScreen.kt — Navegación lateral:**
- `width(220.dp)` fijo para la barra lateral. En monitores de 4K o con escalado, 220dp es estrecho. En monitores pequeños puede ser demasiado. No hay `minWidth`/`maxWidth`.
- Todos los ítems de navegación son `Button` idénticos — ninguna indicación visual del ítem seleccionado actualmente. El usuario no sabe en qué sección está sin leer el contenido principal. Viola directamente la heurística de Nielsen "Visibility of system status".
- El botón "Logout" está en la misma columna que la navegación, separado solo por `padding(top = 12.dp)`. Un cajero que quiere ir a "POS" puede presionar "Logout" por accidente — están demasiado cerca.
- `Text("User: ${state.session.user.username}")` y `Text("Terminal: ${state.session.terminal}")` como texto plano. Sin formato, sin jerarquía visual. Crítico para un POS: el cajero necesita confirmar en qué terminal está trabajando.
- El indicador de offline (`Text("Offline mode. Last failure: ...")`) es texto rojo sin icono, sin tratamiento visual destacado. Un estado crítico (sin conexión) necesita un tratamiento visual urgente, no texto plano.

**CashierSaleScreen.kt — Pantalla más crítica del sistema:**
- Dos columnas con `weight(1f)` y `weight(1.2f)` — proporciones arbitrarias sin justificación funcional.
- "Resultados" de búsqueda de productos en un `Card` sin `height` definida. Si hay 20 resultados y muchas líneas en el draft, los componentes compiten por espacio vertical sin control.
- Botones de acción de línea (`+ Qty`, `- Qty`, `Eliminar`) son texto plano mínimo. Para un cajero que trabaja rápido, estos botones necesitan ser grandes y tener iconos. El texto "Eliminar" en un botón rojo sería una convención POS estándar.
- Los 4 campos de edición de línea (`Qty`, `Unidad`, `Precio`, `Lote`) están expuestos permanentemente, aunque la mayoría de las transacciones no requieren edición de precio ni lote. Esto añade carga cognitiva constante (Hick's Law).
- El TOTAL (`28.sp`, `FontWeight.Bold`) es el dato más importante — bien que sea prominente — pero está dentro de un `Card` genérico. Necesita más protagonismo visual: fondo de color, tamaño mayor.
- Los botones de acción final (`Validar`, `Resolver lotes`, `Cobrar efectivo`, `Cobrar credito`, `Nueva venta`) están en un `Row` plano sin distinción visual entre acciones primarias, secundarias y destructivas. "Cobrar efectivo" y "Cobrar credito" son las acciones más importantes del flujo — deberían ser botones grandes con iconos diferenciados por color.
- `F5` y `F9` como atajos de teclado no documentados en la UI. Un cajero nuevo no los descubrirá nunca.
- `Text("Draft: ${state.draft?.id ?: "-"} v${state.draft?.version ?: 0}")` — datos técnicos internos (draft ID, version) expuestos en la UI de caja. Esto no tiene valor para el cajero y añade ruido visual.

**CashSessionScreen.kt:**
- Los labels de los campos usan snake_case del API (`opening_cash`, `counted_cash`, `expected_close`, `movement_in`) directamente expuestos al usuario. Esto es texto de desarrollador, no UI de producto.
- La pantalla mezcla dos flujos completamente diferentes: apertura/cierre de caja Y reporte diario. Deberían ser tabs o secciones claramente separadas.
- `Text("Efectivo en caja (running): ${...}")` — cálculo inline en el Composable. Problema secundario pero síntoma del estado del código.

**CatalogScreen.kt:**
- Cards anidadas — `Card` conteniendo `LazyColumn` de `Card`s individuales. Doble elevación visual que se acumula y produce un aspecto "acartado" y pesado.
- El campo `Limit` (paginación) expuesto como input de texto libre. Un administrador que ponga `0` o `abc` puede causar errores. Debería ser un selector o tener validación visible.
- Labels de campos en inglés técnico (`factor_to_base`, `lot_code`) mezclados con labels en español. Inconsistencia de idioma en toda la pantalla.
- La sección de "Validar barcode" está enterrada al final del panel derecho. Si es una función de validación/debugging, tiene sentido; si se usa frecuentemente, debe estar más visible.

**OperationsScreen.kt:**
- Los tabs usan `Button.entries.forEach { tab -> Button(onClick = ...) { Text(tab.name) } }`. `tab.name` devuelve el nombre del enum en mayúsculas: `PURCHASES`, `INTERNAL_REQ`, `ON_HAND`. Texto técnico en mayúsculas como etiquetas de tab.
- Labels en inglés/snake_case: `product_id`, `business_unit`, `qty_delta`, `reason_code`, `paid_cash`, `lot_id`. La persona que registra una compra en la fonda necesita ver "Producto", "Cantidad", "Unidad", no IDs de campos de API.
- El checkbox `paid_cash` con texto "paid_cash" — un cajero no entiende qué significa esto.
- No hay confirmación antes de `submitPurchase()`. Una compra es una transacción financiera — necesita un diálogo de confirmación.

**ManagerPanelScreen.kt:**
- `Column(verticalArrangement = Arrangement.spacedBy(8.dp))` sin `fillMaxSize()` ni scroll. Si hay muchos deudores o issues de integridad, el contenido se corta.
- El `LazyColumn` del Integrity Check dentro de un `Column` dentro de un `Card` dentro de otro `Column` sin `fillMaxSize` — comportamiento de scroll indefinido.
- Los totales diarios se muestran como texto plano: `"${it.terminalId} ${it.businessUnit ?: "-"} count=${it.salesCount} total=${it.total}"`. Un manager necesita ver esto en una tabla o en tarjetas con formato monetario.

**DiagnosticsScreen.kt:**
- Funciona bien para su propósito. El problema menor es que el `support bundle text` se renderiza con `Text()` en lugar de un campo copiable o un formato monoespaciado.

### 1.3 Problemas Transversales

| Problema | Impacto | Frecuencia en código |
|---|---|---|
| Sin tokens de espaciado — valores hardcoded (4.dp, 6.dp, 8.dp, 10.dp, 12.dp, 16.dp) | Inconsistencia visual global | Todas las pantallas |
| Mezcla de idiomas ES/EN en labels | Confusión para usuarios no técnicos | CashSession, Operations, Catalog, DailyHistory |
| `fontWeight = FontWeight.Bold` inline sin sistema tipográfico | Jerarquía inconsistente | Todas las pantallas |
| `fontSize = 22.sp`, `28.sp` hardcoded fuera del sistema tipográfico | Escala tipográfica rota | CashierSaleScreen |
| Sin una estructura compartida de layout/navegación — cada pantalla resuelve header, padding y jerarquía por su cuenta | Inconsistencia visual y más código repetido | Todas las pantallas |
| Sin estados de carga diferenciados — solo `Text("Cargando...")` | Experiencia de espera pobre | CashierSale, CashSession, Catalog, Operations, Reports |
| Sin feedback en acciones críticas — no hay `Snackbar`, no hay diálogos de confirmación | Errores silenciosos o irreversibles | Operations, Catalog |
| `CircularProgressIndicator()` en el loading inicial de App.kt — sin contexto | Estado de arranque poco informativo | App.kt |

---

## 2. Sistema de Diseño Propuesto

### 2.1 Paleta de Colores M3 — Identidad Visual POS Fonda Mexicana

La paleta se basa en terracota/adobe (color dominante de la cocina mexicana tradicional) + crema + verde serrano. No usa morados genéricos de SaaS ni azules corporativos.

```kotlin
// shared/ui/src/commonMain/kotlin/com/posfab/shared/ui/theme/PosColors.kt
package com.posfab.shared.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Colores de marca ---
// Terracota (primario): color de adobe, barro, cocina tradicional
val PosRed900        = Color(0xFF7B1A0F)  // dark
val PosRed700        = Color(0xFFB5290E)  // primary
val PosRed500        = Color(0xFFD94A2B)  // medium
val PosRed100        = Color(0xFFFFDAD6)  // container light

// Crema/ámbar (secundario): tortilla, maíz
val PosAmber800      = Color(0xFF5C3D00)
val PosAmber600      = Color(0xFF8B5E00)
val PosAmber200      = Color(0xFFFFDFA0)
val PosAmber50       = Color(0xFFFFF8E1)

// Verde (terciario): epazote, serrano
val PosGreen800      = Color(0xFF1B4D1F)
val PosGreen600      = Color(0xFF2E7D32)
val PosGreen200      = Color(0xFFA5D6A7)

// Neutros oscuros para dark mode de POS
val PosNeutral950    = Color(0xFF0F0D0C)  // background más oscuro
val PosNeutral900    = Color(0xFF1A1714)  // surface principal
val PosNeutral800    = Color(0xFF2A2522)  // surface container
val PosNeutral700    = Color(0xFF3D3733)  // outline subtle
val PosNeutral300    = Color(0xFFB0A89E)  // texto secundario
val PosNeutral100    = Color(0xFFF0EBE6)  // texto primario sobre oscuro

// Semánticos
val PosError         = Color(0xFFFF6B6B)
val PosSuccess       = Color(0xFF4CAF50)
val PosWarning       = Color(0xFFFFC107)
val PosOffline       = Color(0xFFFF5722)

// --- Esquema oscuro para POS (modo principal de caja) ---
// El esquema oscuro se propone como default inicial para flujos de caja,
// sujeto a validación visual y operativa en terminales reales.
val PosDarkColorScheme = darkColorScheme(
    // Primario: terracota — botones principales, elementos activos
    primary          = PosRed500,
    onPrimary        = Color.White,
    primaryContainer = PosRed900,
    onPrimaryContainer = PosRed100,

    // Secundario: ámbar/dorado — elementos de acento, info
    secondary        = PosAmber200,
    onSecondary      = PosAmber800,
    secondaryContainer = PosAmber800,
    onSecondaryContainer = PosAmber200,

    // Terciario: verde — estados de éxito, sesión abierta
    tertiary         = PosGreen200,
    onTertiary       = PosGreen800,
    tertiaryContainer = PosGreen800,
    onTertiaryContainer = PosGreen200,

    // Error
    error            = PosError,
    onError          = Color(0xFF690005),
    errorContainer   = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Surfaces — escalonadas para jerarquía visual
    background       = PosNeutral950,
    onBackground     = PosNeutral100,
    surface          = PosNeutral900,
    onSurface        = PosNeutral100,
    surfaceVariant   = PosNeutral800,
    onSurfaceVariant = PosNeutral300,

    // Tokens M3 1.3.x — declararlos explicitamente para no heredar
    // tonos purpura del default de Material 3.
    surfaceDim              = PosNeutral950,
    surfaceBright           = Color(0xFF3A3531),
    surfaceContainerLowest  = Color(0xFF0A0807),
    surfaceContainerLow     = PosNeutral900,
    surfaceContainer        = Color(0xFF221E1B),
    surfaceContainerHigh    = PosNeutral800,
    surfaceContainerHighest = Color(0xFF35302C),

    // Outline
    outline          = PosNeutral700,
    outlineVariant   = Color(0xFF524A46),
)

// --- Esquema claro (opcional, para Reports/Admin en oficina) ---
val PosLightColorScheme = lightColorScheme(
    primary          = PosRed700,
    onPrimary        = Color.White,
    primaryContainer = PosRed100,
    onPrimaryContainer = PosRed900,
    secondary        = PosAmber600,
    onSecondary      = Color.White,
    secondaryContainer = PosAmber50,
    onSecondaryContainer = PosAmber800,
    tertiary         = PosGreen600,
    onTertiary       = Color.White,
    background       = Color(0xFFFFF8F5),
    onBackground     = Color(0xFF1A0A07),
    surface          = Color.White,
    onSurface        = Color(0xFF1A0A07),
    surfaceVariant   = Color(0xFFF5EDEA),
    onSurfaceVariant = Color(0xFF534340),
)
```

### 2.2 Tipografía — JetBrains Mono + System Default

Para una POS de escritorio en Compose Multiplatform/Desktop, conviene usar una tipografía monoespaciada para montos y códigos, más la fuente del sistema para texto general. Durante la primera implementación puede usarse `FontFamily.Monospace`; si se quiere consistencia visual entre terminales, JetBrains Mono debe empaquetarse explícitamente como recurso de la app.

```kotlin
// shared/ui/src/commonMain/kotlin/com/posfab/shared/ui/theme/PosTypography.kt
package com.posfab.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// FontFamily.Monospace no garantiza JetBrains Mono; usa la fuente
// monoespaciada disponible en el sistema operativo.
// Si se busca consistencia visual entre terminales, incluir JetBrains Mono
// como resource en app-desktop y declararla explícitamente.

val PosTypography = Typography(
    // Display: total de venta — el número más importante
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),

    // Headlines: títulos de sección, totales secundarios
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),

    // Title: encabezados de card, nombre de sección activa
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // Body: texto de contenido
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),

    // Label: botones, chips, tabs
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

### 2.3 Tokens de Espaciado

```kotlin
// shared/ui/src/commonMain/kotlin/com/posfab/shared/ui/theme/PosSpacing.kt
package com.posfab.shared.ui.theme

import androidx.compose.ui.unit.dp

object PosSpacing {
    val xxs  = 2.dp
    val xs   = 4.dp
    val sm   = 8.dp
    val md   = 12.dp
    val lg   = 16.dp
    val xl   = 24.dp
    val xxl  = 32.dp
    val xxxl = 48.dp
}

// Espaciado semántico para POS
object PosLayout {
    val navWidth        = 240.dp   // barra lateral fija
    val contentPadding  = PosSpacing.lg
    val cardPadding     = PosSpacing.md
    val sectionSpacing  = PosSpacing.lg
    val fieldSpacing    = PosSpacing.sm
    val actionBarHeight = 64.dp   // barra de acciones fija en pantalla de caja
    val touchTarget     = 48.dp   // mínimo táctil Fitts's Law (WCAG 2.5.5)
    val loginFormWidth  = 420.dp  // ancho máximo del formulario de login
}
```

### 2.4 Shapes

```kotlin
// En PosTheme.kt — agregar Shapes personalizados
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

val PosShapes = Shapes(
    // Cards y contenedores: bordes suaves pero no pill-shape genérico
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
```

### 2.5 PosTheme Actualizado

```kotlin
// shared/ui/src/commonMain/kotlin/com/posfab/shared/ui/theme/PosTheme.kt
package com.posfab.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    // Pre-MVP: tema unico, dark, fijo. No se introduce parametro `darkMode`
    // hasta que exista un caso real que lo requiera (p. ej. impresion de
    // tickets o pantallas de Reports en light) — esto se trata fuera de
    // este plan, ver seccion 11.
    MaterialTheme(
        colorScheme = PosDarkColorScheme,
        typography  = PosTypography,
        shapes      = PosShapes,
        content     = content,
    )
}
```

**Call-site existente — no requiere cambios.** El `App.kt` actual ya invoca `PosTheme { ... }` (sin parametros). Como la nueva firma tampoco recibe parametros, **no hay nada que actualizar en `app-desktop`**. Lo que cambia es solo el contenido visual al activarse `PosDarkColorScheme`, `PosTypography` y `PosShapes`.

---

## 3. Componentes Compartidos a Crear o Refactorizar

### 3.1 PosComponents.kt — Refactorización Completa

El archivo actual tiene 2 componentes básicos. Necesita estos 12:

**Prerequisito de compilación para iconos:**
- Si se usan iconos fuera del set base de Material, agregar `material-icons-extended` en cada módulo consumidor que renderice esos iconos.
- En este plan, eso potencialmente aplica a `shared/features/sale`, `shared/features/shell` y `shared/features/reports` por los snippets propuestos.
- Si no se agrega esa dependencia, reemplazar esos iconos por alternativas disponibles en core o por texto sin icono.

```kotlin
// shared/ui/src/commonMain/kotlin/com/posfab/shared/ui/components/PosComponents.kt

// --- 1. PosTextField (refactorizar) ---
@Composable
fun PosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        supportingText = if (errorMessage != null && isError) {
            { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
        } else null,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
    )
}

// --- 2. PosPrimaryButton (refactorizar) ---
// El padding interno no debe estar en el componente — pertenece al layout padre
@Composable
fun PosPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(PosLayout.touchTarget),
        enabled = enabled && !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(PosSpacing.xs))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// --- 3. PosSecondaryButton (nuevo) ---
@Composable
fun PosSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(PosLayout.touchTarget),
        enabled = enabled,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(PosSpacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// --- 4. PosDestructiveButton (nuevo) ---
@Composable
fun PosDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(PosLayout.touchTarget),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(PosSpacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// --- 5. PosSectionCard (nuevo) ---
// Reemplaza el patrón Card { Column { ... } } repetido en todas las pantallas
@Composable
fun PosSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PosLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            if (title != null || actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                    if (actions != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                            content = actions,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = PosSpacing.xs))
            }
            content()
        }
    }
}

// --- 6. PosMoneyText (nuevo) ---
// Para todos los montos monetarios — usa tipografía monoespaciada
@Composable
fun PosMoneyText(
    amount: Double,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    prefix: String = "$",
) {
    Text(
        text = "$prefix${formatMoney(amount)}",
        style = style.copy(fontFamily = FontFamily.Monospace),
        color = color,
    )
}

// --- 7. PosTotalDisplay (nuevo) ---
// El total de la venta — el componente más visto en la pantalla de caja
@Composable
fun PosTotalDisplay(
    subtotal: Double,
    tax: Double,
    total: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PosLayout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Subtotal", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                PosMoneyText(subtotal, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            // Fila de IVA solo cuando aplica — la fonda puede operar
            // en regimen sin IVA y mostrarlo siempre seria ruido.
            if (tax > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("IVA", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    PosMoneyText(tax, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "TOTAL",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Black,
                )
                PosMoneyText(
                    total,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

// --- 8. PosStatusBadge (nuevo) ---
// Para estados de sesión de caja (OPEN, CLOSED, etc.)
@Composable
fun PosStatusBadge(
    status: String,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) = when (status.uppercase()) {
        "OPEN"   -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "CLOSED" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        "NONE"   -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else     -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        color = bgColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = PosSpacing.sm, vertical = PosSpacing.xxs),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

// --- 9. PosOfflineBanner (nuevo) ---
@Composable
fun PosOfflineBanner(
    lastFailureAt: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PosSpacing.md, vertical = PosSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Sin conexion",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    "Sin conexion al servidor",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (lastFailureAt != null) {
                    Text(
                        "Ultimo fallo: $lastFailureAt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

// --- 10. PosLoadingOverlay (nuevo) ---
@Composable
fun PosLoadingOverlay(
    isLoading: Boolean,
    message: String = "Procesando...",
) {
    if (!isLoading) return
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(PosSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// --- 11. PosNoticeRow (nuevo) ---
// Reemplaza el patrón state.notice?.let { Text(it) } / state.errorMessage?.let { Text(it) }
@Composable
fun PosNoticeRow(
    notice: String?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
        notice?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        errorMessage?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// --- 12. PosConfirmDialog (nuevo) ---
@Composable
fun PosConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirmar",
    cancelLabel: String = "Cancelar",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ) else ButtonDefaults.buttonColors(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        },
    )
}

// Helper privado de formato monetario (mover desde CashierSaleScreen.kt)
private fun formatMoney(value: Double): String {
    val cents = (value * 100.0).roundToLong()
    val whole = kotlin.math.abs(cents) / 100
    val fraction = kotlin.math.abs(cents) % 100
    val suffix = if (fraction < 10) "0$fraction" else "$fraction"
    val sign = if (cents < 0) "-" else ""
    return "$sign$whole.$suffix"
}
```

---

## 4. Plan de Mejora por Pantalla — Priorizado

### P1 — Crítico (Bloquea operación o causa errores de usuario)

---

#### P1-1: PosTheme — Activar paleta de colores real

**Archivo:** `shared/ui/src/commonMain/kotlin/com/posfab/shared/ui/theme/PosTheme.kt`

**Cambio:**
```kotlin
// ANTES
private val PosColorScheme = darkColorScheme()

// DESPUÉS — reemplazar con:
// 1. Crear PosColors.kt (ver sección 2.1)
// 2. Crear PosTypography.kt (ver sección 2.2)
// 3. Crear PosSpacing.kt (ver sección 2.3)
// 4. Actualizar PosTheme.kt (ver sección 2.5)
```

**Impacto:** Toda la aplicación. Sin esto, los demás cambios visuales tienen poco efecto.
**Esfuerzo:** 2 horas.

---

#### P1-2: LoginScreen — Formulario usable en desktop

**Archivo:** `shared/features/login/src/commonMain/kotlin/com/posfab/shared/features/login/LoginScreen.kt`

**Problemas a resolver:**
- Formulario sin `maxWidth` — se estira al ancho completo de pantalla
- Error sin color de error
- Mezcla de idiomas (Sign In / Iniciar Sesión)
- RadioButton sin región clickeable ampliada
- Sin feedback visual de carga en el botón

**Código propuesto:**
```kotlin
@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(PosLayout.loginFormWidth)  // max 420dp, centrado
                .padding(PosLayout.contentPadding),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
        ) {
            // Logo o nombre del negocio (placeholder)
            Text(
                text = "POS Fonda",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Iniciar sesion",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(PosSpacing.sm))

            PosTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = "Usuario",
                enabled = !state.isLoading,
                leadingIcon = { Icon(Icons.Filled.Person, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            PosTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contrasena",
                enabled = !state.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )

            // Terminal selector — usar SegmentedButton en lugar de RadioButton
            Text("Terminal", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TerminalCode.entries.forEachIndexed { index, terminal ->
                    SegmentedButton(
                        selected = state.terminal == terminal,
                        onClick = { viewModel.onTerminalChange(terminal) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TerminalCode.entries.size,
                        ),
                        enabled = !state.isLoading,
                    ) {
                        Text(terminal.name)
                    }
                }
            }

            PosPrimaryButton(
                text = "Iniciar sesion",
                onClick = viewModel::submit,
                enabled = !state.isLoading,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            // Error con color semántico correcto
            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
```

**Impacto:** Primera impresión de la app. Error de error sin color puede llevar a cajeros a reintentar sin saber qué fallé.
**Esfuerzo:** 1.5-2.5 horas.

---

#### P1-3: ShellScreen — Navegación con estado activo visible

**Archivo:** `shared/features/shell/src/commonMain/kotlin/com/posfab/shared/features/shell/ShellScreen.kt`

**Problemas a resolver:**
- Sin indicador de ruta activa
- Logout demasiado cerca de la navegación
- Sin formato para usuario/terminal
- Banner offline sin tratamiento visual

**Código propuesto:**
```kotlin
@Composable
fun ShellScreen(
    viewModel: ShellViewModel,
    onLoggedOut: () -> Unit,
    posContent: @Composable () -> Unit,
    cashContent: @Composable () -> Unit,
    historyContent: @Composable () -> Unit,
    catalogContent: @Composable () -> Unit,
    operationsContent: @Composable () -> Unit,
    reportsContent: @Composable () -> Unit,
    diagnosticsContent: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val network by NetworkHealthTracker.state.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // --- Barra lateral de navegacion ---
        Surface(
            modifier = Modifier.width(PosLayout.navWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(PosSpacing.md),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Sección superior: info de sesion + navegacion
                Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    // Info de sesion
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(PosSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
                        ) {
                            Text(
                                state.session.user.username,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Terminal: ${state.session.terminal}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(PosSpacing.sm))

                    // Banner offline — solo visible cuando es necesario
                    if (network.isOffline) {
                        PosOfflineBanner(
                            lastFailureAt = network.lastFailureAt?.toString(),
                        )
                        Spacer(Modifier.height(PosSpacing.sm))
                    }

                    // Items de navegacion
                    state.allowedRoutes.forEach { route ->
                        val isSelected = route == state.selectedRoute
                        NavigationDrawerItem(
                            label = { Text(routeDisplayName(route)) },
                            selected = isSelected,
                            onClick = { viewModel.select(route) },
                            modifier = Modifier.fillMaxWidth(),
                            // Agregar icono por ruta (ver mapa de iconos abajo)
                            icon = { Icon(routeIcon(route), contentDescription = null) },
                        )
                    }
                }

                // Sección inferior: Logout — separado visualmente
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = PosSpacing.sm))
                    NavigationDrawerItem(
                        label = { Text("Cerrar sesion") },
                        selected = false,
                        onClick = { viewModel.logout(onLoggedOut) },
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.error,
                            unselectedTextColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
            }
        }

        // --- Contenido principal ---
        Column(modifier = Modifier.fillMaxSize()) {
            when (state.selectedRoute) {
                ShellRoute.POS         -> posContent()
                ShellRoute.CASH        -> cashContent()
                ShellRoute.HISTORY     -> historyContent()
                ShellRoute.CATALOG     -> catalogContent()
                ShellRoute.OPERATIONS  -> operationsContent()
                ShellRoute.REPORTS     -> reportsContent()
                ShellRoute.DIAGNOSTICS -> diagnosticsContent()
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${routeDisplayName(state.selectedRoute)} — Proximamente",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// Etiquetas visibles en español para la navegación
private fun routeDisplayName(route: ShellRoute): String = when (route) {
    ShellRoute.POS         -> "POS"
    ShellRoute.CASH        -> "Caja"
    ShellRoute.HISTORY     -> "Historial"
    ShellRoute.CATALOG     -> "Catalogo"
    ShellRoute.OPERATIONS  -> "Operaciones"
    ShellRoute.REPORTS     -> "Reportes"
    ShellRoute.DIAGNOSTICS -> "Diagnosticos"
    ShellRoute.CREDIT      -> "Credito"
    ShellRoute.ADMIN       -> "Admin"
}

// Mapa de iconos por ruta — agregar en el mismo archivo o en ShellRoute.kt
private fun routeIcon(route: ShellRoute): ImageVector = when (route) {
    ShellRoute.POS         -> Icons.Filled.ShoppingCart
    ShellRoute.CASH        -> Icons.Filled.Payments
    ShellRoute.HISTORY     -> Icons.Filled.History
    ShellRoute.CATALOG     -> Icons.Filled.Inventory2
    ShellRoute.OPERATIONS  -> Icons.Filled.Warehouse
    ShellRoute.REPORTS     -> Icons.Filled.BarChart
    ShellRoute.DIAGNOSTICS -> Icons.Filled.BugReport
    ShellRoute.CREDIT      -> Icons.Filled.CreditCard
    ShellRoute.ADMIN       -> Icons.Filled.AdminPanelSettings
}
```

**Impacto:** Todos los usuarios, cada sesión. El botón de logout cerca de POS puede causar cierres de sesión accidentales en horas pico.
**Esfuerzo:** 2-3 horas.

---

#### P1-4: CashierSaleScreen — Rediseño del flujo principal de caja

**Archivo:** `shared/features/sale/src/commonMain/kotlin/com/posfab/shared/features/sale/ui/CashierSaleScreen.kt`

**Problemas a resolver:**
- Datos técnicos (draft ID/version) en pantalla de cajero
- Botones de acción sin diferenciación visual
- Total sin suficiente protagonismo
- Campos de edición permanentemente visibles
- Atajos de teclado sin documentación
- Row de botones de cobro sin separación semántica

**Cambios de archivo a aplicar en el mismo PR:**
- Eliminar la funcion privada `formatMoney(...)` actual de `CashierSaleScreen.kt`. Una vez exista en `PosComponents.kt` (via `PosMoneyText`), no debe quedar duplicada en la pantalla.
- Eliminar el `Text("Draft: ${state.draft?.id ?: "-"} v${state.draft?.version ?: 0}")` de la cabecera. Si se necesita esa info para soporte, exponerla solo en `DiagnosticsScreen`.
- **Nunca renderizar `line.qty` directamente.** `qty: Double` produce `"1.0 EA"`, `"2.0 KG"`, etc. — texto basura en la UI de caja. Usar siempre el helper `formatDisplayQty(line.qty, line.unit)` declarado al final del snippet, que normaliza enteros (`1.0 -> "1"`) y conserva decimales solo cuando son significativos. Si se prefiere centralizar, mover el helper a `PosComponents.kt` como `internal fun formatDisplayQty(...)` o exponer un `PosQuantityText(qty, unit)` siguiendo el patron de `PosMoneyText`.
- El snippet principal ya incorpora dos guards inline en `onPreviewKeyEvent`:
  - `if (state.confirmDialog != null) return@onPreviewKeyEvent false` — para no disparar atajos cuando un `AlertDialog` esta visible.
  - `if (state.isCheckoutInFlight) return@onPreviewKeyEvent false` — para no disparar acciones no idempotentes durante un cobro en vuelo.
- Si `CashierSaleState` **aun no tiene un campo `confirmDialog`**, agregarlo en el ViewModel/State cuando se introduzca el primer `PosConfirmDialog` (por ejemplo, en `Nueva venta` con lineas pendientes). Mientras no exista, el compilador marcara el guard — esto es deliberado para forzar la decision de modelado en el mismo PR.

**Cambios clave:**

```kotlin
@Composable
fun CashierSaleScreen(viewModel: CashierSaleViewModel) {
    val state by viewModel.state.collectAsState()

    if (state.isInitializing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
                CircularProgressIndicator()
                Text("Cargando venta...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize()
                .padding(PosLayout.contentPadding)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    // Si hay un dialogo de confirmacion abierto, no interceptar
                    // atajos globales — F5/F9 quedan a disposicion del dialogo.
                    if (state.confirmDialog != null) return@onPreviewKeyEvent false
                    // Si hay un cobro en vuelo, F5/F9 podrian disparar acciones
                    // no idempotentes. Bloqueamos hasta que termine el cobro.
                    if (state.isCheckoutInFlight) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter -> {
                            if (state.barcodeInput.isNotBlank()) viewModel.addByBarcode()
                            else viewModel.searchProducts()
                            true
                        }
                        Key.F5 -> { viewModel.validateDraft(); true }
                        Key.F9 -> { viewModel.checkoutCash(); true }
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.lg),
        ) {
            // --- Columna izquierda: búsqueda de productos ---
            Column(
                modifier = Modifier.width(340.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                Text("Buscar producto",
                    style = MaterialTheme.typography.titleMedium)

                PosTextField(
                    value = state.barcodeInput,
                    onValueChange = viewModel::onBarcodeChange,
                    label = "Codigo de barras (Enter)",
                    enabled = !state.isBusy,
                    leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) },
                )

                PosTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    label = "Buscar por nombre",
                    enabled = !state.isBusy,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                )

                PosSectionCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    title = if (state.searchResults.isEmpty()) "Resultados"
                            else "Resultados (${state.searchResults.size})",
                ) {
                    if (state.searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = PosSpacing.lg),
                            contentAlignment = Alignment.Center) {
                            Text("Sin resultados",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                        ) {
                            items(state.searchResults) { product ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surface,
                                    onClick = { if (!state.isBusy) viewModel.addProduct(product) },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(PosSpacing.sm),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(product.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis)
                                            Text(
                                                listOfNotNull(product.sku, product.barcode)
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        PosMoneyText(
                                            product.unitPrice,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Atajos de teclado — documentados en la UI
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(PosSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        Text("Enter: agregar", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("F5: validar", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("F9: cobrar", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // --- Columna derecha: lineas del draft + acciones de cobro ---
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                // Encabezado de la venta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Venta en caja",
                        style = MaterialTheme.typography.titleLarge)
                    // Acciones de línea seleccionada
                    // Guard combinado: la linea no puede modificarse durante
                    // un cobro en vuelo porque chocaria con el versioning del draft.
                    val canEditLine = !state.isBusy && !state.isCheckoutInFlight
                            && state.selectedLineId != null
                    Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                        PosSecondaryButton(
                            text = "+",
                            onClick = viewModel::incrementSelectedLineQty,
                            enabled = canEditLine,
                            modifier = Modifier.width(56.dp),
                        )
                        PosSecondaryButton(
                            text = "-",
                            onClick = viewModel::decrementSelectedLineQty,
                            enabled = canEditLine,
                            modifier = Modifier.width(56.dp),
                        )
                        PosDestructiveButton(
                            text = "Eliminar",
                            onClick = viewModel::removeSelectedLine,
                            enabled = canEditLine,
                            icon = Icons.Filled.Delete,
                        )
                    }
                }

                // Lista de líneas
                PosSectionCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    if (state.draft?.lines.isNullOrEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = PosSpacing.xl),
                            contentAlignment = Alignment.Center) {
                            Text("Venta vacia — busca o escanea un producto",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                        ) {
                            items(state.draft!!.lines) { line ->
                                val isSelected = line.id == state.selectedLineId
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    onClick = { viewModel.selectLine(line.id) },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = PosSpacing.md,
                                                vertical = PosSpacing.sm),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                line.productName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (line.lotTracked) {
                                                Text(
                                                    "Lote: ${line.lotId ?: "pendiente"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (line.lotId == null)
                                                        MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(
                                            formatDisplayQty(line.qty, line.unit),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = PosSpacing.sm),
                                        )
                                        PosMoneyText(
                                            line.lineTotal,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Editor de línea — solo visible si hay línea seleccionada
                // Antes era permanentemente visible, ahora es contextual
                if (state.selectedLineId != null) {
                    PosSectionCard(
                        title = "Editar linea seleccionada",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            PosTextField(value = state.editQtyInput,
                                onValueChange = viewModel::onEditQtyChange,
                                label = "Cantidad",
                                modifier = Modifier.width(100.dp))
                            PosTextField(value = state.editUnitInput,
                                onValueChange = viewModel::onEditUnitChange,
                                label = "Unidad",
                                modifier = Modifier.width(110.dp))
                            PosTextField(value = state.editPriceInput,
                                onValueChange = viewModel::onEditPriceChange,
                                label = "Precio",
                                modifier = Modifier.width(120.dp))
                            PosTextField(value = state.editLotInput,
                                onValueChange = viewModel::onEditLotChange,
                                label = "Lote",
                                modifier = Modifier.width(140.dp))
                            PosPrimaryButton(
                                text = "Aplicar",
                                onClick = viewModel::applySelectedLineEdits,
                                enabled = !state.isBusy,
                                modifier = Modifier.width(100.dp),
                            )
                        }
                    }
                }

                // Totales — protagonista visual
                PosTotalDisplay(
                    subtotal = state.draft?.totals?.subtotal ?: 0.0,
                    tax = state.draft?.totals?.tax ?: 0.0,
                    total = state.draft?.totals?.total ?: 0.0,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Issues de validacion
                if (state.validationIssues.isNotEmpty()) {
                    PosSectionCard(
                        title = "Observaciones",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.validationIssues.forEach { issue ->
                            Text(
                                "- ${issue.lineId ?: "General"}: ${issue.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // Fila de acciones — diferenciacion semántica
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                ) {
                    // Acciones secundarias de flujo — todas con doble guard
                    val canRunSecondary = !state.isBusy && !state.isCheckoutInFlight
                    PosSecondaryButton(
                        text = "Validar",
                        onClick = viewModel::validateDraft,
                        enabled = canRunSecondary,
                        icon = Icons.Filled.CheckCircle,
                        modifier = Modifier.weight(1f),
                    )
                    PosSecondaryButton(
                        text = "Lotes",
                        onClick = viewModel::resolveLots,
                        enabled = canRunSecondary,
                        icon = Icons.Filled.Inventory,
                        modifier = Modifier.weight(1f),
                    )
                    PosSecondaryButton(
                        text = "Nueva venta",
                        onClick = viewModel::startNewSale,
                        enabled = canRunSecondary,
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f),
                    )
                    // Divisor visual entre acciones secundarias y cobro
                    Spacer(Modifier.width(PosSpacing.lg))
                    // Acciones primarias de cobro — más grandes
                    PosPrimaryButton(
                        text = "Cobrar efectivo  F9",
                        onClick = viewModel::checkoutCash,
                        enabled = !state.isCheckoutInFlight,
                        icon = Icons.Filled.Payments,
                        modifier = Modifier.weight(1.5f),
                    )
                    PosPrimaryButton(
                        text = "Cobrar credito",
                        onClick = viewModel::checkoutCredit,
                        enabled = !state.isCheckoutInFlight,
                        icon = Icons.Filled.CreditCard,
                        modifier = Modifier.weight(1.5f),
                    )
                }

                // Resultado del cobro
                state.checkoutResult?.let { result ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(PosSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                "Venta completada — Folio: ${result.folio}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                PosNoticeRow(notice = state.notice, errorMessage = state.errorMessage)
            }
        }

        // Overlay de carga para operaciones en vuelo
        PosLoadingOverlay(
            isLoading = state.isCheckoutInFlight,
            message = "Procesando cobro...",
        )
    }
}

private fun formatDisplayQty(qty: Double, unit: String): String {
    val normalized = if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()
    return "$normalized $unit"
}
```

**Impacto:** Pantalla de mayor uso — cada venta pasa por aquí. Reducción de errores de cajero, mayor velocidad de cobro.
**Esfuerzo:** 5-8 horas.

---

### P2 — Alto (Mejora significativa de usabilidad)

---

#### P2-1: CashSessionScreen — Separar flujos y limpiar labels

**Archivo:** `shared/features/cash/src/commonMain/kotlin/com/posfab/shared/features/cash/ui/CashSessionScreen.kt`

**Cambios clave:**
- Labels de campos en español: `"Efectivo de apertura"`, `"Efectivo contado"`
- `PosStatusBadge` para el estado de sesion
- Separar la sección de reporte diario con un `HorizontalDivider` y título claro
- Mostrar montos con `PosMoneyText` en lugar de `Text("opening_cash: ${...}")`
- Agregar `PosConfirmDialog` antes de cerrar sesión

**Código del estado de sesión (sección principal):**
```kotlin
// Reemplazar el Card de estado actual con:
val session = state.currentSession

PosSectionCard(
    modifier = Modifier.fillMaxWidth(),
    title = "Estado de caja",
    actions = {
        PosStatusBadge(status = session?.status?.name ?: "NONE")
    },
) {
    if (state.isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    } else {
        if (session == null) {
            Text("No hay sesion activa",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Datos con etiquetas en español y montos con PosMoneyText
            LabeledRow("Apertura de caja") {
                PosMoneyText(session.openingCash)
            }
            LabeledRow("Entradas") {
                PosMoneyText(session.movementIn)
            }
            LabeledRow("Salidas") {
                PosMoneyText(session.movementOut)
            }
            LabeledRow("Efectivo esperado") {
                PosMoneyText(session.expectedClose)
            }
            HorizontalDivider()
            LabeledRow("Abierta por") {
                Text(session.openedBy ?: "-",
                    style = MaterialTheme.typography.bodyMedium)
            }
            LabeledRow("Hora de apertura") {
                Text(session.openedAt ?: "-",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// Helper local:
@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
```

**Esfuerzo:** 2-4 horas.

---

#### P2-2: OperationsScreen — Etiquetas en español y tabs con estado

**Archivo:** `shared/features/operations/src/commonMain/kotlin/com/posfab/shared/features/operations/ui/OperationsScreen.kt`

**Cambios clave:**
- Reemplazar `Button.entries.forEach { Button { Text(tab.name) } }` con `TabRow` o `PrimaryTabRow`
- Mapear nombres de tabs de enum a etiquetas legibles en español
- Reemplazar labels snake_case con etiquetas en español
- Agregar `PosConfirmDialog` antes de `submitPurchase`, `submitWaste`, `submitAdjustment`
- Eliminar de la UI los `Text` que exponen `purchaseIdempotencyKey`, `requisitionIdempotencyKey`, `wasteIdempotencyKey` y `adjustmentIdempotencyKey`
- Verificar que cada tab con formularios/listas largas quede dentro de un contenedor scrollable cuando aplique

```kotlin
// Mapa de nombres de tab legibles
private val OperationsTab.displayName: String get() = when (this) {
    OperationsTab.PURCHASES    -> "Compras"
    OperationsTab.INTERNAL_REQ -> "Requisiciones"
    OperationsTab.ON_HAND      -> "Inventario"
    OperationsTab.LOTS         -> "Lotes"
    OperationsTab.WASTE        -> "Mermas"
    OperationsTab.ADJUSTMENTS  -> "Ajustes"
}

// Reemplazar el Row de botones con (preferido):
PrimaryTabRow(
    selectedTabIndex = OperationsTab.entries.indexOf(state.selectedTab),
    modifier = Modifier.fillMaxWidth(),
) {
    OperationsTab.entries.forEachIndexed { index, tab ->
        Tab(
            selected = state.selectedTab == tab,
            onClick = { viewModel.selectTab(tab) },
            text = { Text(tab.displayName) },
        )
    }
}
```

**Fallback si `PrimaryTabRow` no esta disponible en CMP 1.7.3:**

`PrimaryTabRow` se introdujo en M3 1.2+. La version exacta de M3 incluida en Compose Multiplatform 1.7.3 deberia exponerlo, pero si por algun motivo el subset disponible no lo incluye, usar `TabRow` clasico — la API es compatible y el resultado visual es practicamente igual:

```kotlin
TabRow(
    selectedTabIndex = OperationsTab.entries.indexOf(state.selectedTab),
    modifier = Modifier.fillMaxWidth(),
) {
    OperationsTab.entries.forEach { tab ->
        Tab(
            selected = state.selectedTab == tab,
            onClick = { viewModel.selectTab(tab) },
            text = { Text(tab.displayName) },
        )
    }
}
```

Tomar la decision al primer intento de compilacion — no merece bloqueo. Si `PrimaryTabRow` no resuelve, sustituir por `TabRow` y continuar la fase.

**Esfuerzo:** 3-5 horas.

---

#### P2-3: CatalogScreen — Eliminar cards anidadas, mejorar formulario

**Archivo:** `shared/features/catalog/src/commonMain/kotlin/com/posfab/shared/features/catalog/ui/CatalogScreen.kt`

**Cambios clave:**
- La lista de productos debe usar `LazyColumn` directamente en un `PosSectionCard`, no `Card` dentro de `Card`
- Label `"factor_to_base"` -> `"Factor de conversion"`
- Eliminar el campo `Limit` libre y reemplazar con un selector de tamaño de página predefinido (10, 25, 50)
- Los filtros de activo/inactivo deben usar `FilterChip` en lugar de 3 botones iguales
- Si el panel de detalle/formulario excede la altura visible, envolverlo en `verticalScroll(rememberScrollState())`

```kotlin
// Reemplazar los 3 Button de filtro con:
Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
    FilterChip(
        selected = state.activeFilter == ActiveFilter.ALL,
        onClick = { viewModel.onActiveFilterChange(ActiveFilter.ALL) },
        label = { Text("Todos") },
    )
    FilterChip(
        selected = state.activeFilter == ActiveFilter.ACTIVE_ONLY,
        onClick = { viewModel.onActiveFilterChange(ActiveFilter.ACTIVE_ONLY) },
        label = { Text("Activos") },
    )
    FilterChip(
        selected = state.activeFilter == ActiveFilter.INACTIVE_ONLY,
        onClick = { viewModel.onActiveFilterChange(ActiveFilter.INACTIVE_ONLY) },
        label = { Text("Inactivos") },
    )
}
```

**Esfuerzo:** 3-4 horas.

---

### P3 — Medio (Pulido y mejoras de experiencia)

---

#### P3-1: ManagerPanelScreen y DailyHistoryScreen — Datos tabulares con formato

**Cambios:**
- `Column` raíz necesita `fillMaxSize()` + `verticalScroll(rememberScrollState())`
- Los totales diarios deben renderizarse en una tabla simple o tarjetas con `PosMoneyText`
- El input de fecha debe usar un formato claro con placeholder `"YYYY-MM-DD"` y validación visual (no solo texto sin hint)
- Los deudores deben mostrar el monto con `PosMoneyText`

**Esfuerzo:** 2-4 horas.

---

#### P3-2: DiagnosticsScreen — Support bundle copiable

**Cambios:**
- El `state.supportBundleText` debe renderizarse en un `OutlinedTextField` de solo lectura con `readOnly = true`, `fontFamily = FontFamily.Monospace`, y botón de copiar al portapapeles

```kotlin
// Reemplazar Text(state.supportBundleText) con:
if (state.supportBundleText.isNotBlank()) {
    PosSectionCard(title = "Support Bundle") {
        OutlinedTextField(
            value = state.supportBundleText,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
        )
        PosPrimaryButton(
            text = "Copiar al portapapeles",
            onClick = { /* Clipboard.copy(state.supportBundleText) */ },
            icon = Icons.Filled.ContentCopy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

**Esfuerzo:** 1-2 horas.

---

#### P3-3: App.kt — Loading state con contexto

**Cambio:**
```kotlin
// Reemplazar:
CircularProgressIndicator()

// Con:
Column(horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(PosSpacing.md)) {
    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    Text("Iniciando sistema...",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

**Esfuerzo:** 15-30 minutos.

---

#### P3-4: Accesibilidad — Targets táctiles y contraste

**Checklist a verificar tras implementar el sistema de diseño:**

| Item | Acción requerida |
|---|---|
| Todos los botones deben tener `height >= 48.dp` | `PosLayout.touchTarget = 48.dp` aplicado via `PosPrimaryButton` |
| `NavigationDrawerItem` tiene 48dp por defecto en M3 | Verificar que no se reduzca con padding externo |
| Contraste de texto sobre `primaryContainer` (terracota oscuro) | Verificar con PosColors propuestos — target 4.5:1 AA |
| `PosTextField` con `isError = true` debe incluir descripción de error | Cubierto con `supportingText` en PosTextField refactorizado |
| `RadioButton` en Login reemplazado con `SegmentedButton` | Cubre el problema de target pequeño |
| `Checkbox` con label no clicable | Usar `Row(Modifier.toggleable(...))` o reemplazar con `Switch` |
| Navegacion con `NavigationDrawerItem` tiene estado `selected` visible | Cubierto en ShellScreen P1-3 |

---

## 5. Orden de implementacion por fases pequenas

El plan se ejecuta como una secuencia de **fases cortas, autocontenidas e independientemente ejecutables**. Cada fase deja la app compilable y funcional end-to-end. Se prefieren muchas fases pequenas a pocas fases grandes — porque el proyecto esta pre-MVP, no hay presion de mantener UI vieja conviviendo con UI nueva, y se puede iterar en tiempo real.

Tamano objetivo de cada fase: **1 a 4 horas de trabajo**, con un commit/PR limpio al final.

### Fase 0 — Prerequisitos tecnicos (30-60 min)

Sin pantallas todavia. Solo dejar el proyecto listo para que las fases siguientes compilen.

- 0.1 Agregar `compose.materialIconsExtended` en cada modulo consumidor que renderice iconos en los snippets de este plan: `shared/ui`, `shared/features/login`, `shared/features/sale`, `shared/features/shell`, `shared/features/reports`, `shared/features/catalog`, `shared/features/operations`.
- 0.2 Compilar `./gradlew :app-desktop:build` — debe seguir verde sin cambios funcionales.

**Resultado:** la dependencia esta disponible globalmente. Cierre con un PR de "build only".

---

### Fase 1 — Tokens de diseno (1-2 h)

Solo crear archivos de tema. No tocar pantallas.

- 1.1 `shared/ui/.../theme/PosColors.kt` — copiar de seccion 2.1 (paleta + tokens `surfaceContainer*`).
- 1.2 `shared/ui/.../theme/PosSpacing.kt` — copiar de seccion 2.3.
- 1.3 `shared/ui/.../theme/PosTypography.kt` — copiar de seccion 2.2.
- 1.4 Compilar — los archivos se introducen pero todavia no se usan.

**Resultado:** infraestructura de diseno disponible. La UI sigue identica visualmente.

---

### Fase 2 — Activar `PosTheme` (30-60 min)

- 2.1 Reemplazar `PosTheme.kt` por la version de seccion 2.5 (con `darkColorScheme(...)` real, `PosTypography`, `PosShapes`).
- 2.2 Verificar el unico call-site real (`app-desktop/.../App.kt` o equivalente) — el parametro `darkMode` tiene default, no rompe llamadas existentes.
- 2.3 Compilar y arrancar `:app-desktop:run`. La app cambia de purpura/morado a terracota/crema. Aun no hay rediseno de pantallas.

**Resultado:** identidad visual activa en toda la app. Cierre con captura antes/despues.

---

### Fase 3 — Refactor de `PosComponents.kt` (3-4 h)

Reemplazar el archivo completo con los 12 componentes de seccion 3.1, con los imports declarados en 0.3.3.

- 3.1 Implementar componentes basicos primero: `PosTextField` (refactor), `PosPrimaryButton` (refactor), `PosSecondaryButton`, `PosDestructiveButton`.
- 3.2 Implementar componentes de layout: `PosSectionCard`.
- 3.3 Implementar componentes de dato: `PosMoneyText`, `PosTotalDisplay`, `PosStatusBadge`.
- 3.4 Implementar componentes de feedback: `PosOfflineBanner`, `PosLoadingOverlay`, `PosNoticeRow`, `PosConfirmDialog`.
- 3.5 Compilar — las pantallas siguen usando los componentes viejos via los nombres `PosTextField` / `PosPrimaryButton`, que se mantuvieron compatibles.

**Resultado:** libreria de componentes disponible. Pantallas existentes siguen funcionando.

---

### Fase 4 — `LoginScreen` (1.5-2.5 h)

Aplicar P1-2 completo. Pantalla pequena, riesgo bajo, primera prueba real de los componentes nuevos.

- 4.1 Aplicar snippet de seccion 4 P1-2.
- 4.2 Verificar que no se mete texto en ingles ("Sign In" -> "Iniciar sesion").
- 4.3 Validar manualmente: login con `POS1`, `POS2`, `ADMIN`, error de credenciales, estado loading.

**Resultado:** primera pantalla con sistema de diseno aplicado.

---

### Fase 5 — `ShellScreen` (2-3 h)

- 5.1 Reemplazar `Button` por `NavigationDrawerItem` con icono y estado `selected`.
- 5.2 `routeDisplayName(...)` y `routeIcon(...)` a nivel de archivo (no extension property dentro del composable).
- 5.3 `Icons.AutoMirrored.Filled.Logout` para Logout.
- 5.4 `PosOfflineBanner` integrado.
- 5.5 Validar navegacion por rol: `CASHIER`, `MANAGER`, `ADMIN`. Logout claramente separado.

**Resultado:** navegacion clara, estado activo visible, idioma consistente.

---

### Fase 6 — `CashierSaleScreen` parte A: cabecera y total (2-3 h)

Dividir el rediseno mas grande en dos pasos. Esta primera parte es no-disruptiva.

- 6.1 Quitar el `Text("Draft: ... v...")` y el `formatMoney` privado del archivo.
- 6.2 Reemplazar el bloque de totales (`Card { Column { Text("TOTAL")... } }`) por `PosTotalDisplay`.
- 6.3 Documentar atajos en la UI (chip/`Surface` con `Enter / F5 / F9`).
- 6.4 Compilar y validar: el flujo de venta sigue funcionando 1:1.

**Resultado:** la pantalla se siente mas profesional sin cambiar layout.

---

### Fase 7 — `CashierSaleScreen` parte B: layout y acciones (3-5 h)

- 7.1 Reorganizar columnas (`Row` con columnas tipadas, no weight arbitrario).
- 7.2 Editor de linea condicional (`if (state.selectedLineId != null)`).
- 7.3 Botones `+/-/Eliminar` con `canEditLine` (guard combinado `isBusy` + `isCheckoutInFlight`).
- 7.4 Fila de acciones final con jerarquia: secundarias (`canRunSecondary`) vs primarias de cobro.
- 7.5 `PosLoadingOverlay` durante `isCheckoutInFlight`.
- 7.6 Guard de F5/F9 ante `confirmDialog != null` (agregar el campo a `CashierSaleState` si aun no existe).
- 7.7 Validacion manual completa: buscar, agregar, editar, validar, lotes, cobrar efectivo, cobrar credito, nueva venta.

**Resultado:** pantalla de caja con jerarquia visual y proteccion contra acciones concurrentes.

---

### Fase 8 — `CashSessionScreen` (2-4 h)

- 8.1 Aplicar snippet P2-1 declarando `val session = state.currentSession` ANTES del `PosSectionCard`, para que `actions` y `content` compartan la misma referencia sin errores de scope.
- 8.2 Labels en espanol, `PosMoneyText`, `PosStatusBadge`.
- 8.3 Separar visualmente apertura/cierre vs reporte diario con `HorizontalDivider` o tabs (decision rapida durante implementacion).
- 8.4 `PosConfirmDialog` antes de cerrar sesion.

**Resultado:** caja operable sin labels tecnicas.

---

### Fase 9 — `OperationsScreen` (3-5 h)

- 9.1 Extension property `OperationsTab.displayName` en archivo, no inline.
- 9.2 `PrimaryTabRow` + `Tab` con texto en espanol.
- 9.3 Labels de formulario en espanol (`product_id` -> "Producto", etc.).
- 9.4 Eliminar de la UI `purchaseIdempotencyKey`, `requisitionIdempotencyKey`, `wasteIdempotencyKey`, `adjustmentIdempotencyKey`. Si necesitan inspeccion, llevarlos a `DiagnosticsScreen`.
- 9.5 `PosConfirmDialog` antes de `submitPurchase`, `submitWaste`, `submitAdjustment`.
- 9.6 Si algun tab tiene formulario largo, envolver en `verticalScroll(rememberScrollState())`.

**Resultado:** registro de operaciones legible para personal no tecnico.

---

### Fase 10 — `CatalogScreen` (3-4 h)

- 10.1 Eliminar Cards anidadas — `LazyColumn` directo en `PosSectionCard`.
- 10.2 `FilterChip` para `ALL / ACTIVE_ONLY / INACTIVE_ONLY`.
- 10.3 Limit como selector predefinido (10 / 25 / 50). **Antes de fijar valores, verificar con `bed/` que esos `limit` son aceptados** — un `limit` no permitido devolveria 400.
- 10.4 Labels de formulario en espanol.
- 10.5 Si el panel derecho excede altura, `verticalScroll(rememberScrollState())`.

**Resultado:** catalogo navegable sin doble elevacion ni labels tecnicas.

---

### Fase 11 — `ManagerPanelScreen` + `DailyHistoryScreen` (2-4 h)

- 11.1 `Column` raiz con `fillMaxSize()` + `verticalScroll(rememberScrollState())`.
- 11.2 Totales diarios renderizados con `PosMoneyText` en filas/tarjetas, no como string concatenado.
- 11.3 Input de fecha con placeholder claro `YYYY-MM-DD`.

**Resultado:** reportes presentables a manager.

---

### Fase 12 — `DiagnosticsScreen` (1-2 h)

- 12.1 Antes de tocar la UI, leer el estado real (`DiagnosticsState`) y confirmar que `supportBundleText` existe con ese nombre. Si no, ajustar.
- 12.2 `OutlinedTextField(readOnly = true, fontFamily = Monospace)` para support bundle.
- 12.3 Boton "Copiar al portapapeles" (en desktop puede usar `java.awt.Toolkit.getDefaultToolkit().systemClipboard` desde un `expect/actual` o helper en `app-desktop`).

**Resultado:** soporte tecnico copiable.

---

### Fase 13 — Pulido final (1-2 h)

- 13.1 `App.kt` loading state con texto de contexto.
- 13.2 Recorrido visual rapido en dos tamanos de ventana (1366x768 y 1920x1080).
- 13.3 Verificar contraste de los colores propuestos (texto sobre `primaryContainer`, errores, `surfaceVariant`).
- 13.4 Verificar `touchTarget >= 48.dp` en todos los botones (los `PosPrimaryButton` ya lo aplican via `PosLayout.touchTarget`).

**Resultado:** acabado consistente, listo para usuarios internos.

---

### 5.1 Definicion de terminado por fase

Cada fase solo se considera terminada si cumple todo lo siguiente:

1. El proyecto compila en `fed` sin warnings nuevos relevantes atribuibles al cambio.
2. La fase no depende de correcciones posteriores en otra fase para compilar o comportarse correctamente.
3. No se pierden atajos ni flujos criticos del cajero (`Enter`, `F5`, `F9`, seleccion de linea, cobro, nueva venta).
4. Los textos visibles al usuario quedan en espanol consistente.
5. Estados de error, carga, seleccion y exito tienen tratamiento visual distinguible.
6. Validacion manual de la pantalla tocada antes de pasar a la fase siguiente.
7. Commit/PR pequeno y descriptivo (una fase = un PR).

### 5.2 Validacion minima antes de cerrar la implementacion completa

1. `./gradlew test` (ambos modulos `bed` y `fed` si aplica).
2. `./gradlew :app-desktop:run`.
3. Login en `POS1`, `POS2` y `ADMIN`.
4. Flujo completo de venta: buscar, agregar, editar, validar, resolver lotes, cobrar efectivo, cobrar credito, nueva venta.
5. Caja: abrir, revisar sesion actual, cerrar, ver conciliacion.
6. Navegacion por rol: `CASHIER`, `MANAGER`, `ADMIN`.
7. Revision visual en al menos dos tamanos de ventana de desktop.
8. Revision de contraste y targets minimos en acciones criticas.

### 5.3 Estimacion total

- Fases 0-3 (fundamentos): ~5-9 h.
- Fases 4-7 (pantallas P1): ~9-14 h.
- Fases 8-10 (pantallas P2): ~8-13 h.
- Fases 11-13 (pantallas P3 y pulido): ~4-8 h.

**Total: ~26-44 h efectivas**, distribuidas en 13 fases independientes. Cada fase es un commit/PR. Si una fase se traba, no bloquea las anteriores ya integradas.

---

## 6. Decisiones de Diseño Justificadas

### Por qué dark mode como default
Se propone dark mode como punto de partida para caja porque puede reducir brillo percibido y dar mejor contraste al resaltar totales, errores y estado de sesión. No debe tratarse como dogma global: después del primer sprint conviene validar en terminales reales si caja, catálogo y reportes deben compartir esquema o si Admin/Reports rinden mejor en light mode.

### Por qué NavigationDrawerItem en lugar de Button para nav
`NavigationDrawerItem` de M3 tiene estado `selected` integrado, ripple correcto, y tamaño táctil adecuado. Los `Button` usados actualmente no tienen estado visual de selección — el usuario no sabe en qué pantalla está. Esto viola la heurística #1 de Nielsen (Visibility of system status).

### Por qué SingleChoiceSegmentedButton para terminal en Login
El `RadioButton` actual tiene un target de ~20×20px. `SingleChoiceSegmentedButton` produce botones del ancho completo disponible, completamente clickeables. Con 3 terminales (POS1, POS2, ADMIN) el segmented button muestra las opciones de forma clara y sin riesgo de selección incorrecta.

### Por qué FilterChip en lugar de Button para filtros de catálogo
Los `Button` son indistinguibles en estado activo/inactivo. `FilterChip` tiene estado `selected` visible integrado en M3. El usuario puede ver de un vistazo qué filtro está activo.

### Por qué campos de edición condicionales en CashierSaleScreen
Los 4 campos de edición (Qty, Unidad, Precio, Lote) son necesarios ocasionalmente, no en cada venta. Mantenerlos siempre visibles añade carga cognitiva permanente. Con `if (state.selectedLineId != null)` solo aparecen cuando hay una línea seleccionada — el contexto donde tienen sentido.

### Por qué JetBrains Mono para montos
Los montos monetarios en un POS son información crítica. La tipografía monoespaciada hace que los dígitos se alineen verticalmente en listas y que la diferencia entre `$1,000.00` y `$10,000.00` sea visualmente obvia. En implementación, primero puede usarse `FontFamily.Monospace`; si el resultado varía demasiado entre terminales, el siguiente paso es empacar JetBrains Mono explícitamente.

### Por qué PosTotalDisplay con primaryContainer
El total de la venta es el dato más importante que ve el cajero y el cliente. Usar `primaryContainer` (terracota oscuro en dark mode) lo diferencia visualmente del resto de la pantalla sin necesidad de tamaño extremo. Contrasta con las `Card`s estándar de `surfaceVariant`.

---

## 7. Lo Que No Debe Cambiarse

- La arquitectura ViewModel + StateFlow — está bien estructurada y no necesita cambios de UI
- La lógica de `RouteGuard` — el control de acceso por rol funciona correctamente
- El manejo de `onPreviewKeyEvent` en CashierSaleScreen — los atajos F5/F9/Enter son correctos y necesarios para velocidad de caja
- La estructura de `SaleDraft` con versioning — es un patron correcto para POS con sincronización
- El `PosConfirmDialog` no debe interferir con los atajos de teclado — verificar que `AlertDialog` no capture F5/F9 globalmente

---

## 8. Archivos a Crear/Modificar — Resumen

| Archivo | Acción | Prioridad |
|---|---|---|
| `shared/ui/.../theme/PosColors.kt` | Crear | P1 |
| `shared/ui/.../theme/PosTypography.kt` | Crear | P1 |
| `shared/ui/.../theme/PosSpacing.kt` | Crear | P1 |
| `shared/ui/.../theme/PosTheme.kt` | Modificar | P1 |
| `shared/ui/.../components/PosComponents.kt` | Refactorizar completo | P1 |
| `shared/features/login/.../LoginScreen.kt` | Modificar | P1 |
| `shared/features/shell/.../ShellScreen.kt` | Modificar | P1 |
| `shared/features/sale/.../CashierSaleScreen.kt` | Modificar | P1 |
| `shared/features/cash/.../CashSessionScreen.kt` | Modificar | P2 |
| `shared/features/operations/.../OperationsScreen.kt` | Modificar | P2 |
| `shared/features/catalog/.../CatalogScreen.kt` | Modificar | P2 |
| `shared/features/reports/.../ManagerPanelScreen.kt` | Modificar | P3 |
| `shared/features/reports/.../DailyHistoryScreen.kt` | Modificar | P3 |
| `shared/features/reports/.../DiagnosticsScreen.kt` | Modificar | P3 |
| `app-desktop/.../App.kt` | Modificar (loading state) | P3 |

---

*Plan generado tras lectura directa de los archivos Composable del proyecto. Los fragmentos de código son una base de implementación compatible en intención con Compose Multiplatform 1.7.3 + Material 3, pero deben verificarse antes de aplicar literalmente: imports, APIs disponibles en la versión actual, nombres reales del estado/viewmodel y detalles de compilación por módulo.*

---

## 9. Auditoria del plan — estado consolidado

**Fecha de la auditoria original:** 2026-04-30
**Fecha de consolidacion:** 2026-04-30 (segundo pase)

**Archivos leidos para cruzar referencias:**
- `shared/ui/src/.../PosTheme.kt`, `PosComponents.kt`
- `shared/features/login/.../LoginScreen.kt`, `LoginState.kt`, `LoginViewModel.kt`
- `shared/features/shell/.../ShellScreen.kt`, `ShellViewModel.kt`, `ShellRoute.kt`
- `shared/features/sale/ui/CashierSaleScreen.kt`, `CashierSaleState.kt`, `CashierSaleViewModel.kt`
- `shared/features/sale/domain/SaleModels.kt`
- `shared/features/cash/ui/CashSessionScreen.kt`, `CashSessionState.kt`
- `shared/features/cash/domain/CashModels.kt`
- `shared/features/catalog/ui/CatalogScreen.kt`, `CatalogState.kt`
- `shared/features/operations/ui/OperationsScreen.kt`, `OperationsState.kt`, `OperationsViewModel.kt`
- `shared/features/reports/manager/ManagerPanelScreen.kt`
- `shared/core/diagnostics/NetworkHealthTracker.kt`
- `shared/ui/build.gradle.kts`, `gradle/libs.versions.toml`

**Nota de vigencia:** los hallazgos de la auditoria original ya fueron incorporados al cuerpo del plan (secciones 0.3, 2.1, 3.1, 4.x, 5.x). Esta seccion se conserva como bitacora condensada y referencia rapida.

### 9.1 Calificacion general

**Plan ejecutable** una vez aplicada la Fase 0 (prerequisitos tecnicos). Los riesgos restantes son de detalle (imports, nombres de parametros M3 1.3.x), no de diseno ni de comprension del producto.

El diagnostico por pantalla esta verificado contra el codigo real. Los nombres de metodos y campos referenciados en los snippets coinciden con lo que existe en `LoginViewModel`, `CashierSaleViewModel`, `SaleModels`, `CashModels`, `OperationsViewModel`, `CatalogState`. La paleta y tipografia propuestas usan APIs validas de M3.

### 9.2 Diagnostico por pantalla — ya verificado contra el codigo

Los siguientes diagnosticos de la seccion 1 fueron confirmados leyendo el archivo correspondiente. No requieren retrabajo:

- LoginScreen: `"Sign In"` hardcoded, RadioButton con target chico, error sin color.
- ShellScreen: `width(220.dp)`, sin estado activo, logout pegado a navegacion.
- CashierSaleScreen: `Text("Draft: ... v...")` expuesto, columnas `weight(1f)`/`weight(1.2f)`, botones planos sin diferenciacion.
- CashSessionScreen: labels en snake_case (`opening_cash`, `counted_cash`, `expected_close`), apertura/cierre y reporte diario mezclados en mismo ViewModel (no requiere separar VM, solo separar UI con tabs/divisor).
- ManagerPanelScreen: `Column` raiz sin `fillMaxSize` ni scroll.
- OperationsScreen: `tab.name` (mayusculas del enum) como label, snake_case en formulario, claves de idempotencia visibles en UI.
- CatalogScreen: Cards anidadas, mezcla ES/EN, panel derecho sin scroll.

### 9.3 Riesgos de compilacion — todos resueltos en Fase 0 o en los snippets

Estos puntos eran riesgos en la primera revision; con los ajustes ya aplicados al plan no deberian aparecer al ejecutar:

| # | Riesgo | Resolucion |
|---|--------|------------|
| 1 | Iconos extendidos (`QrCodeScanner`, `Search`, `Payments`, `History`, `ContentCopy`, `Logout`, `Inventory2`, `Warehouse`, `BarChart`, `BugReport`, `AdminPanelSettings`, `CreditCard`, `Person`, `Lock`, `CheckCircle`, `Add`, `Inventory`) no estan en el set core de M3. | **Fase 0.1** agrega `compose.materialIconsExtended` en cada modulo consumidor. |
| 2 | `Icons.Filled.ExitToApp` / `Logout` deprecados. | **Seccion 0.3.2** + snippets actualizados a `Icons.AutoMirrored.Filled.Logout`. |
| 3 | Imports faltantes en `formatMoney`, `PosLoadingOverlay`, `PosTextField`. | **Seccion 0.3.3** lista los imports requeridos en `PosComponents.kt`. |
| 4 | Tonos purpura residuales por defaults M3 1.3.x en `surfaceContainer*`. | **Seccion 2.1** declara `surfaceContainer*`, `surfaceDim`, `surfaceBright`. |
| 5 | `formatMoney` duplicado al moverlo a `PosComponents.kt`. | **Seccion 4 P1-4** indica eliminar la version original de `CashierSaleScreen.kt` en el mismo PR. |
| 6 | Botones secundarios habilitados durante `isCheckoutInFlight`. | Snippet de `CashierSaleScreen` aplica `canRunSecondary` y `canEditLine`. |
| 7 | F5/F9 disparados con dialogo de confirmacion abierto. | Guard `state.confirmDialog != null` documentado en P1-4. |
| 8 | Fila de IVA siempre visible aunque `tax = 0`. | `PosTotalDisplay` ya envuelve la fila en `if (tax > 0.0)`. |
| 9 | Scoping invertido en snippet P2-1 (`session` antes de declararse). | Snippet ya declara `val session = state.currentSession` antes del `PosSectionCard`. |
| 10 | `route.title` en ingles. | Sustituido por `routeDisplayName(route)` a nivel de archivo en seccion 4 P1-3. |

### 9.4 Riesgos restantes — verificar al compilar

Estos puntos no se pueden cerrar leyendo el codigo; se confirman cuando se compile cada fase:

1. **`HorizontalDivider` vs `Divider`** — En Compose Multiplatform 1.7.3 con M3 1.3.x, `HorizontalDivider` es la API esperada. Si por alguna razon de version no compila, sustituir por `Divider()`. No es bloqueante.
2. **`SingleChoiceSegmentedButtonRow` y `PrimaryTabRow`** — Disponibles en M3 1.3.x. Riesgo bajo; se confirma en Fase 4 (Login) y Fase 9 (Operations).
3. **Parametros nombrados de `NavigationDrawerItemDefaults.colors`** — `unselectedIconColor` y `unselectedTextColor` existen en M3 1.3.x. Verificar al compilar Fase 5.
4. **`DiagnosticsState.supportBundleText`** — La Fase 12 abre por leer `DiagnosticsState.kt` antes de aplicar el snippet. Si el campo se llama distinto (p. ej. `bundle`, `supportText`), ajustar.
5. **Limites de paginacion en `bed`** — Antes de fijar `[10, 25, 50]` en `CatalogScreen` (Fase 10), confirmar con el backend que esos valores son aceptados.

### 9.5 Gaps cubiertos por las nuevas fases

Hallazgos que la auditoria detecto y que ahora forman parte explicita del plan:

- Eliminacion de claves de idempotencia visibles en `OperationsScreen` → Fase 9.4.
- Scroll vertical en `CatalogScreen` panel derecho → Fase 10.5.
- Scroll en `OperationsScreen` cuando algun tab tenga formulario largo → Fase 9.6.
- Verificacion de `DiagnosticsState` antes de tocar la UI → Fase 12.1.
- Scoping correcto de `session` en CashSessionScreen → Fase 8.1 referencia el Ajuste A.
- Eliminacion del `formatMoney` duplicado → Fase 6.1 / P1-4.

### 9.6 Lo que esta correcto y no debe tocarse

- Arquitectura ViewModel + StateFlow.
- Logica de `RouteGuard` (acceso por rol).
- Atajos de teclado `Enter`, `F5`, `F9` en `CashierSaleScreen` (solo se anade el guard de `confirmDialog`).
- Estructura de `SaleDraft` con versioning optimista.
- Nombres de metodos del ViewModel y campos del `State` ya verificados (no se renombran).
- Set de tokens de espaciado `PosSpacing` / `PosLayout`.
- Decision de dark mode como default para caja (revisable en Fase 13).

---

## 10. Antipatrones evitados explicitamente

Por estar en pre-MVP e in-house, el plan **no** introduce:

- Internacionalizacion (`stringResource`, archivos `strings.xml`, `expect/actual` para textos). Espanol hardcoded directo.
- Feature flags ni dual-UI ni rollback. Cada fase reemplaza la UI vieja en el mismo PR.
- Convivencia de componentes viejos y nuevos. `PosComponents.kt` se reemplaza completo en Fase 3.
- Migracion de la arquitectura ViewModel + StateFlow.
- Snapshot tests / screenshot tests. Pre-MVP, validacion manual basta. Pueden agregarse despues del MVP si se justifica.
- Parametros de tema dinamicos por usuario / por terminal. Tema unico, dark, fijo.
- Soporte para tablets/movil. La app desktop es el unico target del MVP.

---

## 11. Lo que queda fuera de este plan

Los siguientes temas se identificaron pero **no estan cubiertos** aqui — son trabajo posterior si el MVP los requiere:

- Localizacion / multi-idioma.
- Light theme funcional probado (existe el snippet en seccion 2.1 pero no se aplica en ninguna fase).
- Impresion de tickets desde la UI (si el ticket se renderiza dentro de un Composable, dark mode requiere envolverlo en `PosTheme(darkMode = false) { ... }`).
- Atajos de teclado adicionales (F2 nueva venta rapida, etc.).
- Empaquetado de fuente JetBrains Mono como recurso (hoy se usa `FontFamily.Monospace` del SO).
- Tests automatizados de UI con `runComposeUiTest`.
- Accesibilidad avanzada (lectores de pantalla, TalkBack equivalente).

Estos temas pueden tratarse en planes incrementales una vez el MVP este operando.
