# Flutter Assets Generator

[English](#english) | [中文](#chinese)

<a name="english"></a>
## English

A powerful Android Studio / IntelliJ plugin that automatically generates a type-safe, hierarchical asset index for your Flutter projects.

### Core Features

- **Hierarchical Generation**: Generates classes that mirror your directory structure (e.g., `Assets.images.logo`).
- **Multi-Module Support**: Seamlessly supports nested Flutter modules and monorepos (e.g. `example/` or `packages/`).
- **Legacy Compatibility**: Supports `style: legacy` to generate flat variable names (e.g. `Assets.imagesLogo`) for easy migration.
- **Smart Type Support**: Automatically detects `SVG`, `Lottie` and `Rive` files.
- **Widget Integration**: Generates `.svg()`, `.lottie()` and `.rive()` methods directly on asset objects.
- **Auto Dependency Management**: Automatically checks and adds `flutter_svg`, `lottie` or `rive` dependencies.
- **Smart Auto Update**: 
    - **Assets**: Watch for image additions/deletions and regenerate automatically.
    - **Config**: Triggered on **File Save** (Cmd+S) in `pubspec.yaml`. Smart diffing ensures builds only run when necessary.
    - **Performance**: Fully **Asynchronous** generation process that never blocks the IDE UI. Say goodbye to freezes.

### Usage

#### 1. Quick Setup (Recommended)

For first-time users, use the one-click setup:

- **Menu**: Click `Tools` -> `Flutter Assets Generator` -> `Setup Project Configuration`.

This will automatically add the default configuration to your `pubspec.yaml`:

```yaml
flutter_assets_generator:
  output_dir: lib/generated/
  output_filename: assets
  class_name: Assets
  auto_detection: true
  auto_add_dependencies: true
  style: robust # Options: robust (default), legacy (legacy)
  package_parameter_enabled: false
  path_ignore: []
```

#### 2. Manual Configuration (Optional)

```yaml
flutter_assets_generator:
  # Sets the directory of generated files. Default: lib/generated
  output_dir: lib/generated/
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
  # Ignore specific paths. Default: []
  path_ignore: ["assets/fonts"]
```

#### 3. Generate File

- **Menu**: Click `Tools` -> `Flutter Assets Generator` -> `Generate Assets`.
- **Shortcut**: Press `Option`(Mac) / `Alt`(Win) + `G`.
- **Auto Format**: Generated code is automatically formatted.

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
-   **智能自动更新**：
    -   **资源文件**: 监听图片文件增删，自动重新生成。
    -   **配置文件**: 监听 `pubspec.yaml` 的**保存**动作 (Cmd+S)。智能 Diff 算法确保只在配置真正变化时构建。
    -   **性能优化**: 核心生成逻辑完全**异步化**，不再阻塞 IDE UI 线程，彻底告别卡顿。

### 使用方法

#### 1. 快速配置（推荐）

首次使用时，使用一键配置功能：

-   **菜单**: 点击 `Tools` -> `Flutter Assets Generator` -> `Setup Project Configuration`。

这将自动在您的 `pubspec.yaml` 中添加默认配置：

```yaml
flutter_assets_generator:
  output_dir: generated/
  output_filename: assets
  class_name: Assets
  auto_detection: true
  auto_add_dependencies: true
  # 生成风格: robust (默认), legacy (旧版兼容)
  # robust: 分层级风格 (Assets.images.logo)
  # legacy: 扁平风格 (Assets.imagesLogo)
  style: robust
  package_parameter_enabled: false
  path_ignore: []
```

#### 2. 手动配置（可选）

您也可以手动在 `pubspec.yaml` 中添加 `flutter_assets_generator` 进行自定义：

```yaml
flutter_assets_generator:
  # 生成文件的输出目录。默认: lib/generated
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
  # 忽略的路径。默认: []
  path_ignore: ["assets/fonts"]
```

#### 3. 生成文件

-   **菜单**: 点击 `Tools` -> `Flutter Assets Generator` -> `Generate Assets`。
-   **快捷键**: 按下 `Option`(Mac) / `Alt`(Win) + `G`。
-   **自动格式化**: 生成的代码会自动格式化。

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
