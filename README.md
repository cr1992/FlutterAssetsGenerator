# FlutterAssetsGenerator

It's a plugin that generates an asset index which we can easily find.It can be used on Android Studio or Idea.

## Installing

Drag it to your IDE to install.

## How to use

1.  define your assets dir in `pubspec.yaml`.

2.  - Build -> Generate Assets class
- Alt/Opt + G

It will generate assets.dart on lib/generated.

![image-20201121183150581](https://i.loli.net/2020/11/21/ruD9M8dv27zsbUK.png)

Simply use it like:

```dart
Image.asset(
          Assets.imageLoading,
          width: 24,
          height: 24,
          fit: BoxFit.contain,
        )
```

Plugin will observe your changes on assets path and update file.

## Settings

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
```