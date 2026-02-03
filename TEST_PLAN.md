# Flutter Assets Generator v3.0.0 测试用例 (Test Cases)

本文档旨在提供一套完整的测试流程，用于验证 v3.0.0 版本的功能正确性、稳定性和兼容性。

## 1. 环境准备 (Setup)
*   **IDE**: Android Studio 或 IntelliJ IDEA (兼容 2021.3+)
*   **Plugin**: `build/distributions/FlutterAssetsGenerator-3.0.0.zip`
*   **Test Project**: 一个包含 `pubspec.yaml` 的标准 Flutter 项目（建议包含 `assets/images/` 和 `assets/icons/` 目录）。

## 2. 核心功能测试 (Core Functionality)

### TC-01: 插件安装与基本状态
*   **步骤**: 
    1.  Preferences -> Plugins -> Install Plugin from Disk -> 选择 zip 包。
    2.  重启 IDE。
*   **预期**: 
    - 插件安装成功，IDE 无报错。
    - **验证点**: 打开 Preferences，确认**没有** `FlutterAssetsGenerator` 的设置页面（Settings UI 已移除）。

### TC-02: 一件初始化配置 (One-Click Setup)
*   **步骤**: 
    1.  打开一个未配置过本插件的 Flutter 项目。
    2.  点击菜单栏 `Tools` -> `Flutter Assets Generator` -> `Setup Project Configuration`。
*   **预期**: 
    - `pubspec.yaml` 自动添加了 `flutter_assets_generator` 配置块。
    - 默认配置应为：
        ```yaml
        flutter_assets_generator:
          output_dir: lib/generated/
          class_name: Assets
          style: robust
        ```

### TC-03: 默认生成 (Robust Style)
*   **前置**: 确保 `pubspec.yaml` 中配置了 `assets:` 路径，并且目录下有图片文件 (e.g. `assets/images/logo.png`)。
*   **步骤**: 
    1.  手动保存 `pubspec.yaml` (Cmd+S)。
    2.  或者点击菜单 `Tools` -> `Flutter Assets Generator` -> `Generate Assets`。
*   **预期**: 
    - 在 `lib/generated/` 下生成 `assets.dart`。
    - 生成的代码结构通过类层级访问: `Assets.images.logo`。
    - 返回类型为 `AssetGenImage` (如果项目依赖了 flutter, 否则为 String)。

### TC-04: 自动更新 (Auto Watch)
*   **步骤**: 
    1.  在 `assets/images/` 下粘贴一个新的图片文件 `new_icon.png`。
    2.  切换回 IDE 窗口，等待 1-2 秒（或手动构建项目触发索引更新）。
    3.  查看 `assets.dart`。
*   **预期**: 
    - `assets.dart` 自动更新，新增了 `newIcon` 字段。
    - 删除该文件，`assets.dart` 中对应的字段应自动移除。

### TC-05: 自动依赖注入 (Dependency Injection)
*   **步骤**: 
    1.  在 `assets/` 下放入一个 `.svg` 文件。
    2.  打开 `pubspec.yaml`，确认 Dependencies 中**没有** `flutter_svg`。
    3.  保存 `pubspec.yaml` 或触发生成。
*   **预期**: 
    - 插件检测到 SVG 文件，自动在 `pubspec.yaml` 中添加 `flutter_svg` 依赖。
    - 生成的代码中，该 SVG 字段支持 `.svg()` 方法。
*   **同理测试**: `.json` / `.lottie` 文件触发 `lottie` 包的自动注入。

## 3. 配置兼容性测试 (Configuration Compatibility)

### TC-06: 兼容模式 (Legacy / Camel Case)
*   **步骤**: 
    1.  修改 `pubspec.yaml` 配置：
        ```yaml
        flutter_assets_generator:
          style: camel_case
        ```
    2.  保存文件。
*   **预期**: 
    - `assets.dart` 重新生成。
    - 类结构变回扁平化。
    - 字段名变为驼峰式拼接: `static const String imagesLogo = 'assets/images/logo.png';`
    - **验证点**: 确保没有编译错误，且与旧版插件生成逻辑一致。

### TC-07: 自定义类名与路径
*   **步骤**: 
    1.  修改配置：
        ```yaml
        flutter_assets_generator:
          output_dir: lib/src/res/
          class_name: R
          output_filename: r
        ```
    2.  保存。
*   **预期**: 
    - 文件生成在 `lib/src/res/r.dart`。
    - 类名为 `class R { ... }`。

### TC-08: 路径忽略 (Ignore)
*   **步骤**: 
    1.  配置忽略项：
        ```yaml
        flutter_assets_generator:
          path_ignore:
            - "assets/ignore_me/"
        ```
    2.  并在该目录下放文件。
*   **预期**: 
    - 该目录下的文件**不会**出现在生成的 Dart 类中。

## 4. 特殊场景测试 (Edge Cases)

### TC-09: 非法文件名处理
*   **步骤**: 
    1.  创建文件 `assets/images/2.0x/icon-with-dashes & spaces.png`。
    2.  创建文件 `assets/images/do.png` (Dart 关键字)。
*   **预期**: 
    - 生成代码不报错。
    - 变量名被安全处理: 
        - `iconWithDashesSpaces` (去除特殊字符并驼峰化)。
        - `kDo` (关键字加前缀)。

### TC-10: 多模块支持 (Multi-Module)
*   **场景**: 一个 Project 中包含 `module_a` 和 `module_b` 两个 Flutter 模块。
*   **步骤**: 
    1.  分别配置两个模块的 `pubspec.yaml`。
    2.  分别触发生成。
*   **预期**: 
    - 两个模块各自生成自己的 `assets.dart`，互不干扰。
    - `PubspecConfigCache` 正确隔离了不同模块的配置。

## 5. UI 交互测试 (UI/UX)

### TC-11: Line Marker (行标记)
*   **步骤**: 
    1.  在 Dart 代码中引用资源字符串 `'assets/images/logo.png'`。
    2.  观察行号左侧。
*   **预期**: 
    - 出现资源缩略图（图片或 SVG 图标）。
    - 点击图标打开对应的资源文件。
    - **性能验证**: 快速滚动代码，界面不卡顿（验证缓存优化是否生效）。

### TC-12: 右键菜单配置
*   **步骤**: 
    1.  在 Project 视图右键点击 `assets` 文件夹。
    2.  选择 `Flutter: Configuring Paths`。
*   **预期**: 
    - 该路径自动添加到 `pubspec.yaml` 的 `flutter: assets:` 列表中。



### TC-14: 空目录过滤 (Empty Directory Skip)
*   **步骤**:
    1.  在 `assets` 目录下创建一个空文件夹 `empty_folder`。
    2.  再创建一个文件夹 `ignore_folder`，并在其中放入一个文件，但配置 `path_ignore` 忽略该文件夹。
    3.  触发代码生成。
*   **预期**:
    - 生成的 `assets.dart` 中**不应**包含 `empty_folder` 和 `ignore_folder` 对应的类或字段。
    - 确保生成的代码整洁，没有无用的空类。

### TC-15: 输出文件名变更检测
*   **步骤**:
    1.  修改 `pubspec.yaml` 中的 `output_filename` 配置，例如从 `assets` 改为 `res`。
    2.  保存 `pubspec.yaml`。
*   **预期**:
    - 插件检测到配置变更，自动删除旧的 `assets.dart`（或保留，视具体实现而定，通常是生成新的），并在指定目录下生成 `res.dart`。

### TC-16: 字符串生成格式 (String Format / Const Removal)
*   **步骤**:
    1.  查看生成的代码中，普通文件（如 `.txt`、`.pdf` 或未识别类型）的字段定义。
    2.  查看生成的 `assets.dart`。
*   **预期**:
    - 字段定义应为 `final String fieldName = 'path/to/file';`。
    - **不应**包含 `const` 修饰符（例如 `final String a = const '...';` 是错误的）。
    - 确保字符串内容没有意外的换行符。

