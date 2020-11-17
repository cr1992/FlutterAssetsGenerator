# Flutter Assets generator
It's a plug-in that generates an asset index which we can easily find.It can be used on Android Studio or Idea.
## installing
Drag it to your IDE to install.
## How to use
- Build -> Generate Assets class
- Alt/Opt + G

It will generate assets.dart on lib/generated.
Simply use it like: 
```dart
Image.asset(
          Assets.loading_gif,
          width: 24,
          height: 24,
          fit: BoxFit.contain,
        )
```
You can also define it in your keymaps.

## Settings
You can set your configuration in Preferences -> Tools -> Flutter assets generator
, plugin supports auto-detection, default setting is off.