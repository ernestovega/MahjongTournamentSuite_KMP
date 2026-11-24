# Design system

The suite has a light design system on top of Material 3. Material keeps the color scheme, the type scale and the
shapes. The design system adds what Material does not have, and every screen reads it through `MtsTheme`, like
`MaterialTheme`.

Status: phases 0 to 4 done (phase 5 closed: the owner keeps white text on the colors). Both apps and `ui` read the domain colors from `MtsTheme.colors` (the two colors that do not change with the theme, the black center of the master medal and the dark text on a light sector of a chart, are in `MtsFixedColors`). The shared components are in `designsystem`, the screens read the layout tokens, and no padding, gap or corner shape is off the scale: the ratchet is at 0 raw colors, 0 raw shapes and 0 off-scale spacings (24-nov-2026).

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
| `MtsTheme.shapes` | `pill` (50%) for chips and badges, `tag` (2 dp) for outlined tags | `MtsTheme.shapes.pill` |

In `ui`, `screenLayout` gives the layout tokens of the screens of the app: the compact ones when the app provides the
compact layout (`LocalCompactLayout`, only the admin app), else the wide ones. `ScreenColumn`, `SectionCard` and the
admin screens read it, so a screen does not write `if (compact) 12.dp else 16.dp`.

In light mode the page is light grey (`background` and `surface`, #E9EBEE) and the cards are white
(`surfaceContainerHigh` and `surfaceContainerHighest`), so a card stands out without a border (Ernesto, 24-nov-2026).
The dark mode keeps its colors.

A screen whose content is a single list (Players, Teams, Tournaments, Users, Organizations, App errors) does not
wrap it in a big card: it uses `SectionCard(container = false)`, which keeps the title, subtitle and actions but
draws them straight on the screen background. Cards stay for screens with several sections or panels.

## Components

In `designsystem/.../presentation/components`:

| Component | What |
| --- | --- |
| `MtsTabRow`, `MtsScrollableTabRow`, `MtsTab` | The tab rows with the color of the top bar (`CompactTab` in `ui` adds the analytics). |
| `MtsOutlinedTag` | A tag with a thin border, like the status of a tournament or the place of the player. |
| `MtsTintedChip` | A pill tinted with an accent, with an icon and a text, like the way of winning a hand. |
| `signedText`, `signedColor` | A value with its sign ("+12") and the color of a gain (green), a loss (red) or zero. |
| `Modifier.scrollIndicators`, `verticalScrollWithIndicators`, `horizontalScrollWithIndicators` | The fading edges and the always visible scrollbar of every list (see below). |
| `MtsCheckboxRow`, `MtsSwitchRow`, `MtsRadioRow` | A checkbox, switch or radio button with its text, where the whole line is the control (see the rules). |

`MtsCatalog` (same folder as the tokens) shows them all. Open its previews in the IDE, or in the admin app: an administrator opens
**DEV > Design system** in the top bar of the tournament list (`DesignSystemRoute`).

## Rules

- No `Color(0x...)` outside the theme package. A color with a meaning gets a token in `MtsColors`.
- No `RoundedCornerShape(...)` outside the theme package. Use `MaterialTheme.shapes` or `MtsTheme.shapes`.
- Paddings and gaps use the spacing scale or the layout tokens. Sizes of things (icons, widths) can stay in dp.
- A value with a sign uses `signedText` and `signedColor`, not its own `if (value > 0) "+$value"`.
- Buttons are `Debounced*` or `Focused*` (`ButtonConventionTest`).
- Every list, column or row that scrolls (lazy or not, vertical or horizontal, also a strip of chips) has its scroll
  indicators: `Modifier.scrollIndicators(state)` before the scroll, or `verticalScrollWithIndicators()` /
  `horizontalScrollWithIndicators()` instead of `verticalScroll()` / `horizontalScroll()`.
- A checkbox, switch or radio button changes also with a tap on its text (Ernesto, 2026-11-24): use `MtsCheckboxRow`,
  `MtsSwitchRow` or `MtsRadioRow`, where the whole line is one control (one node for the keyboard and TalkBack). An
  info icon goes outside the line, beside it. A Material `Checkbox`, `Switch` or `RadioButton` is allowed only without
  its own click (`onCheckedChange = null` / `onClick = null`) inside a `toggleable` or `selectable` parent.
  `LabeledControlsTest` (ui, jvmTest) fails otherwise; its short allowed list holds the boxes that select a row of a
  list whose tap opens the row, and the boxes of the hand table.

### Scroll indicators (fading edges and scrollbar)

Ernesto's rule of 2026-11-19 (it replaces the scrollbars that showed only while scrolling):

- A **fading edge**: a soft shadow on each edge that still has content behind it. It shows also when the edge falls in
  the gap between two items, so a list cut there still looks like a list. It grows with the first pixels of the scroll.
- A **scrollbar that is always visible** while the content can scroll, on every platform (also Android and web). The
  thumb can be dragged (with a finger too) and a tap in the strip takes it there.
- Where the scrollbar goes (`MtsScrollbar`):
  - `Outside` (default): its own 10 dp strip beside the list (under a horizontal one), outside the content. The strip
    comes the first time the content can scroll (a list that fits keeps all its width) and then stays, so content that
    gets shorter when narrower cannot make the layout go back and forth.
  - `Overlay`: on the edge of the container, over its own margin, for a screen or a list whose content already keeps
    a margin of 10 dp or more on the end (the player lists, the statistics, `ScreenColumn`). Both margins stay equal.
  - `Margin`: like `Outside`, but the strip goes past the end of the list, into the margin of the screen, so the list
    keeps the width of the search field and filters above it and looks the same on both sides. For the single list of a
    screen whose margin is 10 dp or more and does not clip (admin Players, Teams, Users, App errors, phone Tournaments).
    The admin desktop Tournaments table moves its `DraggableScrollbar` into the margin the same way.
  - `None`: only the shadows, for a list that has its own `DraggableScrollbar` (the tables of the player app with the
    round bubble, the tournament table of the admin app).

`DesignTokenRatchetTest` (ui, jvmTest) counts the raw colors, raw corner shapes and paddings off the scale. It fails
when a count goes up. When a change lowers a count, lower its baseline in the same commit.

## Migration phases

0. Tokens, catalog, ratchet test (no visible change).
1. The raw colors of the screens move to `MtsTheme.colors`, with the same values.
2. The duplicated components move to `designsystem`: tabs (`CompactTab`), signed scores, position badges, status chips.
3. `ScreenColumn`, `SectionCard` and `AppScaffold` read `MtsTheme.layout` and `MtsTheme.windowWidth`.
4. Screen by screen, when a screen is changed anyway: spacing and shapes to tokens, and the ratchet goes down.
5. Contrast fixes of the green and the red (a visible change, after the owner's review).
