# Repository Guidelines

## Project Structure & Module Organization

BeeCountAutoPatch is a single-module Android app (`:app`) implementing an LSPosed module that injects a BeeCount adapter into AutoAccounting (`net.ankio.auto`).

- `app/src/main/kotlin/com/beecount/autopatch/` — all Kotlin sources. `BeeCountHook` is the Xposed entry point, `BeeCountAdapter` the dynamic `IAppAdapter` proxy, and `BillMapper` / `CategoryStore` / `LogSpec` are dependency-free JVM helpers. UI-side files (`MainActivity`, `CategoryListFile`, `RootShell`, `RemoteLog*`) must stay free of Xposed APIs.
- `app/src/main/assets/xposed_init` — registers the hook class; update it if the entry point moves.
- `app/src/main/res/values/arrays.xml` — Xposed scope. Only add packages that are genuinely supported.
- `app/src/test/kotlin/com/beecount/autopatch/` — JUnit unit tests. The proxy, the Xposed bridge, and all Android-touching code are **not** unit-testable here; put logic in `BillMapper` / `CategoryStore` instead.
- `README.md` — user-facing install, logging, and known-limitation notes; keep it in sync with behavior changes.
- `.github/workflows/build.yml` — CI: tests, APK artifact per push/PR, GitHub Release on `v*` tags.

## Build, Test, and Development Commands

Requires JDK 17 and Android SDK 35. There is no Gradle wrapper; use a local Gradle 8.11.1.

- `gradle assembleDebug` — build `app/build/outputs/apk/debug/app-debug.apk`.
- `gradle testDebugUnitTest` — run unit tests.
- `gradle lint` — run Android lint (non-blocking, `abortOnError = false`).

Set `JAVA_HOME` and `ANDROID_HOME`, or open the root in Android Studio. CI (`.github/workflows/build.yml`) runs the same two commands and publishes `BeeCountAutoPatch-debug` — use it if you have no local toolchain.

## Coding Style & Naming Conventions

Kotlin with the official style (`kotlin.code.style=official`): 4-space indentation, trailing commas, no wildcard imports. Classes use `UpperCamelCase`, methods and properties `lowerCamelCase`, constants `UPPER_SNAKE_CASE` in companion objects. Add KDoc to public classes and non-obvious logic. Comments, log messages, and commit bodies are Simplified Chinese. Keep reflection targets centralized and documented, since upstream renames are the main breakage source.

## Testing Guidelines

JUnit 4 lives in `app/src/test/...`. Test names use `subject_expectedBehavior` (for example `buildUri_transferHasNoCategoryAndUsesToAccount`). Prefer extending `BillMapper`-style pure-JVM logic over mocking Android. Run `gradle testDebugUnitTest` before opening a PR; there is no enforced coverage threshold.

## Commit & Pull Request Guidelines

Follow Conventional Commits with a Chinese summary, matching history: `fix(log): ...`, `feat: ...`, `docs: ...`. Keep commits scoped to one change.

PRs should describe the change and motivation, list manual device testing (Android version, LSPosed, AutoAccounting version), link related issues, and include log excerpts from the module screen for hook or mapping changes. Bump `versionCode`/`versionName` in `app/build.gradle.kts` for user-visible releases.
