<!-- Keep a Changelog guide -> https://keepachangelog.com -->

[//]: # (# FlutterAssetsGenerator Changelog)
## [3.0.0]
### BREAKING CHANGES / 重大变更
- **Default Generation Style**: The default generated code is now **hierarchical** (e.g., `Assets.images.logo`) instead of flat. To restore the old flat behavior, add `style: camel_case` to your `plugin` config in `pubspec.yaml`.
  - **默认生成风格**: 默认生成的代码现在是 **分层级** 的 (例如 `Assets.images.logo`) 而不是扁平的。如果需要恢复旧版的扁平风格，请在 `pubspec.yaml` 配置中添加 `style: camel_case`。

### Added / 新增
- **Multi-Module Support**: Complete isolation for config per module/project.
  - **多模块支持**: 实现模块/项目级别的配置完全隔离，互不干扰。
- **Legacy Compatibility**: Added `style` option (`robust` vs `camel_case`) to support legacy flat generation style.
  - **旧版兼容**: 新增 `style` 选项 (`robust` 或 `camel_case`)，支持恢复旧版的扁平生成风格。
- **Safe Generation**: Moved generation logic to `invokeLater` to prevent file locking issues.
  - **安全生成**: 将生成逻辑移至 `invokeLater` 执行，彻底解决文件锁冲突问题。
- **Auto Format**: Restored automatic code formatting for generated files.
  - **自动格式化**: 恢复了生成代码的自动格式化功能。

### Changed / 变更
- **Smart Trigger**: Configuration changes now trigger on **File Save** (Cmd+S) instead of typing, reducing unnecessary builds.
  - **智能触发**: 配置文件变更现在通过 **保存文件** (Cmd+S) 触发，而不是在键入时频繁触发，减少冗余构建。
- **Config Reading**: Prioritize reading from Editor Memory (Document) over Disk to prevent stale data issues.
  - **配置读取**: 优先从编辑器内存 (Document) 读取配置，防止因磁盘写入延迟导致的旧数据问题。
- **Refined Naming**: Unified special character handling for asset filenames across all modes.
  - **命名优化**: 统一了所有模式下资源文件名的特殊字符处理逻辑，确保生成的变量名始终合法。

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