# Repository Guidelines

## Project Structure & Module Organization

BeeCountAutoPatch is a single-module Android app (`:app`) implementing an LSPosed module that injects a BeeCount adapter into AutoAccounting (`net.ankio.auto`).

- `app/src/main/kotlin/com/beecount/autopatch/` — all Kotlin sources. `BeeCountHook` is the Xposed entry point, `BeeCountAdapter` the dynamic `IAppAdapter` proxy, and `BillMapper` / `CategoryStore` / `LogSpec` are dependency-free JVM helpers. UI-side files (`MainActivity`, `CategoryListFile`, `RootShell`) must stay free of Xposed APIs. The entry class is registered in `META-INF/xposed/java_init.list`; scope and metadata live in `scope.list` / `module.prop`.
- `META-INF/xposed/scope.list` — Xposed scope; only add genuinely supported packages.
- `app/src/test/kotlin/com/beecount/autopatch/` — JUnit unit tests. The proxy, the Xposed bridge, and all Android-touching code are **not** unit-testable here; put logic in `BillMapper` / `CategoryStore` instead.
- `README.md` — user-facing install/logging notes; keep it in sync with behavior changes.
- `.github/workflows/build.yml` — CI: tests, APK artifact per push/PR, GitHub Release on `v*` tags.

## Build, Test, and Development Commands

Requires JDK 17 and Android SDK 35. There is no Gradle wrapper; use a local Gradle 8.11.1.

- `gradle assembleRelease` — R8 + resource shrinking; smallest APK, the one to install.
- `gradle assembleDebug` — unminified; use it to tell whether a bug comes from R8.
- `gradle testDebugUnitTest` — run unit tests.
- `gradle lint` — Android lint (non-blocking, `abortOnError = false`).

Release builds are minified; never drop the keep rules in `app/proguard-rules.pro` — they keep the entry class and rewrite `java_init.list` when obfuscating.

Set `JAVA_HOME` and `ANDROID_HOME`, or open the root in Android Studio. CI runs the same commands and publishes both APKs — use it if you have no local toolchain.

## Coding Style & Naming Conventions

Kotlin with the official style (`kotlin.code.style=official`): 4-space indentation, trailing commas, no wildcard imports. Classes use `UpperCamelCase`, methods and properties `lowerCamelCase`, constants `UPPER_SNAKE_CASE` in companion objects. Add KDoc to public classes and non-obvious logic. Comments, logs, and commit bodies are Simplified Chinese. Keep reflection targets centralized and documented, since upstream renames are the main breakage source.

## Testing Guidelines

JUnit 4 lives in `app/src/test/...`. Test names use `subject_expectedBehavior` (e.g. `buildUri_transferHasNoCategoryAndUsesToAccount`). Prefer pure-JVM logic over mocking Android; run `gradle testDebugUnitTest` before a PR.

## Commit & Pull Request Guidelines

Follow Conventional Commits with a Chinese summary, matching history: `fix(log): ...`, `feat: ...`, `docs: ...`. Keep commits scoped to one change.

PRs should describe the change and motivation, list device testing (Android version, LSPosed, AutoAccounting version), link related issues, and paste log excerpts for hook or mapping changes. Bump `versionCode`/`versionName` in `app/build.gradle.kts` for user-visible releases.
