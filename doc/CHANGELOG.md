<!-- Keep a Changelog guide -> https://keepachangelog.com -->

[//]: # (# FlutterAssetsGenerator Changelog)
## [Unreleased]
- **AI Guidance Header**: Generated Dart files now include an AI-facing header that points assistants to the project usage guide before changing asset access code.
  - **AI 指引注释**: 生成的 Dart 文件头部现在会附带面向 AI 的提示，要求修改资源访问代码前先阅读项目使用说明。
- **Disable Cleanup**: Modules with `enable: false` now stop generating and remove the previously generated Dart file.
  - **禁用清理**: 配置为 `enable: false` 的模块现在会停止生成，并清理之前生成的 Dart 文件。
- **Module Setup Entry**: Added a Project View right-click setup entry that appears only on the exact Flutter module root, improving monorepo usability.
  - **模块配置入口**: 新增项目树右键 setup 入口，并且仅在精确命中 Flutter 模块根目录时显示，提升 monorepo 下的可用性。
- **Pubspec Filtering**: Filter out generated Flutter mirror directories such as `ephemeral` and `.plugin_symlinks` during module discovery to reduce false positives and scan noise.
  - **Pubspec 过滤**: 模块发现阶段过滤 `ephemeral`、`.plugin_symlinks` 等 Flutter 生成目录，减少误判和扫描噪音。
- **Project Activation Rules**: Separate project-level activation from module-level eligibility so only real Flutter modules are configurable, while workspace roots or pure Dart packages are excluded.
  - **项目激活规则**: 拆分项目级激活与模块级可配置判断，只将真实 Flutter 模块视为可配置目标，排除 workspace 根目录和纯 Dart package。

## [3.2.0]
### Added / 新增
- **Enable Config**: Added `enable` to explicitly control whether a module participates in watching and generation.
  - **启用开关**: 新增 `enable` 配置，用于显式控制模块是否参与监听和生成。
- **Leaf Type Config**: Added `leaf_type` for `robust` style so hierarchical APIs can return either typed wrappers or raw `String` paths.
  - **叶子类型配置**: 为 `robust` 风格新增 `leaf_type` 配置，支持在保留分层 API 的同时选择返回包装类型或原始 `String` 路径。

### Changed / 变更
- **YAML Source of Truth**: 3.x now treats `pubspec.yaml` as the source of truth. Modules without a `flutter_assets_generator` block are no longer watched or generated automatically.
  - **YAML 单一配置源**: 3.x 以后统一以 `pubspec.yaml` 为准。未配置 `flutter_assets_generator` 的模块不再自动监听或生成。

### Fixed / 修复
- **Name Style Handling**: Fixed `name_style` handling so generated identifiers now consistently respect `camel` and `snake`.
  - **命名风格处理**: 修复 `name_style` 配置未被完整应用的问题，确保生成标识符稳定遵循 `camel` 和 `snake`。
- **Legacy Naming Compatibility**: Restored legacy flat naming behavior for numeric assets such as `assets/images/0.png -> Assets.images0`.
  - **Legacy 命名兼容**: 恢复旧版扁平命名在数字资源下的兼容行为，例如 `assets/images/0.png -> Assets.images0`。
- **Robust Directory Naming**: Directory helper classes now use the full physical asset path to avoid collisions when different roots share the same folder name or tree shape.
  - **Robust 目录命名**: 目录内部类改为基于完整物理路径生成，修复不同资源根目录同名或同结构目录发生冲突的问题。
- **Robust Root Flattening**: Root flattening now only applies to a single `assets/` root so intermediate directories remain stable in deep paths.
  - **Robust 根目录扁平化**: 根目录扁平化现在仅对单一 `assets/` 根生效，避免深层路径丢失中间目录。

## [3.1.0]
### Added / 新增
- **Legacy Naming**: Added `named_with_parent` option for `legacy` style to restore old directory-prefixed variable naming (e.g., `imagesBtnLogo`).
  - **遗留命名兼容**: 为 `legacy` 风格新增 `named_with_parent` 配置项，彻底还原旧版的带目录前缀变量名生成逻辑 (例如 `imagesBtnLogo`)。
- **Rive Support**: Added support for `.rive` files and `rive` dependency automation.
  - **Rive 支持**: 新增对 `.rive` 文件及 `rive` 依赖自动化的支持。
- **Submodule Support**: Fixed `flutter pub get` execution context for submodules.
  - **子模块支持**: 修复了子模块中 `flutter pub get` 的执行上下文问题，确保在正确的目录执行。
- **Auto Dependencies Config**: Added `auto_add_dependencies` config to control automatic dependency injection.
  - **自动依赖配置**: 新增 `auto_add_dependencies` 配置，用于控制是否自动添加依赖。

### Fixed / 修复
- **Diacritic Handling**: Automatically remove diacritics from filenames to generate valid ASCII Dart variable names (e.g., `crème` -> `creme`).
  - **变音符号处理**: 自动移除文件名中的变音符号，确保生成合法的 ASCII Dart 变量名 (例如 `crème` -> `creme`)。

## [3.0.0]
### BREAKING CHANGES / 重大变更
- **Default Generation Style**: The default generated code is now **hierarchical** (e.g., `Assets.images.logo`) instead of flat. To restore the old flat behavior, add `style: legacy` to your `plugin` config in `pubspec.yaml`.
  - **默认生成风格**: 默认生成的代码现在是 **分层级** 的 (例如 `Assets.images.logo`) 而不是扁平的。如果需要恢复旧版的扁平风格，请在 `pubspec.yaml` 配置中添加 `style: legacy`。

### Added / 新增
- **Multi-Module Support**: Complete isolation for config per module/project.
  - **多模块支持**: 实现模块/项目级别的配置完全隔离，互不干扰。
- **Legacy Compatibility**: Added `style` option (`robust` vs `legacy`) to support legacy flat generation style.
  - **旧版兼容**: 新增 `style` 选项 (`robust` 或 `legacy`)，支持恢复旧版的扁平生成风格。
- **Safe Generation**: Moved generation logic to `invokeLater` to prevent file locking issues.
  - **安全生成**: 将生成逻辑移至 `invokeLater` 执行，彻底解决文件锁冲突问题。
- **Auto Format**: Restored automatic code formatting for generated files.
  - **自动格式化**: 恢复了生成代码的自动格式化功能。
- **Package Parameter Support**: Added `package_parameter_enabled` option to generate assets with `package` parameter, enabling usage across modules.
  - **Package 参数支持**: 新增 `package_parameter_enabled` 选项，支持生成带 `package` 参数的资源引用，方便在多模块中跨包使用。

### Changed / 变更
- **Smart Trigger**: Configuration changes now trigger on **File Save** (Cmd+S) instead of typing, reducing unnecessary builds.
  - **智能触发**: 配置文件变更现在通过 **保存文件** (Cmd+S) 触发，而不是在键入时频繁触发，减少冗余构建。
- **Config Reading**: Prioritize reading from Editor Memory (Document) over Disk to prevent stale data issues.
  - **配置读取**: 优先从编辑器内存 (Document) 读取配置，防止因磁盘写入延迟导致的旧数据问题。
- **Refined Naming**: Unified special character handling for asset filenames across all modes.
  - **命名优化**: 统一了所有模式下资源文件名的特殊字符处理逻辑，确保生成的变量名始终合法。

### Fixed / 修复
- **Notification Group**: Fixed duplicate `NotificationGroup` registration warning by using singleton pattern.
  - **通知组**: 使用单例模式修复了重复注册警告。
- **Redundant Generation**: Prevented unnecessary code regeneration after dependency injection by ignoring dependency version changes in config comparison.
  - **重复生成**: 通过在配置比较中忽略依赖版本变化,防止依赖注入后的不必要代码重新生成。

### Improved / 性能优化
- **Flutter Version Detection**: Implemented 3-tier caching mechanism (Cache > File > Command) for version detection.
  - **Flutter 版本检测**: 实施三级缓存机制 (缓存 > 文件 > 命令),大幅提升检测速度。
  - Performance: 60x faster on first call (~10ms vs ~600ms), 600x faster on subsequent calls (<1ms vs ~600ms)
  - 性能提升: 首次调用快 60 倍 (~10ms vs ~600ms),后续调用快 600 倍 (<1ms vs ~600ms)
  - Eliminated Flutter command lock conflicts
  - 消除了 Flutter 命令锁冲突
  - Supports Puro and other third-party Flutter management tools
  - 支持 Puro 等第三方 Flutter 管理工具

## [2.5.0]
### Fixed
- Bug fix.
### Added
- add enable leading with package name option
## [2.4.2]
### Added
- `Flutter: Configuring Paths` operation deletes paths that don't exist.
## [2.4.1]
### Fixed
- Bug fix.
## [2.4.0]
### Added
- Global configuration.
- Register assets to pubspec with one click.
## [2.3.0]
### Added
- Configuring ignore paths.
## [2.2.0]
### Fixed
- Svg preview not displaying.
### Added
- Multi module flutter project support.
## [2.1.0]
### Fixed
- Bug fix.
### Added
- Filename split supports config.
