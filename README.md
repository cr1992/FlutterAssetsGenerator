It's a plugin that generates an asset index which we can easily find.It can be used on Android Studio or Idea.

## How to use

### 1.Configuring paths in pubspec.yaml

Plugin now supports automatic configuration:
- Right-click on folder or file, then click
  <br>`Flutter: Configuration complete`.
<br>![](https://tva1.sinaimg.cn/large/008vxvgGly1h9h9nxz6ttg30ed0ootl5.gif)
### 2.Generate file

You can generate file by these ways:

- `Build` => `Generate Flutter Assets`
<br>![](https://tva1.sinaimg.cn/large/008vxvgGly1h9h9rnd51mg30hv0orjz6.gif)
- Press `Option`(mac)/`Alt`(win) + `G`,It will generate assets.dart on lib/generated.

Simply use it like:

```dart
Image.asset(
          Assets.imageLoading,
          width: 24,
          height: 24,
          fit: BoxFit.contain,
        )
```

### 3.Extras

- You can locate file quickly by click line-marker.
  <br>![](https://tva1.sinaimg.cn/large/008vxvgGly1h9h9vyjccyg30hv0ordpz.gif)
- Plugin will observe your changes on assets path and update file.
  <br><img style="max-width:100%;overflow:hidden;" src="https://tva1.sinaimg.cn/large/008vxvgGly1h9h6yqqx09g312l0q4k4n.gif" alt="">

## Settings
### Global
`Preferences` => `Tools` => `FlutterAssetsGenerator`
### Module based
You can change default settings by add following contents in your `pubspec.yaml`.

```yaml
flutter_assets_generator:
  # Optional. Sets the directory of generated localization files. Provided value should be a valid path on lib dir. Default: generated
  output_dir: generated
  # Optional. Sets whether utomatic monitoring of file changes. Default: true
  auto_detection: true
  # Optional. Sets file name conversion rules. Default: true
  named_with_parent: true
  # Optional. Sets the name for the generated localization file. Default: assets
  output_filename: assets
  # Optional. Sets the name for the generated localization class. Default: Assets
  class_name: Assets
  # Optional. Sets the filename split pattern for filename split. Default: [-_]
  filename_split_pattern: "[-_]"
  # Optional. Configuring ignore paths. Default: [],e.g: ["assets/fonts", "assets/images/dark", ...]
  path_ignore: []
```
