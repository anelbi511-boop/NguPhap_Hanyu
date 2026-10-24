# Chinese Grammar AI — Android app

Kotlin + Jetpack Compose (Material 3), MVVM, Room, Hilt, Ktor Client. Package `com.gia.chinesegrammar`.

## Build verification status

**This source tree was authored on a VM without Java/Android SDK, so it has NOT been compiled yet.**
To verify: install **JDK 17** and the **Android SDK (platform 34, build-tools 34)**, then:

```bash
cd android
gradle wrapper --gradle-version 8.7   # gradle-wrapper.properties is committed; the wrapper JAR is not (authoring VM had no Gradle)
./gradlew test assembleDebug
```

CI does exactly this: `.github/workflows/android.yml` (JDK 17 + Android SDK → `test` + `assembleDebug`).

Signing a release APK requires a keystore created by the owner (`keytool`) — none is committed, by design.

## Architecture

- `data/remote` — typed Ktor client + `@Serializable` contract models mirroring the Windows API exactly
  (`GET /health`, `GET /capabilities`, `POST /v1/analyze`, `POST /v1/pair`, `POST /v1/revoke-device`;
  analyze response: tokens/offsets/POS/pinyin/role, dependencies with per-parser `source`, translation_vi,
  explanation_vi, patterns, corrections, warnings, provenance, uncertainty 0..1).
- `data/local` + `data/repository` — Room (history, saved vocab, dictionary) with Flow DAOs.
- `offline` — `OfflineAnalyzer` interface; `RuleBasedAnalyzer` (maximum-match segmentation against the bundled
  HSK1–3 dictionary, char pinyin fallback, SVO role heuristics, pattern detection for 了/过/着/把/被/是…的/比/在/因为所以/如果/吗).
  **Honesty rule:** the rule engine returns `dependencies = emptyList()` — it never fabricates a parse tree.
  `LlamaCppEngine` wraps a JNI bridge interface for llama.cpp (`libllama_jni.so` must be built from llama.cpp's
  Android target and packaged separately) and returns an explicit `InferenceState.Error` when the native lib or
  GGUF model is missing — it never fakes output. `ModelManager` lists the Qwen3 GGUF catalog, checks storage
  (StatFs + safety margin), streams downloads to `.part` with progress, verifies SHA-256 (real implementation),
  and deletes models. Nothing downloads without an explicit user tap.
- `hybrid` — `ModeManager` (OFFLINE / ADVANCED / AUTO + consent flag for AUTO, **default off**) and
  `SecureTokenStore` (pairing token in EncryptedSharedPreferences backed by Android Keystore; token is never
  logged and never shown in Diagnostics). `HybridAnalyzer` implements AUTO fallback to offline on timeout (>20s)
  or error, with the fallback reason surfaced in the result warnings. ADVANCED fails explicitly instead of
  silently falling back. Sentence text goes only to the user's own paired PC — no external AI providers.
- `ui` — Compose screens, all wired in `ui/navigation/NavGraph.kt`: Home, Analyze (token chips + roles +
  translation + patterns + corrections + uncertainty/provenance), SyntaxTree (Canvas dependency arcs, tap to
  highlight, pinch zoom/pan, parser-disagreement listing — only rendered when real dependency data exists),
  Tutor (Windows `/v1/tutor` when paired+consented, otherwise clearly-labeled rule/dictionary answers),
  Dictionary (Room search), Vocabulary (CRUD + JSON export/import via Storage Access Framework with strict
  validation in `util/ExportImport.kt`), History (search, favorites, delete one/all, restore a past result),
  ModelManager, Pairing (pair/health/capabilities/revoke + consent switch), Settings (mode, AUTO consent,
  HSK slider, theme light/dark/system, privacy: clear history, pairing management), Diagnostics.
  Vietnamese strings throughout; dark mode follows the Settings override or system.

## Security posture

- `AndroidManifest.xml`: only `INTERNET` + `ACCESS_NETWORK_STATE`; `usesCleartextTraffic=false`;
  `res/xml/network_security_config.xml` sets `cleartextTrafficPermitted=false` — pair over the server's TLS
  endpoint (or a Tailscale hostname with TLS). The client additionally rejects non-HTTPS/LAN base URLs.
- No WebView anywhere; imported backup files are length- and schema-validated; model file names are
  path-traversal-checked; `allowBackup=false`.

## Tests

`app/src/test`: `RuleBasedAnalyzerTest` (segmentation offsets, longest match, SVO roles, pattern detection,
no-fabricated-dependencies invariant, input validation) and `TokenAlignmentTest` (offset validation, realign,
backup import validation). `app/src/androidTest`: Compose launch smoke test (needs a device/emulator).

## Known limitations (need SDK/device to verify/complete)

1. Not yet compiled — first Gradle build may surface minor import/API fixes; CI is the arbiter.
2. `libllama_jni.so` (llama.cpp Android build) and GGUF downloads are not bundled; without them the offline
   LLM path reports an explicit error and the rule engine remains fully functional.
3. Catalog SHA-256 values are empty until pinned from the official Qwen GGUF release; the Model screen shows
   the actual computed hash so the owner can pin/verify manually.
4. On-device latency/memory benchmarks for Qwen3 0.6B/1.7B require a physical Android device.
