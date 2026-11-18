# Cosmetics

Visual-consistency work for the app. Started 2026-09-28.

**Read the baseline section before proposing a change.** The app already has a
working design system - it is just unnamed and copy-pasted, so most of the first
job is *naming* what already works, not inventing something new. Changing a
colour in 46 places is a different risk from changing it in one.

---

## 1. Measured baseline

Counted across `ui/*.kt` on 2026-09-28. Re-run before starting; these drift.

### Colours: 25 unique hex values, 185 hardcoded uses

| Hex | Count | What it actually is |
|---|---|---|
| `#8B95A1` | 46 | dim / secondary text |
| `#E6E9EE` | 42 | primary body text |
| `#B0BAC5` | 16 | app bar navigation icon |
| `#8BC34A` | 13 | green: legal, success, up |
| `#FFC107` | 12 | amber: warning, issue, missing |
| `#1B2026` | 10 | surface (app bar, cards) |
| `#FF8A80` | 9 | red: delete, danger, down |
| `#EE6DCA` | 6 | **primary accent** (also in `Palette`) |
| `#20252B` | 6 | hairline / divider |
| `#23131E` | 4 | text on accent (the pink badge) |
| `#2A2130` | 3 | selected leader background |
| `#CFD6DE` | 3 | body text, secondary variant |
| `#5865F2` | 2 | secondary accent (power chart) |
| others (13) | 18 | one-offs, incl. translucencies `0x33…`, `0x66…`, `0xE6…`, `0xCC…` |

Five values carry 129 of the 185 uses. **The other 13 are single-use** and are
where the app stops being consistent.

### Type: 11 literal sizes, 154 uses

| Size | Count |
|---|---|
| 8.sp | 1 |
| 9.sp | 5 |
| 10.sp | 11 |
| **11.sp** | **49** |
| **12.sp** | **49** |
| 13.sp | 18 |
| 14.sp | 6 |
| 15.sp | 6 |
| 16.sp | 2 |
| 18.sp | 4 |
| 20.sp | 3 |

**11.sp and 12.sp are 98 of 154.** That is the single biggest visual weakness:
a card name and a statistic are nearly the same size, so the screens read flat.
8/9/10.sp exist only for captions added late in the build.

### Spacing: 39 literal `.dp` values, 278 uses

Top five - `8` (x48), `6` (x47), `4` (x34), `12` (x29), `16` (x20) - are **178
of 278**, so a 4/6/8/12/16 rhythm already exists informally.

Caveat when counting: large values are often **not** layout. `targetPx` on
`CardImage` is in pixels, and 110/160/180/260/300/320/520/600/720 are image
targets. Exclude `targetPx` before treating a value as a spacing outlier, or the
outlier count will be overstated.

---

## 2. Task A - name the palette - DONE 2026-09-28 (no visual change)

- [x] Add `ui/Colors.kt` with a `object AppColors` exposing the five load-bearing
      roles: `dim`, `onSurface`, `surface`, `hairline`, `legal`, `warning`,
      `danger`, `accent`, `accentOn`, `selected`.
- [x] Keep the exact hex values. **This task must not change how anything looks.**
- [x] Replace the 185 literals. Do it file by file, building between each, and
      confirm the count of remaining literals falls.
- [x] Fold the existing top-level `Palette` in `AppRoot.kt` into the same object
      (or have it delegate) so the accent is not defined twice.
- [x] Decide what to do with the 13 single-use colours: promote one, or delete it.
- [x] Add a short "add new colours here" note at the top of `Colors.kt`, because
      the reason this task exists is that colours were invented ad hoc.

**Outcome, 2026-09-28.** Done and verified as a pure rename:

- `ui/Colors.kt` added with 26 named roles over the same 25 hex values.
- **185 references across 6 files, zero stray literals** - the same count of call
  sites as before, so nothing was missed or double-replaced.
- Every value checked against the pre-rename measurement: 26/26 roles match and
  the set of distinct hexes is still exactly 25.
- Screenshot of the metagame before and after is visually identical.

Two things the measurement had wrong, corrected here: `0xFF20252B` is a **raised
panel**, not a divider as I first assumed, and it is now `SurfaceRaised`. There
is no general divider colour in the app; `DividerChart` is only used by the
analytics panel.

One trap worth recording: the first version of `Colors.kt` would not compile
because its own KDoc contained `ui/*.kt`, and the `/*` in that glob **opens a
nested block comment** in Kotlin. It surfaced as 150+ spurious
`Unresolved reference 'AppColors'` errors in other files, which looked like the
rename had broken the package. Avoid globs inside KDoc.
**Why first:** it is mechanical, needs no screenshot comparison, and every task
below gets cheaper once a colour is changed in one place.

---

## 3. Task B - a type scale - DONE 2026-09-28

- [x] Map 11 sizes onto 5 roles:
      - `caption` 10.sp - timestamps, counts-aside, the "max N" under a tile's
        + button. Absorbs the old 8/9/10.sp strays, which only existed because
        captions were added late and went ad hoc.
      - `meta` 11.sp - stats, counts, subtitles (the x49 cluster)
      - `body` 13.sp - card names, effect text, descriptions
      - `title` 15.sp - screen and dialog titles
      - `display` 20.sp - the rare large number
- [x] Define them in `ui/Type.kt` as a plain object of `TextUnit`, **not** a
      Material `Typography`, so each site changes only its `fontSize` value and
      every other `TextStyle` argument stays untouched.
- [x] Apply file by file: `DeckAnalyticsPanel` 18, `DeckMissingScreen` 9,
      `MetaScreen` 21, `BuilderScreen` 24, `AppRoot` 82.
- [x] **Checked the screens afterwards for wrapping.** 12.sp -> 13.sp was a real
      increase, so this got a screenshot, not just a build.

**Outcome, 2026-09-28.** 11 sizes and 154 call sites are now 5 roles and 154
call sites. The app now has visible hierarchy for the first time: a card name and
a statistic are no longer nearly the same size.

What the screenshots confirmed, in the card grid and the deck editor:

- The `max 1` caption, previously 8-9.sp, is now a legible 10.sp and still fits
  between the - and + buttons with no clipping, and the buttons stay on one
  baseline.
- Long card names still wrap to two lines where they genuinely need to
  (`DON!! Card (Gol.D.Roger)`) and stay on one line where they used to
  (`Charlotte Pudding`, `Trafalgar Law`, `Sabo`).
- The editor title, the `49 / 50 cards` line, the chip row, and the
  `Standard: Blocks 2-5, ban list as of 2026-09-28` caption now read as distinct
  levels instead of four near-identical grey lines.
- Nothing was truncated that was not already truncated before.

One thing this surfaced: tile heights in a row are still ragged, because a
two-line card name makes its tile taller than its neighbours. That is pre-existing
and `body` only makes it slightly more frequent. The fix is to reserve two lines
of name height on every tile, which is Task C1 territory rather than a type change.

**A trap worth recording.** Two silent failures here, both of which reported
success while replacing nothing:

1. A map keyed on literal strings like `"8.0sp"` matches nothing, because the
   source writes `8.sp`.
2. Matching `\d+(\.\d+)?sp` also matches nothing, because in Kotlin `13.sp` the
   dot is part of the *unit* syntax. The number is `13` and `.sp` follows it. The
   pattern has to be `(\d+(?:\.\d+)?)\.sp`.

The check that actually proves a bulk edit landed is counting replacements and
asserting it equals the original literal count - "compiled fine" and "0 replaced"
look identical otherwise.

---

## 4. Task C - visual changes (pick, do not do all)

- [x] **C1. Frame the card tiles.** DONE 2026-09-28. Every card tile now sits in
      a 12.dp radius frame with a 1.dp `AppColors.Hairline` border over
      `AppColors.Surface`, so the grid reads as a set of objects rather than
      illustrations floating on the background.

**Outcome, 2026-09-28.** Four things came out of this that the write-up did not
predict:

1. **The palette had no border colour at all.** `DividerChart` was documented as
   "hairline and chart gridlines", but it was only ever used for chart gridlines.
   So C1 needed a new named colour, `Hairline = 0x1AFFFFFF` - about 10% white, so
   it reads as an edge and not a line. `DividerChart`'s doc comment is now
   accurate. Distinct colour count is therefore 26, not 25.
2. **`minLines = 2` on the card name, which is what actually fixed the grid.**
   The frame made the ragged row heights obvious: tiles are laid out by
   `GridCells.Adaptive`, so one two-line name made its tile taller than its
   neighbours. Reserving both lines gives every tile the same height. Screenshot
   confirms rows now end flush.
3. **There are two card tile composables, not one.** `CardTile` in `AppRoot.kt` is
   file-private and is used by the Cards tab, Collection, related cards and the
   deck-contents grid. The deck editor has its own separate tile at the end of
   `BuilderScreen.kt` with 110.dp of art, a caption-size name, a chosen-Leader
   tick, a copy count and a per-card warning. The editor's already had a
   `Surface` background, which is why C1 looked like it had missed that screen at
   first. **Both now share the same frame.** Consolidating them is still open -
   see below.
4. **The editor tile's name is still `caption` (10.sp) where the Cards tab uses
   `body` (13.sp).** Left alone deliberately: the editor is denser by design, and
   promoting the name would change wrapping in the one grid where vertical space
   is tightest. It is a judgement call, not an oversight.

**A near-miss worth recording.** After the change, the editor tiles appeared to
have lost their copy-cap caption, which looked exactly like a regression. It was
not: `BuilderScreen.kt` shows `max N` only when the cap is *below* 4, and the
DON!! cards in that deck are all `max 4`, so by design there is nothing to say.
The lesson is the one from Task B - when a bulk change appears to break
something, go and read the code that draws it before reverting. A revert here
would have fixed nothing and re-broken the frame.
- [x] **C2. Round and frame the card image on the detail page.** DONE 2026-09-28.
      It was square-cornered and unframed, sitting centred in a full-width
      `SurfaceRaised` slab, so it floated.

**Outcome, 2026-09-28.** The real problem was not the corners, it was the box.
`CardImage` defaults to `ContentScale.Fit`, so art with a portrait ratio inside a
full-width 320dp band was being letterboxed with large empty panels either side,
and because `CardImage` paints `SurfaceRaised` on itself - the same colour as
the band behind it - those panels were invisible. The art simply floated on an
unbroken slab of grey.

So the fix was to stop letterboxing rather than to draw a border:

- The frame is now sized to the card's real proportions, 59mm x 86mm, via
  `aspectRatio`, so the art fills it exactly.
- 12.dp radius and a 1.dp `AppColors.Hairline` border, matching the tiles.
- The band is 336dp with 8dp of vertical padding, so the art is still 320dp tall -
  exactly its previous size. First attempt set the band to 320dp with no padding,
  which put the art ~7px under the app bar; widening the band kept the art size
  and fixed the crowding.
- The printing-id badge's inset went 8dp -> 10dp, because a square-cornered badge
  would poke out of a 12dp corner. Checked rather than eyeballed: the badge's
  top-left sits 2.8dp from the corner arc's centre, well inside the radius.

This is also the confirmation that image loading was never duplicated - all six
`CardImage` call sites share one loader and one cache, and this was purely
layout.
- [x] **C3. Empty states.** DONE 2026-09-28. Was a single grey line from
      `ErrorBox`.

**Outcome, 2026-09-28.** The important discovery is that `ErrorBox` was doing
**two jobs at once** - reporting real failures *and* saying "there is nothing
here". A dead-end search, an empty account and a network error were therefore
pixel-identical, and none of them said what to do next.

- `ui/StateViews.kt` splits them: `EmptyState` (icon, title, optional body,
  optional action) and `ErrorState` (same shape, but the title and icon are
  tinted `Danger`, so a failure no longer looks like an ordinary empty list).
  `ErrorBox` is deleted.
- `CardsGrid` now takes `emptyState: @Composable () -> Unit` instead of
  `emptyMessage: String`. A string cannot carry an icon or a button, and each
  caller knows a different reason for being empty.
- **10 states converted** across `AppRoot`, `BuilderScreen` and `MetaScreen`:
  card grid, Collection grid (search vs empty binder are two different states),
  decks list, binder error, no-binder, related cards, the editor's card pool,
  the Builder's deck list, and both metagame states.
- Only the 3 states that had a real next step got a button: **Clear search and
  filters** (`vm.clearQuery()`, which clears the filter tokens too, since the
  filter sheet works by editing the search text) on the card grid, Clear search
  on Collection and on the editor's pool, and the deck list points at New deck.
  The rest name the situation and the route out in prose instead of inventing a
  control that does not exist.

**Deliberately not converted:** the `"No cards yet."` placeholders inside
`DeckAnalyticsPanel`. Those are captions in a collapsed panel, not full-screen
states, and a 56dp icon in a one-line stat row would be absurd.

**One real bug found on the way, unrelated to looks.** The metagame screen did
`state.metaError ?: "No metagame data yet."` into a single grey line, so a
*failed* fetch and a *never-fetched* snapshot were indistinguishable. Those are
now `ErrorState` and `EmptyState` respectively.

**And a tooling change was needed to verify any of this.** The search box sits
behind a tap, synthetic taps are blocked, and there was no launch spec for the
query - so not one of these states could actually be reached on a device. Added
`query:<text>` to the debug router. First attempt was subtly wrong: it set the
query before the card database had loaded, so the filter ran against an empty
source and the subsequent database load reset `visible` to everything. The
search text appeared in the box while the grid stayed unfiltered. Fixed the same
way the file already handles `card:` and `editor:` - do not mark the spec handled
until its target has resolved.

Also worth knowing for future shell-driven checks: a `|` separator in a spec is
eaten by the device's shell even when quoted from PowerShell, and a literal
`3,063` written into copy reads as a typo next to the `Cards (3063)` title, so
the count is now formatted from `state.cards.size`.
- [x] **C4. Loading skeletons.** DONE 2026-09-28. For the card grid and deck
      list, instead of a bare spinner.

**Outcome, 2026-09-28.** `ui/Loading.kt`, with two placeholders and one shared
pulse. Five call sites converted; the progress **bars** were deliberately kept.

- `SkeletonCardGrid` replaces the full-screen spinner on the Cards tab, both
  Collection spinners, and it holds the real tile silhouette: same adaptive
  columns, same 8dp gutters, same 12dp frame and hairline, same 140dp of art,
  same two-line name plus set line.
- `SkeletonRowList(rows, thumb)` replaces the metagame's centred bar and the
  public decks list. `thumb` is a parameter because the rows differ - 52dp on
  `DeckRow`, none on an archetype - and it is `null` rather than 0dp so the
  placeholder does not reserve a zero-width square.
- `TileShape` went from `private` to `internal` so the placeholder reuses the
  real tile's corner radius. A skeleton with a different silhouette makes the
  swap a jump, which is the thing being removed.
- Scrolling is disabled on the grid. There is nothing to scroll to, and letting
  a placeholder scroll implies there is.

**Kept as-is, on purpose:** the determinate download bar with its percentage, the
indeterminate bars under the app bar, the deck-detail bar (that screen has real
content above it that should stay visible), and the spinner at the foot of the
decks list, which means "*more* pages" rather than "nothing yet" and is correctly
a spinner. A skeleton is for a whole screen that has nothing in it; a bar is for
progress within a screen that does.

**The pulse is one slow alpha fade on the whole grid**, not a travelling
gradient. A travelling highlight has to be clipped and phase-aligned per tile or
the tiles look like they are blinking independently, and it costs a gradient and
a graphics layer on every tile on screen. Verified on device: the grid skeleton
holds the exact 3-column layout, and the row skeleton reads as a list.

**Both placeholders were forced on for the screenshot and then reverted.** The
card database is cached, so loading is instant and a skeleton is normally
invisible - there is no way to confirm this change is right without temporarily
forcing it. The forced build and its backup were both removed, and a sweep for
`if (true ||` across the ui package confirms no hack survived.

**Also found:** the second Collection spinner
(`state.binderLoading && ownedCards.isEmpty()`) is **unreachable** - the branch
above it already returns on `binderLoading`. Left in place but made consistent
rather than deleted, and marked as dead in a comment. Makes cold start feel faster.
- [x] **C5. Accent colour.** DONE 2026-09-28 - **decision: keep the pink.** No
      code changed.

**Outcome, 2026-09-28. The accent stays, and the reason is not taste.**

`Accent` is already the single source: 8 direct `AppColors.Accent` references,
16 `Palette.primary` references, plus `Palette`'s own `primary = AppColors.Accent`,
so the whole thing is one line. Task A already paid for itself here.

Both obvious alternatives were built and screenshotted on the same grid rather
than argued about. **Both are worse, and for the same predictable reason: the
cards are red and gold.** The +/- buttons and the selected tab label sit directly
on the artwork, so a red or gold accent stops being a signal and becomes
camouflage. In the red build the "+" on a red card is genuinely hard to see, and
the filter icon reads as destructive.

The second reason is the one that settles it, and it is not visual. Sorting the
palette by hue:

| role | hue |
|---|---|
| `Danger` | 5 deg - delete, illegal, rule violation |
| `TokenAttribute` | 36 deg - card attribute tokens |
| `Warning` | 45 deg - needs attention |
| `Positive` | 88 deg - legal, complete |
| `TokenType` | 190 deg - card type tokens |
| `AccentAlt` | 235 deg - secondary, analytics series |
| **`Accent`** | **317 deg - current** |

**Red is already `Danger`, and gold is already `Warning`.** The two obvious brand
choices are exactly the two hues the app has already spent on *meaning*. A red
accent would make "delete this deck" and "this tab is selected" the same colour,
which is the one thing a semantic palette must not do.

The only large free zone is 88-190 deg (teal/blue), but `TokenType` at 190 and
`AccentAlt` at 235 already sit at its far end, so a teal accent would leave the
accent, the type tokens and the analytics series as three similar blues.

Pink at 317 deg has **48 deg of clearance from `Danger` and 82 deg from
`AccentAlt`** - the largest clearances of any bright hue in the palette. It is
the best-positioned choice available, and it is the one that stays legible over
red-and-gold card art.

**What this does not settle:** "loud" is a fair criticism of `#EE6DCA` and
nobody has argued otherwise. If the goal is to feel less loud, the fix is to
desaturate slightly or drop its lightness, which keeps the hue and every
clearance above intact. That is a one-line change and a preference rather than a
defect, so it is left to whoever owns the taste.

---

## 5. Decisions still open

- [x] **Keep the pink accent or try an alternative?** Settled under C5: keep it.
      Red and gold are both already spent on meaning - `Danger` at 5 deg and
      `Warning` at 45 deg - and both were built, screenshotted and are worse
      against red-and-gold card art, where the accent becomes camouflage.
- [ ] Should the app visually track **onepiece.gg**? It has never tried, and
      matching a site's look is a legitimate goal or an anti-goal depending on
      whether this is meant to feel like an unofficial client or a better one.
- [ ] Light theme? Every colour here is tuned for dark; a light theme is a real
      project, not a palette flip. Worth noting the dark palette is not a
      neutral base for it: `Hairline` and `BadgeScrim` are white overlays, which
      invert to black on a light surface and would need re-deriving, not reusing.

---

## 6. Ground rules for this work

- [ ] **Screenshot before and after** for any task in C. A and B are mechanical
      and build-verified; C is not, and several of these changes are a matter of
      taste where "it looks better" is the only test available.
- [ ] **Do not mix a cosmetic change with a behaviour change.** The history in
      this project is that fixes get tangled - the analytics panel, the card
      detail markup and the segment chips each arrived alongside a layout change.
      Keep them separate so a regression is attributable.
- [ ] Note that **synthetic taps are blocked on the test device**, so anything
      judged by interaction cannot be self-verified. Visual comparison can still be
      done with `screencap`, provided the display is awake.
