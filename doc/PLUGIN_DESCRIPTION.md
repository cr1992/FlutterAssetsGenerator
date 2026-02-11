A powerful Android Studio / IntelliJ plugin that automatically generates a type-safe, hierarchical asset index for your Flutter projects.

## Core Features

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
- **Package Support**: Option to generate `package: 'name'` parameter for all assets, perfect for modular projects where assets live in a separate package.

## Usage

### 1. Quick Setup (Recommended)

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

### 2. Manual Configuration (Optional)

```yaml
flutter_assets_generator:
  # Sets the directory of generated files. Default: lib/generated
  output_dir: lib/generated/
  # Sets the name for the generated file. Default: assets
  output_filename: assets
  # Sets the name for the root class. Default: Assets
  class_name: Assets
  # Enable package parameter generation (package: 'your_package_name'). Default: false
  package_parameter_enabled: false
  # Enable/Disable auto monitoring and dependency management. Default: true
  auto_detection: true
  # Add dependencies to pubspec.yaml automatically. Default: true
  auto_add_dependencies: true
  # Generation style: robust (Hierarchical) or legacy (Flat legacy). Default: robust
  style: robust
  # Ignore specific paths. Default: []
  path_ignore: ["assets/fonts"]
```

### 3. Generate File

- **Menu**: Click `Tools` -> `Flutter Assets Generator` -> `Generate Assets`.
- **Shortcut**: Press `Option`(Mac) / `Alt`(Win) + `G`.
- **Auto Format**: Generated code is automatically formatted.

### 4. Access Assets in Code

The plugin generates a strict, type-safe hierarchy:

```dart
// Standard Image
Assets.images.logo.image(width: 24, height: 24);

// SVG (Requires flutter_svg dependency)
Assets.icons.home.svg(color: Colors.blue);

// Lottie (Requires lottie dependency)
Assets.anim.loading.lottie(repeat: true);

// Custom widget builder - allows you to build any widget with the asset path
Assets.images.logo.custom(
  builder: (context, assetPath) {
    return YourCustomWidget(assetPath: assetPath);
  },
);
    
// Get raw path string
String path = Assets.images.logo.path;
```
