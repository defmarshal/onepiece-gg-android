# OnePiece.gg Android Client

Unofficial Android app for the [onepiece.gg](https://onepiece.gg) One Piece Card
Game database — card browser with typed search, collection binder, public deck
browser, and related-card discovery.

**Not affiliated with or endorsed by OnePiece.gg.** Unofficial, reverse-engineered,
and unsupported by them.

---

## Installing it (public release)

APKs are attached to the repository's **Releases** page. Download the `.apk`,
allow "install unknown apps" for your browser/file manager, and open it. Android
7.0 (API 24) or newer. There is no Play Store listing.

### Known limitations

- **It can break without warning.** The backend is undocumented and belongs to
  someone else. If they change an endpoint, a sync or the sign-in hand-off, the
  app may stop working until it is fixed. Cached browsing keeps working offline.
- **Sign-in goes through the real site in an in-app browser** (Google, Discord,
  or email + password). Google and Discord were both verified end-to-end on a
  signed release build on 2026-11-18. If Google ever refuses the embedded
  browser, use Discord or email - they use the same page.
- **2FA is not implemented.** The site's login page is used as-is, so whatever
  steps it demands are what you get; the app has no authenticator screen of its
  own. Reported accounts with 2FA have not been tested.
- **Reads are offline-capable; writes are not.** Viewing cards, binders and
  decks works from cache; adding/removing cards and saving decks need a
  connection and are not queued.
- **Deck editing is partial.** New decks can be created and cards added; editing
  or renaming an existing deck is not implemented and "delete deck" is a no-op.
- **Not every write endpoint has been audited**, so a failed save can
  occasionally report success. Check the site if a write matters.
- **A VPN or a broken IPv6 route can stall first load.** The backend has no IPv6
  (see §6); if the device prefers IPv6 the app can appear frozen until it falls
  back.

### Privacy, in one paragraph

No analytics, no ads, no trackers, no crash SDK. The only servers contacted are
onepiece.gg / `api.dotgg.gg` (data and card images from their CDN) and the
OAuth providers you choose on the login page. On the device the app stores your
session token (user id + token) and caches of card/deck data in its private app
storage - unencrypted, but inside the Android app sandbox, readable only by the
app on a non-rooted phone. Your password never reaches the app: sign-in happens
on the site's own page, and the app only reads the token the site issues for
itself. **Log out** deletes the token; uninstalling deletes everything.

### Support

Open an issue on the GitHub repository hosting this README (Issues tab). Include
the Android version, the device model, what you tapped, and a screenshot if the
problem is visual. **Never paste your account token** - it grants full access to
your binder and decks.

---

## 1. What this is

OnePiece.gg publishes no API. Everything here runs on the same undocumented PHP
backend the website uses, `api.dotgg.gg`, plus a WordPress admin-ajax endpoint
for authentication. The contract was reverse-engineered from the site's own
shipped JavaScript bundles and then verified against the live service.

**Status: 389/389 tests passing. Debug and R8-minified release APKs install and run on a real device.**

The one caveat that used to head this section - "I have never seen this app
render" - no longer applies: every change since the first device install has
been checked with screenshots and `uiautomator` dumps on a physical phone
(card grids, binders, deck search, the metagame tab, dialogs and the R8 release
build). The Android emulator on this machine still crashes `surfaceflinger`, so
the phone remains the only way to verify anything visual.

---

## 2. Quick start

```powershell
$p = "C:\Users\defma\projects\onepiece-gg-android"

# No JDK is on PATH on this machine; a Temurin 21 was installed for this project.
$env:JAVA_HOME = "C:\Users\defma\.dotgg-tools\jdk21\jdk-21.0.12.1+1"

& "$p\gradlew.bat" -p $p :app:testDebugUnitTest    # 389 tests, incl. live-API tests
& "$p\gradlew.bat" -p $p :app:assembleDebug       # -> app\build\outputs\apk\debug\app-debug.apk (20.2 MB)
& "$p\gradlew.bat" -p $p :app:assembleRelease     # -> app\build\outputs\apk\release\app-release.apk (2.1 MB, R8)

adb install -r "$p\app\build\outputs\apk\debug\app-debug.apk"
# If the device already has the app signed with another key (e.g. a release APK),
# `install -r` fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE:
#   adb uninstall gg.opgg.android   # wipes cached data and the saved session
#   adb install "$p\...\app-debug.apk"

### Release signing

The release build is minified and resource-shrunk. Signing is opt-in: without
`keystore.properties` it is signed with the debug key, so `assembleRelease` still
works for local testing. **Published APKs must not be debug-signed** - the debug
key is public and shared with every Android build machine.

Create the key **once** and back it up (both files). Keep the `.jks` outside the
repository so a `git clean` or a repo delete cannot destroy it:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore "$env:USERPROFILE\.dotgg-tools\keystore\opgg-release.jks" `
  -storetype PKCS12 -alias opgg -keyalg RSA -keysize 4096 -validity 10000 `
  -dname "CN=OPGG Unofficial Client, O=OPGG, C=ID"
```

Then add `keystore.properties` at the repository root (never commit it):

```properties
# forward slashes: Java .properties treats \ as an escape
storeFile=C:/Users/you/.dotgg-tools/keystore/opgg-release.jks
storePassword=…
keyAlias=opgg
keyPassword=…
```

Build and confirm the signature before publishing:

```powershell
& "$p\gradlew.bat" -p $p :app:assembleRelease
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
    "$p\app\build\outputs\apk\release\app-release.apk"
```

Losing the keystore means the app can never be updated under the same identity -
every existing install would have to be uninstalled first. `versionCode` /
`versionName` live in `app/build.gradle.kts`.

Installed for this project, none of it pre-existing: Temurin JDK 21, Gradle
8.14.3 (via wrapper), Android platform 36, and `cmdline-tools`.

`local.properties` must use **forward slashes** — Java `.properties` treats `\`
as an escape, and a backslash path fails with a misleading
`SdkLocationSource.validateSdkPath` error.

---

## 3. Architecture

Plain Kotlin + Jetpack Compose, no DI framework. ~3,800 lines of app code,
~720 of tests.

```
app/src/main/java/gg/opgg/android/
├── MainActivity.kt          hosts Compose, wires API + stores + ViewModel
├── SignInActivity.kt        WebView sign-in, extracts the DotGG session token
├── data/
│   ├── Models.kt            @Serializable DTOs for every endpoint
│   ├── OpggApi.kt           Ktor client, bot-guard detection, error mapping
│   ├── CardCache.kt         on-disk card database, atomic writes
│   ├── SessionStore.kt      DataStore persistence for the auth token
│   ├── PrefsStore.kt        DataStore persistence for sort order
│   ├── CardSearch.kt        typed search: tokenizer, parser, matcher, suggester
│   ├── RelatedIndex.kt      related cards: meta co-occurrence + crew fallback
│   └── AppViewModel.kt      UiState + all state transitions
└── ui/
    ├── AppRoot.kt           all screens (1,579 lines — the main weak spot)
    └── theme/Theme.kt
```

Stack: AGP 8.13.0, Kotlin 2.2.0, Compose BOM 2025.06.00, Ktor 3.1.3 (OkHttp),
Coil 2.7.0, DataStore 1.1.7. minSdk 24, target/compile SDK 36.

Data flow: `OpggApi` (suspend, typed) → `AppViewModel` (one `MutableStateFlow<UiState>`)
→ Compose (`collectAsStateWithLifecycle`). All filtering, sorting and relatedness
is client-side; the app holds 3,063 cards in memory and does no server-side querying.

### Navigation
A real back stack, not a pile of booleans:

```kotlin
sealed interface OpggScreen {
    data object Tabs
    data class CardDetail(val card: Card, val list: List<Card>)
    data class RelatedList(val seed: Card)
}
```

`BackHandler` pops it. Booleans could not express "back from this card returns
to the related list", which is exactly what kept breaking. Swiping inside a card
page calls `replaceTopWithCard` — it moves *within* a screen, so it replaces the
top entry rather than pushing, and it no-ops when the card is unchanged.

---

## 4. Features

| Tab | Status | Notes |
|---|---|---|
| **Cards** | Done | 3,063 unique cards, cached on disk, reprints collapsed |
| **Collection** | Done | Identical grid, showing only cards you own |
| **Decks** | Done | Public deck browser, 30/page, detail with owned markers |
| **Account** | Done | Sign in/out, diagnostics showing whether auth is accepted |

### Card detail
A full-screen destination, not a bottom sheet (a sheet's drag gesture fights the
scroll). Contains: hero image, **reprint switcher**, stat chips, attribute icon,
effect/trigger, a four-row price panel, the related-cards button, and binder
controls.

- **Reprint switcher** — a horizontal strip of every printing, each with its own
  art, price, and a green dot if held. Tap to switch; a pink badge shows the
  printing id when it isn't the base. Only shown when a card has >1 printing.
  Per-printing art genuinely exists (`card/EB01-015_r1.webp` → 200), which is
  what makes this worth having.
- **Per-printing binder detail** — steppers act on the *selected* printing, with
  an explicit "Editing printing EB01-015_r1" label, a tappable breakdown of every
  printing held, and a card-level total.
- **Prices** — TCGplayer and Cardmarket, normal and foil, deltas coloured.
  Cardmarket opens in the browser.
- **Swipe left/right** pages through whichever list you came from: a search
  result set pages its own results; a card opened from Collection pages your
  binder; a card opened from the related list pages that list.

### Collection
Deliberately **identical** to the Cards tab — one shared `CardsGrid`, same tiles,
same badges, same search field and filter chips. The only difference is the list:
your owned cards, using the printing you actually hold. Counts are edited on the
detail page, where the reprint selector makes it unambiguous.

### Typed search
Mirrors the filter semantics from the site's own `cards.filterConfig`.

| token | matches | site semantics |
|---|---|---|
| *(bare words)* | name, id, set, cardtype, colour, effect, ANDed | `containsText` |
| `name:` | exact name | `equalText` |
| `t:` / `cardtype:` | CHARACTER EVENT LEADER STAGE DON | `commaSplitSearchTerms` |
| `type:` | crew/faction | `arrayTextItemsPassAny` |
| `c:` / `color:` | Red, Blue, … and `Red/Blue` | `arrayTextItemsPassAny` |
| `attr:` | Slash Strike Ranged Wisdom Special | `arrayTextItemsPassAny` |
| `rarity:` | C R SR SEC `SP CARD` TR L UC P | `equalText` |
| `set:` | OP01, ST22, EB01… | `equalTextAny` |
| `cost:` | 1–10 | `numberList` |
| `effects:` | effect text | `containsText` |
| `block:` `counter:` | 1–5,X / counter value | `equalText` |
| `usdprice:` | minimum price | `floatGreaterThen` |

AND across fields, OR within one field, `-` negates, quotes group phrases.
Unrecognised tokens fall back to a text search and are flagged in yellow.

The filter sheet writes into the **query string** via `toggleTerm`, so dropdowns
and typed tokens are one mechanism. Each field is an `ExposedDropdownMenuBox` with
checkbox multi-select; vocabularies over 12 items get a type-to-filter box.
Collapsed dropdowns summarise as `c:red` or `set:OP01 +3`.

### Autocomplete
Context-aware: `t` → field names, `t:` → every valid value, `t:lea` → `t:LEADER`,
`luf` → card names. Values come from the loaded database, so they cannot be
invalid. Ranked so an exact canonical prefix always wins.

### Sort
Options mirror the site's `sortConfig`: name, set, card number, cost, power,
counter, price, foil price, 1d/7d % gain, 1d/7d $ gain, plus a descending
toggle. **Persisted** via DataStore and restored on launch. Applies to both
grids, computed inside the debounced filter step on `Dispatchers.Default` so it
never reintroduces per-frame cost.

### Related cards
Two tiers, because the meta snapshot only covers the staples:
1. **"See N related cards"** → a full screen, for the **443** cards in the meta
   snapshot. Partners are scored by summed inclusion weight across the archetypes
   you share, with "In N archetypes · in X% of those decks".
2. **Same crew** for the other ~2,620 cards, ranked by meta inclusion rate where
   known, and **labelled as crew rather than passed off as a recommendation**.

Opening a related card pushes a new `CardDetail` onto the stack, so back returns
to the related list.

---

## 5. API contract

All under `https://api.dotgg.gg/cgfw/<action>?game=onepiece`, except auth.
The site uses exactly **44** actions; all are enumerated in the code comments and
the task list.

### Public
| action | returns |
|---|---|
| `getcards` (`?mode=indexed` recommended) | 5,540 printing rows, 34 fields |
| `getsets` | 70 sets |
| `getmetasnapshot` | 53 archetypes with curated card lists, plus tier data |
| `getuserdata` | `{user, collection, settings, binders, activeBinderId, …}` |
| `getdecks?rq=<json>` | 30 deck summaries **including full card lists** |
| `getdeck?slug=<slug>` | one deck, adds `public`, `description`, `history` |
| `gettrading`, `gettradingcard`, `getposts` | trade listings, cross-site news |

### Auth required
`getbindercards`, `savebindercard`, `savebatchbindercard`, `listbinders`,
`createbinder`, `deletebinder`, `renamebinder`, `reorderbinder`, `movecards`,
`getdecks` with `my`, `getdeckcommitments`, `edituser`, `setusersetting`,
`importcollection`, `exportcollection`, `synclocalcollection`, `listrules*`,
`getgacha`, `getalerts`, `exporttoken`, `deleteaccount`, `resetcollection`,
`resetdecks`, **`savedeck`**.

### `getdecks` parameters — the important negative result
**Only `page` and `my` affect the result set.** All of these were tried at the
top level and nested under `getdecks.*`, comparing an MD5 of the full result
set, and every one was silently ignored:

`search, q, query, name, deckname, humanname, srt, sort, sortby, orderby, format,
sets, set, card, cards, archetype, archetype_id, author, nick, physical,
tournament, views, limit, per_page, offset, ids, minprice, price, top, leader`

`leader` in particular **does not** filter — an earlier reading suggesting it did
was a PowerShell 5.1 artifact (`$null.Count` returns 1). So all deck filtering is
client-side over the pages already held.

### Deck writing
`savedeck` is the real write endpoint; an empty body creates a deck and returns
`{id, deckname, slug}`. **`deletedeck` exists as an action but is a no-op** —
HTTP 200 with an empty body for every payload shape tried, removing nothing. The
body shape for setting card counts was not determined; the write code is not in
the bundles available, it lives in a lazy chunk.

### Writing to the collection — the important bit
There are **four** collection write APIs, and they differ in whether they need a binder:

| endpoint | body | binder? |
|---|---|---|
| `savebindercard` | `{card, type, count, binder_id, extra}` | **yes** |
| `savebatchbindercard` | `{binder_id, cards, …}` | **yes** |
| **`savebatchcollection`** | `{cards:[{card,type,count}], source}` | **no** |
| `synclocalcollection` | `{items:[{card, standard, foil, trade, wish, …}]}` | **no** |

Most accounts have **no binder** — `listbinders` returns `[]` and `activeBinderId`
is `0` — while their collection still lives at binder 0. `savebindercard` against
binder 0 therefore fails with:

```
HTTP 200  {"error":true,"error_text":"Binder not found."}
```

Reads at binder 0 work fine, which is what makes this so confusing. The website
sends `savebatchcollection`, which needs no binder row. The app now routes the
same way: **binder 0 → `savebatchcollection`, a real named binder →
`savebindercard`**, so the binder picker still works for accounts that have them.

`savebatchcollection` was verified **additive** before being trusted: in a game
where the collection was empty, adding card A then card B left both present
(`processed:1, total:1` each time, 1 row then 2 rows). The test data was then
removed with `resetcollection`.

**Its response has two failure modes** and both must be checked:
- `error: true` — request-level failure
- `error: false` **plus** a non-empty `errors` array — e.g.
  `{"error":false,"message":"OK","processed":0,"total":1,"errors":["Card #0: card ID 'X' not found"]}`

Checking only the first would let failures pass silently, which is exactly the
bug that made collection writes look like they worked.

### Authentication
There is **no social login endpoint.** Every auth route in the site's JS was
enumerated: `login, register, handoff, me, profile, profile_update,
account_email, avatar_upload, check_nick, discord_link, premium_view`, plus
`/do.php?cmd=` for 2FA. Google sign-in happens entirely inside WordPress.

The app uses the flow the website itself uses:
1. A WebView loads the DotGG login page (`dotgg.gg/login/?returl=…`) with a
   `DotGGApp/` user-agent token — the site's own companion-app mode, which
   strips its header/footer/ads/cookie banner — and the user signs in there
   (Google, Discord, or email + password).
2. The site's JS calls `POST /wp-admin/admin-ajax.php?action=dotgg_token_exchange`
   with the WP session cookie.
3. That returns `{user, token}`, written to `localStorage`.
4. The app polls `localStorage` and copies the token into DataStore.

Authenticated calls send `Dotgguserauth: {user_id}:{token}` — the user id is the
**left half** of that header, not a bearer token.

**Dead end, documented so nobody retries it:** `POST /auth/v2/login` with an email
and password is unusable. It returns `{"error":"Unknown site."}` for every value
of its required `site` field, including omitting it entirely, and the value is
read from a `window.dotggCommentsConfig.site` global that **is not present** in
onepiece.gg's served HTML. Password login was therefore removed rather than left
as a broken button.

---

## 6. Critical API behaviours

- **Unknown routes return HTTP 200** with the body `Hacker! Go home!` — never a
  404. Header-less requests get the same. `OpggApi` checks for this string
  explicitly, because otherwise a blocked response is indistinguishable from
  valid empty data. Regression-tested.
- **The card database is 3,148,059 bytes** via `?mode=indexed`, or
  **5,230,704** in the default array-of-objects form. The difference is purely
  that the columnar form states the 34 field names once instead of repeating
  them across 5,540 rows. **The phone's measured link to `api.dotgg.gg` was
  ~3.9 KB/s**, so a full download is a multi-minute wait. Hence the cache and
  the progress bar.
- **The API sends `Last-Modified` but no `ETag`.** Replaying
  `If-Modified-Since` returns **304 Not Modified** with no body, which is what
  makes the cache cheap. `Cache-Control: max-age=3600` sets the staleness window.
- **Cloudflare rate limiting (HTTP 1015/429)** after roughly 50 rapid requests.
  **Per IP** — research traffic from a dev machine and the phone on the same
  Wi-Fi share one budget. The app maps 429 to a readable message and pages the
  deck list only on explicit user action.
- **Multi-value fields are `/`-separated**: `Color: "Red/Blue"`,
  `Type: "Alabasta/Straw Hat Crew"`, `Attribute: "Slash / Wisdom"` (mixed
  spacing — both forms occur).
- **Collection counts arrive as strings**: `standard`, `foil`.
- **`capabilities` is an object**, `{"subscriber":true}` — typing it as a list
  throws and killed the whole collection view.
- **Static icons:** `icon/{attribute}.webp` exists (Slash, Strike → 200).
  `icon/{rarity}.webp` and `card/DON.webp` **do not** (404) — I had claimed
  otherwise in an earlier version of this document.
- **Deck code format** is `4xOP01-016` lines, exported by the site and imported by
  matching card id case-insensitively. A ready-made interchange format.

### Caching — **verified working on device**
Paint from disk first, then check the server in the background:
1. First run: fetch 3.1 MB (`?mode=indexed`), dedup to 3,063 cards, store those
   plus the `Last-Modified` value.
2. Later runs: read the file and render. If the cached copy is **under an hour
   old, no network request is made at all** — the server is not contacted.
3. Once stale, send `If-Modified-Since`; a `304` costs a few hundred bytes.
4. Manual refresh is unconditional.
5. On a network error the cached copy stays on screen.

Measured on the phone:

```
cache hit: 2674028 bytes
opgg-cards.json  2,674,028 bytes, 3,063 cards, 0 × searchBlob
```

Writes are atomic via temp file + rename, so a kill mid-write cannot leave a
truncated database.

### The device cannot resolve IPv6 — and that broke everything
The phone has **no global IPv6 route** (`ip -6 addr show scope global` is empty;
`curl -6` fails, `curl -4` returns 200 in 0.45 s). `curl` uses `AI_ADDRCONFIG`,
which suppresses the AAAA query when there is no IPv6, but Java's
`InetAddress.getAllByName` does not — and this device's resolver **fails the AAAA
query outright instead of falling back to the A record**.

Result: OkHttp threw `UnknownHostException` on *every* request, so the cache was
never written and the card database never loaded — while `curl` from the same
phone worked fine. Fixed with `PreferIpv4Dns` (prefers A records, retries three
times) plus a `retryingNetwork` wrapper, because a single DNS blip was poisoning
the cache for the whole session.

Worth remembering: **`curl` working is not evidence the app's resolver works.**

### Cache size
2,674,028 bytes for 3,063 cards. Two things had doubled it:
- `searchBlob` was being serialised. `@Transient` had been written **without an
  import**, so it silently resolved to `kotlin.jvm.Transient` — the JVM field
  modifier — rather than `kotlinx.serialization.Transient`. A body-declared `val`
  has a backing field, and kotlinx serialises every property that has one.
- The cache stored all 5,540 raw printing rows instead of the 3,063 deduped ones.

Together: 6,195,811 → 2,674,028 bytes, a 57% reduction, which also halves parse
time on every cold start.

### One Piece deck rules (from the game's own config)
`cardsInDeck: 50` · `maxCardsCopies: 4` · `maxColorsInDeck: 2` ·
`LEADER` exactly 1 and **separate from** the 50-card deck (so a legal deck is
**50 main-deck cards + 1 Leader = 51**), with `deckDefiner: true` +
`colorLimiter: true`, meaning **the leader's colour defines what you may play**.
`copyLimitAcrossPrintings: false`, so the 4-copy limit is per printing.
Unresolved: DON handling — the picker excludes both `LEADER` and `DON`.

---

## 7. Verification status — read this honestly

**Verified by automated tests against the live API (65 total):**
- 5,540 cards parse in **both** formats; columnar compared field by field against
  the object format for all 5,540 cards, zero mismatches
- Conditional GET returns **304** on both endpoints
- 5,540 → 3,063 reprint collapse; no duplicate keys; no card loses its id
- 2,012 `_pN` + 465 `_rN` reprints both present and both mapped to real groups
- `getmetasnapshot` parses and builds a related index covering **443** cards, with
  a real seed producing related cards and never related to itself
- 70 sets; `getuserdata` envelope; `Hacker! Go home!` detection
- Signed-in payload shape, using a real response as the fixture
- 17 search, 11 suggestion, 12 sort, 10 related-index tests

Live tests carry a `@Before` guard that skips the class when the API is
throttling, because a 429 previously reported as six unrelated failures.

**Verified by the user on a real device:** the app installs, launches, signs in,
loads the collection, searches, sorts, filters, opens card detail, swipes,
navigates back, and **edits the collection** — the `+`/`−` controls were
confirmed working. Every visual bug in §8 was found this way.

**Verified by me, on the device, over adb — including screenshots:**
- The card database cache round-trips: 2,674,028 bytes, 3,063 cards, read at
  startup, and **no network request at all** while it is under an hour old
- The `304` conditional GET, against the live API
- The phone's IPv4/IPv6 behaviour, using `curl -4` / `curl -6`
- **Every screen rendered**, via a debug launch argument (below): Cards,
  Collection, Decks, deck detail, card detail, related list, Account

Synthetic input is blocked on this device by Android's "USB debugging (Security
settings)" gate, so `adb shell input tap` does nothing. To review a screen:

```
adb shell am start -n gg.opgg.android/.MainActivity --es screen <spec>
# spec: tab:0..3 | card:<id> | related:<id> | deck:<slug> | deckfirst
```

This is a debug affordance, not a feature. It is worth keeping only while
reviewing.

### A rendering artefact that is not ours
Every screenshot — including the Android Settings screen — shows a small greenish
rounded rectangle at the left edge, about a quarter of the way down. It is a
HyperOS system overlay, confirmed by reproducing it outside this app. Chased it
far enough to be sure before dismissing it.

---

## 8. Bugs found and fixed

All found by the user testing on a device, or by live tests. Do not regress.

| bug | root cause |
|---|---|
| Tab bar invisible | `fillMaxSize()` in a `Column` squeezed it to zero height |
| Search field hidden under cards | a `Box` **stacks** children; the grid drew over it |
| Collection appeared empty | `capabilities` typed as `List<String>`, is an object |
| Collection still empty | used `getbindercards`; the site uses `getuserdata` |
| Count saves did nothing | `savebindercard` type is `"standard"`, not `"normal"` |
| 3 owned cards vanished | `_rN` reprints unmapped to their canonical group |
| 6 owned cards showed no badge | lookup by base id, but holdings are on printing ids — and editing one would create a **duplicate row** |
| Every tap ignored | `shouldOverrideUrlLoading` returned `true` for non-Google hosts |
| OAuth popups vanished | no `WebChromeClient` |
| `rarity:sp card` found nothing | tokenizer split on whitespace, leaking a stray AND-ed word |
| `c:red luffy` ate "luffy" | greedy value absorption; vocabulary-aware completion fixed it |
| `c:` buried in suggestions | aliases `ct`/`crew` outranked the exact canonical match |
| Filter chips wiped the query | chips were tappable and cleared everything |
| Dead Cardmarket button | `onClick = { }` |
| Cards tab unusable | multi-MB JSON decoded on `Dispatchers.Main` |
| Every keystroke stuttered | getter rescanning 3,063 cards 2–3× per recomposition |
| ~24,500 strings/frame | `lowercase()` on 8 fields per card; now precomputed |
| Database never downloaded | 60s timeout vs a ~3.9 KB/s link needing ~22 min |
| Failures invisible | exceptions swallowed with no logging |
| Live tests failed while throttled | no skip guard; a 429 looked like a defect |
| **Black text on black** | page built outside a `Scaffold`, so `LocalContentColor` defaulted to `Color.Black` |
| **Sort button did nothing** | the `if (sortOpen) { SortSheet(...) }` block was never inserted — a silent PowerShell replace |
| **Descending sort put nulls first** | direction applied *after* the null comparison |
| **Back closed the app** | no `BackHandler`; the page replaced the tabs in place |
| **Scroll reset on back** | `LazyGridState` not hoisted above the branch |
| **Collection swipe showed the wrong card** | grid rendered a filtered list, pager walked an unfiltered one — plus a `LaunchedEffect` keyed on a list that was a new instance every recomposition, re-firing `onNavigate` in a loop |
| **Back needed N presses** | `onNavigate` *pushed* on every settled page, so each swipe added a screen |
| **Hero image blank until scrolled** | `AsyncImage` derived its request size from constraints measured inside a scroll-in-pager; now pinned via `ImageRequest.size` |
| **Collection +/- bounced to 0** | app used `savebindercard`, which needs a real binder and returns `Binder not found` at binder 0; now routes to `savebatchcollection` |
| **Silent write failures** | the API returns HTTP 200 with `{"error":true}`; several call sites treated that as success. Now checked explicitly, plus the per-card `errors` array |
| **Sort did nothing on Collection** | sorting only ran inside the Cards-tab filter step; the collection built its own list and never sorted it |
| **Rotation reset the UI** | all state used plain `remember{}`; now `rememberSaveable`, with the nav stack saved as tags and rebuilt against the card DB |
| **Back needed N presses** | `onNavigate` *pushed* on every settled pager page, so each swipe added a screen; now replaces the top entry |
| **Back closed the app** | no `BackHandler`; the page replaced the tabs in place |
| **My decks showed public decks** | sent `"my":1`; the server ignores that and returns the public list. It needs the numeric user id |
| **Deck "Load more" did nothing** | the `rq` JSON had a trailing comma, so the server discarded it and always returned page 1 |
| **Deck detail crashed on open** | typed `history` as `String?`; the server sends an **object** |
| **Autocomplete re-sorted 3,063 names per keystroke** | `names.sortedBy {}` on every call; input was already sorted |
| **Card database never loaded on the phone** | the device has no global IPv6 route and its resolver fails the AAAA query outright instead of falling back to A; OkHttp threw `UnknownHostException` on every request, while `curl` from the same phone worked |
| **One DNS blip poisoned the whole session** | the background refresh runs once at startup and gave up; now retried with backoff |
| **Cache was 6.2 MB instead of 2.7 MB** | `@Transient` was written with no import, so it resolved to `kotlin.jvm.Transient` and `searchBlob` was serialised; separately the cache stored 5,540 raw rows instead of the 3,063 deduped ones |
| **A unit test passed while the device disagreed** | the JVM saw the correct annotation, the APK did not — a reminder that `@Transient`/`@Volatile` are easy to resolve to the wrong symbol when the import is missing |
| **Related list was permanently empty** | `remember(card.key) { relatedFor(card) }` was keyed on the card alone, but the meta index loads asynchronously — the screen composed once, cached the empty result and never recomputed. Now keyed on index coverage as well. Found by looking at the screen |
| **Related copy contradicted itself** | printed "0 cards from the same crew" when the list was empty |
| **"No related cards found." floated mid-screen** | used `ErrorBox`, which is `fillMaxSize`, so it centred in the leftover space |
| **Decks with no leader showed a blank box** | no leader art recorded; now falls back to the deck's first card |
| **Saved sort was ignored on launch** | `applyCards` set `visible = cards` in raw API order, and the restore path's `scheduleFilter` ran before the cache had loaded, so it filtered an empty list. The sort sheet showed the right setting while the list ignored it |
| **"Descending" didn't look descending** | only the primary key was direction-flipped; the tie-break was hard-coded ascending, so "Set descending" gave `ST36-001, 002, …` and read as unsorted |
| `mailto:` links silently dropped | `handleUrl` returned `isFailure` where it meant `isSuccess` |
| Deck "Load more" never returned | `decksExhausted` not reset on refresh |
| Failed binder save left a wrong number | rollback re-read the already-overwritten optimistic map |

### Claims I had to retract
All from inferring instead of testing:
- That `binder_id = 0` hides the collection. It does not.
- That `leader` filters the deck list. It does not.
- That the payload was "multi-megabyte" without measuring it. It is **5,230,704 bytes**.
- That `setdecks` was a write endpoint. It is not — my token scan matched the
  substring "setdecks" inside **`resetdecks`**. The real name is `savedeck`.
- That `card/DON.webp` and rarity icons exist. They 404.
- That `savebindercard` was the endpoint for adding a card. It is not; the site
  uses `savebatchcollection`, and I had no way to know that without seeing the
  request. Several rounds of guessing came before I asked for it.

### Debris I left on the account
Probing for `savedeck` created **five empty decks** (`1837006`, `1837007`,
`1837687`, `1837688`, `1837689`, all "New Deck"). `deletedeck` returns HTTP 200
with an empty body and removes nothing, so they must be deleted by hand in the
site or app UI. Their real deck was untouched. Probing writes on a live account
is not a technique I should have reached for repeatedly.

### Process mistakes worth recording
- **Three shell-based file edits silently failed** on line-ending mismatches,
  leaving a dead sort sheet, a mangled `TASKS.md` and duplicated imports. The
  edit tool fails loudly instead. Use it for code.
- **I wrote a regression test that reimplemented the logic inside the test**, so
  it would have passed with the production code broken. Extracted the function
  (`collapseOwnedCounts`) so the test exercises the real thing.
- **A live test was flaky** because it compared a price across two requests
  (0.77 → 0.78). It now asserts the field survives rather than its value.
- **I asserted a specific root cause confidently and was wrong** — I blamed a
  printing-id key mismatch for the bouncing `+` when the real cause was the
  rejected endpoint. I should have run the no-op write test earlier; it was
  non-destructive and would have found it in one call.

### Debugging notes for this environment
- `adb exec-out screencap` **corrupts** the PNG through PowerShell redirection.
  Use `adb shell screencap` then `adb pull`.
- A blank screenshot usually means **the screen is off**. The log says
  `E/VRI[MainActivity]: performDraw: Not drawing due to screen off`. I chased
  this as a rendering bug before reading the log.
- `$null.Count` returns **1** in PowerShell 5.1, not 0. This produced a false
  positive in the deck research.

---

## 9. What needs doing

### Urgent
- [ ] **Delete the five stray "New Deck" entries** in the site or app UI.
- [ ] **Audit the remaining write endpoints** for HTTP-200-with-`error`. Two were
      found the hard way (`savebindercard`, and `savebatchcollection`'s per-card
      `errors` array); `setusersetting`, `movecards`, `importcollection` and
      `createbinder` are still unchecked.

### Next
- [ ] **Review the deck and related-list screens on device.** Never rendered.
- [ ] **Cache the deck list.** Paging is the only listing mechanism and the
      corpus is small (~600 decks) — cache it whole.
- [ ] **Tests for the deck data layer.**
- [ ] **Migrate to `navigation-compose`.** It is a declared, unused dependency.
      Hand-rolled navigation caused the back-closes-app, scroll-reset,
      wrong-swipe-list and back-needs-N-presses bugs — all four symptoms of one
      wrong choice, made before the app had four tabs and nested overlays.
- [ ] **Move `ownedGroups` / `ownedCount` / `visibleDecks` out of `UiState`
      getters into computed state.** They recompute fresh objects per access,
      which already caused one bug (unstable list identity feeding the pager).
- [ ] **Split `AppRoot.kt`** — 1,579 lines with four tabs, four screens and three
      bottom sheets. The main maintainability problem.
- [ ] **Pass real image sizes to Coil** more carefully, or verify the current
      pinned sizes are appropriate.

### Open questions
- [ ] Does deck list paging ever end, and where? Verified 30 decks on pages 1–20;
      the binary search was invalidated by rate limiting.
- [x] Google sign-in inside the WebView - **works.** Verified end-to-end on a
      signed release build (2026-11-18): Google OAuth in the in-app browser, the
      site minted the token, the app read it and authenticated
      (`Server sees user id` populated). Discord is offered on the same page as a
      fallback; email + password is the third route. Digital Asset Links are
      still not declared, so Google could tighten this later.
- [ ] 2FA. The API has `requires_2fa` and a TOTP flow; there is no 2FA screen,
      so an account with 2FA enabled cannot sign in.
- [ ] **Find the `savedeck` body shape.** The endpoint exists and creates (an empty
      body seeds an 8-card deck), but `id`, `slug` and `name` are all ignored as
      selectors — every payload produced a *new* deck. The site's own JS never
      calls it in any bundle or lazy chunk reachable from `main.js`. Settle it by
      capturing one save request in the browser, the same way the collection
      write was settled.
- [ ] DON handling in deck legality.

---

## 10. Feature suggestions

Roughly in value order. None are started.

1. **"Cards I still need"** - **deck view shipped**. Long-press a deck for
   "Cards I need (N)" and see exactly what it runs that your collection does not
   cover, biggest gap first. Printings collapse (a parallel you own satisfies the
   card), and the Leader counts as a requirement. Counting is a pure function in
   `DeckDiff.kt` with 15 JVM tests. The *set* view is not started. See
   [`TASKS.md`](TASKS.md) section 2.2.
2. **Deck vs collection diff** - "this deck needs 12 cards you don't own" on the
   deck detail screen. The data is already computed; only presentation is missing.
3. **Deck actions menu** - **shipped**. Long-press a deck for Rename / Duplicate /
   Delete. Copy naming is collision-free (`Beast (copy)`, `Beast (copy 2)`), the
   copy gets its own card map, and delete now asks first - the unconfirmed
   delete button that used to sit on every row is gone. Naming rules are pure
   functions in `LocalDeckOps.kt` with 12 JVM tests. See
   [`TASKS.md`](TASKS.md) section 2.7.
4. **Deck analytics** - **shipped**. A collapsible panel at the top of the
   editor: cost curve, counter buckets (none / +1000 / +2000+, using the game's
   own `extraCurves` definition), card types, colours, blockers and a power
   curve. Pure functions in `DeckAnalytics.kt` with 17 JVM tests. Reports shape
   only, deliberately: real decks are multi-modal on blockers, so any "ideal
   ratio" hint would be wrong as often as right. See
   [`TASKS.md`](TASKS.md) section 2.5.
5. **Clean up raw markup in card text** - **shipped**. Rules text used to show
   `<br>` and `{Straw Hat Crew}` literally. It is not HTML but a small token
   vocabulary: `<br>` (1,737), `<Slash>`/`<Strike>`/`<Special>` attributes, 61
   `{...}` crew tokens, `[...]` keywords and `&#039;`. Now parsed by
   `data/CardText.kt`, with keywords, crews and attributes styled the way the
   printed card prints them. 13 JVM tests over real payload strings. See
   [`TASKS.md`](TASKS.md) section 2.6.
6. **A deck builder** - planned in [`DECK_BUILDER.md`](DECK_BUILDER.md). The
   local-first build is now **done**: rules engine, deck-code/CSV codec, atomic
   local store and the editor UI. Server sync is the only outstanding piece and
   it is blocked because no `savedeck` update path exists.
7. **Deck code that actually moves** - **shipped**. The site does not accept the
   text it exports: it emits `1xEB01-015_r1` but imports `1xEB01-015`, reprint
   suffix dropped. So "Copy deck code" is the lossless local format, and "Copy
   deck code for onepiece.gg" emits the shape the site will accept. See
   [`TASKS.md`](TASKS.md) section 2.10.
7. **Editor tile cleanup** - **shipped**. The In-deck filter no longer greys or
   captions tiles, and a deck-wide reason ("no Leader", "deck full") never
   prints on all 50 tiles any more. Availability is decided once in
   `TileAvailability.kt` and expressed through the `+` button rather than prose.
   Spec in [`TASKS.md`](TASKS.md) section 2.8.
8. **Collection value summary** - total spend from `price`/`foilPrice`, plus
   completion percentage per set. Fields already parsed.
9. **Meta / tier list** - **shipped**. Decks tab -> Meta: 51 archetypes from 737
   real tournament decks across 33 events, ranked by rank and share, with win
   rate, top-8 rate **shown against the field's own benchmark**, and expandable
   rows carrying the tier reasoning, research notes, recent finishes and staples.
   Tiers are a badge rather than the backbone - only 9 of 51 archetypes carry
   one. The app was already fetching this payload for related cards; it just
   discarded everything else.
10. **Price history charts** via `getprices` - returns `{fromdate, todate, lines}`;
    `lines` was empty anonymously.
11. **Card comparison** - pick 2-3 cards, diff stats. Pure UI over in-memory data.
12. **CSV import/export** for the collection. The site supports it
    (`importcollection` / `exportcollection` exist as actions).
13. **Offline mode** - reading works (cache-first loads, §2.37). Still open:
    offline writes with sync-on-reconnect (§2.00).
---
Visual consistency work - naming the palette, a type scale, and the open
visual questions - is tracked in [`COSMETICS.md`](COSMETICS.md).

## 11. Security and privacy notes

- The DotGG token is a **bearer credential** granting full read/write access to
  the account's binder and decks. It is stored **unencrypted** in DataStore
  (`datastore/opgg_prefs.preferences_pb`) and so are the deck/card caches, all
  inside the app sandbox. Encrypting it with the Android Keystore is the one
  known security rough edge left; it is not a blocker for a free unofficial
  release, but it is for anything with real accounts at stake.
- It has appeared in chat transcripts during development, because the collection
  bug was diagnosed by reading it off the device via `run-as` (the build is
  debuggable). Sign out and back in to rotate it. It is **not** hardcoded in the
  repo or in any test.
- Reading the token out of a WebView's `localStorage` is only safe because it is
  the user's own account on their own device. A Play Store release would need a
  proper OAuth redirect.
- `minifyEnabled` is now `true` for release (R8 + resource shrinking, 20.2 MB →
  2.1 MB), with the rules in `app/proguard-rules.pro`. The release build has
  been installed and exercised on the device: collection, binders, deck search,
  card detail and the metagame snapshot all decode correctly.
- Release signing reads `keystore.properties` at the repo root; the key is a
  4096-bit RSA self-signed certificate (alias `opgg`, 10,000 days) kept outside
  the repository at `~/.dotgg-tools/keystore/opgg-release.jks`. Verified with
  `apksigner verify --print-certs` both on the built APK and on the
  `base.apk` pulled back off the device
  (`SHA-256 4b96d02f95137bc0d7be93efcc8e7b5e97f53b1eb47c0c16babfae01aa8af494`).
  **Back up the .jks and its password** - losing them means no future update can
  install over an existing one.
- No analytics, no crash reporting, no third-party SDKs beyond Coil and Ktor.
- Sign-in failures are no longer silent: a main-frame load error shows a
  "Can't reach OnePiece.gg" card with a **Retry** button instead of the
  browser's bare error page.


---

## 12. Further reading

- `TASKS.md` — the action list, kept current
- The site's JS bundles that everything was derived from:
  - `wp-content/plugins/dotgg-game/games/onepiece/js/main.*.js` — routes, game config, deck rules
  - `wp-content/plugins/dotgg-game/common/js/common.*.js` — API client, card filters, binder and deck calls
  - `wp-content/plugins/dotgg-game/common/js/async/*.js` — ~40 lazy chunks; the deck **write** code is in one of these and was not located
- Runtime config embedded in every page:
  `DOTGG_GAME={"apiUrl":"https://api.dotgg.gg/onepiece","TheGame":"onepiece","BlogID":"28","mode":"prod"}`
