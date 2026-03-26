# Repository Guidelines

## Project Structure & Module Organization
This repository is a Gradle-based IntelliJ/Android Studio plugin, not a Flutter app. Main plugin code lives in `src/main/java/com/crzsc/plugin/` and is organized by responsibility: `actions/`, `listener/`, `provider/`, `cache/`, and `utils/`. Plugin metadata and UI resources live in `src/main/resources/`, especially `META-INF/plugin.xml` and `messages/MessagesBundle.properties`. Tests are under `src/test/kotlin/`. Supporting docs, release notes, and design notes live in `doc/`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root:

- `./gradlew build` builds the plugin and runs tests.
- `./gradlew test` runs the JUnit test suite only.
- `./gradlew runIde` launches a sandbox IDE for manual plugin testing.
- `./gradlew patchPluginXml` regenerates plugin metadata from `doc/PLUGIN_DESCRIPTION.md` and `doc/CHANGELOG.md`.

Run commands from the repository root so Gradle picks up `gradle.properties` and plugin settings correctly.

## Coding Style & Naming Conventions
Source files are Kotlin (`.kt`) stored under `src/main/java/`; keep package names under `com.crzsc.plugin`. Follow existing Kotlin style: 4-space indentation, expressive method names, `UpperCamelCase` for classes, `lowerCamelCase` for functions and properties, and one top-level responsibility per file where practical. Prefer small utility methods over deeply nested logic. Use IntelliJ’s formatter before submitting; there is no dedicated ktlint or detekt configuration in this repo.

## Testing Guidelines
Tests use JUnit 4 with Mockito. Add new tests in `src/test/kotlin/` and name them `*Test.kt`. Match existing patterns by covering edge cases in code generation, config parsing, and regression scenarios. Run `./gradlew test` before opening a PR; changes to generation or config parsing should include regression coverage.

## Commit & Pull Request Guidelines
Recent history uses short, imperative subjects in either English or Chinese, for example `chore: release 3.2.0` and `修复兼容性问题`. Keep the first line concise and scoped to one change. PRs should describe the user-facing effect, list validation steps, and note any IntelliJ/Flutter compatibility impact. Include screenshots or GIFs when changing actions, notifications, or editor UI. If behavior or release messaging changes, update `README.md`, `doc/CHANGELOG.md`, and other relevant files in `doc/`. Keep release docs aligned with actual defaults in `SetupProjectAction`, especially `enable: true`, `output_dir: generated/`, and `name_style: camel`.

## Release Notes Checklist
When updating the release version or plugin description, keep the following in sync:

- Update `pluginVersion` in `gradle.properties`.
- Move the current release notes from `doc/CHANGELOG.md` `Unreleased` into a concrete version section such as `## [3.2.0]`.
- Keep `README.md`, `doc/PLUGIN_DESCRIPTION.md`, and `doc/TECHNICAL_DESIGN.md` aligned with the current release number when they mention a specific version.
- Keep documentation examples aligned with actual code defaults from `SetupProjectAction`, especially `enable: true`, `output_dir: generated/`, `style: robust`, and `name_style: camel`.
- If plugin marketplace description or change notes changed, run `./gradlew patchPluginXml`.
- Before release, verify there are no stale examples such as `lib/generated/` if the code now writes `generated/`.
