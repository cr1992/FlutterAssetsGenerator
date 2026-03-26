# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

IntelliJ/Android Studio plugin that generates type-safe Dart asset access code for Flutter projects. It scans `pubspec.yaml` files, builds an asset tree, and generates Dart classes. Supports multi-module/monorepo projects.

## Build & Test Commands

```bash
./gradlew build          # Build plugin + run tests
./gradlew test           # Run JUnit tests only
./gradlew buildPlugin    # Build distributable zip (build/distributions/)
./gradlew runIde         # Launch sandbox IDE for manual testing
./gradlew compileKotlin  # Quick compile check without full build
```

Tests are JUnit 4 + Mockito, located in `src/test/kotlin/`. Run a single test class:
```bash
./gradlew test --tests "com.crzsc.plugin.test.ListenerLogicTest"
```

## Architecture

The plugin is event-driven with three trigger paths:

1. **Manual actions** (`actions/`) — user clicks menu items (Generate Assets, Setup Project, Generate Dir)
2. **PSI tree listener** (`listener/PsiTreeListener.kt`) — watches asset file changes, debounces 300ms, then regenerates
3. **Document save listener** (`listener/PubspecDocumentListener.kt`) — watches `pubspec.yaml` saves, diffs config via `PubspecConfigCache`, regenerates only on meaningful changes

### Key data flow

```
pubspec.yaml discovery (FileHelperNew.getAssets)
  → config parsing (ModulePubSpecConfig)
  → asset tree building (AssetTreeBuilder / AssetNode)
  → Dart code generation (DartClassGenerator)
  → file write + dependency management (FileGenerator, DependencyHelper)
```

### Core files by responsibility

| Responsibility | File |
|---|---|
| Module/pubspec discovery + config reading | `utils/FileHelperNew.kt` |
| Generation orchestration (background task → EDT write) | `utils/FileGenerator.kt` |
| Asset tree model + building | `utils/AssetTree.kt` |
| Dart source code generation | `utils/DartClassGenerator.kt` |
| Auto-add flutter_svg/lottie/rive deps | `utils/DependencyHelper.kt` |
| Config diff/cache to avoid redundant regeneration | `cache/PubspecConfigCache.kt` |
| Project startup (registers listeners) | `listener/MyProjectManagerListener.kt` |
| Gutter icons for asset references | `provider/AssetsLineMarkerProvider.kt` |
| Plugin registration, actions, shortcuts | `resources/META-INF/plugin.xml` |

### Module discovery gotcha

`FileHelperNew.getAssets()` uses `FilenameIndex` to find all `pubspec.yaml` in the project. It filters out cache directories (`.dart_tool`, `.pub-cache`, `.pub`, `.symlinks`, `build`) by path segment matching. This is critical — without filtering, Flutter dependency packages get scanned too (80+ false modules).

## Generation Styles

Two styles controlled by `style` key in pubspec config:
- `robust` (default) — hierarchical nested classes with type-safe wrappers (`AssetGenImage`, `SvgGenImage`, etc.)
- `legacy` — flat `static const String` fields, optional parent-directory name prefixing

## Plugin Config Block

All behavior is driven by the `flutter_assets_generator:` block in each module's `pubspec.yaml`. Modules without this block are ignored by listeners and generation.

## Coding Conventions

- Kotlin source under `src/main/java/com/crzsc/plugin/`, package `com.crzsc.plugin`
- Tests under `src/test/kotlin/`, package `com.crzsc.plugin.test`
- Use `Logger.getInstance(...)` for diagnostics (viewable in IDE's idea.log)
- Commit messages: short imperative subjects, Chinese or English, e.g. `fix: 修复扫描范围过大的问题`
- Plugin metadata version/compatibility in `gradle.properties`, not `build.gradle`
