<!-- Keep a Changelog guide -> https://keepachangelog.com -->

[//]: # (# FlutterAssetsGenerator Changelog)
## [3.1.0]
### Added / 新增
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