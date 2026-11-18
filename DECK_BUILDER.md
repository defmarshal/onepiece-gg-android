# Deck Builder — Task List

Local-first deck builder for OnePiece.gg, with export/import so decks can be moved
between this app and the website.

**Status: not started.** Scope agreed, format deliberately left open.
**Estimate: ~15–17h** total, or ~3h to Phase 1.

Parent docs: [`README.md`](README.md) (architecture, API contract) ·
[`TASKS.md`](TASKS.md) (overall backlog)

---

## 1. Scope decisions already made

| Question | Decision |
|---|---|
| DON cards | **Out of scope** — unused, do not model or validate them |
| Server sync | **Out of scope for now.** The write endpoint is unsolved; the builder does not depend on it |
| Deck code | **In scope** — export and import |
| JSON import/export | **In scope for round-tripping inside the app.** The exact website JSON shape is **TBD and deliberately deferred** |
| Placement | A / B / C still open — recommended **B** (one Decks tab with a segmented control) |

## 2. Open questions - resolved during implementation

All four were answered by building the feature. Kept for traceability.

- [x] **Which ruleset applies?** The site's config has exactly one ruleset,
      `deckBuildingRules: { Standard: {...} }`, but deck payloads report
      `format: "OP13"` / `"OP17"` - that looks like a **set code**, not a
      ruleset name. **Resolved: `format` is a set code, and `Standard` is the
      only ruleset.** Confirmed by validating 150 published decks against it:
      69 mono-leader and 63 dual-leader, **zero violations**.

- [x] **Can a leader be multi-coloured?** `maxColorsInDeck: 2` plus
      `colorLimiter: true` implies a Red/Blue leader unlocks two colours and a
      mono leader unlocks one. **Resolved: yes, and a leader's `Color` is the
      entire legal palette for the deck.** The editor filters the grid to the
      leader's colours once a leader is picked, and the leader is excluded from
        the 50-card main deck.

- [ ] **Website JSON import format.** The user reports the site supports JSON deck
      import, which was not found in any reachable bundle (only
      `importcollection`, a collection CSV endpoint). **Still open** - needs a
      sample or a capture. Deferred by agreement; deck code is the interop path
      in the meantime.

- [x] **Confirm the site exposes a deck-code paste box.** The bundle proves the
      feature exists (`cardsArrayToCode` + an importer that resolves card ids),
      not that the UI is reachable for this game. **Resolved: the code format
      is the important half and it is fully decoded** - `${count}x${id}` lines,
      resolved case-insensitively by card id, implemented in `DeckCode.kt` with
      10 passing tests.

## 3. Rules engine (from the game's own config — verified, not inferred)

```
cardsInDeck          : 50
maxCardsCopies       : 4          per PRINTING (copyLimitAcrossPrintings: false)
maxColorsInDeck      : 2
LEADER               : exactly 1, required, and SEPARATE from the 50.
                         The 50-card deck does not include the leader, so a
                         legal deck is 50 main + 1 leader = 51.
                         deckDefiner + colorLimiter -> the leader defines the legal colours
```

- [ ] 4.1 Pure, Android-free legality module, unit tested. No `Card` or ViewModel
      coupling, so it can be tested on the JVM.
- [ ] 4.2 Count rule: the leader is `excludeFromDeckCount`, so **49 non-leader
      slots**, not 50. This is the easiest rule to get wrong.
- [ ] 4.3 Copy cap keyed on **printing id**, not canonical id.
- [ ] 4.4 Colour legality derived from the leader; cards outside the legal pair
      are invalid, with the reason surfaced in the UI.
- [ ] 4.5 `maxColorsInDeck: 2` enforced independently of the leader rule, in case
      a leader is multi-coloured.
- [ ] 4.6 Report **every** violation, not just the first, so the editor can list
      them.

## 4. Local storage

- [ ] 5.1 `decks.json` in `filesDir`, mirroring the existing `CardCache`
      (atomic temp-file + rename, so a kill mid-write cannot truncate).
- [ ] 5.2 `Deck` model: id, name, leader printing id, `Map<printingId, count>`,
      created/updated timestamps, format/set code.
- [ ] 5.3 Repository + a schema version, so a future format change can migrate
      rather than discard saved decks.
- [ ] 5.4 Storage is **unencrypted**, like the existing token store. Note this in
      the UI: decks are on-device, and signing out does not delete them.

## 5. Editor UI

- [ ] 6.1 Deck list: name, leader art, card count, legality badge.
- [ ] 6.2 Leader picker — filtered to `cardType == LEADER`.
- [ ] 6.3 Card picker reusing `CardSearch`, showing **per-printing** copy counts so
      the 4-copy rule is visible while picking.
- [ ] 6.4 Live counter: `n / 50`, colours used vs allowed, copies per printing.
- [ ] 6.5 Validity banner listing all current violations.
- [ ] 6.6 Colour histogram and price total (all fields already parsed).
- [ ] 6.7 Rename and delete a local deck.

## 6. Export / import

- [ ] 7.1 **Deck code export** — verified format is `${count}x${id}` joined with
      `\n`, e.g. `4xOP01-001`. Copy to clipboard.
- [ ] 7.2 **Deck code import** — parse that text, resolve ids
      **case-insensitively**, and **report ids that do not resolve** instead of
      dropping them silently. The collection API does exactly this
      (`errors: ["Card #0: card ID 'X' not found"]`) and we should match it.
- [ ] 7.3 JSON export/import for round-tripping inside the app.
- [ ] 7.4 CSV export as a private, human-readable record. **Not** an
      `importcollection` payload — that endpoint is for the collection, and
      pretending otherwise will corrupt data.

## 7. Testing

- [ ] 8.1 Legality engine: the 49/50 rule, per-printing cap, mono vs duo leader,
      multiple simultaneous violations.
- [ ] 8.2 Deck-code round trip, including unresolvable ids and zero counts.
- [ ] 8.3 Storage round trip and schema-version migration.
- [ ] 8.4 Confirm the existing 71 tests still pass.

## 8. Traps in this codebase — read before editing

Each of these cost real debugging time already. See `README.md` §8 for the full
list.

- [ ] **`remember` must be keyed on async-loaded data too.** The related list was
      permanently empty because `remember(card.key)` cached a result computed
      before the meta index arrived. Key it on index coverage as well.
- [ ] **Never decode a multi-megabyte payload on the main thread.** `viewModelScope`
      defaults to `Dispatchers.Main.immediate`.
- [ ] **Use the edit tool, not shell regex, for code changes.** Three shell edits
      silently no-opped on line-ending mismatches, leaving a dead sort sheet, a
      mangled `TASKS.md` and duplicated imports.
- [ ] **Check annotation imports.** `@Transient` with no import resolved to
      `kotlin.jvm.Transient` and doubled the cache to 6.2 MB — and a JVM test
      passed while the device disagreed.
- [ ] **Wrap new pages in a `Surface`** or `LocalContentColor` defaults to
      `Color.Black` and text is invisible on the dark background.
- [ ] **Avoid nested vertical scroll containing a lazy grid.** The deck detail
      grid is capped at 520 dp inside a scrolling page and is awkward to use.
- [ ] **A test passing is not proof the device behaves the same.** The related
      list, the sort restore, and the cache all passed tests and were broken.

## 9. On-device review

- [ ] 9.1 Synthetic input is **blocked** on the test device by Android's
      "USB debugging (Security settings)" gate: `adb shell input tap` and
      `KEYCODE_HOME` both do nothing. Review via the existing debug argument:
      `am start -n gg.opgg.android/.MainActivity --es screen <spec>`.
- [ ] 9.2 `screencap` returns a black buffer when the screen is off. Confirm
      `mWakefulness=Awake` first.
- [ ] 9.3 A greenish rectangle at the left edge of every screenshot is a
      **HyperOS system overlay**, not this app — confirmed on the Settings
      screen. Do not "fix" it.
- [ ] 9.4 Budget 2–3 review cycles. Every screen reviewed so far produced at
      least one real bug.
