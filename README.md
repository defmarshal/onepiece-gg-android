# OPGG

Unofficial Android client for [onepiece.gg](https://onepiece.gg) — the One Piece Card
Game database. Browse every card, track your binder and wishlist, read decks, and build
your own.

**Not affiliated with or endorsed by OnePiece.gg.** Unofficial, reverse-engineered, and
unsupported by them. Data comes from `api.dotgg.gg`, the same backend the website uses.

[**Download the latest APK**](https://github.com/defmarshal/onepiece-gg-android/releases/latest) ·
Android 7.0+ · 2.1 MB · [MIT licensed](LICENSE)

---

## Install

1. Open the [latest release](https://github.com/defmarshal/onepiece-gg-android/releases/latest)
   and download `OPGG-v*.apk`.
2. Allow "install unknown apps" for your browser or file manager when prompted.
3. Open the downloaded file and install.

There is no Play Store listing. Release APKs are signed with the maintainer's key, so
later versions install over earlier ones without uninstalling.

## What you can do

- **Browse the full card database** — image grid with set, colour, type, cost, power,
  counter and rarity filters.
- **Typed search** — `c:red cost:5 t:leader`, `-c:blue`, `type:straw hat`, quoted text,
  comma for alternatives (`c:red,blue`). Fields: `name`, `t`, `type`, `c`, `attr`,
  `rarity`, `set`, `cost`, `effects`, `block`, `counter`, `usdprice`. Autocomplete
  suggests field names and the values the database actually contains.
- **Collection** — counts for normal and foil, multiple binders, and a wishlist.
- **Decks** — browse public decks from the site, inspect lists against the cards you
  own, and build decks locally with live legality checking (50 cards, max 4 per
  printing, max 2 colours, the leader defines the colours). Built decks stay on the
  device and export as a deck code you can paste on onepiece.gg.
- **Metagame** — archetype shares from the site's snapshot.
- **Offline browsing** — the card database and your collection are cached, so the app
  opens and browses without a connection.

## Sign-in

Sign-in is optional: cards, decks, the deck builder and the metagame work signed out.
Only your **collection** and **wishlist** need an account, because those live on the
site.

The app opens OnePiece.gg's own login page inside the app, where you sign in with
**Google**, **Discord**, or **email + password**. Your password is never typed into the
app — it only reads the session token the site issues for itself afterwards. Google
sign-in was verified end-to-end on a signed release build; Discord and email use the
same page but have not been tested from the app.

## Known limitations

- **It can break without warning.** The backend is undocumented and belongs to someone
  else. If they change an endpoint, sync or the sign-in hand-off, the app may stop
  working until it is fixed. Cached browsing keeps working offline.
- **2FA is not implemented.** The site's own login page is shown as-is, but no
  two-factor account has been tested.
- **Reads are offline; writes are not.** Adding cards to your binder needs a connection
  and is not queued.
- **Decks you build stay on your device.** They are not published to onepiece.gg — the
  site's save endpoint is still unsolved — so use "copy deck code" there.
- **Not every write endpoint has been audited**, so a failed collection save can
  occasionally report success. Check the website if a write matters.
- **A VPN or a broken IPv6 route can stall the first load.** The backend has no IPv6;
  if the device prefers it, the app can appear frozen until it falls back.

## Privacy

No analytics, no ads, no trackers, no crash SDK. The only servers contacted are
`onepiece.gg` / `api.dotgg.gg` (data and card images from their CDN) and whichever OAuth
provider you pick on the login page.

On the device the app stores your session token and caches of card/deck data in its
private app storage — unencrypted, but inside the Android app sandbox, readable only by
the app on a non-rooted phone. **Log out** deletes the token; uninstalling deletes
everything. `allowBackup` is off, so the token is not copied into Android cloud backups.

## Support

Open an issue in the [Issues tab](https://github.com/defmarshal/onepiece-gg-android/issues).
Include your Android version, device model, what you tapped, and a screenshot if the
problem is visual. **Never paste your account token** — it grants full access to your
binder and decks.

## Build from source

Requires JDK 21 and Android SDK platform 36. The Gradle wrapper downloads Gradle itself.

```powershell
$p = "C:\path\to\onepiece-gg-android"
$env:JAVA_HOME = "C:\path\to\jdk-21"

& "$p\gradlew.bat" -p $p :app:testDebugUnitTest    # 389 tests, incl. live-API tests
& "$p\gradlew.bat" -p $p :app:assembleDebug       # app\build\outputs\apk\debug\app-debug.apk
& "$p\gradlew.bat" -p $p :app:assembleRelease     # app\build\outputs\apk\release\app-release.apk

adb install -r "$p\app\build\outputs\apk\debug\app-debug.apk"
```

`local.properties` must use **forward slashes** — Java `.properties` treats `\` as an
escape, and a backslash path fails with a misleading `SdkLocationSource` error.

If the device already has the app signed with a different key (for example a release
APK), `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall first,
which wipes app data and the saved session.

### Release signing

The release build is minified and resource-shrunk with R8. Signing is opt-in: without
`keystore.properties` the release APK is signed with the debug key, which is fine for
local testing but **must not be published** — the debug key is public.

Create the key once and back it up. Keep the `.jks` outside the repository so a
`git clean` or a repo delete cannot destroy it:

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

Confirm the signature before publishing:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
    "$p\app\build\outputs\apk\release\app-release.apk"
```

Losing the keystore means the app can never be updated under the same identity — every
existing install would have to be uninstalled first. `versionCode` and `versionName` live
in `app/build.gradle.kts`.

## Project documents

| File | Contents |
|------|----------|
| [`NOTES.md`](NOTES.md) | API contract, verified behaviours, bug history, feature research |
| [`TASKS.md`](TASKS.md) | Current action list and open gaps |
| [`COSMETICS.md`](COSMETICS.md) | Visual design notes |
| [`DECK_BUILDER.md`](DECK_BUILDER.md) | Deck builder specification |
| [`DECK_THEORY.md`](DECK_THEORY.md) | Deck construction and metagame research |

## License

MIT — see [`LICENSE`](LICENSE). Card data and card images belong to their respective
owners; this app only displays data and assets served by onepiece.gg.
