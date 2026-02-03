<!-- Keep a Changelog guide -> https://keepachangelog.com -->

[//]: # (# FlutterAssetsGenerator Changelog)
## [3.0.0]
### BREAKING CHANGES
- **Default Generation Style**: The default generated code is now **hierarchical** (e.g., `Assets.images.logo`) instead of flat. To restore the old flat behavior, add `style: camel_case` to your `plugin` config in `pubspec.yaml`.

### Added
- **Multi-Module Support**: Complete isolation for config per module/project.
- **Legacy Compatibility**: Added `style` option (`robust` vs `camel_case`) to support legacy flat generation style.
- **Safe Generation**: Moved generation logic to `invokeLater` to prevent file locking issues.
- **Auto Format**: Restored automatic code formatting for generated files.

### Changed
- **Smart Trigger**: Configuration changes now trigger on **File Save** (Cmd+S) instead of typing, reducing unnecessary builds.
- **Config Reading**: Prioritize reading from Editor Memory (Document) over Disk to prevent stale data issues.
- **Refined Naming**: Unified special character handling for asset filenames across all modes.

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