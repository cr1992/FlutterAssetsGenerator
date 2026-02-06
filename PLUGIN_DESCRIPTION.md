A powerful Android Studio / IntelliJ plugin that automatically generates a type-safe, hierarchical asset index for your Flutter projects.

## Core Features

- **Hierarchical Generation**: Generates classes that mirror your directory structure (e.g., `Assets.images.logo`).
- **Multi-Module Support**: Seamlessly supports nested Flutter modules and monorepos (e.g. `example/` or `packages/`).
- **Legacy Compatibility**: Supports `style: camel_case` to generate flat variable names (e.g. `Assets.imagesLogo`) for easy migration.
- **Smart Type Support**: Automatically detects `SVG` and `Lottie` files.
- **Widget Integration**: Generates `.svg()` and `.lottie()` methods directly on asset objects.
- **Auto Dependency Management**: Automatically checks and adds `flutter_svg` or `lottie` dependencies.
- **Smart Auto Update**: 
    - **Assets**: Watch for image additions/deletions and regenerate automatically.
    - **Config**: Triggered on **File Save** (Cmd+S) in `pubspec.yaml`. Smart diffing ensures builds only run when necessary.
    - **Performance**: Fully **Asynchronous** generation process that never blocks the IDE UI. Say goodbye to freezes.

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
  style: robust # Options: robust (default), camel_case (legacy)
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
  # Enable/Disable auto monitoring and dependency management. Default: true
  auto_detection: true
  # Generation style: robust (Hierarchical) or camel_case (Flat legacy). Default: robust
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
