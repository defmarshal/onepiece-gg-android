# OnePiece.gg Android — Task List

Working action list. See `README.md` for install/build, and `NOTES.md` for the full
research write-up (API contract, verified behaviours, bug history).

**Status: 389/389 tests passing. Installed on device. No open compile errors.**

---

## Build / test commands

```powershell
$p = "C:\Users\defma\projects\onepiece-gg-android"
$env:JAVA_HOME = "C:\Users\defma\.dotgg-tools\jdk21\jdk-21.0.12.1+1"   # no JDK on PATH by default
& "$p\gradlew.bat" -p $p :app:testDebugUnitTest    # 394 tests, incl. live-API tests
& "$p\gradlew.bat" -p $p :app:assembleDebug       # -> app\build\outputs\apk\debug\app-debug.apk
adb install -r "$p\app\build\outputs\apk\debug\app-debug.apk"
```

The emulator here cannot launch any activity (`surfaceflinger` crash-loops on
the only installed image). Test on the physical phone over wireless adb.

Debugging notes: `adb exec-out screencap` corrupts the PNG through PowerShell —
use `adb shell screencap` then `adb pull`. A blank screenshot usually means the
**screen is off**. `$null.Count` returns **1** in PowerShell 5.1, not 0, which
produced one false positive in the deck research.

**Use the edit tool for code changes.** Three shell-based edits silently failed
on line-ending mismatches during this project, leaving dead UI, a mangled
`TASKS.md` and duplicated imports.

---

## 1. Done

### Data loading and performance
- [x] **Cache the card database — verified on device.** Was re-downloading
      **5,230,704 bytes** on every cold start. Now 3,063 deduped cards written
      atomically to `files/opgg-cards.json` (**2,674,028 bytes**), painted on
      launch, with **no network request at all** while the copy is under an hour
      old, and an `If-Modified-Since` check returning **304** after that.
- [x] **Fix IPv6 DNS, which prevented the cache ever being written.** The phone
      has no global IPv6 route; its resolver fails the AAAA query outright
      instead of falling back to A, so OkHttp threw `UnknownHostException` on
      every request while `curl` worked. Added `PreferIpv4Dns` plus
      `retryingNetwork` so a single DNS blip cannot poison the session.
- [x] **Shrink the cache 6.2 MB → 2.7 MB (-57%).** Two causes: `@Transient` had
      no import and resolved to `kotlin.jvm.Transient`, so `searchBlob` was being
      serialised; and the cache stored 5,540 raw printing rows instead of the
      3,063 deduped ones. Covered by `CacheEnvelopeTest`.
- [x] **Permanent cache/network logging** — `OpggCache`, `OpggDns` and `OpggNet`
      tags cover cache hits, writes, 304s, downloads, DNS failures and retries.
- [x] **Switch to `?mode=indexed`** — columnar JSON, **3,148,059 bytes (-40%)**.
      Positional rows zipped against `names` **by name**, not index. Verified: all
      5,540 cards compared field by field against the object format, zero
      mismatches.
- [x] **Download progress bar** with percentage and "first run only" copy.
- [x] **Main-thread stall** — the multi-MB decode and dedup ran on
      `Dispatchers.Main`; moved to `Dispatchers.Default`.
- [x] **Per-recomposition O(n) work** — `visibleCards` was a getter read 2–3× per
      frame; `freeTextMatch` allocated ~24,500 strings per pass. Now stored in
      state, debounced 140 ms, matched against a precomputed `searchBlob`.
- [x] **Request timeout 60s → 5 min.** The phone's link to `api.dotgg.gg`
      measured **~3.9 KB/s**, so 5.2 MB needed ~22 minutes.
- [x] **Autocomplete re-sorted 3,063 names per keystroke** — input was already
      sorted; iterate directly.
- [x] **Logging** added to cache read/write and the refresh catch, which had been
      swallowing failures.
- [x] **Live tests skip when throttled** via `@Before` + `Assume`.

### Cards and collection
- [x] **Fix the `_rN` bug** — 3 of 361 owned cards dropped. Replaced
      suffix-stripping regex with an exact printing→canonical index from the
      server's own `id_normal`.
- [x] **Fix reprint holdings being invisible** — 6 more cards (of 361) showed no
      owned badge, and editing one would have created a duplicate row. Added
      `ownedByNormal` (collapsed by canonical id) and made the stepper write to
      the printing actually held.
- [x] **Fix collection writes being rejected** — the app used
      `savebindercard`, which needs a real binder and returns
      `{"error":true,"error_text":"Binder not found."}` at binder 0. Most
      accounts have no binder. Now routes binder 0 → **`savebatchcollection`**
      (no binder needed, what the site itself calls) and a named binder →
      `savebindercard`. Verified working on device.
- [x] **Check both failure modes of the write response** — `error: true`, *and*
      `error: false` with a non-empty per-card `errors` array. The second was
      returning `error:false` for an unknown card id.
- [x] **Collection tab made identical to the Cards tab** — one shared `CardsGrid`,
      same tiles, badges, search field and filter chips. Only the list differs.
- [x] **Typed search** mirroring the site's `filterConfig` — 12 token families,
      AND across fields / OR within, negation, quoted phrases, unknown tokens
      flagged. Filter sheet writes into the query string, so chips and tokens are
      one mechanism.
- [x] **Autocomplete** — field names, valid values, card names; ranked so an
      exact canonical prefix wins.
- [x] **Sort, persisted** via DataStore. Mirrors the site's `sortConfig` plus a
      descending toggle. Applies to both grids, computed inside the debounced
      filter step. Nulls always sort last, in both directions.

### Card detail
- [x] **Full-screen destination** instead of a bottom sheet, wrapped in a
      `Surface` so `LocalContentColor` is not `Color.Black` (it had rendered
      black-on-black).
- [x] **Reprint switcher** — every printing with its own art, price and an
      owned dot; tap to switch; pink id badge when not the base printing.
- [x] **Per-printing binder detail** with an explicit "Editing printing …" label.
- [x] **Price panel** — TCGplayer and Cardmarket, normal and foil, coloured
      deltas; Cardmarket link opens the browser (was a dead button).
- [x] **Swipe left/right** to page through whichever list you came from.
- [x] **Related-cards button** → full related-list screen → tapping a card pushes
      a new detail page, so back returns to the list.
- [x] **Related index**, two tiers: meta co-occurrence for the 443 covered
      cards, same-crew fallback labelled as such. Printing ids normalised, a card
      is never its own partner.
- [x] **`CardImage` helper** pinning Coil's request size, fixing a blank hero
      image that only appeared after scrolling.

### Navigation
- [x] **Real back stack** (`OpggScreen`), replacing booleans. Chain works:
      Tabs → Card → Related → Card → back → Related → back → Card → back → Tabs.
- [x] **Swipe replaces the top stack entry** rather than pushing, so back needs
      one press regardless of how far you swiped.
- [x] **`BackHandler`** so system back no longer exits the app from a detail page.
- [x] **Grid scroll state hoisted** above the branch so back restores position.
- [x] **Quick add/remove on card tiles** — `−` bottom-left, `+` bottom-right,
      with a tappable `STD n` / `FOIL n` pill for the copy type, since a bare `+`
      is ambiguous on a card that has both. Writes to the printing held.
- [x] **Snackbar for messages** — `message` was being consumed and thrown away,
      so "log in first" and every save failure were invisible.
- [x] **Sort applies to the Collection tab too** — it previously only sorted the
      Cards list, so the control silently did nothing there.
- [x] **Survive rotation** — tab, sheets and the navigation stack are
      `rememberSaveable`; the stack is saved as tags and rebuilt against the card
      database. Grid scroll index is persisted by hand, because
      `rememberSaveableLazyGridState` does not exist in this Compose version.
- [x] **Load the collection on launch** rather than waiting for refresh.

---

## 2. Outstanding - requested, not started

### 2.00 Offline writes, synced on reconnect — PLANNED 2026-11-02 (deferred)

Requested by the user; design and estimate agreed, **no code written yet**. Put
aside in favour of other work after §2.31 made stores readable offline.

**Wanted:** with the phone offline, `+/-` and wishes (later: card moves) still
work locally; when the phone is back online they are pushed to onepiece.gg
automatically. Today the app refuses writes offline and greys the buttons out.

**Settled design**

- New `PendingWrites` store on disk (versioned, atomic, per account), keyed
  `(storeId, printingId)` → absolute `{normal, foil, wish}`. Absolute counts
  coalesce repeated taps and make replay idempotent.
- Overlay pending values onto every read (live and `StoreCache`) before
  grouping, so a refresh or restart cannot visually revert an edit.
- `setOwned` / `quickWish` / `setWished` keep their optimistic update; on a
  network failure they enqueue instead of rolling back. Remove the "you're
  offline, your collection cannot be changed" branch.
- Flush on app start, connectivity regained, manual refresh and a **Sync now**
  action. Payloads are composed at flush time and **must echo `wish`** -
  `savebatchcollection` zeroes every field it does not carry.
- UI: "N changes waiting to sync" + Sync now; the cached-store banner changes
  from "changes are disabled" to "will sync when online".

**Estimate:** **10–12 h** for counts + wishes (~1.5 days). Bulk moves add
**3–4 h** because `movecards` is not idempotent and needs an ordered op log.
Binder CRUD offline is **out of scope** — a binder created offline has no
server id, which breaks every queued write into it.

**Risks**

- Last-write-wins: queued absolute counts overwrite edits made on the website
  while the phone was offline.
- Rate limiting: saves are one card per request; a long offline session bursts
  and trips Cloudflare. Probe the `savebatchcollection` batch payload or
  throttle the flush.
- A binder deleted/renamed elsewhere invalidates queued writes; drop them and
  say so instead of looping.
- Session expiry while queued: keep the queue, resume after re-login.

**Touched files (expected):** new `data/PendingWrites.kt`; `AppViewModel`
(`setOwned`, `quickWish`, `setWished`, `moveCards`, `loadBinderAttempt` overlay);
`StoreCache` merge; `AppRoot` banner + sync action; new `PendingWritesTest`.

### 2.0 Visual review of every screen - done, six bugs found
Screens were reviewed from device screenshots. Synthetic input is blocked on
that phone ("USB debugging (Security settings)"), so a debug launch argument was
added: `am start -n gg.opgg.android/.MainActivity --es screen <spec>`, spec being
`tab:0..3 | card:<id> | related:<id> | deck:<slug> | deckfirst`.

- [x] **Related list permanently empty** - `remember(card.key)` cached the empty
      result computed before the meta index arrived. Now keyed on index coverage.
      Verified showing "11 cards that share tournament archetypes".
- [x] **Related copy contradicted itself** - "0 cards from the same crew".
- [x] **"No related cards found." floated mid-screen** - it used `ErrorBox`
      (`fillMaxSize`), so it centred in the leftover space.
- [x] **Leaderless decks showed a blank grey box** - now falls back to the
      deck's first card.
- [x] **Editor scroll reset on card detail** - **confirmed fixed by the user.**
      `AppRoot.kt:316` renders exactly one screen per stack entry, so pushing a
      card detail left the composition and discarded the editor's state object.
      Fixed by hoisting `rememberLazyGridState()` into `AppRoot` and passing it
      into `DeckEditorScreen` as a parameter.
- [x] **Leader change was a one-way door** - reported by the user: on a Red/Blue
      deck, switching to a mono-Red Leader filtered every Red/Blue Leader out of
      the Leader list, so the wider Leader could never be chosen again. Two
      causes, both fixed:
      - **Leaders were colour-filtered with the main deck.** The colour rule
        limits the *main deck*; a Leader *defines* that palette rather than
        obeying it. Leaders are now exempt. Verified on device: with Sanji (Red)
        set, the Leader list still offers Ace & Newgate (Blue) and Luffy & Ace
        (Red/Black).
      - **Removing a Leader orphaned `leaderId`.** The `-` control dropped the
        Leader from `cards` but left the id set, so the deck validated as "no
        Leader" while the editor still resolved colours from the old Leader -
        the pool stayed stuck on Red with no way out. `LocalDeck.withCount` now
        clears `leaderId` when its own card reaches zero, and the editor resolves
        the Leader from the cards actually present, so a stale id cannot wedge it
        again. 4 new tests.
- [ ] **`PersistGridScroll` is redundant - remove it.** Checked against the
      Compose 1.8.2 sources: `rememberLazyGridState` is itself
      `rememberSaveable(saver = LazyGridState.Saver)`, so it already survives
      rotation and process death. The manual index bookkeeping adds a second
      restore path that can fight the built-in one, and it can silently drop the
      position because it calls `scrollToItem` once, before the grid has measured
      its items. Same treatment for `cardsGridState` / `collectionGridState`.
- [ ] The greenish rectangle at the left edge of every screenshot is a **HyperOS
      system overlay**, not this app - confirmed by reproducing it on the Settings
      screen. No action.
- [ ] Consider removing the debug `screen` argument once review is finished.

### 2.1 Deck builder - local-first build is DONE, server sync still blocked
**Full plan in [`DECK_BUILDER.md`](DECK_BUILDER.md).** The local builder is
**implemented and tested**: rules engine (`DeckRules`, 13 tests), deck code +
CSV codec (`DeckCode`, 10 tests), atomic local store (`LocalDeckStore`), and
the editor UI. DON cards and server sync were deliberately left out of scope.

- [x] **The open questions in `DECK_BUILDER.md` section 2 are resolved** - the
      ruleset is `Standard` (`format: "OP13"` is a set code, not a ruleset), and a
      leader's `Color` *is* the deck's whole legal palette. Both verified against
      150 published decks: 69 mono, 63 dual, zero violations.
- [x] **Placement decided: B** - segmented control inside the Decks tab. A fifth
      tab was tried and did not fit.
- [x] **Deck-code import/export** - `${count}x${id}` lines, case-insensitive by
      card id, with 10 passing tests.
- [x] **Server sync was thought to be blocked, then solved.** `savedeck` is not
      create-only: its selector is `deckname`, which the nine earlier probes never
      sent. Re-POSTing a `deckname` updates in place. Fixed 2026-11-19 - see §2.43.
      `deletedeck` still returns HTTP 200 with an empty body and removes nothing.
- [x] **The repeated "no Leader" text on every tile is gone** - it was the same
      root cause as 2.8 and was fixed by the same `TileAvailability` change.
### 2.2 "Cards I still need" - deck view DONE; set view not started
What a deck runs that the collection does not cover. The counting was already
available - `ownedByNormal` and `printingToNormal` both existed - so this is
mostly presentation over existing data.

- [x] **Deck view shipped.** Long-press a deck -> "Cards I need (N)", where N is
      the number of copies short, which answers "can I play this tonight"
      without opening anything. A complete deck reads "complete" in green.
- [x] A dedicated screen listing each missing card with art, name, have/need and
      the shortfall, **biggest gap first** - that is the card worth buying first.
      Tapping a row opens the card, matching the grid everywhere else.
- [x] Counting is a pure function, `data/DeckDiff.kt`, with **15 JVM tests**.

Two judgement calls, both deliberate and both pinned by tests:

- [x] **Printings collapse.** The deck is authored in printings, but holding
      `OP12-034_p2` does satisfy a requirement for `OP12-034` - you would play
      the parallel. Counting per printing would report you 4 short of a card you
      own four printings of, which is a false answer to "what do I buy".
- [x] **The Leader counts as something you need.** It sits outside the 50, but
      without it the deck cannot be played, so it is a real requirement. It is
      flagged in the UI.
- [x] Foil counts as owned. A foil is a playable copy; the question is whether
      the card can be fielded, not whether it is standard.
- [x] An unknown printing id is treated as its own card rather than dropped, so a
      deck referencing something the database has not seen still reports.

- [x] **Never claim a verdict from an unloaded collection.** The menu originally
      read "complete" for every deck because an *empty* `ownedByNormal` map makes
      every card look owned. The label now distinguishes "sign in to check",
      "checking your collection...", "collection unavailable", "collection
      empty", and only then a real verdict. The same gate guards the screen, so
      a green "You own everything this deck needs" cannot be a false positive.
- [ ] **The user's report is not yet explained.** They saw "complete" on every
      deck. Diagnostics confirmed the counting itself is right (with an empty
      collection every card reports missing), and one earlier run *with* a
      loaded 359-entry collection genuinely reported every card covered. Two
      explanations remain and they cannot be separated while the device is
      offline: either the local decks really are fully owned (they were built
      with Owned only, which makes that true by construction), or the collection
      is loading partially and the Owned-only caps seen earlier ("max 1",
      "max 2") were from an incomplete load. **Needs one check with the device
      online:** open a deck in Owned only, note a card showing "max 1" or
      "max 2", then long-press that same deck. If it reports complete, the
      collection is loading partially and that is a real bug in the binder
      fetch worth chasing.
- [ ] **Set view not started** - the original spec also asked for a diff against
      a *chosen set* for completing a booster. That needs a target (how many
      copies per card counts as "complete"), which is a product decision: 4 of
      every non-leader, or something set-specific.

### 2.3 Meta / tier list screen - DONE 2026-09-28
Which decks are actually being played, and how well. The endpoint was already
being called - the app used it to build the related-cards index and threw the
rest away - so the plumbing existed and only the model, retention and screen
were missing.

- [x] `data/MetaSnapshot.kt`, moved out of `RelatedIndex.kt` and widened from 8
      fields to the ~30 the payload actually carries. 157 KB, 51 archetypes,
      737 decks, 33 events, 1,448 matches. **Works signed out.**
- [x] **14 JVM tests** plus the existing live test, which decodes a real
      snapshot.
- [x] Reachable as a fourth segment of the Decks tab (Public / Mine / Builder /
      Meta). Not a fifth bottom tab - five did not fit, and a fifth segment
      would have hidden Meta off-screen.      **The segment switcher was a real bug, found by the user, and is now a
      single enum** (`DeckSegment`) rather than two booleans. Tapping "Public"
      while on Meta used to show the Builder, and tapping it again went back to
      Meta, because the handler toggled both flags. With one enum the state
      "builder and meta both open" is unrepresentable, and tapping a chip
      selects it rather than toggling it off. `data/DeckSegment.kt` with
      `tap()` and **12 tests** in `DeckSegmentTest.kt`, including a walk of
      every segment in the order a user would try it, asserting it always lands
      where tapped. Worth doing rather than shipping another fix I cannot tap to
      verify: synthetic input is blocked on this device.
- [x] Ranked by `rank`, falling back to share then name so the order is stable.
      Tier is a **badge, never the backbone**: it is present on only 9 of 51
      archetypes, so a tier-led list would be 42 empty rows.
- [x] **Field benchmarks shown inline** ("top 8 28% (field 25%)"). A tier is
      written against the field, so 20% share earning "S" means nothing without
      the field's own 25% beside it.
- [x] Expandable rows: `tier_reason`, `research_note`, recent finishes, staples
      with inclusion rates, and sources. **Collapse to read the rest of the list.**
- [x] Refresh in the app bar, and the previous snapshot stays on screen if a
      refresh fails rather than blanking.

Three things the data taught me, all of which I had wrong first:

- [x] **`form` is not prose.** It is a list of tournament finishes
      (`{event, place, players, date, entries, cut}`). I had assumed it was a
      deck write-up from the key name and my first measurement even called it
      "long and the most interesting field". The live test caught it.
- [x] **`field_top8_rate` is flat on the snapshot**, not nested under a
      `field` object. Nesting it parses to null and loses the benchmarks
      silently.
- [x] **`evidence[].date` is a date string** (`2026-09-14`) while `form` and
      `best_decks` dates are epochs. Mixed types in one payload; getting it wrong
      throws at decode time.
- [x] **Staples are real card entries, not text.** Each is a thumbnail + name +
      copies + inclusion rate, resolved through the shared lookup and opened
      through the same card page as the Cards and Collection tabs. The swipe list
      is the archetype's own staples, so paging a card page walks that deck's core
      rather than the whole database. Falls back to plain text if an id is not in
      the card database.
- [x] **How to refresh:** the refresh icon in the app bar, which calls
      `vm.refreshMeta()`. Two caveats worth knowing:
      - it re-runs `loadRelatedIndex()`, so refreshing the meta also rebuilds the
        related-cards index. Correct, but more work than a meta-only fetch.
      - the snapshot is **not cached on disk**; it lives in `UiState` for the
        session, so every cold start re-fetches ~157 KB. The window is 30 days
        and changes slowly, so a disk cache plus pull-to-refresh would be the
        improvement, and neither is built yet.
- [ ] **Staples not verified rendered.** The device display went to sleep and
      would not wake over adb, so the new card entries have been compiled and
      installed but never seen. Needs a look once it is awake.
- [ ] **Matchups are still missing.** `available.matchups` is true and
      `matchup_notes_total` exists, but no notes array ships in the payload, so
      it needs a second call that has not been found. Deliberately left out
      rather than guessed at.
- [ ] **The snapshot only covers the staples** - about 446 of 3,063 cards - so
      this describes the competitive slice, not the game. The archetype list also
      rotates: 51 one day, 53 the next.


### 2.4 Housekeeping
- [ ] **Migrate to `navigation-compose`** (declared, unused). Hand-rolled
      navigation caused four separate bugs.
- [ ] **Move `ownedGroups` / `ownedCount` / `visibleDecks` out of `UiState`
      getters** into computed state. Unstable list identity already caused one
      bug.
- [ ] **Split `AppRoot.kt`** — 1,579 lines, four tabs, four screens, three sheets.
- [ ] **Cache the deck list** (~600 decks, paging is the only listing mechanism).
- [ ] **Tests for the deck data layer.**
- [ ] **Collection value summary** — spend from `price`/`foilPrice`, completion
      per set.
- [ ] **Price history charts** via `getprices`.
- [ ] **CSV import/export** for the collection (`importcollection` /
      `exportcollection` exist).

### 2.5 Deck analytics - DONE 2026-09-28
Collapsible panel at the **top of the deck editor**, above the card grid, showing
what a deck is made of. Collapsed by default so the grid still gets the screen.
**Shipped and verified on device** (both collapsed and expanded states screenshotted).

- [x] **Cost distribution.** Histogram over the cards in the deck plus the mean.
      Bars are scaled against the fullest cost, not the deck total, so a single
      9-drop does not vanish next to 12 one-drops.
- [x] **Counter totals**, bucketed exactly as the game does it, from the game's
      own `extraCurves` config: `["0", "1000", "2000"]` with
      `bucket = Counter >= 2000 ? 2 : Counter >= 1000 ? 1 : 0`. So: how many cards
      give no counter, +1000, or +2000. Cards with +3000 or higher belong in the
      2000+ bucket - tested explicitly.
- [x] **Character / Event / Stage distribution**, with the Leader excluded and
      reported on its own since it sits outside the 50.
- [x] **Colour spread** and a **Power curve** (1k-8k+, same `extraCurves` shape).
      Both were on the nice-to-have list and are in.

Implementation:

- [x] Pure functions in `data/DeckAnalytics.kt` with JVM unit tests in
      `DeckAnalyticsTest.kt` (17 tests). No Android types.
- [x] Recomputed only when the deck changes - `remember(deck, printingsByNormal)`,
      matching how the rules check is already keyed.
- [x] **Expansion state lives in the ViewModel** (`editorAnalyticsExpanded` in
      `UiState`, set through `setEditorFlags`), not `remember` in the editor -
      the same trap that made the filter chips reset on back.
- [x] The Leader is excluded from every main-deck figure, per the 50 + 1 = 51 rule.

Design decisions worth keeping:

- [ ] **Deliberately offers no opinion on whether the numbers are good.** Measured
      across 619 complete published decks, blocker count is strongly multi-modal
      (peaks at 4 and 8; 9.5% of decks run none at all), so any "you should have
      ~6 blockers" hint would be wrong as often as right. It reports shape only.
- [ ] Card-type split is nearly a constant across real decks (81/17/2), so it is
      the lowest-information row in the panel. It is there because it was asked
      for, not because it discriminates.
- [ ] Unparseable costs are counted and disclosed ("N card(s) declare no cost")
      rather than silently treated as cost 0, which would corrupt the curve.
- [ ] Still open: the *ban list* and the *Block Number* regulation, both of which
      are hard legality rules this panel does not consider. See
      `DECK_THEORY.md` sections 3, 4 and 9.

### 2.6 Raw markup in card text - DONE 2026-09-28
Card rules text contains the site's own markup, which used to render literally
as `<br>` and `{Straw Hat Crew}`.

**The markup is not HTML**, which is why this is a small parser rather than an
HTML renderer. Measured across the live card database, `Effect` and `Trigger`
together contain:

| Token | Count | Meaning |
|---|---|---|
| `<br>` | 1,737 | line break between keyword blocks |
| `<Slash>` | 42 | an **attribute** reference, e.g. "by `<Slash>` cards" |
| `<Strike>` | 6 | attribute |
| `<Special>` | 1 | attribute |
| `{...}` | 61 distinct | a **crew** name, e.g. `{FILM}` |
| `[...]` | many | keywords, e.g. `[On Play]`, `[Blocker]`, `[DON!! x1]` |
| `&#039;` | 207 | a plain apostrophe |

- [x] **Correction to the original spec: there is no `{COLOUR}` form.** All 61
      brace tokens are crew names, checked against the `Type` field of all 5,300
      non-DON cards. An earlier draft of `DECK_THEORY.md` claimed a matching
      colour form; that was wrong and has been corrected.
- [x] `<br>` becomes a real line break, so the keyword blocks separate the way
      the printed card does.
- [x] Keywords, crews and attributes are styled: keywords bold pink, crews cyan,
      attributes orange, each with a faint background. This matches the printed
      card, which highlights the same tokens.
- [x] **A "pill" is a span background, not a row of boxes.** A box per token
      would break mid-sentence, change the line height and destroy the paragraph.
- [x] `&#039;` is unescaped, so "K.O.'d" no longer shows as "K.O.&#039;d".
- [x] One helper, `data/CardText.kt` (pure) + `ui/CardTextBody.kt`, so no screen
      can drift back to `Text(card.Effect)`.
- [x] **13 JVM tests** in `CardTextTest.kt` over **real payload strings** taken
      from the database (`OP13-025`, `OP03-008`, `OP06-096`, `ST22-001`), not
      invented ones. Includes a structural round-trip test asserting no text is
      lost, plus cases for an unclosed `<`, an unknown entity and empty tokens.
- [x] Checked the other fields: `Effect` and `Trigger` carry the markup,
      `CardSets` a little, and `Type` / `name` / `rarity` / `Attribute` none.
- [ ] **Only one render site exists** - the card detail page. Deck detail and the
      deck editor show card counts, not rules text, so the spec's "everywhere"
      reduced to this one place. Anything added later must go through
      `CardTextBody`.

### 2.7 Deck actions menu - rename / duplicate / delete - DONE 2026-09-28
Long-press a deck in the builder list for **Rename**, **Duplicate** and
**Delete**. A tap still opens the editor, so the long-press is additive.

- [x] **Rename** - dialog with a text field pre-filled with the current name.
      Blank or whitespace-only is rejected with an inline message and Save
      disabled, falling back to the existing name rather than producing a
      nameless deck.
- [x] **Duplicate** - a fresh id, its own card map, both timestamps reset, and
      the copy **opens immediately** because a duplicate you then have to hunt
      for is no use.
- [x] **Delete** - asks first. Destructive, with no undo.
- [x] **Removed the standalone delete button that was on every row.** It
      deleted with no confirmation at all, which is the exact hazard this menu
      removes. Tap no longer deletes anything; delete is now long-press, then
      confirm.
- [x] **Naming rules are pure functions** in `data/LocalDeckOps.kt`:
      - blank name -> falls back, never nameless
      - `Beast` -> `Beast (copy)`, and a second copy -> `Beast (copy 2)` with
        the number *inside* the brackets, not `Beast (copy) 2`
      - numbering skips names already taken, so a gap is not filled wrongly
      - the copy's `cards` map is rebuilt, never aliased to the original
- [x] All three go through `AppViewModel` -> `LocalDeckStore`, which already
      writes atomically. **No new persistence path.**
- [x] **12 JVM tests** in `LocalDeckOpsTest.kt`, including a round-trip edit test
      proving editing a copy leaves the original untouched.
- [x] Dialogs are parented at **screen level, not per row** - a dialog inside a
      `LazyColumn` item is recreated mid-scroll and takes the dialog with it.
- [x] The per-row menu's open flag is `remember(deck.id)`, which is safe here:
      it only has to survive a recomposition, and scrolling the row away should
      close the menu rather than leave one anchored to nothing.
- [x] **Local decks only.** Server deck deletion is a no-op
      (`deletedeck` returns HTTP 200 with an empty body), so no server-sourced
      deck is ever offered a Delete that silently does nothing.
- [ ] **Not verified on device:** the long-press itself and the dropdown, because
      synthetic input is blocked. The list and the rename dialog were both
      screenshotted and are correct; a `builder` debug spec was added so the list
      is reachable without a tap. Worth one manual long-press to confirm the
      menu anchors where expected.

### 2.8 Editor tile noise - DONE 2026-09-28
With the **In deck** filter on, tiles were dimmed and captioned with remarks
("you only own 4 of this", "Deck has 51 cards…"). Worse, the same prose was
repeated on all 50 tiles at once whenever the deck had no Leader or was full.

- [x] **No remarks and no greying when In deck is on.** Verified on device with a
      49-card deck: tiles at full opacity, no captions.
- [x] **The rules are still enforced, structurally rather than in prose.** The
      `+` button is disabled at the 4-copy cap and when the deck is full, so the
      limit is legible from the control itself instead of a sentence. Removing is
      always available.
- [x] **One fix for both problems**, as the spec asked, via a new pure function
      `data/TileAvailability.kt`. It returns a typed `TileBlock` rather than a
      message, and the important part is `deckLevel`: `NO_LEADER`,
      `MULTIPLE_LEADERS` and `DECK_FULL` are properties of the *deck* and are
      already stated in the status line, so they never render per-tile text and
      never dim anything. Only a card's own problem (`AT_OWNED_CAP`,
      `AT_COPY_LIMIT`, `COLOUR_NOT_ALLOWED`) is captioned.
- [x] The old path called `DeckRules.canAdd` per tile and printed its message,
      which is what made a deck-wide condition look like a per-card one.
      `canAdd` is still used and still tested; the tiles no longer use its text.
- [x] **13 JVM tests** in `TileAvailabilityTest.kt`, covering an under-owned
      card, an owned cap of zero, a full-copy printing, a wrong-colour card, a
      dual-colour Leader, a full deck, no Leader, and a Leader tile (never
      blocked, since setting one replaces the current Leader). Includes a
      cross-check that [TileAvailability] agrees with `DeckRules.canAdd` on
      addability across 5 deck shapes x 3 cards, so the two cannot drift.
- [x] **Tile button alignment and cap placement.** The "+" slot was a Column
      holding the "+" plus a "max N" caption underneath, so the slot was taller
      than the "-" and the Row's vertical centring lifted "+" above it. The cap
      now sits **centred between the two buttons** rather than tucked under the
      "+", so it reads as a property of the tile rather than of the button, and
      because an 8sp label is shorter than the 26dp buttons it cannot change the
      row height at all. No fixed-height slot is needed - `+` and `-` share a
      baseline whether or not a cap shows, and tiles differing only by a cap line
      up with each other across the grid too.
- [x] **Caption redundancy removed.** The "You only own N of this." body caption
      repeated the "max N" under the "+" button, so `AT_OWNED_CAP` now carries no
      label. `AT_COPY_LIMIT` was shortened from "Max 4 copies of a printing." to
      "max 4" because the long form wrapped to two lines and made those tiles
      taller than their neighbours.
- [ ] **Not addressed:** tiles in the same grid row can still differ in height when
      one carries a *block* caption (e.g. "max 4" for the 4-copy limit) that the
      others do not, so their button rows do not line up across tiles. Fixing that
      means a constant-height tile body, which also has to cope with card names
      wrapping to one or two lines.

### 2.9 Ban list + Block Number regulation - DONE 2026-09-28
Tournament legality, which the builder was silently ignoring: a deck built on
the most-played Leader in the meta reported as perfectly legal, and Block 1 cards
were treated the same as current ones.

- [x] **`data/Regulation.kt`**, pure and Android-free, with **21 JVM tests**.
- [x] **Verified the API has no ban status**, so this had to be hand-maintained:
      ten candidate endpoints probed, every one answered HTTP 200 with
      `Hacker! Go home!` (this API's reply to an unrecognised action). No card
      field mentions ban, and `OP14-020` looks ordinary with `BlockIcon=4`.
- [x] **The ban list carries a visible "as of" date** in the editor. It is
      hand-kept, so it will drift; printing the date is what stops anyone
      trusting it blindly.
- [x] Ban stored **canonically** so parallels are covered, and `isBanned` strips
      the reprint suffix itself rather than trusting callers to.
- [x] **Block window computed from the date**, never hardcoded: Bands 2-5 now,
      3-6 from April 2027, with no code change.
- [x] **Empty `BlockIcon` settled empirically** - tournament decks run such cards
      (1.8% of copies), so it means legal. Unparseable values are also legal,
      because guessing "restricted" would outlaw real cards.
- [x] **Extra Regulation** skips both bans and the window, so banned and old cards
      stay playable.
- [x] Reported through the existing rules engine as `BANNED_CARD` and
      `BLOCK_NOT_LEGAL`, so there is still one place legality is decided.
- [x] Picker **hides banned cards** in Standard (the notice says they "cannot be
      included in any deck") but keeps Block-window cards **visible**, because
      the window moves every April and hiding a quarter of the pool would be
      worse than explaining it.
- [x] **7 new rules tests**, including that a legal 50-card deck still validates
      under Standard, so this cannot quietly outlaw everything.
- [x] **Verified on device.** Standard / Extra chips, the "as of" line and the
      collapsed issues section all render correctly.
- [x] **Two layout bugs found by looking at it and fixed:** the chip row was a
      plain `Row`, so six chips overflowed onto a second line that the parent
      `Column` clipped - the Standard chip was half cut off. It is now a
      `LazyRow` that scrolls sideways, matching the pattern already used by the
      filter panel.
- [x] **Issues are collapsible**, showing "3 issues  show" as one line. This
      was needed because a deck with a wrong-coloured Leader reports one
      violation per card, and earlier that buried the grid under dozens of
      identical lines. Expansion state lives in the ViewModel, not `remember`,
      so it survives navigating to a card and back.
- [ ] **Block 1 at 3.1% of tournament-deck copies is still unexplained.** Most
      likely the public deck list spans older events, but the API has no date
      filter, so the window rule is probable rather than proven.


### 2.10 Deck export/import - site import shape DONE 2026-09-28
The site does **not** accept the text it exports. Confirmed by the user against
a real round trip:

| Direction | Shape |
|---|---|
| site exports | `1xEB01-015_r1`, `1xP-069_p3`, `1xST02-007_p4` |
| site **imports** | `1xEB01-015`, `1xP-069`, `1xST02-007` |

So a deck copied off the site could not be pasted back in, and a deck built in
the app could not be moved to the site. The rule is: **strip the `_pN`/`_rN`
reprint suffix, keep the set prefix.** Nothing else changes.

- [x] `DeckCode.exportForSite(deck)` strips `_[p|r]<n>` and nothing else.
- [x] **Kept separate from `export()` on purpose.** The full printing id is what
      `LocalDeck` stores, so the existing export is the lossless one and is what
      our own importer should be fed. Collapsing printings is a one-way trip.
- [x] Reachable from the deck's long-press menu as "Copy deck code for
      onepiece.gg", next to the plain "Copy deck code". Menu items rather than a
      second icon on the row: two near-identical copy icons are ambiguous.
- [x] **Collisions are reported, not merged.** If a deck holds both a printing
      and its reprint of one card number, both collapse onto the same id and the
      site cannot tell them apart. Merging would produce a count that breaks the
      4-copy rule, so the app says so instead of quietly producing a wrong deck.
- [x] **10 new tests**, including a round trip proving the site shape is still
      accepted by our own importer, so a deck that has been through the site can
      come back.
- [x] Validated against the user's real 35-line site export: 51 copies, 3
      suffixes stripped, no collisions.
- [ ] Not yet tried end to end by pasting the app's output into the site. The
      rule came from the user, not from the site's code - the cached bundle is
      only the main chunk and the deck importer is in a lazily loaded page chunk.


### 2.11 Cosmetics - two of the mechanical tasks done
Tracked in full in [`COSMETICS.md`](COSMETICS.md), which carries the measured
baseline. Summary of what that measurement found:

- [x] **Name the palette.** `ui/Colors.kt`, 26 named roles over the same 25
      hexes, **185 references across 6 files, zero strays**. Verified as a pure
      rename: every value matches the pre-rename measurement and the distinct
      colour count is still 25, with a before/after screenshot confirming no
      visual change. `Palette` now delegates to `AppColors`.
- [x] **A type scale.** `ui/Type.kt`, 5 roles (`caption`/`meta`/`body`/`title`/
      `display`) replacing 11 literal sizes at **154 call sites, zero strays**.
      11.sp and 12.sp had been 98 of 154 uses, so a card name and a statistic
      were nearly the same size, which is why the screens read flat. `body` went
      12 -> 13.sp, so the grid tiles and editor were screenshotted to confirm
      nothing newly clips or wraps.
- [ ] **A spacing scale.** 4/6/8/12/16 already account for 178 of 278 `.dp` uses,
      so a rhythm exists informally. Note that large values are often `targetPx`
      on `CardImage`, which is pixels, not layout.
- [x] **Visual changes - C1 done, the rest open.** Each needs a before/after
      screenshot because it is judged by eye.
- [ ] **C1 done.** Card tiles are framed: 12.dp radius, 1.dp `AppColors.Hairline`
      border over `Surface`, on **both** card tiles (the Cards tab's and the deck
      editor's separate one). Needed a new named colour, because the palette had
      no general border colour - `DividerChart` was chart gridlines only. Also
      `minLines = 2` on the card name, which is what made rows end flush instead
      of ragged.
- [x] **C2 done.** The detail page's card art was floating: `ContentScale.Fit`
      inside a full-width 320dp band letterboxed it, and `CardImage` paints its own
      `SurfaceRaised` mat - the same colour as the band - so the empty side panels
      were invisible. The frame is now sized to the card's real 59:86 aspect, with
      a 12dp radius and the same hairline as the tiles. Art is unchanged at 320dp
      tall. Image loading was never duplicated: all 6 call sites share one
      `CardImage`.
- [x] **C3 done.** `ui/StateViews.kt` splits `ErrorBox` - which was doing two jobs
      at once - into `EmptyState` (icon, title, body, optional action) and
      `ErrorState` (danger-tinted, so a failure no longer looks like an empty
      list). `CardsGrid` takes a composable `emptyState` instead of a message
      string, because a string cannot carry an icon or a button. 10 states
      converted across 3 files. Buttons only where a real next step exists.
- [x] **C4 done.** `ui/Loading.kt`: `SkeletonCardGrid` holds the real tile
      silhouette (same adaptive columns, 8dp gutters, 12dp frame, 140dp art, 2-line
      name) and `SkeletonRowList(rows, thumb)` covers the metagame and public
      decks. 5 call sites converted; the download bar, the app-bar bars and the
      pagination footer spinner were kept, since a bar means progress within a
      screen that has content and a skeleton means a whole screen that does not.
      `TileShape` became `internal` so both reuse the real corner radius. Verified
      by temporarily forcing each placeholder, then reverting - the cached
      database loads too fast to catch otherwise.
- [ ] **Dead code found, not removed:** the second Collection spinner branch
      (`state.binderLoading && ownedCards.isEmpty()`) can never run, because the
      branch above already returns on `binderLoading`. Left in place, marked.
- [ ] **Unify the two card tiles.** `AppRoot.CardTile` (Cards tab, Collection,
      related cards, deck contents) and `BuilderScreen.DeckTile` (the editor's
      card picker) are separate implementations. Image loading is already shared;
      the *layout* is not. They now agree on the frame, but the editor tile still
      uses a caption-size name where the other uses body, and has no `minLines`,
      so the editor grid still has ragged rows. Worth unifying so one change
      reaches both - but the editor needs four behaviours the other does not
      (Leader tick, copy cap, per-card warning, blocked dimming), so this is a
      flag-set design, not a merge.
- [ ] **Rename `BuilderScreen.DeckTile`.** It renders a *card* inside a deck, not
      a deck, and `AppRoot.DeckRow` is the thing that renders a deck. The names
      are actively misleading.
- [x] **C5 decided: keep `#EE6DCA`.** No code changed. The accent is already a
      single source (8 direct refs, 16 via `Palette.primary`, plus the theme's own
      `primary`). One Piece red and gold were both built and screenshotted and are
      both worse: the cards are red and gold, so the accent becomes camouflage over
      the artwork, and independently **red is already `Danger` (5 deg) and gold is
      already `Warning` (45 deg)** - the two obvious brand choices are exactly the
      two hues the palette has spent on meaning. Pink at 317 deg has 48 deg of
      clearance from `Danger` and 82 deg from `AccentAlt`. Only real open
      question is whether it is too *loud*, fixable by desaturating without
      moving the hue.
- [ ] **The two card tiles remain separate** (see below), and `BuilderScreen.DeckTile`
      is still misnamed.
- [ ] **Undecided:** whether to track onepiece.gg's look, and whether a light
      theme is wanted. Neither depends on anything above.

### 2.12 App identity - icon, name, splash - DONE
The app had **no icon at all** (the manifest had no `android:icon` and there were
zero drawables) and was labelled `OnePiece.gg`, which on a home screen reads as
the official app.

- [x] **Name is now `OPGG`.** Short, fits under an icon without truncating, and
      the site's own initialism without claiming the full name. Note that `OPGG`
      is also a long-established League of Legends stats site, so this is not
      trademark-free either - but it is one the user chose knowingly, and it is
      a far smaller ask than shipping Bandai's mark.
- [x] **An original "OP" monogram, plain white on the app's own `#13171B`.**
      Letterforms built from circles and rounded rects, not traced from anything.
      Deliberately *not* the One Piece logo, and the rejected interior motifs are
      recorded in COSMETICS.md.
- [x] **Full asset coverage:** adaptive icon (background + foreground + a
      **monochrome layer** for Android 13 themed icons), legacy PNGs at all five
      densities square and round, and a 512px opaque no-alpha store asset.
- [x] **Splash without a new dependency.** `androidx.core:core-splashscreen` is
      not in the Gradle cache, so rather than risk an offline build failure the
      splash is a layer-list `windowBackground` (background colour + centred mark)
      for API 24-30, plus a `values-v31` override adding the
      `windowSplashScreen*` attributes for API 31+. Same result, zero new
      dependencies.
- [x] Verified with `aapt2 dump badging`: `application-label:'OPGG'`, and
      `application-icon-*` resolves to `mipmap-anydpi-v26/ic_launcher.xml` at
      every density including the `65534` any-bucket. Splash and icon both
      screenshotted on device.

**The geometry is generated from one source of truth** - and getting that claim
right took two attempts.

- [x] **First attempt was wrong, and shipped broken.** The PNGs were drawn with
      Pillow primitives and the vector `pathData` was written by a *separate*
      function, so the two were different implementations of the same shape. Only
      the PNGs were ever looked at, and the launcher draws the vector. Two bugs
      shipped unnoticed:
      1. **The O rendered as a solid disc, not a ring.** `fmt()` emitted a single
         `M` for the whole string, so the inner circle became more `L` commands in
         the *same* subpath. `evenOdd` only makes a hole between *separate*
         subpaths.
      2. **The P's stem was mangled** - the outline was missing its final `V`, so
         it arced straight across the bottom instead of running down the left edge.
- [x] **Second attempt: one path source, and the previews render it.** The path
      data is built as strings and a small interpreter (`M/L/H/V/A/Z`) rasterises
      *those exact strings* for the PNGs, so a preview cannot pass while the
      vector fails. The interpreter is not a general SVG engine and does not
      pretend to be.
- [x] **Third bug, in the scaling code:** `mark_png` composited a full-size image
      at an offset into a same-size canvas, which crops it, then pasted the result
      at `(0,0)` instead of centring. The splash mark was visibly truncated and
      the legacy icons sat slightly up and left. A uniform resize already scales
      about the centre; all that is needed afterwards is to centre the padding.
      This one was masked by the vector being correct - the device check passed
      while the PNGs were still wrong.
- [x] **Verified the way the user sees it:** the app-info screen renders the
      adaptive icon with the system's own mask, and a timed screencap catches the
      splash. Checking `aapt2 dump badging` proves the *packaging*; it says
      nothing about whether the shape is right.

**The lesson:** for a launcher icon, verifying the raster is not verifying the
icon. The launcher uses the vector, and only a system-rendered view - the
app-info screen, or a real home screen - exercises it.

### 2.13 Printing artwork, the related count, and the lossy cache - DONE
Three bugs, found together, all rooted in the same misunderstanding: the card
database has **5,540 rows - one per printing** - and the app treated it as
3,063 cards with some extra metadata.

- [x] **Artwork was per-card, not per-printing.** `Card.imageUrl` was built from
      `idNormal`, so every printing of a card resolved to the base card's image.
      The CDN serves one file per printing id (`.../card/P-058_p1.webp`), so the
      right URL was always available. For P-058 "Where the Wind Blows" the
      difference is not subtle: the base printing is a dark card and `_p1` is a
      completely different illustration.
      **1,172 of the 3,063 cards (38%) have more than one printing, and 2,477 of
      the 5,540 rows were showing the wrong picture.** Now `imageUrl` uses the
      row's own `id`. `key` deliberately still resolves to `idNormal` - that is
      what groups printings for ownership and the rules engine.
- [x] **The related-card button always said "See 6 cards from this crew".** The
      button printed `relatedFor(card).size`, and that list is capped at 6 for
      the preview, so it reported the number *shown* rather than the number that
      exists. Added `RelatedIndex.relatedCount`, which counts without sorting or
      allocating, and used the same basis rule as `related` so the button and
      the list screen always agree.
- [x] **The cache was lossy, which broke all of it on every cold start.** The
      cache stored the *deduped* 3,063-card set, and the comment claimed "the
      cache is re-deduped on load anyway". Dedup is not re-honourable: it
      discards the 2,477 printing rows outright, and they cannot be rebuilt from
      what is left. So on every launch after the first:
      - the Printings strip was **gone entirely**, which meant the per-printing
        artwork fix above was invisible;
      - `printingToNormal` knew only base ids, so meta-snapshot entries naming a
        printing failed to normalise and the related list showed **"11 cards"
        above two tiles**;
      - `printingLookup` missed printing ids, so deck and collection entries
        keyed by a printing lost their card.
      The cache now stores the raw 5,540 rows (4.7 MB instead of 2.7 MB) and
      `applyCards` re-derives the deduped set on load, exactly as for a fresh
      download.

- [x] **Which pack each artwork came from.** `CardSets` holds the pack name -
      "ST-11 Uta Deck Battle Participation Pack", "-Green Uta- [ST-16]", "Offline
      Regional Champion Card Set 25-26 Season 1" - and it is the **only** field
      that can answer it. The `set` code is the card's *series* ("P", "OP03",
      "ST05") and is identical for every printing of that card: across the 1,172
      multi-printing cards, `set` differs on **none** of them and `CardSets`
      differs on **783**.
      Shown on the detail page as "From <pack>", which follows the selected
      printing. **Not** in the reprint strip - on request. The strip tried it
      first, at 112dp wide with three lines per tile, and it was too cramped:
      the names run to 80 characters, and a 112dp box only letterboxes the
      portrait art instead of fitting it. Back at 78dp the art fills the tile
      and five printings are visible at once instead of three.
      The raw values carry HTML entities (`&#039;`, `&amp;`, `&quot;`) and stray
      whitespace, so they are cleaned on the way in; `CardText.decodeEntities`
      is now shared rather than duplicated.

- [x] **What kind of printing each artwork is.** The API has **no flag for
      alternate art** - there is no art column among the 34 in `getcards` and no
      art-variant endpoint (nine plausible names probed, all 404). It has to be
      inferred from two signals that happen to agree:
      - the id suffix: `_pN` is the parallel family (2,012 rows) and `_rN` the
        reprints (465). Every one of them is a genuinely different picture -
        verified by hashing the artwork, 60/60 sampled printing-vs-base pairs
        differ;
      - the site's own name label, which appends "(Parallel)" to 91 rows, always
        in the `_p` family and never to a base row. The label is the more precise
        answer where it is present, so it wins.
      The name is only consulted for a short list of known tags, because most
      trailing brackets in a card name are part of it - "Chadros.Higelyges
      (Brownbeard)", "DON!! Card (3D Text)", "DON!! Card (Ace)".
      The reprint strip now shows **Standard / Parallel / Reprint** instead of the
      raw id. The artwork identifies the printing on its own, and the id is still
      the image's accessibility label. The detail page's "From <pack>" line is
      unchanged and still names the selected printing's pack.

      **Worth knowing:** "(Alternate Art)" and "(Gold)" are on *base* rows, so for
      those cards the base is the special version and the ordinary one is a
      reprint. The suffix alone does not say which printing is the desirable one;
      the name tag does.

- [x] **Cache format version.** An existing version-1 cache cannot supply the
      printings, so it is detected and the database is re-fetched unconditionally.
      It is still *used* to paint the grid, so offline users keep their cards.
      Note that clearing `Last-Modified` matters as much as resetting the
      staleness clock: replaying it asks the server "has this changed?", the
      server says no - it has no idea our copy kept only the deduped set - and
      the refresh comes back 304 with the lossy cache still on disk.

**A useful discovery for future work on this device:** synthetic **taps** are
blocked by Android's "USB debugging (Security settings)" gate, but synthetic
**swipes** are not. `adb shell input swipe` scrolls the page, so anything below
the fold can be reached and screenshotted after all.

**Verified on device:** cold start from cache shows "Printings 2" for P-058 with
two genuinely different artworks and their different prices ($0.34 / $0.19), and
the related list now renders all 11 cards its header claims.

**What was checked and found fine:** the deck cover and leader art. Deck ids
carry no `1x` prefix in the API (that is the site's *export* format) and resolve
directly, so `Deck.leaderImage` was always correct.

### 2.14 Collection not loading - session rejected, reported as "no binder" - FIXED
### 2.15 Card images appearing late or not at all - FIXED
Reported as "for some reason the collection is not loading".

- [x] **The cause was the stored token, not a code regression.** The app held a
      session for user 158557252, and the server answered every authenticated
      call as if nobody were signed in: `getuserdata` returned
      `{"user":{"user_id":0,...},"collection":[]}` and `listbinders` returned
      `{"error":true,"error_text":"Not authorized!"}` - **HTTP 200 in both cases**.
      Tested with the exact stored credentials against the live API; the
      authenticated request was byte-for-byte the same as the unauthenticated one.
      The token had been exposed in debug output during the image-loading work
      and is no longer accepted. **Signing out and back in is the fix, and it
      has to be done by hand** - the sign-in is a Google WebView flow.
- [x] **The app was lying about why.** `BinderListResponse` had no error fields,
      so the server's "Not authorized!" body was decoded into an *empty binder
      list* and the app went on to say "No binder yet - create a binder on
      onepiece.gg", sending the user off to create a binder they already had.
      `UserDataResponse` already had `error`/`error_text` "so we can tell 'empty
      binder' apart from 'auth rejected'"; `BinderListResponse` now has them too,
      and `listBinders()` throws instead of returning an empty list.
- [x] **The Collection tab now says what happened**: "Your session has expired",
      with a **Sign in again** button. A rejected session is also detected from
      the payload - the server reporting a user id of 0 while we hold a real one -
      because the site never returns a 401.
- [x] 7 new tests, including a decode of the exact error body the server sends.

**Known and still open:** the "Decks > Mine" tab has the same weakness - with a
dead token the server ignores the `my` filter and returns the *public* deck
list, so it shows other people's decks rather than saying the session is dead.
Not changed here, because the collection is the tab that misleads.



Reported as "sometimes the card image is not appearing, or appearing later,
especially when using filter and scrolling down/up".

- [x] **Root cause was Coil's lazy disk-cache init, not the request.** Logcat:
      `Long monitor contention ... at coil.disk.DiskLruCache.initialize()
      ... for 796ms waiters=7`. Coil opens the disk cache on the first request
      that needs it, holding a lock. The first screenful of grid tiles is many
      requests at once, so they all piled onto that lock: every tile showed its
      blank `SurfaceRaised` background, then the whole screen popped in together.
      It read as missing images rather than a lock, which is why it was not
      obvious. Filtering made it worse because a filter change brings a fresh
      screenful of never-before-requested art into view.
- [x] **`ui/CardImages.kt`** configures the loader with an explicit cache
      directory and 160MB disk cache, installed in `MainActivity.onCreate`
      *before* `setContent` so the cache opens once, early, off the critical path.
- [x] **Warm-up** requests the first 40 visible thumbnails at 300px
      `INEXACT` the moment the card database arrives, on `Dispatchers.IO`, which
      both opens the disk cache and fills the memory cache so the first real
      grid renders from memory.
- [x] **Crossfade is now opt-in.** It was a blanket `crossfade(150)` on every
      request, so a thumbnail that was already in memory still spent 150ms fading
      in, and the fade restarted on every re-issue during a scroll or after a
      filter change. That reads as "the image is late" even when nothing is wrong.
      Only the card detail page asks for a fade now.
- [x] Verified on device: `DiskLruCache` contention **zero** lines (was 8 with
      7 waiters at 796ms), worst skipped-frame stall **345 -> 127 frames**, worst
      HWUI frame **2905ms -> 725ms**, and `cache/card_images` holds 1.9MB,
      proving the configured loader is the one in use.

**Two API traps hit while fixing this**, both of which compile fine against a
wrong guess and only fail at runtime:

1. **Coil 2 has no `Priority`.** It was removed, and the only `Priority` symbols
   left in the library are Android *log* levels. `ImageRequest.Builder.priority`
   does not exist, so the warm-up cannot be deprioritised against the grid's own
   requests. It does not need to be: the warm-up exists to run *before* them.
2. **`ImageLoader` has no `context` property** in Coil 2.7, so a helper taking
   only an `ImageLoader` cannot build a request; the context has to be passed in.

`LocalContext.current` also cannot be read inside a `withContext` block - it is
only valid while composing - so the context is captured before the coroutine.


### 2.16 Wishlist — DONE 2026-11-02

A wishlist count, synced to the same server rows the collection lives in. This
turned out to be nearly free server-side: every `getuserdata` collection row
already carried **`wish`** and **`wish_foil`** string counts, parsed and thrown
away since the first collection release. The website's own JS bundle (a 1.4 MB
`common.js`, greppable without a session) confirmed the write shape and the
endpoints, so no session probing was needed to design against it.

**What shipped**

- `Owned` carries `wish`/`wishFoil` alongside normal/foil; `collapseOwnedCounts`
  keeps wish-only rows alive (the old `normal==0 && foil==0` filter dropped
  them) and sums wishes across printings.
- **A wish is a target count, not a flag.** The wishlist shows only the
  shortfall: a card is listed while `wishTotal > owned`, with the tile reading
  `NEED n`. Owning enough copies removes it from the list automatically, and an
  owned `±` that reaches the target zeroes the wish in the same server write
  (`setOwned` echoes `wish = 0` once satisfied), so the website agrees.
- Bookmarking an owned card aims for one copy more than owned; the badge shows
  the shortfall, so an owned card with a stale/satisfied wish row draws an empty
  bookmark. Tapping a listed card clears its wish.
- `UiState.ownedGroups` excludes wish-only cards; new `wishGroups` lists
  shortfall cards; `wishCount` totals the copies still needed.
- Collection tab has **Owned / Wishlist** segments (an int, not a bool, so a
  "for trade" segment can follow). Wishlist view hides the binder chips on
  purpose: wishes are main-collection-only server-side.
- Tiles: bookmark badge (outline → pink bookmark + need) on every Cards-tab
  tile. In the wishlist view the ± row edits the wish; the owned badge stays
  visible because "own 2 of the 4 wanted" is the point.
- Detail page: `Wish:` stepper row under the Normal/Foil steppers; the
  per-printing list now shows wish-only printings too; card total line gained
  `wished N · need M`. Account tab gained `Still needed: N`.
- `toggleWish`/`quickWish`/`setWished`: optimistic write, offline guard,
  rollback, `Binder not found` blocking — the full `setOwned` treatment.

**Server mechanics, verified with a live probe** (session had been restored by
the user, so this could be tested for real):

- **`savebatchcollection` rows carry a top-level `wish` Int** —
  `{card:"ST24-003", type:"standard", count:2, wish:1}` returned
  `{"error":false,"processed":1}` and read back `wish:"1"` with `standard:"2"`
  untouched. Reverting `wish:0` read back clean.
- **Rows overwrite every omitted field they own.** Editing owned counts with
  no `wish` field zeroed a wish just set (`standard:"3"`, `wish:"0"`). The site's
  own saves always echo current values; so must ours — `setOwned` now sends
  `wish = previous.wish`. (Found by probe before the app ever shipped the bug.)
- **`wish_foil` is ignored** by `savebatchcollection` (sent 1, read back 0), so
  it is display-only: rows wished as foil on the site are shown in totals but
  not editable from the app.
- **`savebindercard`/`savebatchbindercard` rows have no wish field** — wishes
  live only in the main collection, hence `setWished` always routes to
  `savebatchcollection` regardless of the binder being viewed.
- The site exposes a `wishlist` collection filter and a `synclocalcollection`
  endpoint carrying `trade`/`trade_foil`/`wish_foil` — possible future trade
  segments. The web collection page's save call is debounced/batched
  (`keepalive ≤ 48 KB`), same as ours.
- Probe account residue: clean — ST24-003 restored to `standard:2, wish:0`.

**12 tests** in `WishlistTest.kt` (Owned/wish sums, JSON parse of the string
counts, collapse keeping wish-only rows, both views not leaking into each
other, representative art prefers the wished printing, and the shortfall rule:
satisfied, over-owned and partial wishes). **307 tests, 22 classes, 0
failures.** Verified on device: wish set server-side shows the
badge with count on the Cards tab tile and `Wish: 1` on the detail page; both
screenshots reviewed. Full round trip via real taps: the bookmark on a Cards
tile wrote `wish=1` (server read back 1, `standard` preserved); the Wishlist
segment listed the card with a `WISH 1` stepper; its minus wrote `wish=0`
(server read back 0) and the list returned to the empty state. The wishlist
view hides the binder chips, and with an empty list shows "Your wishlist is
empty". Note the account has a real binder named `Wishlist (0)` on the
website, so those two labels coexist on the Owned view.

Shortfall rule verified end-to-end on the real account: bookmarking an owned-2
card stored `wish=3` (need 1); tapping owned `+` (2 -> 3) cleared the wish in
the same write, so main read `standard:3, wish:0` and the card left the list;
the `-` restored `standard:2`. That test uncovered the routing bug in 2.17.


### 2.17 Collection edits silently written to a binder, not the collection — FIXED 2026-11-02

Found while verifying the wishlist shortfall rule: a `+` tap showed `STD 3`
optimistically but the main collection stayed at `standard:2`. The row was
found in the account's **"Wishlist" binder (id 4259)** instead.

**Cause:** `loadBinder(0)` promoted binder 0 to the account's first binder
(`binders.firstOrNull { count > 0 } ?: minByOrNull(sort)`). That account has a
single binder, so `state.binderId` became 4259, and `setOwned` routes any
non-zero binder id to `savebindercard`. Every owned `±` therefore wrote into a
side binder while the screen read `getuserdata`, which ignores `binder_id` and
returns the same 407 rows either way - so the count sprang back on the next
load and nothing looked wrong at write time.

**Why it was invisible:** wishlist writes always go to `savebatchcollection`
(see 2.16), so wishes worked while owned counts did not. The stale binder row
made the mistake detectable only by reading `getbindercards`.

**Fix:** `loadBinder` no longer promotes binder 0. Binder 0 *is* the default
collection and a valid write target; switching binders is an explicit chip
tap. Verified after the fix: `+` on ST24-003 wrote `standard:3` to the main
collection (and, with the shortfall rule, cleared its `wish`), while binder
4259 stayed empty. Stray test row cleared with `savebindercard count=0`, which
does delete the row. Account left at its original `standard:2, wish:0`.


### 2.18 Binder system — V1 DONE 2026-11-02

The website's binder feature is fully exposed by the API, and its whole path
has been exercised with the live session. **V1 (reads + CRUD) is now in the
app**; V2/V3 remain planned.

**V1 shipped**

- **Store picker is the app-bar title** — `Collection ▾` / `Wishlist ▾` /
  `Trade ▾`, one line so binder names get the width; the quantity
  (`800 copies` / `N needed`) sits in its own row directly below the bar. Tapping
  the title opens a dropdown: Collection, Wishlist, each binder with a `⋮` for
  rename/delete, and New binder. No permanent chip row: the earlier
  Owned/Wishlist-over-binders layout made "Owned" read like a binder, and even
  the merged single row cost vertical space.
- Per-binder reads use `getbindercards`; the collection keeps using
  `getuserdata`. Binder contents live in `binderOwned`/`binderOwnedByNormal`
  *beside* the collection maps, so the Cards tab's owned badges and the
  wishlist never silently switch meaning. `±` edits the store the screen is
  showing; Cards-tab edits always go to the collection.
- In a binder the mode switch is a **segmented control** (`In binder | Add
  cards`), not another row of chips: `Add cards` shows the card database with
  binder counts so an empty binder can be filled.
- Wishes are hidden in binder views (bookmark badge, detail stepper): `wish`
  lives only on collection rows, and the Wishlist menu entry returns to the
  collection first.
- `+ New` name dialog; long-press a binder chip → Rename / Delete, with delete
  confirming the card count. Deleting the selected binder returns to the
  collection.
- Chip counts are adjusted optimistically on `±` (they come from `listbinders`
  and would otherwise sit stale until the next reload), and reverted on failure.
- Offline still fails with the friendly message; binder contents are not cached.

**Verified on device (2026-11-02):** created "Trade", added X.Drake x1 from
`Add cards` (server binder 4356 count=1; collection ST24-005 stayed at 4),
renamed to "Trade Box" (server confirmed), deleted it with confirmation
(binders back to one, collection still 407 rows). After the header redesign:
the title dropdown opens with Collection / Wishlist / binders / New binder;
selecting Wishlist shows `Wishlist · 0 needed`; selecting the account's
"Yamato" binder shows `Yamato · 0 copies` with the segmented In binder /
Add cards control; `⋮` opens rename/delete; New binder opens the create
dialog. Screenshots reviewed.

**Bug caught while verifying:** `onLongPress = onLongClick?.let { { it() } }`
bound the inner lambda's implicit `it` (an Offset) instead of the captured
callback, so the chip's long-press did nothing. Fixed with an explicit
`{ _: Offset -> onLongClick() }` parameter.

**Second bug, reported by the user:** "+ New" did nothing on the empty
Wishlist (and would have failed on an empty binder too). The dialog composables
were emitted at the bottom of `CollectionScreen`, *after* the empty-state branch
`return`ed, so the state flipped but no dialog was ever composed. Dialogs now
sit immediately after the chip row, before any content branch.

**Tests:** 7 new in `BinderTest.kt` (title fallbacks, real binder-row payload,
empty binder, create response success/failure). **323 tests, 24 classes, 0
failures.**

**V2 — moves + binder polish shipped 2026-11-02**

- `movecards` semantics verified live: it moves the **whole row** for a
  printing (`moved:1, copies:4` for a four-copy row; `moved:2` for a two-id
  batch), and binder `0` means the **collection in both directions**.
- The card detail page has **Move to…** in the collection block, for the
  selected printing, when the store holds at least one copy. It opens a
  confirmation listing Collection (when in a binder) and every other binder;
  the copy count and "no partial move" are stated before acting. The hidden
  "Wishlist" binder is not offered.
- **Bulk move**: the quantity row becomes a toolbar with **Select**, entering an
  explicit selection mode (long-press is the peek, so it cannot double as
  select). Tapping tiles toggles them, selected tiles get an accent border and
  check, and `±`, the type toggle, the wish bookmark and the peek are disabled
  while selecting. **Move…** opens the same destination dialog and passes every
  selected printing id in one `movecards` call.
- **Colours**: the binder actions dialog shows the site's fixed 10-colour
  palette (two rows of five - one row overflowed and hid the last swatch).
  Colours appear as a dot in the picker and in the manage list. The API accepts
  only those exact hex values; verified applied and reset.
- **Reorder**: a **Manage binders** entry lists user binders with up/down
  arrows and sends the full id order to `reorderbinder` (position becomes
  `sort`). The system-created `Wishlist` binder is excluded from the list and
  pinned ahead in the payload, because it is a fixed destination under
  Collection rather than a movable binder. Verified: Yamato up/down round-trip.
- `AppViewModel.moveCards` / `moveCard` share guards and refresh; `setBinderColor`
  and `reorderBinders` refresh the list on success.
- Tests: destination lists, real move response, and four `swapOrder` cases.
  **330 tests, 24 classes, 0 failures.**
- Verified on device: selection + bulk dialog (2 tiles, both destinations),
  colour applied to Yamato then reset, reorder up/down round-trip, and a
  two-id API bulk move (`moved:2`) on a throwaway binder. All test data
  removed; the account is back to its original binders and counts.

**Value totals + All binders shipped 2026-11-02**

- Binder views show `N copies · $X` in the row under the app bar. The value
  uses TCGplayer standard/foil prices per printing (`ownedValue`): foils use the
  foil price, falling back to the standard price when it is missing or zero;
  unparseable prices count as 0. Four tests cover this.
- **All binders** is a third destination in the title picker (after Wishlist).
  It **unions the collection with `getbindercards?binder_id=all`** - the API's
  `all` is binders only because binder 0 is the implicit default, but the app
  presents Collection as a location, so the total view must include it.
  `mergeOwnedRows` sums duplicate printings across the two sources. The view is
  **read-only**: no `±`, no Add cards, no Select/move, and the detail page shows
  an "All binders is a read-only summary of your collection and every binder"
  note instead of steppers. Cards remain tappable (and peekable) for reading.
- Title handling: `binderTitle` returns "All binders" for -1; the quantity row
  carries the aggregate count and value.
- Tests: `swapOrder`, value math, the aggregate title, and the duplicate-summing
  merge. **337 tests, 24 classes, 0 failures.**
- Verified on device: All binders shows `838 copies · $139.95` (800 collection +
  38 binders) with edit controls absent; opening a card shows the read-only
  block. Yamato shows its own `38 copies · $3.76` with editing intact.

**Still planned**

- **V3 (+4–6 h)**: folder tree via `parent_id`, public sharing + deep link.

**Verified API surface** (create/list/delete/read proven live on 2026-11-02;
the rest read from the site's own `common.js`):

- `listbinders` → `{id, name, slug, type, sort, created, public, count, color,
  parent_id}`; `count` updates immediately (0 → 1 when filled).
- `createbinder {name, color, parent_id}` → `{id, slug, sort, ...}`; auto-slug
  (`"OPGG Test"` → `opgg-test`), `sort` takes the next slot.
- `getbindercards?binder_id=N` → rows **in the same shape as collection rows**
  (`standard`, `foil`, `wish`, `trade`, …), so `OwnedCard` parsing can be
  reused. `binder_id=all&parent=N` returns the aggregate view.
- `savebindercard` / `savebatchbindercard` for counts; `movecards
  {from_binder, to_binder, cards}` for relocation.
- `renamebinder`, `deletebinder`, `setbindercolor`, `reorderbinder {order}`,
  `setbinderparent {binder_id, parent_id}` (folders), `setbinderpublic
  {binder_id, public}`, `getpublicbinder?user=&slug=`.
- Deleting a binder removes its rows (test binder 4354 created with one card,
  then deleted; `listbinders` back to one).

**Open for V2+**: move cards (single-card detail action first; bulk needs an
explicit selection mode because long-press is the press-and-hold peek),
colours/reorder, folder tree (`parent_id`), public sharing, `binder_id=all`
aggregate, per-binder value totals.

**System binder**: `Wishlist` is a fixed location under Collection, not a
user-managed binder. It is hidden from the picker, move destinations and the
manage list, and always pinned first in reorder payloads. `withoutSystemBinders()`
is the single filter used by all three.


### 2.19 Card-image zoom — DONE 2026-11-02

- [x] **Detail-page full-screen loupe.** Tap the art (or the zoom glyph) to open
      a black overlay; pinch 1–3×, pan clamped to the fitted image, double-tap
      1× ↔ 2.5×, close button / back to dismiss. **~4 h actual.**
- [x] **Grid press-and-hold peek.** Holding any tile on the Cards or
      Collection/wishlist grids raises a large preview over a dimmed grid;
      releasing shrinks it away. Haptic tick on the threshold. The decks/related
      tiles keep plain taps. **~1 h actual.**
- [x] *(cleanup)* `CardImage` gained `nativeSize` (`Size.ORIGINAL`). The detail
      page used `targetPx = 900`, which means "fit a 900x900 box" and upscaled
      the 600x838 file to 644x900; it now decodes native.

**What shipped**

- `ui/CardImageZoom.kt`: `ZoomMath` (scale clamp 1–3, double-tap target,
  fit computation for portrait/wide viewports, pan bounds from the *fitted*
  size rather than the viewport) + `CardImageZoomOverlay` (`Dialog` with
  `usePlatformDefaultWidth = false` and `decorFitsSystemWindows = false`).
- Gestures: `detectTransformGestures(panZoomLock = true)` for pinch/pan and
  `detectTapGestures(onDoubleTap)` for the toggle, with a 220 ms `animate`
  for the double-tap. Pinch stays instant — interpolating it adds lag.
- The affordance is `Icons.Outlined.ZoomIn` at the art's top-right; the
  bottom-right corner overlapped the printed card ID.
- Grid peek: `CardTile` uses `detectTapGestures` with both `onLongPress` (start)
  and `onPress` (`tryAwaitRelease` = end) - using only `onPress` would suppress
  the tap distinction and open the card on release. The preview is drawn in a
  `Box` over the grid (not a Dialog, so the press gesture is never interrupted),
  with an `Animatable` driving scale 0.86 -> 1 and alpha 0 -> 1 on hold, reversed
  on release. Only `CardsGrid` passes peek callbacks, so deck and related tiles
  are unchanged.
- **9 tests** in `ZoomMathTest.kt` (clamp bounds, toggle threshold, width- and
  height-limited fits, zero pan at 1×, overhang clamping, unmeasured viewport).

**Deviation:** drag-down-to-dismiss was dropped. A vertical drag detector fights
the pinch/pan detector, and a threshold without an end callback accumulates
across gestures; the close button and back are unambiguous. Revisitable with a
custom `awaitEachGesture` if it is missed.

**Verification:** opened from the detail page, double-tapped to 2.5×, panned to
the card edge (clamped, no background gap), back returned to the detail page;
held a grid tile (preview rose over the dimmed grid) and released (grid
restored, no accidental navigation). Screenshots reviewed. Offline was **not**
re-tested this run: toggling Wi-Fi also kills wireless adb on this phone. The
loupe uses the same URL and loader as the grid, whose art has rendered offline
from the disk cache before.

**Bug caught while verifying:** the close button was drawn under the status bar
(`decorFitsSystemWindows = false`), and taps in that strip go to the system - so
the X silently did nothing. Fixed with `statusBarsPadding()` on the button
(`navigationBarsPadding()` on the hint); re-verified by tapping the X's dumped
bounds. Our own screenshots made it look present and correct, which is the
lesson: a control can render fine and still be untappable.

**Source limits (unchanged):** one 600×838 webp per printing; `-1024`/`-768`/
`_large` variants 404; the website has no lightbox. Full-screen at 1× already
stretches 600 px across a 1080 px screen, so zoom is for reading effect text,
not print inspection, and is capped at 3×. Any future improvement needs a
higher-resolution source, which does not exist today.

**Effort:** ~4 h actual against a 5–7 h estimate (the exploration had already
settled the design; the gesture mixing was the only friction).


### 2.20 Grid stuck on its loading skeleton — FIXED 2026-11-02

Reported as "sometimes the grid just keeps loading". Reproduced on device: the
Collection tab sat on `SkeletonCardGrid` indefinitely while the quantity still
read `800 copies`.

**Cause:** every request shared the HTTP client's **5-minute** timeout, which
exists for the multi-megabyte card database. A stalled collection or binder read
(20-40 KB) therefore kept the skeleton up for minutes with no error and no retry.

**Fixes (four layers):**

1. **Per-request timeouts.** `getText`/`postJson` take a timeout: 20 s for
   `getuserdata`, `listbinders`, `getbindercards` (and the all-binders read),
   plus deck/set reads; 30 s for writes; 60 s for the ~157 KB meta snapshot; the
   5-minute default remains for the card database.
2. **Timeout is not offline.** `NetworkStatus` now classifies timeouts *before*
   IO failures (a socket timeout is an IOException too) and words them as
   "onepiece.gg took too long to respond. Try again." `isOfflineFailure()` keeps
   the offline banner off for a stalled server.
3. **Stale-request guard.** `loadBinder` stamps each load with a monotonically
   increasing id; results and errors from an older request are dropped. This
   makes Retry-while-loading safe and stops fast store switching from letting an
   old response overwrite a newer screen (or resurrect the loading flag).
4. **Escape hatch.** If the skeleton is still up after 8 s, the grid shows
   "Still loading… / Retry" over it rather than placeholders with no way out.

**Verification:** with a black-hole system proxy (`10.255.255.1:9`) the skeleton
became the timeout error at ~20 s; clearing the proxy and relaunching loaded the
collection normally. Four new `NetworkStatusTest` cases cover timeout vs offline
vs server messages. **341 tests, 25 classes, 0 failures.**

### 2.21 Offline meta cache — DONE 2026-11-02

The card database was cached but the metagame snapshot was not, so offline the
Meta screen was empty and the related-cards index never built.

- `MetaCache` stores the decoded snapshot (~135 KB on device) in
  `files/opgg-meta.json` behind a versioned envelope, written atomically
  (temp + rename). Corrupt files and older versions read as absent.
- `loadRelatedIndex` now serves the cached snapshot **before** touching the
  network, then refreshes. If the fetch fails, cached meta is kept and no error
  box replaces it. If the fetched snapshot has the same `generatedAt` as the
  cached one, the index is not rebuilt a second time.
- Cache IO and index building run off the main thread.
- The snapshot request has a 60 s timeout (rather than the 5-minute card-download
  default), so a stalled refresh cannot spin forever.
- Deck and set reads also got the 20 s quick timeout after the offline test
  showed the Decks tab sitting on its skeleton.
- **Tests:** 4 in `MetaCacheTest` (round-trip, missing, corrupt, old version).
  **345 tests, 26 classes, 0 failures.**
- **Verified offline** with a black-hole proxy: Meta rendered all 57 archetypes
  from cache with no error, and a meta card's detail showed
  "See 22 related cards · In 2 archetypes" — the index came entirely from disk.
  Online launch afterwards loaded normally.

### 2.22 Reprint-strip foil price fallback — DONE 2026-11-02

Parallel and reprint rows frequently carry no standard price but do carry a foil
one, so the strip showed `-` for 1,828 of 2,477 non-base printings.

- `reprintPriceLabel(price, foilPrice)` now: standard price when positive,
  otherwise `"$X.XX foil"`, otherwise `-`. Zero and non-numeric strings count as
  missing (not free), and values are shown to two decimals.
- Four tests in `ReprintPriceTest` cover preference, fallback, zero/garbage, and
  formatting. **349 tests, 27 classes, 0 failures.**
- Installed and **visually verified**: P-077's three printings show
  `$9.23 foil`, `$12.68 foil` and `$399.00 foil` where the strip previously
  showed `-`, and standard-priced printings still show their normal price.

### 2.23 Binder V3: folders and public sharing — DONE 2026-11-02

- **Folders.** `setbinderparent` moves a binder under another; `parent_id` 0 is
  the top level. The title dropdown and Manage binders render tree order with
  children indented, and `Move under…` excludes the binder itself and its
  descendants so a folder cannot be moved into its own subtree.
  `orderWithDepth()` is cycle-safe and emits every binder at most once.
- **Indentation (follow-up).** 12 dp read as no indent at all on device, and an
  uncoloured child skipped the colour chip, so its name started further left
  than its coloured parent's - nesting looked absent. Each level is now a 10 dp
  slot with a faint vertical rule, and the colour chip keeps a fixed slot even
  when a binder has no colour. (16 dp was reported as too deep once the rule
  made the step obvious.)
- **Colour on a child binder.** Works: `setbindercolor` on Yellow Yamato
  (`id=4376`) set `#3b82f6` and the picker drew its dot. Two earlier attempts
  from the dialog failed with the 30 s write timeout; that message is only
  visible because of 2.24, so it previously looked like the colour silently did
  nothing. Yamato's original orange was restored after the test.
- **Compact picker (follow-up).** `DropdownMenuItem` enforces a 48 dp minimum
  height (`sizeIn(minHeight = MenuListItemContainerHeight)`), so padding cannot
  make it shorter; the picker now uses a local `MenuRow` at 36 dp with the old
  label metrics, 36 dp action buttons and a 26 dp rule. The whole menu fits in
  roughly three quarters of its previous height.
- **Reordering with folders.** `reorderbinder` stores the array position as
  `sort`, and only siblings are ever compared, so Manage binders now swaps a
  binder with its neighbour *at the same level* (`siblingSwapOrder`) instead of
  swapping flat positions (which crossed folder boundaries and looked like a
  no-op). Up/down are disabled at the ends of each level.
- **Sharing.** `setbinderpublic` toggles `public`; `Share…` opens a dialog with
  the switch, the link and `Copy link` (clipboard). The link uses the site's own
  shape: `u/<nickname>/binder/<slug>`, falling back to
  `u/<nickname>/collection?binder=<id>`. The action reads `Sharing (public)` when
  the binder is already shared.
- **Nickname source.** Share links need the site nickname, and a session imported
  without one produced no link. `getuserdata`'s `user.nickname` is now persisted
  into the session on every collection read, so links work for imported sessions.
- Five tests added (`orderWithDepth`, cycle handling, descendants, share URL
  building, sibling reordering). **357 tests, 27 classes, 0 failures.**
- Verified on device against the live account: toggling Yamato made it
  `public=1` (`getpublicbinder` then returned its 10 rows) and reverting made it
  private again; moving Supernova under Yamato produced `parent_id=4357` and the
  indented row, and moving it back restored the original order and parents.
  Manage binders' arrows swapped Yamato and Supernova (server sorts 1/2) and
  swapped them back. Account state was restored exactly
  (Wishlist/Supernova/Yamato, sort 0/1/2, all private, all top level).
- Later visual check with the real account: Yellow Yamato under Yamato
  (`parent_id=4357`) now renders with the rule and the 10 dp step in both the
  picker and Manage binders.

### 2.24 "New binder" looked like a dead tap — FIXED 2026-11-02

The cause was not creation at all: **no `SnackbarHost` was composed anywhere**.
`AppRoot` created a `SnackbarHostState` and an effect called
`showSnackbar(message)`, but no host rendered it, so the call suspended and a
second effect cleared the message immediately after. Every failure message in
the app - create, rename, move, import, collection writes - was therefore
invisible, which made a rejected create look like nothing happening.

- Added one `SnackbarHost` at the root `Box`, above the tab bar on the Tabs
  screen and 24 dp from the bottom elsewhere, styled with `SurfaceRaised` /
  `OnSurface` (Material's inverse surface is near-white in this dark theme).
- Removed the duplicate `LaunchedEffect(state.message)` that consumed the
  message; only the displaying effect consumes it now.
- The name field now submits on the IME Done action as well as Save.
- Verified on device: a duplicate name ("Yamato") shows
  "A binder with that name already exists.", and creating **Yellow Yamato**
  through the picker succeeded (id 4376, sort 3) and opened the new binder.
- **357 tests, 27 classes, 0 failures.**

### 2.25 Grid images stop loading after a while — FIXED 2026-11-02

Not a rendering bug: the image cache was capped at **160 MB** while the card
catalogue is about **1.2 GB** of encoded art (5,671 printings at 160-300 KB
each), and on device the cache directory was already at 164 MB. Once full,
every scroll position was a cache miss, every write ran an eviction, and tiles
depended entirely on the CDN. Coil does not retry, so one stalled socket or
timed-out request left its tile blank for the rest of the session.

- Disk cache raised to **1 GiB** (the device has 38 GB free), so the working
  set stops thrashing.
- `respectCacheHeaders(false)`: artwork is immutable per printing id, but the
  CDN sends `max-age=86400`, which made Coil treat every cached image as stale a
  day later and re-download the grid.
- OkHttp tuned for image bursts: 8 requests per host instead of 5, 15 s connect,
  20 s read, connection retry on.
- `CardImage` now retries a failed load twice (1.5 s, 6 s) and skips retrying a
  404, which means the printing genuinely has no art.
- Also fixed `warm()`: it called `dispose()` on every request it enqueued, which
  **cancels** the request - the "preload the first screen" step had never
  actually loaded anything.
- Verified with the old, full cache: a fast 20-swipe scroll through ST27/ST26
  rendered every tile, and a card page still loads. **357 tests, 0 failures.**

### 2.26 Binder/collection reads hang until the app restarts — FIXED 2026-11-02

Opening Yamato showed the grid skeleton and "Still loading…", the binder picker
was empty, and the Collection read 0 copies - while the phone's network was
fine (`ping` to both hosts worked, and the same API calls succeeded from the
PC). Restarting the app fixed it.

Cause: the Ktor/OkHttp **connect timeout was 30 s, longer than the 20 s request
timeout a quick read uses**. `api.dotgg.gg` resolves to several Cloudflare
addresses; when the first one stops answering (DNS cache outliving a mapping,
or a rotated address), the connect attempt eats the entire request budget, so
OkHttp's `retryOnConnectionFailure` never gets to try the next address. Every
read then times out for the life of the process.

- Connect timeout lowered to **6 s** in both `HttpTimeout` and the OkHttp engine,
  so a dead address fails fast and the fallback is tried within the same request.
- A binder load that times out now **retries itself once** after 500 ms (fresh
  socket), instead of leaving the grid on an error until the user acts.
- Failed reads/writes now log `OpggHttp: GET/POST <url> failed after <ms>:
  <exception>` so a report like this can be diagnosed from logcat.
- Verified: after install the picker lists all four binders, Collection shows
  800 copies, and Yamato opens with `38 copies · $3.76`. **357 tests, 0 failures.**

### 2.27 App HTTP client gets stuck while the network is fine — FIXED 2026-11-02

Opening the empty Supernova binder left the grid on the skeleton again. The
app logged `listbinders` timing out after 20 009 ms (twice, including the
retry), while at the same moment:

- Chrome **on the phone** loaded `api.dotgg.gg` instantly
  (`{"error":true,"error_text":"Not authorized!"}` without the token), and
- the PC got `getbindercards?binder_id=4361` in 1.3 s as
  `{"error":false,"binder_id":4361,"collection":[]}`.

So the empty binder is not special and the server is fine: the app's pooled
connections were dead but still being reused, so every request paid the full
20 s timeout until a restart.

- OkHttp now sends **HTTP/2 pings every 20 s**, which detects a connection that
  died silently (expired NAT mapping, Wi-Fi power save) instead of reusing it.
- After **3 consecutive timeouts** the whole Ktor client is closed and rebuilt
  (`OpggHttp: HTTP client rebuilt after repeated timeouts`). Closing drops every
  pooled socket, so recovery no longer needs an app restart.
- Smoke test after install: Supernova `0 copies` (empty state), Yamato
  `38 copies · $3.76`, Yellow Yamato `0 copies`, Collection `800 copies`.
  **357 tests, 0 failures.**

### 2.28 Public deck search and foil-only printings — DONE 2026-11-02

**Deck search.** The deck list had no search box because every key tried
(`q`, `search`, `name`, `leader`, ...) was silently ignored. The site's own
request nests its filters: `rq.getdecks.smartsrch` is the text search.

- `buildDeckRequest` now emits `"getdecks":{"smartsrch":"…"}` alongside `page`
  and `my`, and `getDecks` repeats it on every page so "Load more" keeps
  filtering.
- Public/Mine gained a search field (400 ms debounce, clear button). A request
  id guards the list, so a slow previous search cannot append unfiltered decks,
  and the empty/exhausted text names the query.
- A no-match search answers with the JSON **string** `"[]"`, not an array, which
  made decoding fail and looked like a broken request. `unwrapDeckList` unwraps
  that extra layer once, so "No decks match" is reachable.
- Verified on device: typing `zoro` returned the Roronoa Zoro decks, Mine +
  `sanji` returned only that deck, and a nonsense query showed "No decks match".

**Foil-only printings.** Parallel printings (`_pN`, and rows the site labels
"(Parallel)") are printed as foil only, so "STD 0" was asking for copies that
cannot exist - and the first `+` wrote a standard count onto a foil-only row.

- `Card.foilOnly` is true for the parallel family.
- The grid tile forces `FOIL n` for such a printing and hides the type toggle
  (no click node at all, rather than a disabled one); the card page replaces the
  Normal stepper with "Foil only - this printing has no standard version."
- The tile's edit type now also follows ownership: a card held only as foil no
  longer opens on `STD 0`.
- `quickAdjust` uses that same type, so the label and the write agree.
- Verified on device with `P-069_p3` (foil 1): the tile reads `FOIL 1`; `+`
  produced `std=0 foil=2` on the server and `-` restored `foil=1`.
- **363 tests, 28 classes, 0 failures.**

### 2.29 Release prep: R8, resource shrinking, signing — DONE 2026-11-02

`minifyEnabled` was false, so the release build was a 20 MB unshrunk debug-like
artifact.

- Release now runs **R8 + resource shrinking**: `app-release.apk` is **2.1 MB**
  (debug: 20.2 MB).
- `app/proguard-rules.pro` keeps the app's `@Serializable` Companions and
  generated `$$serializer` classes, preserves source-file/line attributes, and
  silences the optional Conscrypt/BouncyCastle/OpenJSSE/native-crypto probes.
- Signing is opt-in: `keystore.properties` at the repo root selects a real
  keystore; without it the release build uses the debug key, so it installs over
  the debug app and can be tested without wiping app data. `.gitignore` excludes
  the keystore and its properties. The real keystore has not been created (the
  user must own and back it up); the `keytool` command is in the README.
- `android:allowBackup` is now **false**: the app stores a bearer token in
  DataStore and the sign-in WebView, and neither should leave the device in a
  cloud backup.
- Installed the minified build and exercised the risky surfaces on device:
  collection (800 copies), binder (Yamato, 38 copies · $3.76), public deck search
  (`zoro`), card detail (`P-069_p3`, foil-only), and the metagame snapshot
  (57 archetypes). No `SerializationException`, `ClassNotFound` or crash in
  logcat. **363 tests, 0 failures.**
- README now documents the release build and the keystore step.

### 2.30 Deck/editor/related sweep + polish — DONE 2026-11-02

Rendered the three surfaces TASKS still listed as never seen, on the release
build:

- **Deck detail** (public `OP14-041_p1 Blue Yellow…`): format, description HTML
  (link styling intact), ownership summary, card grid, per-card counts and the
  owned dot all render.
- **Deck editor** (local "Caesar Clown"): analytics panel, legality line
  ("Standard: Blocks 2-5…"), filter chips and the card grid with steppers
  render. The chip row scrolls; the last chip is clipped at the edge.
- **Related list** (`Roronoa Zoro`, 11 cards): header, explanatory subtitle and
  the card grid render.

Fixes made while sweeping:

- **Prices**: `PriceRow` printed the feed string verbatim, so a foil price of
  `0.000000` showed as `$0.000000` next to formatted deltas, and real values
  kept six decimals. It now parses, hides non-positive values and formats to two
  decimals (`$2.21`).
- **Stale store counts**: switching binders cleared neither `binderOwned` nor
  `binderOwnedByNormal`, so the header showed the binder you just left ("38
  copies" under Supernova). Both are cleared when the selected id changes;
  verified that Supernova shows `0 copies` immediately.
- **No colour**: the palette can now clear a binder's colour (`onColor("")`);
  the server answers `color:null`. Verified on Yamato, then restored orange.
- **Move under…**: uses `TreeIndent`, so nested parents show the same 10 dp step
  and rule as the picker instead of spaces.

**363 tests, 0 failures.** Release APK rebuilt (2.1 MB) and installed.

### 2.31 Offline collection and binder reads — DONE 2026-11-02

The card database and the metagame snapshot were cached, but the stores were
not: offline, the grid showed a network error even though every card's metadata
was already on the device.

- New `StoreCache` (`files/opgg-stores.json`, versioned, atomic write) keeps the
  **binder list** and the **last successful rows per store** (including
  `"0"` = Collection). Written after every successful read; ~43 KB for five
  stores.
- When a live read fails (after the existing single retry), `loadBinder` restores
  the cached rows, sets `storeCacheAt`, and clears the error. The picker falls
  back to the cached binder list, so it no longer looks like the account has no
  binders.
- Cached rows are marked in the UI: "Offline. Showing the copy saved at 15:12;
  changes are disabled until you reconnect." The tile +/- are disabled while a
  store is showing cached data; writes are still refused offline.
- Verified with a black-hole proxy on the device: offline launch shows
  `800 copies` + the notice; the picker lists all five binders from cache; Yamato
  opens as `38 copies · $3.76` from cache. Online launch shows no notice.
- Eight `StoreCacheTest` cases (round-trip, per-store merge, list preservation,
  missing/corrupt/old-version files). **371 tests, 29 classes, 0 failures.**
- Release APK rebuilt (2.1 MB) and installed.

### 2.32 Offline polish: sticky banner, cache freshness, All binders, deck list — FIXED 2026-11-02

Follow-up audit of the offline work and the earlier gaps:

- **Sticky "Offline" banner.** `offline` was only ever set to true, so one blip
  left the Cards tab claiming to be offline for the rest of the session. It is
  now cleared by every successful store/deck read and every successful write.
  Verified: offline launch shows the notice; after reconnecting and refreshing,
  it is gone.
- **Cache lagged writes.** `StoreCache` was only written by a store *read*, so a
  kill right after an edit reopened offline on the old count. A successful
  owned/wish write now mirrors that store's rows into the cache. Verified:
  `4 -> 5`, kill, offline launch shows `STD 5` / `801 copies`.
- **Per-store timestamps.** `StoreCache` v2 keeps `storeSavedAt` per binder
  (`latestStoreSavedAt()` for the aggregate) instead of one global time, so
  opening an old binder no longer claims a newer "saved at".
- **"All binders" offline.** It was the only store skipped by the cache. The
  aggregate is now rebuilt from every cached store (`allRows()`); verified
  offline at `850 copies · $165.40`.
- **Public deck list offline.** New `DeckCache` (`opgg-decklist.json`) stores the
  accumulated list keyed by `(mine, query, page)`. A first-page failure restores
  it with "Offline. Showing the deck list saved at HH:mm." Fixed en route: the
  screen's first load calls `loadDecks()` without `reset`, so the fallback now
  keys on `page == 1` rather than `reset`.
- Both caches use a `Json` with `encodeDefaults = true`; the shared `AppJson`
  omits defaults, which meant the `version` field was never actually written,
  making future format migrations undetectable.
- **378 tests, 30 classes, 0 failures.** Release APK rebuilt and installed.

### 2.33 Meta list scroll preserved on return — FIXED 2026-11-02

Expanding an archetype, opening one of its staples and pressing Back reset the
Metagame list to the top: `MetaScreen` created its `LazyColumn` state locally,
and pushing the card page removes the whole screen from composition.

- The state is now hoisted into `AppRoot` (`metaListState`) and passed through
  `DecksScreen` to `MetaScreen`, the same pattern already used for the card,
  collection and editor grids.
- Verified on device: expand Rocks.D.Xebec, scroll to Staples, open Streusen,
  Back — the same rows remain in view instead of the list jumping to #1.
- **378 tests, 30 classes, 0 failures.** Release APK rebuilt and installed.

### 2.34 Related list filtered to same type AND colour — FIXED 2026-11-13

The crew fallback tier of the related-cards index grouped by card `Type` only,
so e.g. a red Navy card listed every other Navy card regardless of colour.

- `RelatedIndex` now also indexes `Color`, and the fallback (in both `related`
  and `relatedCount`, so the button and the list still agree) keeps only cards
  matching the seed's colour. A seed with a blank `Color` keeps the type-only
  behaviour, so colourless cards do not lose their list.
- **Colours are compared per component, not by whole string.** Multicolour
  leaders store `"Red/Green"`; the first exact-match pass left e.g. Kouzuki
  Oden with only the handful of cards carrying the identical combination.
  Colours are now split on `/` into sets and matched by intersection, so a
  Red/Green leader sees its type's Red and Green cards, and a single-colour
  card likewise picks up multicolour crew-mates that share its colour.
- The META tier (cards sharing tournament archetypes) is unchanged — it is a
  "played together" signal, not a type grouping.
- Wording updated: "See N cards of this type and colour" on the card page and
  "N cards of the same type and colour" on the list screen.
- **382 tests, 30 classes, 0 failures** (added: colour filter, colourless-seed
  fallback, multicolour component matching). Release APK rebuilt; verified on
  device (USB `a2b4905`): related list for EB01-001 (Kouzuki Oden, Land of
  Wano, Red/Green) shows "51 cards of the same type and colour" — exact match
  with the computed expectation; OP02-055 (Blue) went 56 → 58, exactly the two
  multicolour Blue crew-mates added by the intersection rule.

### 2.35 Builder filter revamp: one mechanism, Cards-tab parity — FIXED 2026-11-13

The editor had three overlapping filter mechanisms: the quick chip row
(Cards/Leader/Owned only/In deck/Standard/Extra), its own inline chip panel
(Colour/Type/Rarity/Counter) and the Cards-tab FilterSheet - two of them editing
the same shared query, and no Sort at all.

- **Filter icon opens the shared FilterSheet**, the same eight dropdowns as the
  Cards tab (Set, Rarity, Card type, Color, Attribute, Cost, Block + now
  Counter). The builder's inline chip panel and its `FilterChipRow` were
  deleted; the Counter buckets moved into the shared sheet (ids kept as tokens,
  friendly labels: "no counter", "+1000", "+2000+" via a `display` map on
  `MultiSelectDropdown`), so the Cards tab gains Counter filtering too.
- **Sort icon added** to the editor app bar, opening the shared `SortSheet`
  (now `internal`); the sort preference is the same persisted one.
- **Quick row slimmed** to the five builder-only toggles: "Leaders" (replacing
  the Cards/Leader radio pair), "Owned only", "In deck (n)", "Standard",
  "Extra".
- **Root cause of the old "sheet never opens from the editor" bug found:** the
  `FilterSheet`/`SortSheet` blocks were composed inside
  `if (current is OpggScreen.Tabs)` in `AppRoot`, so with the editor on top the
  flag was set but the sheet was never in composition. Moved outside; verified
  from the editor end to end (open sheet, Counter +1000 -> grid shows Doma and
  Bingoh only, Reset all clears, Leaders toggle shows only Leaders).
- Removed the stale `editorFiltersExpanded` UiState field in favour of
  `editorSortOpen`; `setEditorFlags` updated.
- **382 tests, 30 classes, 0 failures.** Release APK rebuilt and installed.

### 2.36 Editor filters independent of the Cards tab — FIXED 2026-11-13

The editor reused the Cards tab's query state, and worse: its grid searched
`state.visible`, the Cards tab's already-filtered pool - so a Cards-tab filter
silently shrank the editor's list, and an editor filter leaked into the Cards
grid.

- The editor now has its own query state (`editorQuery` / `editorParsed`) and
  searches the FULL pool (`state.cards`), sorted by the shared sort preference.
  The dead local `query` var is gone.
- `toggleTerm` / `clearTerm` / `clearTokens` were factored into shared private
  helpers; the editor variants (`setEditorQuery`, `toggleEditorTerm`,
  `clearEditorTerm`, `clearEditorTokens`, `clearEditorQuery`,
  `applyEditorSuggestion`) write only editor state and never call
  `scheduleFilter`, whose result only the Cards tab consumes.
- `FilterSheet` takes an `editor` flag: opened from the deck editor it reads
  and writes the editor's query; everywhere else the Cards tab's.
  `ActiveFilterChips` is likewise parametrised (`parsed`, `onClear`).
- Verified on device both ways: Cards tab `c:red` (557) -> editor grid still
  shows Blue cards and an unlit filter icon; editor `counter:+1000` (grid
  narrows to Shirahoshi) -> Cards tab still `Cards (557)` with `c:red` only.
- **382 tests, 30 classes, 0 failures.** Release APK rebuilt and installed.

### 2.37 Offline launch shows the cached snapshot immediately — FIXED 2026-11-18

"Offline mode is not working" really meant "35 seconds of Still loading…" before
the cached snapshot appeared: `loadBinderAttempt` only reached `StoreCache` in
its catch block, after the 20 s read timeout **and** the keep-alive retry, and
`loadDecks` was the same. The cache existed the whole time; the app just took a
timeout-shaped detour to get there.

- **Offline fast path.** `loadBinderAttempt` now checks
  `NetworkStatus.isOnline` before touching the network: offline, it applies the
  cached store at once. The catch-block cache logic was extracted into
  `applyCachedStore(binderId)` so the fast path and the catch block share one
  implementation. An empty cache still falls through to one live attempt, so a
  wrong "offline" verdict fails fast instead of showing nothing.
- The keep-alive retry after a timeout is skipped when offline — retrying a
  network that is not there is pure waiting.
- `loadDecks` got the same fast path (first page only, `applyCachedDecks`
  extracted from its catch block).
- **A failed card-database refresh no longer blanks the Cards tab.** Both the
  bootstrap background refresh and the manual refresh set `cardsError` on any
  failure, and the Cards tab replaces its whole grid with an error screen on
  `cardsError != null` — offline, over a perfectly browsable 5,671-row cache,
  that said "The card list could not be loaded", which was untrue. `cardsError`
  is now set only when there are no cards to show; with cards on screen a
  failed manual refresh becomes a snackbar, a failed background refresh just
  logs (`OpggCards`).
- **Connectivity recovery.** A successful card-database fetch (manual or
  background) now clears `state.offline`, so the Cards tab's OfflineNotice
  does not outlive a proven connection. The Collection tab's CachedStoreNotice
  is deliberately unchanged: its snapshot is still the saved copy until a
  binder load succeeds.

Verified on device (debug APK, WiFi + mobile data off, fresh process):

- Offline launch: "906 copies" + "Offline. Showing the copy saved at …" within
  ~9 s of process start (previously ~35 s).
- Cards tab offline: grid usable, OfflineNotice shown, no error state.
- Reconnect + Cards-tab Refresh: offline notice cleared.
- Reconnect + Collection-tab Refresh: cached snapshot replaced by live data,
  cached notice cleared.

Caveat found during testing: a **VPN whose underlying network is dead** keeps
its `VALIDATED` capability, so `isOnline` returns true and the fast path does
not trigger; the request then hangs to the normal timeouts before the cache
applies. VPN users on a dropped connection see the old slow behaviour. Handling
that (e.g. distrust a validated VPN when no non-VPN transport is validated) is
left as an open item.

- **382 tests, 30 classes, 0 failures.** Debug APK verified; release APK
  rebuilt and installed.

### 2.38 Default order was meaningless; a hidden Descending switch made Name sort read as broken — FIXED 2026-11-18

Two findings from "the sort function — I feel that it's not working":

- **Default order (`SortField.NONE`) sorted by nothing.** It returned the rows
  exactly as the API had produced them, and that row order has no pattern
  (OP16-077 first, then promos, then …), so the grid read as shuffled.
  **Default order is now the card's release date**: newest set first, then card
  number ascending within a set. Promos (date `0`) land after every dated set;
  a card whose set code is unknown to `getsets` sorts last. Implementation:
  - `Card` gained a `@Transient releaseDate: Long?` **constructor** param —
    transient so the cache format does not change, and a constructor param
    because a body-declared `val` cannot be set through `copy()`.
  - `applyCards` stamps every row from the `getsets` list it already receives,
    via a new `setReleaseDates(sets)` map keyed by set code, the hyphen-stripped
    alias ("ST-01" beside "ST01") and `id`. A real date beats a `0` wherever the
    aliases collide; the date is re-derived on every load, so old caches stay
    valid.
  - The Descending switch is deliberately hidden for Default order and the
    direction is not multiplied into it.
- **The Descending switch sat below the fold and a stale flag made Name sort
  look reversed.** The sheet's 13 radio rows fill its 560 dp height, so the
  switch — the last row — never appeared without scrolling. A `descending=true`
  persisted from an earlier session survived, and with Name selected the grid
  showed P-134, P-133, P-132 … (name order descending by code point) with no
  visible way to see or change the direction. That mismatch was the whole
  "sort feels not working" report. The switch is now the **second row of the
  sheet, directly under "Sort by"**, above the field list, visible without
  scrolling whenever a real field is selected.

Verified on device (debug APK): Default order → OP18-001, 002, 003 … (newest
set, number ascending); Name + Descending toggled from the new top position
flips the grid between `...I Want to Live!!` (EB01-050) first and P-134 first;
switch state persists.

- **384 tests, 30 classes, 0 failures.** Debug APK verified on device;
  release APK rebuilt and installed.

### 2.39 Set sort scattered the promo sets (DON!! cards on top); Card number ignored the set — FIXED 2026-11-18

Follow-up on the §2.38 sort work, from "how about the DON set? It shows on top
of the sort result":

- **Set sort is now chronological, not alphabetical.** The string sort of set
  codes put every hyphenated promo code first ("-" sorts below the digits:
  "EB-03" before "EB01", "OP-PR" before "OP01"), and the DON!! cards live in
  exactly those date-0 sets — so they led "Set ascending". Now: the set's
  release date first (ascending = oldest set first), then set code, then the
  id's number. A release date of **0 counts as undated**, and undated sets sit
  last in **both** directions, like every other null — so the promo/DON sets
  can never lead the list. Sets sharing the launch-wave date (OP01, ST01–04)
  order by set code within the date.
- **Within a set group, DON!! cards no longer lead.** They ship inside the
  numbered sets (set code "OP01", id "DON-456059"), and the id tie-break put
  "DON-…" before "OP01-001". The shared tie-break is now set code → id
  **number** → id, so a set group starts at its lowest number and the DON
  cards follow the numbered cards. This tie-break is shared by every sort
  field, which also changes ties on null-set-code rows to number order.
- **Card number sort is now set-aware.** It is still the trailing id number
  first ("OP06-081" → 81, numeric not string order), but cards sharing a
  number now group by the set's release date instead of interleaving by id
  string (EB01-001, OP01-001, ST01-001, …); a DON!! id ("DON-434340" →
  434340) is just a very large number and lands after the numbered cards.
- One verification trap worth recording: LazyVerticalGrid **keeps its scroll
  position across a re-sort**, so reading the "top" of the grid after changing
  the sort without scrolling back to the top (or restarting the process) shows
  an arbitrary offset and looks like the sort is wrong.

Verified on device (debug APK): Set ascending starts OP01-001, 002 … (DON gone
from the top); Default order starts OP18-001; Set descending starts OP18-119,
118 …; Name ascending restored as the persisted preference.

- **391 tests, 30 classes, 0 failures.** Debug APK verified on device;
  release APK rebuilt and installed.

### 2.40 Card number sort removed — 2026-11-18

The sheet's "Card number" option is gone. Nothing needed it once Set sort went
chronological: Default order already is "set, then number", and the number
tie-break inside every other sort covers the rest. `SortField.NUMBER` and its
comparator branch are deleted; `numericSuffix` survives because the shared
set-code → number → id tie-break still uses it.

**Removal is graceful for an existing pref.** `fromKey` falls back to Default
order for any unknown key, so a device whose persisted pref still says
`"number"` loads as Default order — observed live on the device (the sheet
showed Default order selected, Descending hidden, grid sorted newest-set
first, no crash). Verified in a test (`removedNumberKeyFallsBackToDefaultOrder`);
after selecting a field again the pref is rewritten to a live key.

- **389 tests, 30 classes, 0 failures.** Debug APK verified on device
  (sheet: Default order, Name, Set, Cost, Power, … — no Card number);
  release APK rebuilt and installed.

### 2.41 Sign-in now opens the site's own login page, ad-free — 2026-11-18

The sign-in WebView used to load the onepiece.gg **homepage**, leaving the user
to hunt for the site's Log in button inside a page full of ads and a cookie
banner. Two changes, both enabled by the site itself:

- **Load `https://dotgg.gg/login/?returl=https://onepiece.gg/`** — the DotGG
  account system's dedicated login page (the same one `api.dotgg.gg/auth.php`
  redirects to). The sign-in form — Google, Discord, email + password — is in
  front of the user immediately; `returl` sends the site back to the SPA after
  auth, where its own JavaScript performs the token exchange the app polls
  localStorage for. Nothing else in the flow changed.
- **Append `DotGGApp/` to the WebView user agent.** The login page's own CSS
  tests for that token and, when present, strips the site header, footer,
  sidebar, ad slots and the cookie banner (`data-dotgg-app` attribute) — the
  site ships built-in support for companion-app webviews.

Verified on device (debug APK, screenshot): the login page renders dark and
clean, with "Continue with Discord", "Continue with Google", an email/password
form and a "Continue to onepiece.gg" link — no cookie banner, no ads, no site
chrome. **Google sign-in was then completed end-to-end on a signed release
build** (user taps "Continue with Google", the site minted the token, the app
picked it up and authenticated — `Server sees user id` populated), so the
embedded-browser question is answered for now.

Remaining sign-in gap: 2FA unsupported (§3). The WebView now has a retry/error
state (§2.42).

### 2.42 Public-release checklist — 2026-11-18

All checklist items done:

- **Real signing key** — created 2026-11-18 at
  `~/.dotgg-tools/keystore/opgg-release.jks` (4096-bit RSA, alias `opgg`,
  10,000 days, PKCS12), kept outside the repo; `keystore.properties` (git-ignored)
  wires it in; both `.jks` and the password file backed up by the user.
  `assembleRelease` now signs release; `apksigner verify --print-certs` confirms
  the APK (`SHA-256 4b96d02f…af494`), as does the `base.apk` pulled off the
  device after install.
- **End-to-end sign-in on release APK** — verified 2026-11-18 via Google OAuth
  inside the WebView; app auto-closed on token pickup and resumed authenticated.
- **Known-limitations + support section** — README install section now carries
  known limitations (unofficial API, Google-in-WebView status now confirmed
  working but could tighten, 2FA unsupported, read offline/write online, deck
  editing partial, unaudited writes, VPN/IPv6 caveat), privacy paragraph, and a
  support address ("open a GitHub issue; never paste your token").
- **Privacy note** — README: bearer token + caches stored unencrypted in app
  sandbox; token rotates on sign out/in; nothing hardcoded; sign-in happens on
  the site's own page.
- **WebView retry/error state** — `SignInActivity` shows an in-app "Can't
  reach OnePiece.gg" card with a **Retry** button on main-frame load failure
  instead of the browser's bare "Web page not available"; a new navigation
  clears the error so logging in succeeds normally.
- **Fresh second account test** — not done; needs a second account.

Build: **389/389 tests, 30 classes, 0 failures; release APK assembled and
signature-verified.** Installing the fresh key requires uninstall first
(INSTALL_FAILED_UPDATE_INCOMPATIBLE vs. debug-signed prior installs), which
wipes app data - the README quick start documents this.

### 2.43 Decks can be saved to onepiece.gg — 2026-11-19

`§2.1` recorded server sync as blocked because `savedeck` looked create-only
through nine selector variants. It was not create-only: **the selector is
`deckname`**, a machine name the site's builder generates, and `id` / `slug` /
`name` are ignored. We missed it because the write code lives in a lazy JS chunk
that was never found.

The body was captured from the site's own builder
(`onepiece.gg/builder/<deckName>`), then verified against the live API before
any code was written:

- Re-POSTing the captured body with the same `deckname` returned the **same id**
  (`1870759`) with `date_edited` moved - an update, not a duplicate.
- Sending three copies of one card **replaced** the four-card list wholesale.
- `humanname` is the display title; changing it regenerated the public `slug`
  (`ace-newgate-deck-n6a4i` → `opgg-update-test`), while `id`/`deckname` stayed
  stable. So the app stores **`deckname`**, never the slug.
- `public` / `unrestricted` are strings (`"0"`), not booleans.
- No WordPress cookie is needed - `Dotgguserauth` alone, like every other write.

Implementation:

- `data/DeckWrite.kt` - `SaveDeckRequest` with **no default values**, because
  Ktor's kotlinx serializer omits defaults and the site always sends every field.
  `SaveDeckResponse` (accepts `error`/`error_text`), `SiteDeckName.generate()`
  producing the site's 10-char lowercase-alphanumeric shape.
- `OpggApi.saveDeck()` - POSTs `cgfw("savedeck")`, throws on `error:true` even
  though the response is HTTP 200.
- `LocalDeck` gained `siteDeckname` / `siteSlug` (serializable with defaults, so
  existing `opgg-decks.json` files load unchanged). First save stores the name,
  later saves reuse it - renaming in the app renames the site copy instead of
  creating another.
- Deck menu: **Save to onepiece.gg** / **Update on onepiece.gg**.
- `DeckWriteTest` (4 tests) pins the field set, the string-typed flags, the
  printing→count map and the generated name shape.

**394 tests, 31 classes, 0 failures.** Verified end-to-end through the UI on the
release build (2026-11-19): saving the local 50-card "Blue Nami" made it appear
in the account's Mine tab with 51 cards (leader included), and saving again
showed "Update on onepiece.gg" while the deck count stayed at 8 - no duplicate.

## 3. Open gaps and risks
- [ ] **VPN with a dead underlying network defeats the offline fast path.**
      Android keeps `NET_CAPABILITY_VALIDATED` sticky on the VPN network, so
      `NetworkStatus.isOnline` reports online and requests hang into the normal
      timeouts (§2.37). Consider distrusting a validated VPN network when no
      non-VPN transport (WiFi/cellular/ethernet) is itself validated.

- [ ] **Offline writes with sync-on-reconnect** — designed and estimated in
      §2.00 (10–12 h for counts + wishes). Not started.

- [ ] **Delete five stray "New Deck" entries** (ids 1837006, 1837007, 1837687,
      1837688, 1837689) created on the account while probing for `savedeck`.
      `deletedeck` cannot remove them.
- [ ] **Audit the other write endpoints for HTTP 200 with an error body.** Two
      were found the hard way: `savebindercard` (error:true) and
      `savebatchcollection`'s per-card `errors` array (error:false).
      `setusersetting`, `importcollection` and `savebatchbindercard` remain
      unchecked. `movecards`, `createbinder`, `renamebinder` and `deletebinder`
      are checked (they throw on `error:true`).
- [x] **Deck, editor and related-list screens** — rendered and checked on the
      device (§2.30). No crashes; deck detail/editor/related list all draw.
- [x] **The `savedeck` update path is settled** (2026-11-19). The body was
      captured from the site's own builder: the identity field is `deckname` (a
      machine name), the title is `humanname`, and re-POSTing the same
      `deckname` updates in place. Verified live (same id, `date_edited` moved,
      no new deck). The app now saves and updates decks from the builder menu
      (§2.43). `deletedeck` is still a no-op.
- [x] **Google sign-in inside a WebView** — **works.** Verified end-to-end on a
      signed release build, 2026-11-18: Google OAuth completed inside the WebView,
      the site minted the token, the app picked it up and authenticated
      (`Server sees user id` stopped being `-`). Google could still tighten
      embedded-browser rules later; Discord and email + password are the fallbacks
      on the same page.
- [ ] **2FA unsupported.** `requires_2fa` and a TOTP flow exist. The login page is
      used as-is, so whatever it renders is what you get, but no account with 2FA
      has been tested and the older native API path has no TOTP screen.
- [x] **The in-app browser is slow on first load** — fixed as far as it can be:
      it now loads the DotGG login page (323 KB) instead of the ad-laden homepage
      (484 KB + ad tech), with the site's own `DotGGApp/` companion mode stripping
      the header, footer, sidebar, ads and cookie banner (§2.41). Caches after.
      `wp-login.php` still returns 403, so there is no lighter page.
- [ ] **Token is a bearer credential** and has appeared in chat transcripts. Sign
      out / back in rotates it. Not hardcoded anywhere. It is stored **unencrypted**
      in DataStore — see the encryption item below.
- [x] **`minifyEnabled`** — release now uses R8 + resource shrinking (§2.29).
- [x] **Release keystore** — created 2026-11-18: 4096-bit RSA, alias `opgg`,
      10,000 days, kept outside the repo at
      `~/.dotgg-tools/keystore/opgg-release.jks`; `keystore.properties` (git-ignored)
      wires it in. Release APK and the installed `base.apk` both verify against
      `SHA-256 4b96d02f…af494`. **The user must back both files up** — losing them
      means no future update can install over an existing one.
- [ ] **No fuzzy match in search** (`sw` will not find `Slash`); tap-only
      suggestions, no keyboard navigation.
- [ ] **Search has no placeholder** (removed on request), so discoverability rests
      on the suggestion dropdown appearing.
- [ ] **Deck list paging depth unknown** — verified 30 decks on pages 1–20; the
      binary search was invalidated by rate limiting.
- [ ] **⚠ Rate limiting is per IP** — research traffic and the phone on the same
      Wi-Fi share one budget, and roughly 50 rapid requests trips Cloudflare 1015.
---

## 4. Verified data facts

- **5,540** card rows → **3,063** distinct cards (`id_normal`)
- Reprints: **2,012** `_pN` + **465** `_rN** = 2,477; exactly 3,063 rows have
  `id == id_normal`
- **34** fields per card
- Payload: **3,148,059** bytes (`?mode=indexed`) vs **5,230,704** (default)
- Field discriminativeness (for relatedness): crew 175 distinct / median group 7;
  cost 10/200; attribute 7/135; colour 6/476; cardType 5/240 — **crew is the only
  useful client-side signal**
- 70 sets; 5 card types; 9 rarities including `"SP CARD"` (contains a space)
- `Color`, `Type`, `Attribute` are `/`-separated: `"Red/Blue"`,
  `"Alabasta/Straw Hat Crew"`, `"Slash / Wisdom"` (mixed spacing)
- `getuserdata` counts arrive as **strings**: `standard`, `foil`, `wish`,
  `wish_foil`, `trade`, `trade_foil` (plain digits, no padding; only `wish` is
  writable via `savebatchcollection`; omitted fields are **overwritten** —
  owned edits must echo `wish`. `wish_foil` could not be set nonzero through
  the endpoint at all, so its overwrite behaviour is unmeasured)
- `capabilities` is an **object**; `binder_id 0` is valid and returns the
  collection; deck card ids are **printing** ids
- **Cached card database is 2,674,028 bytes for 3,063 cards**; under an hour old
  it triggers **no network request**, older it returns **304**
- API sends `Last-Modified`, **no `ETag`**; `Cache-Control: max-age=3600`
- **The test phone has no global IPv6 route** — `curl -6` fails, `curl -4` works,
  and Java/OkHttp resolution fails on AAAA where curl's `AI_ADDRCONFIG` does not
- Reprint suffix families: `_pN` (2,012) and `_rN` (465)
- Static assets: `icon/{attribute}.webp` exists; `icon/{rarity}.webp` and
  `card/DON.webp` **404**
- `getmetasnapshot`: 53 archetypes, 636 card entries, **443** distinct cards
  covered
- `getdecks`: only `page` and `my` work; 30/page
- Deck write endpoint is **`savedeck`** (not `setdecks`). Its selector is
  **`deckname`**, a 10-char machine name; `id`, `slug` and `name` are ignored, so
  re-POSTing a `deckname` updates and any new one creates (the card map is
  replaced wholesale). The display title is `humanname`, and the slug is derived
  from it. `deletedeck` is still a no-op.
- Collection writes: `savebindercard` **requires a real binder** and fails at
  binder 0 with `Binder not found`; **`savebatchcollection`** takes no binder and
  is what the site calls. Verified additive (two separate writes both persisted).
  `synclocalcollection` and `savebatchbindercard` are the other two variants.
- `savebatchcollection` can return `error:false` **with** a per-card `errors`
  array — both must be checked.
- Deck code format: `4xOP01-016`
- One Piece deck rules: 50 cards, 4 copies **per printing**, max 2 colours,
  1 leader that defines the allowed colours and is excluded from the count
- Columnar response key is **`data`**, not `rows`
- API returns HTTP **200** with body `Hacker! Go home!` for unknown routes and
  for header-less requests — never a 404
