# Design system

The suite has a light design system on top of Material 3. Material keeps the color scheme, the type scale and the
shapes. The design system adds what Material does not have, and every screen reads it through `MtsTheme`, like
`MaterialTheme`.

Status: phase 1 done. Both apps and `ui` read the domain colors from `MtsTheme.colors` (two raw colors are left on purpose: the black center of the master medal and the black or white text on a computed background), and every tab row is `MtsTabRow` or `MtsScrollableTabRow` with `CompactTab` (ui). Next: phase 3 (layout tokens) and phase 4 (spacing and shapes, screen by screen).

## The `designsystem` module

The design system is its own Gradle module, `designsystem`. It depends only on Compose (no `domain`, no `data`), so
it compiles on its own: `./gradlew :designsystem:compileKotlinJvm`. `ui` exposes it with `api(projects.designsystem)`,
so the apps see it through `ui` and need no new dependency.

- `designsystem`: the tokens, `MtsTheme`, the Gang of Three font, `MtsCatalog` and every generic component we
  extract (tabs, signed scores, position badges, status chips...). A component goes here when it needs nothing from
  the domain: it takes plain values (`Int`, `String`, `Color`, lambdas), not models.
- `ui`: what the two apps share and that knows the domain or the platform: sign in, dialogs, statistics, the theme
  choice and its storage, and the bridges from the domain to the design system, like `MtsColors.level(PlayerLevel)`.

New components keep the package `com.etologic.mahjongtournamentsuite.presentation.components` (and the tokens
`...presentation.theme`), so moving one between the modules changes no import.

## Tokens

All of them are in `designsystem/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/theme/MtsTokens.kt`.

| Read | What | Example |
| --- | --- | --- |
| `MaterialTheme.colorScheme` | The Material roles: primary, surface, error... | `colorScheme.onSurfaceVariant` |
| `MtsTheme.colors` | The colors of the domain: gain, warning, info, medals, levels, text on the green header | `MtsTheme.colors.gain`, `MtsTheme.colors.level(level)` (ui) |
| `MaterialTheme.typography` | The Material type scale (Gang of Three on titles and labels) | `typography.titleMedium` |
| `MtsTheme.typography` | `score`, `scoreLarge` (bold, digits of equal width), `emphasis` | `MtsTheme.typography.score` |
| `MtsTheme.spacing` | `xxs` 2, `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24, `xxl` 32, `xxxl` 48 dp | `padding(MtsTheme.spacing.sm)` |
| `MtsTheme.windowWidth` | `Compact` (< 600 dp), `Medium` (< 900 dp), `Expanded` | `if (MtsTheme.windowWidth.isCompact)` |
| `MtsTheme.layout` | `screenGutter`, `cardPadding`, `listGap`, `sectionGap`; smaller on Compact | `padding(MtsTheme.layout.cardPadding)` |
| `MtsTheme.contentWidth` | `form` 520, `reading` 720, `screen` 900, `wide` 1200 dp | `widthIn(max = MtsTheme.contentWidth.form)` |
| `MaterialTheme.shapes` | extraSmall 4, small 8, medium 12, large 16, extraLarge 28 dp | `shape = MaterialTheme.shapes.medium` |
| `MtsTheme.shapes` | `pill` (50%) for chips and badges | `MtsTheme.shapes.pill` |

`MtsCatalog` (same folder) shows them all. Open its previews in the IDE, or in the admin app: an administrator opens
**Profile > Design system** (`DesignSystemRoute`, in no menu).

## Rules

- No `Color(0x...)` outside the theme package. A color with a meaning gets a token in `MtsColors`.
- No `RoundedCornerShape(...)` outside the theme package. Use `MaterialTheme.shapes` or `MtsTheme.shapes`.
- Paddings and gaps use the spacing scale or the layout tokens. Sizes of things (icons, widths) can stay in dp.
- Buttons are `Debounced*` or `Focused*` (`ButtonConventionTest`).

`DesignTokenRatchetTest` (ui, jvmTest) counts the raw colors, raw corner shapes and paddings off the scale. It fails
when a count goes up. When a change lowers a count, lower its baseline in the same commit.

## Migration phases

0. Tokens, catalog, ratchet test (no visible change).
1. The raw colors of the screens move to `MtsTheme.colors`, with the same values.
2. The duplicated components move to `designsystem`: tabs (`CompactTab`), signed scores, position badges, status chips.
3. `ScreenColumn`, `SectionCard` and `AppScaffold` read `MtsTheme.layout` and `MtsTheme.windowWidth`.
4. Screen by screen, when a screen is changed anyway: spacing and shapes to tokens, and the ratchet goes down.
5. Contrast fixes of the green and the red (a visible change, after the owner's review).
