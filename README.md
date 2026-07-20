# Flutter Assets Generator

[English](#english) | [中文](#chinese)

<a name="english"></a>
## English

<!-- Plugin description -->
A powerful Android Studio / IntelliJ plugin that automatically generates a type-safe, hierarchical asset index for your Flutter projects.

### Support

- Found a bug or unexpected behavior? Please report it at [GitHub Issues](https://github.com/cr1992/FlutterAssetsGenerator/issues).
- New plugin releases are published at [GitHub Releases](https://github.com/cr1992/FlutterAssetsGenerator/releases). Check there for the latest release notes and installable packages.

### Core Features

- **Hierarchical Generation**: Generates classes that mirror your directory structure (e.g., `Assets.images.logo`).
- **Multi-Module Support**: Seamlessly supports nested Flutter modules and monorepos (e.g. `example/` or `packages/`).
- **Legacy Compatibility**: Supports `style: legacy` to generate flat variable names (e.g. `Assets.imagesLogo`) for easy migration.
- **Smart Type Support**: Automatically detects `SVG`, `Lottie` and `Rive` files.
- **Widget Integration**: Generates `.svg()`, `.lottie()` and `.rive()` methods directly on asset objects.
- **Auto Dependency Management**: Automatically checks and adds `flutter_svg`, `lottie` or `rive` dependencies.
- **YAML as Source of Truth**: In 3.x, behavior is driven by `pubspec.yaml`. Modules without `flutter_assets_generator` config are not watched or generated automatically.
- **Disable Cleanup**: Setting `enable: false` stops generation for that module and removes the previously generated Dart file.
- **Smart Auto Update**: 
    - **Assets**: Watch for image additions/deletions and regenerate automatically.
    - **Config**: Triggered on **File Save** (Cmd+S) in `pubspec.yaml`. Smart diffing ensures builds only run when necessary.
    - **Performance**: Setup and generation run asynchronously, and generated-file formatting is skipped to keep EDT writes minimal. In the 2026-04-21 monorepo reproduction, batch generation dropped from `23.7s` to `1.512s` (`15.67x` faster, `93.6%` lower total time), while average per-module write time dropped from `1339.88ms` to `7.62ms` (`175.72x` faster, `99.4%` lower).

### Usage

#### 1. Quick Setup (Recommended)

For first-time users, use the one-click setup:

- **Menu**: Click `Tools` -> `Flutter Assets Generator` -> `Setup Project Configuration`.
- **Project View**: Right-click the root directory of a Flutter module, then click `Flutter: Setup Current Module` to configure only that module.

Project activation and module setup follow these rules:

- The plugin treats a project as available when it can find at least one valid Flutter module in the current workspace.
- A valid Flutter module must be rooted at its own `pubspec.yaml` directory and contain Flutter configuration (`flutter:` or Flutter SDK dependency).
- Temporary generated directories such as `.dart_tool`, `build`, `.symlinks`, `.plugin_symlinks`, and `ephemeral` are excluded from module detection.
- The Project View setup entry is shown only when the selected directory exactly matches a Flutter module root. Child folders such as `lib/` or `assets/` do not show the entry.
- `package_parameter_enabled` defaults to `false` for Flutter apps and add-to-app Flutter modules, and defaults to `true` for other Flutter packages.
- Both project-level setup and current-module setup use the same automatic defaults.

This will automatically add the default configuration to your `pubspec.yaml`:

```yaml
flutter_assets_generator:
  enable: true
  output_dir: generated/
  output_filename: assets
  class_name: Assets
  auto_detection: true
  auto_add_dependencies: true
  style: robust # Options: robust (default), legacy (legacy)
  leaf_type: class # Options: class (default for robust), string (default for legacy)
  name_style: camel # Options: camel (default), snake
  package_parameter_enabled: false # Flutter packages default to true
  path_ignore: []
```

#### 2. Manual Configuration (Optional)

```yaml
flutter_assets_generator:
  # Enable/Disable this plugin for the current module. Default: true when the block exists
  enable: true
  # When set to false, generation stops and the previous generated Dart file is removed
  # Sets the directory of generated files. Default: generated/
  output_dir: generated/
  # Sets the name for the generated file. Default: assets
  output_filename: assets
  # Enable package parameter generation (package: 'your_package_name'). Default: false
  package_parameter_enabled: false
  # Sets the name for the root class. Default: Assets
  class_name: Assets
  # Enable/Disable auto monitoring and dependency management. Default: true
  auto_detection: true
  # Add dependencies to pubspec.yaml automatically. Default: true
  auto_add_dependencies: true
  # Generation style: robust (Hierarchical) or legacy (Flat legacy). Default: robust
  style: robust
  # Leaf type: class (typed wrappers) or string (raw asset path). Default: class for robust, string for legacy
  leaf_type: class
  # Name style for generated identifiers. Default: camel
  name_style: camel
  # For legacy style: Prefix variable names with parent directory names. Default: true
  named_with_parent: true
  # Ignore specific paths. Default: []
  path_ignore: ["assets/fonts"]
```

When `leaf_type: string`, asset references return raw `String` paths (e.g., `Assets.icons.user` returns a string). In this mode the plugin will not auto-add `flutter_svg`, `lottie`, or `rive`.

When `style: legacy` and `leaf_type: class`, the generator keeps flat access paths but wraps known asset types with typed helpers:

```dart
// legacy + leaf_type: class
static const AssetGenImage imagesLogo = AssetGenImage('assets/images/logo.png');
static const SvgGenImage iconsHome = SvgGenImage('assets/icons/home.svg');
// Unknown types (e.g. mp4, json) still fall back to String
static const String videosIntro = 'assets/videos/intro.mp4';
```

Modules without a `flutter_assets_generator` block are not monitored automatically. If you run `Generate Assets` before initialization, the plugin will ask you to run `Setup Project Configuration` first.

#### 3. Generate File

- **Menu**: Click `Tools` -> `Flutter Assets Generator` -> `Generate Assets`.
- **Shortcut**: Press `Option`(Mac) / `Alt`(Win) + `G`.
- **Generated File Writes**: Generated Dart files are written directly without an additional IDE reformat pass, reducing EDT stalls in large monorepos.

#### 4. Access Assets in Code

The plugin generates a strict, type-safe hierarchy:

```dart
// Standard Image
Assets.images.logo.image(width: 24, height: 24);

// SVG (Requires flutter_svg dependency)
Assets.icons.home.svg(color: Colors.blue);

// Lottie (Requires lottie dependency)
Assets.anim.loading.lottie(repeat: true);

// Rive (Requires rive dependency)
Assets.anims.input.rive(fit: BoxFit.contain);

// Custom widget builder - allows you to build any widget with the asset path
Assets.images.logo.custom(
  builder: (context, assetPath) {
    return YourCustomWidget(assetPath: assetPath);
  },
);
    
// Get raw path string
String path = Assets.images.logo.path;
```

<!-- Plugin description end -->

---

<a name="chinese"></a>
## 中文

一个强大的 Android Studio / IntelliJ 插件，为您的 Flutter 项目自动生成类型安全、分层级的资源索引类。

### 核心功能

-   **分层生成**：根据目录结构生成对应的嵌套类，精确反映资源层级（例如 `Assets.images.logo`）。
-   **多模块支持**：完美支持嵌套的 Flutter 模块和 Monorepo 项目结构（如 `example/` 或 `packages/`）。
-   **旧版兼容**：支持 `style: legacy` 以生成扁平变量名 (例如 `Assets.imagesLogo`)，方便迁移。
-   **智能类型支持**：自动识别 `SVG`, `Lottie` 和 `Rive` 文件。
-   **Widget 集成**：直接在资源对象上生成 `.svg()`, `.lottie()` 和 `.rive()` 方法。
-   **自动依赖管理**：如果检测到相关资源但缺少依赖，插件会自动向 `pubspec.yaml` 添加 `flutter_svg`, `lottie` 或 `rive`。
-   **YAML 单一配置源**：3.x 以后所有行为都以 `pubspec.yaml` 为准。未配置 `flutter_assets_generator` 的模块不会自动监听或生成。
-   **禁用即清理**：将 `enable: false` 后，插件会停止该模块的生成，并删除之前生成的 Dart 文件。
-   **智能自动更新**：
    -   **资源文件**: 监听图片文件增删，自动重新生成。
    -   **配置文件**: 监听 `pubspec.yaml` 的**保存**动作 (Cmd+S)。智能 Diff 算法确保只在配置真正变化时构建。
    -   **性能优化**: setup 与生成流程都已异步化，并跳过生成文件的额外 IDE 格式化。在 2026-04-21 的 monorepo 实测日志里，批量生成总耗时从 `23.7s` 降到 `1.512s`（`15.67x` 提升，累计耗时下降 `93.6%`），单模块平均写入耗时从 `1339.88ms` 降到 `7.62ms`（`175.72x` 提升，写入耗时下降 `99.4%`）。

### 使用方法

#### 1. 快速配置（推荐）

首次使用时，使用一键配置功能：

-   **菜单**: 点击 `Tools` -> `Flutter Assets Generator` -> `Setup Project Configuration`。
-   **项目树右键**: 在目标 Flutter 模块根目录上右键，点击 `Flutter: Setup Current Module`，只为当前模块补充默认配置。

项目激活和模块配置遵循以下规则：

-   只要当前工作区中存在至少一个合法 Flutter 模块，插件就会认为当前项目可用。
-   合法 Flutter 模块必须以其自身的 `pubspec.yaml` 所在目录为根目录，并且配置中包含 Flutter 信息（`flutter:` 节点或 Flutter SDK 依赖）。
-   `.dart_tool`、`build`、`.symlinks`、`.plugin_symlinks`、`ephemeral` 这类临时生成目录不会参与模块识别。
-   项目树右键入口只会在“精确命中 Flutter 模块根目录”时显示，像 `lib/`、`assets/` 这样的子目录不会显示该入口。
-   对 Flutter app 和 add-to-app Flutter module，`package_parameter_enabled` 默认是 `false`；其他 Flutter package 默认是 `true`。
-   项目级 setup 和单模块 setup 统一走同一套自动默认逻辑。

这将自动在您的 `pubspec.yaml` 中添加默认配置：

```yaml
flutter_assets_generator:
  enable: true
  output_dir: generated/
  output_filename: assets
  class_name: Assets
  auto_detection: true
  auto_add_dependencies: true
  # 生成风格: robust (默认), legacy (旧版兼容)
  # robust: 分层级风格 (Assets.images.logo)
  # legacy: 扁平风格 (Assets.imagesLogo)
  style: robust
  # 引用类型: class(默认 robust) 或 string(默认 legacy)
  leaf_type: class
  # 命名风格: camel (默认) 或 snake
  name_style: camel
  package_parameter_enabled: false # Flutter package 默认会写成 true
  path_ignore: []
```

#### 2. 手动配置（可选）

您也可以手动在 `pubspec.yaml` 中添加 `flutter_assets_generator` 进行自定义：

```yaml
flutter_assets_generator:
  # 是否启用当前模块的插件能力。存在配置块时默认: true
  enable: true
  # 配置为 false 后，将停止生成并删除此前生成的 Dart 文件
  # 生成文件的输出目录。默认: generated/
  output_dir: generated/
  # 生成文件的文件名 (无后缀)。默认: assets
  output_filename: assets
  # 是否启用 package 参数生成 (package: 'your_package_name')。默认: false
  package_parameter_enabled: false
  # 生成的根类名。默认: Assets
  class_name: Assets
  # 是否开启自动监测和依赖管理。默认: true
  auto_detection: true
  # 是否自动添加依赖到 pubspec.yaml。默认: true
  auto_add_dependencies: true
  # 生成风格: robust (分层级) 或 legacy (旧版扁平)。默认: robust
  style: robust
  # 引用类型: class(包装类) 或 string(原始路径)。robust 默认 class，legacy 默认 string
  leaf_type: class
  # 生成标识符的命名风格。默认: camel
  name_style: camel
  # 旧版兼容风格: 将变量名以父级目录名为前缀。默认: true
  named_with_parent: true
  # 忽略的路径。默认: []
  path_ignore: ["assets/fonts"]
```

当 `leaf_type: string` 时，资源引用直接返回原始 `String` 路径（如 `Assets.icons.user` 返回字符串）。该模式下插件不会自动添加 `flutter_svg`、`lottie` 或 `rive` 依赖。

当 `style: legacy` 且 `leaf_type: class` 时，生成器保持扁平访问路径，同时为已知资源类型生成类型安全的包装类：

```dart
// legacy + leaf_type: class
static const AssetGenImage imagesLogo = AssetGenImage('assets/images/logo.png');
static const SvgGenImage iconsHome = SvgGenImage('assets/icons/home.svg');
// 未识别类型 (如 mp4, json) 仍然退化为 String
static const String videosIntro = 'assets/videos/intro.mp4';
```

如果模块没有 `flutter_assets_generator` 配置块，插件不会自动监听。此时手动执行 `Generate Assets` 会提示先运行 `Setup Project Configuration` 完成初始化。

#### 3. 生成文件

-   **菜单**: 点击 `Tools` -> `Flutter Assets Generator` -> `Generate Assets`。
-   **快捷键**: 按下 `Option`(Mac) / `Alt`(Win) + `G`。
-   **生成文件写入**: 生成的 Dart 文件会直接写入，不再额外触发 IDE 格式化，以降低大型 monorepo 下的 EDT 卡顿。

#### 4. 代码调用

生成的代码完美支持 IDE 自动补全和类型检查：

```dart
// 普通图片 (返回 Image Widget)
Assets.images.logo.image(width: 24, height: 24);

// SVG (返回 SvgPicture Widget)
Assets.icons.home.svg(color: Colors.blue);

// Lottie (返回 LottieBuilder Widget)
Assets.anim.loading.lottie(repeat: true);

// Rive (返回 RiveAnimation Widget)
Assets.anims.input.rive(fit: BoxFit.contain);

// 自定义 Widget 构建器 - 允许您使用资源路径构建任何 Widget
Assets.images.logo.custom(
  builder: (context, assetPath) {
    return YourCustomWidget(assetPath: assetPath);
  },
);
    
// 获取原始路径字符串
String path = Assets.images.logo.path;
```
