# One Piece TCG — rules and deck theory

Reference notes for the app. Written 2026-09-28.

**Read the confidence labels.** They are not decoration:

- **AUTHORITATIVE** — quoted or paraphrased from an official Bandai document,
  with the rule number. Safe to implement against.
- **VERIFIED** — I checked it myself against live API data.
- **INDICATIVE** — my own measurement from a small sample. Useful as a sanity
  check, *not* a rule and *not* something to hardcode.
- **UNVERIFIED** — heard, plausible, not confirmed. Do not implement.

I originally intended this file to be about "ideal ratios". Research showed the
honest split is: the *rules* are fully documented and the *ratios* are not
something Bandai publishes and that I can only weakly measure. Those are kept
separate below.

---

## 1. Sources

| Source | Version / date | Notes |
|---|---|---|
| [Comprehensive Rules](https://en.onepiece-cardgame.com/pdf/rule_comprehensive.pdf) | 2026-08-28 | The primary document. 28 pages. All rule numbers below come from here. |
| [Rule Manual](https://en.onepiece-cardgame.com/pdf/rule_manual.pdf) | 2023-06-23 | Older, condensed. Prefer Comprehensive. |
| [Tournament Rules Manual](https://en.onepiece-cardgame.com/pdf/tournament_rules_manual.pdf) | 2026-01-16 | Event/tournament specifics. Not yet mined. |
| [Block Number System](https://en.onepiece-cardgame.com/topics/013.php) | 2025-07-23 | Effective 2026-04-01. Major. See §4. |
| [Banned/Restricted notice](https://en.onepiece-cardgame.com/news/restriction-261001.html) | 2026-09-24 | Effective 2026-10-12. See §3. |
| [Rules index](https://en.onepiece-cardgame.com/rules/) | — | Also holds errata notices, which matter for card text. |

`en.onepiece-cardgame.com` is the NA/EU/OC/LATAM/ME site. There are separate JP,
CN, TC, TH and KR sites with different legal pools.

---

## 2. Deck construction — AUTHORITATIVE

Rule **5-1-2** is the whole legal-deck definition:

> Each player needs **exactly 1 Leader card, a 50-card deck, and a 10-card
> DON!! deck** to play.

| Rule | Requirement |
|---|---|
| 5-1-2 | 1 Leader + 50-card deck + 10-card DON!! deck |
| 5-1-2-1 | The deck is made up of **Character, Event and Stage** cards |
| 5-1-2-2 | Only cards whose colour appears on the **Leader** may be included |
| 5-1-2-3 | **No more than 4 cards with the same card number** |
| 5-2-1-1 | The deck must meet 5-1-2 to be presented for play |

### 2.1 What this means concretely

- **The Leader is NOT one of the 50.** Rule 5-1-2 lists three separate items —
  "1 Leader card, a 50-card deck, and a 10-card DON!! deck" — so the 50-card deck
  is its own pile. A legal deck is **50 main-deck cards + 1 Leader = 51 cards**.

  > **This was a live bug.** The app originally treated the 50 as *including*
  > the Leader and capped main-deck cards at 49. The site's `deckBuildingRules`
  > config makes this easy to get wrong, and an earlier version of this file
  > rationalised the 49 rather than checking. Fixed in `DeckRules.kt`
  > (`MAIN_DECK_SIZE = 50`, `TOTAL_DECK_SIZE = 51`) on 2026-09-28.

  **VERIFIED against the public deck list** (750 decks, of which 619 are
  genuinely complete — exactly 1 Leader plus exactly 50 non-Leader cards):
  - 670 of 750 decks contain 51 cards.
  - **All 104 decks flagged `is_tournament = 1` contain 51 cards.**
  - 619 complete decks span **96 distinct Leaders**; 100 of them are tournament
    decks.
  - The residual sizes are near-complete work-in-progress lists (37-49 cards) and
    a few over-built invalid decks (52-54, and some outliers up to 85).

- **"Same card number" is per printing, not per card.** `OP12-034` and
  `OP12-034_p2` are two different card numbers, so 4 of each is legal. This
  matches the app's per-printing copy limit of 4 and is confirmed by 150
  published decks with zero violations.
- The Leader's colours are a **hard filter** — 5-1-2-2, and the site calls it
  `colorLimiter`. A mono Leader forces a mono deck. Verified across 150 decks:
  69 mono-leader, 63 dual-leader, zero colour violations.
- **DON!! cards are not deck cards.** They live in the separate DON!! deck, which
  is why the app excludes them from the builder. A deck ready for play is
  therefore 51 cards + a 10-card DON!! pile = 61 physical cards.

### 2.2 The escape hatch the app does not model

| Rule | Text |
|---|---|
| 5-1-2-4 | Effects related to deck construction rules are **permanent effects which replace the deck construction rules above** |
| 5-1-2-4-1 | Such effects specify that the deck can / cannot contain a specified number of cards of a certain category |
| 5-1-2-4-2 | They are **valid during deck construction** |

So a Leader *can* override the copy limit or the card count. The app hardcodes
`Standard` rules for every Leader.

**VERIFIED: this is safe today.** I searched all 364 Leader rows for effects
mentioning "deck". 117 matched, but every one is in-play deck manipulation (top
of deck, look at top card, deck-out conditions) — **none** modifies deck
construction. So the current hardcoding produces no wrong answers.

**But it is an assumption, not a guarantee.** If a future Leader changes deck
construction, the app will silently mark a legal deck illegal. Worth a
data-driven check keyed on the Leader's effect text before shipping, or at least
a comment in `DeckRules.kt` recording that this was verified on 2026-09-28
against 364 Leaders.

### 2.3 DON!! deck — AUTHORITATIVE

- Exactly **10 DON!! cards** (5-1-2). It is a separate 10-card pile, not part of
  the 50.
- Each DON!! card gives **+1000 power** to a Leader or Character it is given
  (6-5-5-2). Giving is unlimited (6-5-5-3).
- The DON!! deck is public/open information (3-3-2).
- Not modelled in the app — deliberately out of scope. No composition rule for
  the 10 cards exists in 5-1-2; it is simply 10 DON!! cards.

---

## 3. Banned / restricted — AUTHORITATIVE, now implemented

Effective **2026-10-12** (announced 2026-09-24):

- **OP14-020 Dracule Mihawk — BANNED.** "The following card(s) cannot be
  included in any deck." The notice explicitly includes *parallel cards
  (Alt-Art cards)*, so `OP14-020_p1`, `_p2` and any reprint are equally
  illegal.
- A replacement Leader `P-163 Dracule Mihawk` is planned for future events.

**Now implemented** in `data/Regulation.kt`. Two things were verified first:

- **The API exposes no ban status at all.** Ten candidate endpoints were
  probed and every one answered HTTP 200 with the body `Hacker! Go home!`,
  which is this API's reply to an unrecognised action. No card field
  mentions ban or restriction, and a known banned card (`OP14-020`) looks
  entirely ordinary in the payload with `BlockIcon=4`. The list is
  therefore **hand-maintained**, which is exactly why its age is printed in
  the editor rather than trusted silently.
- The ban is stored **canonically**, so every printing and parallel is
  covered - the notice calls the parallels out explicitly - and `isBanned`
  strips the `_pN`/`_rN` suffix itself, so a caller cannot accidentally
  exempt a banned parallel.
- **Extra Regulation ignores bans.** The notice is framed around tournament
  play and Bandai's stated aim is that fans enjoy numerous decks built
  around the leader card, so a banned card stays playable in Extra rather
  than leaving the game entirely.

The ban policy is worth reading for design intent: Bandai state their primary
policy is that "players and fans can enjoy numerous decks built around the
leader card", and they evaluate a deck on win rate, major-tournament results,
usage rate, and **whether it restricts the viability of other Leaders**. Mihawk
was banned specifically for "severely harm[ing] the diversity of Leader
selection".

**The app models no ban list at all.** A deck built on Mihawk is currently
reported as perfectly legal. Given the ban lands in ~2 weeks from this writing,
and Mihawk is the single most-played Leader in the current meta, this is the
**highest-value correctness gap** in the rules engine.

---

## 4. Block Number System — AUTHORITATIVE, and now implemented

Announced 2025-07-23, **effective 2026-04-01**. This is the most significant
recent change and it is a hard legality constraint.

- The number in the lower-right of each card is a **Block Number**.
- Blocks advance in pairs of boosters: boosters 1-4 = Block 1, 5-8 = Block 2,
  and so on.
- From April 2026 there are **two regulations**:

| Regulation | Card pool (Apr 2026 - Mar 2027) | Purpose |
|---|---|---|
| **Standard Regulation** | Blocks **2-5** (Block 1 excluded) | ~3 years of cards. Consistent difficulty. |
| **Extra Regulation** | Blocks **1**-most recent | Entire back-catalog, maximum flexibility |

- Block numbers are updated annually. For Apr 2027 - Mar 2028, Standard becomes
  Blocks 3-6.
- **Block "X" is always legal** — new Super Parallel Rares and same-number
  cards carry X "regardless of reprints", mirroring existing SPR treatment.
- Bandai's stated motivation: without this, the pool would be 4 years of cards by
  2026 and ten years by 2032.

**Now implemented** in `data/Regulation.kt`. The window is **computed from
the date, never hardcoded**, so nothing needs changing each April. With
`seasonStartYear = year` when month >= 4 else `year - 1`, the legal Blocks
are `2 + (seasonStartYear - 2026) .. that + 3`. September 2026 gives 2-5,
matching the published schedule; April 2027 gives 3-6 with no code change.

**The empty-BlockIcon question is settled empirically, not assumed.** Of the
copies in 52 tournament decks, **1.8% were cards with no BlockIcon at all** -
`OP01-016_p6`, `P-028_p2`, `OP05-005_p1` and similar parallels. Those are
legal and being played, so an empty value cannot mean restricted and is
treated as legal. An unparseable value such as `?` is also treated as legal,
because guessing 'restricted' would wrongly outlaw real cards.

**Block X is always legal**, per Bandai's note that new Super Parallel Rares
and same-number cards are handled regardless of reprint.

**One thing is still not resolved:** 3.1% of those tournament-deck copies
were Block 1, outside the current window. The likeliest explanation is that
the public deck list spans older events, so those cards were legal when the
deck was played - but the API offers no date filter to confirm it. The rule
is therefore *probable* and *not proven*.

**VERIFIED against the live API:** the `getcards` payload carries a **`BlockIcon`
field**, so the app can implement this without new data work.

Distribution across all 5,540 card rows:

| BlockIcon | Rows | Non-DON cards |
|---|---|---|
| 1 | 1,443 | 1,443 |
| 2 | 1,263 | 1,263 |
| 3 | 1,230 | 1,230 |
| 4 | 791 | 791 |
| 5 | 390 | 390 |
| X | 28 | 28 |
| *(empty)* | 395 | 155 |

Two things to note:
- **All 240 DON!! cards have an empty BlockIcon**, which makes sense — DON cards
  are never restricted.
- **155 non-DON cards also have an empty BlockIcon** (128 Character, 16 Leader,
  9 Event, 2 Stage). Whether empty means "unrestricted" or "unknown" is not
  stated by Bandai. **UNVERIFIED** — needs a decision before implementing, and
  guessing wrong would wrongly ban legal cards.

**The app models none of this.** Under Standard Regulation, a deck built almost
entirely from Block 1 cards is legal by the app's reckoning and illegal in a
tournament. A "Standard-legal" toggle is the natural feature here.

Note the interaction with §3: the app's deck list reports `format: "OP17"`, and
the meta snapshot reports `format: "standard"`. Given §4, **"standard" now means
something specific and block-scoped** — so `format` alone is not sufficient to
judge legality.

---

## 5. Turn structure — AUTHORITATIVE

Relevant to deck building because it sets the tempo budget.

- **6-1-1** A turn is Refresh → Draw → DON!! → Main → End.
- **6-3-1** The player going **first does not draw on their first turn**.
- **6-4-1** Each turn places 2 DON!! cards, but **the first player places only 1
  on their first turn**.
- **6-5-2** Main Phase actions may be performed *in any order, as many times as
  you wish*.
- **6-2-4** Everything active at end of turn returns to active at refresh.

So going first trades a card draw for a DON!! card. That is the core tempo
decision behind aggro vs. control in this game.

Setup, also relevant:

- **5-2-1-6** Draw 5; **one** optional redraw (mulligan) per player.
- **5-2-1-7** Each player places a number of cards equal to the **Leader's Life
  value** face-down into their Life area, taken from the top of the deck. This is
  why the Leader's `Life` value matters even before turn 1 — the top of your
  deck is spent on Life, which is why running thin on good cards interacts badly
  with a large Life value.

---

## 6. Keyword notes worth encoding

- **10-1-4 `[Blocker]`** — fulfilled by resting during the Block Step; the card
  becomes the new attack target.
- **7-1-2** The Block Step is skipped entirely if no attack was declared.
- **2-15 Block Symbol** — "specifies the block this card is part of. **It does not
  affect gameplay.**" Purely cosmetic card grouping. Distinct from the Block
  *Number* in §4, which is a legality thing. Easy to confuse.
- **4-10** "If/Then": if the `if` clause cannot be resolved the `then` clause
  cannot either. If a `then` clause cannot be resolved, the rest of the text can
  still resolve — unless an `if` already failed.
- **4-12 "Set Power to 0"** reduces power by the target's power *at that moment*,
  so it cannot reduce a target already in the negative.

**Ruling worth knowing** (VERIFIED in card text, `OP15-022 Brook`):

> "Under the rules of this game, you do not lose when your deck has 0 cards. You
> lose at the end of the turn in which your deck becomes 0 cards."

Deck-out is **not** immediate. Worth remembering when a deck is thin on win
consistencies.

---

## 7. Ratios — MEASURED

Measured across the public deck list (`getdecks`). I select only **genuinely
complete** decks: exactly 1 Leader plus exactly 50 non-Leader cards.

- 750 decks reachable; **619 complete**, spanning **96 distinct Leaders**
- **100 of the 619 are flagged `is_tournament = 1`**
- Leader comparison uses canonical ids (`id_normal`), so reprints collapse and
  the leader is correctly recognised across printings

**Caveat that still applies:** this is the *public* deck list, which is
community-submitted, not an official tournament decklist. It skews toward people
who post decks online. The meta snapshot covers 737 real tournament decks but
exposes no full decklists (§10), so there is no way to weight by actual play.
Treat these as **what decks look like**, not **what wins**. The one prior run of
this analysis used 13 near-complete decks and produced noticeably wrong numbers
(blocker mean 9.0 vs the true 6.4), so take the sample size seriously.

### 7.1 Average cost curve

Mean number of cards at each cost per deck. A deck with no cost-9 card
contributes 0 there, so these sum to 47.8 of the 50 main-deck cards (the
remainder are cards with no parseable cost).

| Cost | Cards/deck |
|---|---|
| 1 | 11.9 |
| 2 | 5.8 |
| 3 | 5.2 |
| 4 | 6.9 |
| 5 | 5.8 |
| 6 | 5.0 |
| 7 | 2.5 |
| 8 | 1.7 |
| 9 | 0.6 |
| 10 | 2.4 |

**The shape is the finding.** A large spike at cost 1 (~12 cards, a quarter of
the deck), a broad plateau across 2-6, then a cliff: costs 7-9 together are under
5 cards. Only 0.6 cards at cost 9 on average.

This follows from the game rather than from taste. DON!! is the real currency
(2 per turn, 1 for the first player on turn 1), so a card is worth what you can
attach DON!! to — a cost-3 and a cost-5 Character that both survive are close to
interchangeable, while a cost-9 card is a hand clog. The cost-10 tail (2.4) is
searchers, which justify their cost by finding more cards.

### 7.2 Card type split

| Type | Share | Per deck |
|---|---|---|
| Character | 81.3% | ~40.7 |
| Event | 16.9% | ~8.5 |
| Stage | 1.8% | ~0.9 |

Structural: Events are one-shot `[Main]` plays and Stages are permanent, so the
deck is overwhelmingly Characters.

**Events per deck:** mean 8.45, median 8, range 0-35. Concentrated at 4, 8 and
10 (13.6%, 12.4%, 9.5%). The high tail is event-heavy control decks.

**Stages per deck:** mean 0.91, median 0. **69.5% of decks run zero Stages.**
That is the most striking figure in this file — Stages are a real card category
but the overwhelming majority of published decks do not use one.

### 7.3 Counter distribution

Bucketed as the game's own `extraCurves` defines it (`["0","1000","2000"]`,
`Counter >= 2000 ? 2 : Counter >= 1000 ? 1 : 0`):

| Bucket | Share |
|---|---|
| No counter | 46.6% |
| +1000 | 35.9% |
| +2000 | 17.5% |

Mechanical reason: 6-5-5-2 makes one DON!! worth +1000 and two worth +2000, so a
card's `Counter` is a statement about how much DON!! it expects to receive.
Nearly half of all cards have no counter at all — they are events, stages, or
Characters played for a `[On Play]` effect rather than for a battle.

### 7.4 Blockers — the most variable stat

Cards with the `[Blocker]` keyword, per 50-card main deck:

| Blockers | Decks | Share |
|---|---|---|
| 0 | 59 | 9.5% |
| 2 | 37 | 6.0% |
| 4 | 97 | 15.7% |
| 6 | 52 | 8.4% |
| **8** | **105** | **17.0%** |
| 9 | 49 | 7.9% |
| 10 | 43 | 6.9% |
| 12 | 31 | 5.0% |
| 14+ | 21 | 3.4% |

Mean 6.42, median 7, **range 0-20**.

**Do not treat this as "decks run 6-7 blockers".** The distribution is strongly
**multi-modal**: 4 and 8 are the peaks, and 9.5% of decks run *no blockers at
all*. Blocker count is a deck's chosen strategy, not a baseline you should warn
someone about. A deck with 3 blockers and a deck with 12 are both coherent
archetypes — the first plans to win the battle, the second plans to win the
turn.

This is where my first (13-deck) analysis was most misleading: it reported a
tight "mean 9.0, median 9" and I wrote that ~8-9 was "the most robust number
here". With 619 decks the true mean is 6.4 and the shape is multi-modal. The
sample size was the problem, not the method.

### 7.5 A metric I dropped

I tried to measure "searchers" and got ~22 per deck, which is obviously wrong —
the regex matched any "draw 1 card" in an on-play trigger and counted each copy.
Removed rather than reported. A real searcher count needs a semantic definition
of "searches your deck for a playable card", which regex cannot give.

---

## 8. What makes a deck good — mechanism, not ratios

The ratios in §7 are downstream of this. The causal chain:

1. **DON!! is the real currency, not cost.** 2 per turn (1 on the first player's
   first turn). Every design question is "what does this card do with my DON!!"
   — attach to a Character, or return to hand, or pay a DON!! cost.
2. **Power is symmetric, so advantage is positional.** Both players give the same
   +1000/DON!!. There is no inherent tempo edge, which is why tempo swings come
   from *when* a card lands relative to the opponent's board, and why the
   first-player draw/DON trade actually matters.
3. **Blockers are the defence budget.** Every attack you don't answer is damage
   you take; every blocker spent is a rested card gone next turn. So blocker
   count trades against your own development speed. ~8-9 is the observed middle.
4. **Life is set up front.** 5-2-1-7 takes the top of your deck as Life, equal
   to the Leader's Life value. A high Life Leader both survives longer and eats
   your own deck before turn 1.
5. **Card advantage is thin.** [Blocker] and search effects are the main ways to
   gain it, and both cost tempo. This is why low-curve decks exist — not to be
   greedy, but to access more of the deck.
6. **The Leader defines the whole pool.** Per 5-1-2-2 the Leader's colours are a
   hard filter, so "deck quality" is largely "did I pick the right Leader and
   find the ceiling of its colour pair". Choosing the Leader first, then
   building, is the correct order of operations.
7. **Diversity is an explicit design goal.** Both the ban policy (§3) and the
   Block system (§4) exist to stop one Leader/deck from crowding out the game.
   Bandai will ban your deck's key card if it restricts other Leaders' viability.
   A deck that wins by being narrow is a deck that gets banned.

---

## 9. Implications for the app

Ordered by how likely each is to produce a wrong answer.

1. **Ban list - SHIPPED 2026-09-28.** `OP14-020` and all its parallels are
   illegal from 2026-10-12, and it is the most-played Leader in the meta.
   Implemented as a hand-maintained, dated list in `data/Regulation.kt`. The API
   exposes no ban status - ten endpoints probed, all answering `Hacker! Go home!`
   - so the list carries a visible "as of" date, and Extra Regulation ignores it.
2. **Block Number regulation - SHIPPED 2026-09-28.** `BlockIcon` was already in
   the payload, so this cost no new data work. The window is computed from the
   date, an empty `BlockIcon` is treated as legal (verified against 52 tournament
   decks), and a Standard/Extra toggle sits beside the editor filters.
3. **Deck size off-by-one — FIXED 2026-09-28.** Was capping the main deck at 49;
   correct value is 50, for 51 total with the Leader (§2.1). The site's
   `deckBuildingRules` config is what led to the mistake, so treat that config as
   unreliable on this point and prefer the printed rules.
4. **Leader deck-construction overrides not modelled — low priority but real.**
   Verified safe today against 364 Leaders (§2.2). Worth a comment and a
   re-check date.
5. **DON!! deck not modelled — out of scope by agreement.** Note that the 10-card
   DON!! pile is a real part of a tournament deck, so deck lists here are
   incomplete for net-decking purposes (51 + 10 = 61 physical cards).
6. **Deck analytics (§2.5 of TASKS.md) can now be grounded**, and with real data:
   - Cost curve is the informative one — see §7.1, and the shape to compare
     against is ~12 cards at cost 1, a plateau across 2-6, and under 5 cards
     total at 7-9.
   - Counter buckets should use the game's own `extraCurves` definition, not
     anything invented.
   - **Type split is low-information**: 81/17/2 is nearly a constant. Reporting
     it as a "distribution" flatters a statistic that barely varies.
   - **Blocker count is worth surfacing, but as a range, not a target.** §7.4
     shows it is multi-modal (4 and 8 are the peaks, 9.5% of decks run zero), so
     any hint that a deck "should" have ~6 blockers would be misleading.
7. **A "Standard-legal" badge** would be genuinely useful and is now
   implementable, once the empty-BlockIcon question is settled.

---

## 10. Open questions

- [ ] Does an **empty BlockIcon** on a non-DON card mean unrestricted, or
      unclassified? Affects 155 cards. Need Bandai or a tournament-legal
      decklist to resolve.
- [ ] Is there an API route to **full tournament decklists**? The meta snapshot
      references them but does not expose them. Without this, no trustworthy
      ratio work is possible.
- [ ] `getdecks` caps at 750 reachable decks and returns a non-deck payload
      beyond ~page 26. Is there a better pagination or filter? The app notes
      paging is the only listing mechanism.
- [ ] Does the **Extra Regulation** have extra construction rules yet? Bandai
      says "we are preparing additional regulation details" and never delivered
      them in anything I found.
- [ ] **Tournament Rules Manual** (2026-01-16) not yet read. May cover sideboard
      rules, best-of-three, and any deck-list submission requirements.
- [ ] Errata notices on the rules index are **not** applied to the card database.
      Several exist for OP-13, OP-14, OP-15 and OP-16, and they revise card
      effect text. If the API serves pre-errata text, card detail and any
      effect-derived logic (blocker detection, searcher detection) can be wrong.
      **This is a live correctness question, not a nicety.**
- [ ] Confirm the **{TYPE} / {COLOUR} token syntax** in card text is what the
      site renders as a styled keyword, and whether there are other site-specific
      markup forms beyond `<br>` (see TASKS.md §2.6).
      **RESOLVED 2026-09-28.** There is **no `{COLOUR}` form** — an earlier draft
      of this file claimed one and it was wrong. All 61 brace tokens are crew
      names, verified against the `Type` field of all 5,300 non-DON cards. The
      angle-bracket tokens are *attributes* (`<Slash>`, `<Strike>`, `<Special>`),
      not HTML tags. `[...]` carries keywords and sometimes card names. Handled
      by `data/CardText.kt` + `ui/CardTextBody.kt`; see TASKS.md §2.6.
