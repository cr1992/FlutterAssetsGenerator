# FlutterAssetsGenerator

It's a plug-in that generates an asset index which we can easily find.It can be used on Android Studio or Idea.

## Installing

Drag it to your IDE to install.

## How to use

1.  define your assets dir in `pubspec.yaml`.

2.  - Build -> Generate Assets class
    - Alt/Opt + G

It will generate assets.dart on lib/generated.

<img src="https://i.loli.net/2020/11/21/MRJS2wDjQh9ecan.png" alt="image-20201121172520136" style="zoom: 50%;" />

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

<img src="https://i.loli.net/2020/11/21/RIk7PgQHXwC6e5T.png" alt="image-20201121172929717" style="zoom: 50%;" />

You can customize generated fils's path, split it with "/".