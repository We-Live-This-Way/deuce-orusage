# OR Usage — OpenRouter usage + key manager (Android)

An Android app for an OpenRouter account: spend and usage broken down by key, server/provider
and model, plus minting and managing API keys from the phone.

Built by Deuce (review/verification bot) for We Live This Way.

## Status

- `./gradlew testDebugUnitTest` — 18 unit tests, all passing (parsing + aggregation + formatting).
- `./gradlew assembleDebug` — builds `app-debug.apk` (10.1 MB), verified with `aapt2 dump badging`.
- **Never launched on a device or emulator.** This box has no emulator/KVM image, so runtime UI
  behaviour is unverified. Compilation and logic are verified; taps are not.
- **No live API call has been made.** I do not have a management key, so every endpoint was verified
  against the published OpenAPI spec (`https://openrouter.ai/openapi.json`), not against live
  responses. First real run against an account is the outstanding check.

## Two API facts that shaped the design

1. **Management keys cannot be minted through the API.** `POST /api/v1/keys` creates regular API
   keys and requires a management key for auth. There is no endpoint that creates a management key —
   the docs are explicit that those are made on the dashboard. So this app mints API keys and
   explains the dashboard step for management keys; it does not fake it.
2. **Activity rows do not carry a key hash.** `GET /api/v1/activity` returns `date`, `model`,
   `provider_name`, `endpoint_id`, `requests`, `usage` and token counts. Per-key history is only
   available by *filtering* `?api_key_hash=…` one key at a time, and the response is capped at the
   last 30 completed UTC days. Both limits are surfaced in the UI rather than papered over.

## What the app does

- **Overview** — credit balance (from `/credits`), today / 7 day / 30 day spend, top servers, top
  keys, top models.
- **Keys** — every key with usage, daily/weekly/monthly spend, spending limit progress, expiry and
  disabled state. Create (name, optional limit, reset interval, expiry preset), enable/disable,
  delete with confirmation.
- **Breakdown** — group the last 30 days by server/provider, model or day; scope everything to a
  single key with a filter chip.
- **Settings** — masked key, refresh, sign out (wipes the stored credential), and a plain-language
  account of what the key can and cannot do.

## Security notes

- The management key is a high-value credential: it can create and delete every API key on the
  account. It is encrypted with an AES-256-GCM key held in the Android Keystore; only ciphertext
  reaches SharedPreferences. `androidx.security.crypto` is deprecated, so this uses the Keystore
  directly.
- `allowBackup=false` and `data_extraction_rules.xml` exclude all shared prefs, so the encrypted
  credential does not travel via cloud backup or device transfer.
- Cleartext HTTP is disabled; the only host contacted is `https://openrouter.ai`.
- A freshly minted key's plaintext exists only in memory, is shown once with a copy button, and
  `FLAG_SECURE` blocks screenshots and the recents thumbnail while it is displayed. Nothing is
  logged; there is no backend, analytics or crash reporting.
- **Caveat for daily use:** the APK here is a *debug* build — signed with the debug keystore and
  marked debuggable, so anything with adb access could attach to it. Build a release variant and
  sign it properly before putting a real management key on it.
- Standard mobile threat model applies: a rooted device with the app's data can still be attacked
  by something running as the same UID. Read-only management keys are recommended for
  monitoring-only use.

## Layout

```
app/src/main/java/com/welivethisway/orusage/
  data/Models.kt        parsing + aggregation, zero Android imports (unit tested on the JVM)
  data/OpenRouterApi.kt thin OkHttp client, one clear error message per HTTP status
  data/SecureStore.kt   Keystore-backed AES/GCM credential storage
  ui/AppViewModel.kt    state, concurrent refresh, key lifecycle
  ui/screens/*.kt       Setup, Overview, Keys, Breakdown, Settings
app/src/test/java/.../ModelsTest.kt   18 tests against documented response fixtures
```

## Building

This machine has no system JDK/SDK packages, so the toolchain lives in the home directory:

```
JAVA_HOME=/home/rakazo/.local/jdk-17.0.21+9
ANDROID_HOME=/home/rakazo/tooling/sdk
&lt;gradle 8.9&gt; testDebugUnitTest assembleDebug --no-daemon --max-workers=1
```

`gradle.properties` is tuned hard for a 2 GB cgroup limit: small heaps, in-process Kotlin compiler,
one worker. Without that the dex merge gets OOM-killed mid-build. Raising those limits on a bigger
machine is fine and will make builds much faster.
