package com.crzsc.plugin.test

import com.crzsc.plugin.utils.AssetNode
import com.crzsc.plugin.utils.Constants
import com.crzsc.plugin.utils.DartClassGenerator
import com.crzsc.plugin.utils.MediaType
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class DartClassGeneratorTest {

    @Test
    fun testDiacriticsRemoval() {
        // 构建测试资源树
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assetsDir)

        // 添加包含变音符号的文件
        // 1. crème_brûlée.png -> cremeBrulee
        assetsDir.children.add(
            AssetNode("crème_brûlée", "assets/crème_brûlée.png", MediaType.IMAGE, null)
        )
        // 2. ångström.txt -> angstrom
        assetsDir.children.add(
            AssetNode("ångström", "assets/ångström.txt", MediaType.UNKNOWN, null)
        )
        // 3. Garçon.png -> garcon
        assetsDir.children.add(
            AssetNode("Garçon", "assets/Garçon.png", MediaType.IMAGE, null)
        )
        // 4. über.png -> uber
        assetsDir.children.add(AssetNode("über", "assets/über.png", MediaType.IMAGE, null))

        // 生成代码
        val config = createMockConfig()
        val generator =
            DartClassGenerator(
                root,
                config,
                hasSvg = false,
                hasSvgDep = false,
                hasLottie = false,
                hasLottieDep = false,
                hasRive = false,
                hasRiveDep = false,
                flutterVersion = null
            )
        val generatedCode = generator.generate()

        println(generatedCode)

        // 验证
        assertTrue(
            "Should contain 'cremeBrulee', but got: $generatedCode",
            generatedCode.contains(
                "static const AssetGenImage cremeBrulee = AssetGenImage('assets/crème_brûlée.png');"
            )
        )
        assertTrue(
            "Should contain 'angstrom', but got: $generatedCode",
            generatedCode.contains(
                "static const String angstrom = 'assets/ångström.txt';"
            )
        )
        assertTrue(
            "Should contain 'garcon', but got: $generatedCode",
            generatedCode.contains(
                "static const AssetGenImage garcon = AssetGenImage('assets/Garçon.png');"
            )
        )
        assertTrue(
            "Should contain 'uber', but got: $generatedCode",
            generatedCode.contains(
                "static const AssetGenImage uber = AssetGenImage('assets/über.png');"
            )
        )
    }

    @Test
    fun testSpecialCharFieldNames() {
        // 构建测试资源树
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assetsDir)

        // 添加特殊命名的文件
        // 1. 数字开头: 3.0.0.1.txt -> a3001
        assetsDir.children.add(
            AssetNode("3.0.0.1.txt", "assets/3.0.0.1.txt", MediaType.UNKNOWN, null)
        )
        // 2. 关键词: class.png -> class_
        assetsDir.children.add(
            AssetNode("class.png", "assets/class.png", MediaType.IMAGE, null)
        )
        // 3. 特殊字符: my-image.png -> myImage
        assetsDir.children.add(
            AssetNode("my-image.png", "assets/my-image.png", MediaType.IMAGE, null)
        )
        // 4. 多重扩展名: archive.tar.gz -> archiveTarGz (注意: AssetTreeBuilder 可能会把 name 设置为
        // archive.tar, 但这里模拟最原始的情况)
        // 假设 AssetTreeBuilder.processFile 中的 name = file.nameWithoutExtension
        // 对于 archive.tar.gz -> name 可能是 archive.tar
        assetsDir.children.add(
            AssetNode("archive.tar", "assets/archive.tar.gz", MediaType.UNKNOWN, null)
        )
        // 5. 仅数字: 123.png -> a123
        assetsDir.children.add(AssetNode("123", "assets/123.png", MediaType.IMAGE, null))
        // 6. 特殊符号开头: _private.png -> private (trim logic)
        assetsDir.children.add(
            AssetNode("_private", "assets/_private.png", MediaType.IMAGE, null)
        )

        // 生成代码
        val config = createMockConfig()
        val generator =
            DartClassGenerator(
                root,
                config,
                hasSvg = false,
                hasSvgDep = false,
                hasLottie = false,
                hasLottieDep = false,
                hasRive = false,
                hasRiveDep = false,
                flutterVersion = null
            )
        val generatedCode = generator.generate()

        println(generatedCode)

        // 验证
        // 3.0.0.1.txt 应转换为 a3001
        assertTrue(
            "Should contain 'a3001Txt'",
            generatedCode.contains(
                "static const String a3001Txt = 'assets/3.0.0.1.txt';"
            )
        )

        // class.png 应转换为 classPng
        assertTrue(
            "Should contain 'classPng'",
            generatedCode.contains(
                "static const AssetGenImage classPng = AssetGenImage('assets/class.png');"
            )
        )
        // my-image.png 应转换为 myImagePng
        assertTrue(
            "Should contain 'myImagePng'",
            generatedCode.contains(
                "static const AssetGenImage myImagePng = AssetGenImage('assets/my-image.png');"
            )
        )
        // archive.tar.gz (name=archive.tar) -> archiveTar
        // 如果我们移除 substringBeforeLast('.')，那么 archive.tar -> archive.tar -> archiveTar
        assertTrue(
            "archive.tar should be archiveTar",
            generatedCode.contains(
                "static const String archiveTar = 'assets/archive.tar.gz';"
            )
        )

        // 123.png (name=123) -> a123
        assertTrue(
            "123.png should be a123",
            generatedCode.contains(
                "static const AssetGenImage a123 = AssetGenImage('assets/123.png');"
            )
        )

        // _private.png (name=_private) -> private
        assertTrue(
            "_private.png should be private",
            generatedCode.contains(
                "static const AssetGenImage private = AssetGenImage('assets/_private.png');"
            )
        )
    }

    @Test
    fun testLegacyCamelStyleKeepsOldNumericPrefixBehavior() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assetsDir)

        val imagesDirVf = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(imagesDirVf.name).thenReturn("images")
        val assetsDirVf = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(imagesDirVf.parent).thenReturn(assetsDirVf)

        val fileVf = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(fileVf.parent).thenReturn(imagesDirVf)

        assetsDir.children.add(
            AssetNode("0", "assets/images/0.png", MediaType.IMAGE, fileVf)
        )

        val config =
            createMockConfig(
                mapOf(
                    "style" to "legacy",
                    Constants.KEY_NAME_STYLE to Constants.NAME_STYLE_CAMEL,
                    Constants.KEY_NAMED_WITH_PARENT to true
                )
            )

        val generatedCode =
            DartClassGenerator(
                root,
                config,
                hasSvg = false,
                hasSvgDep = false,
                hasLottie = false,
                hasLottieDep = false,
                hasRive = false,
                hasRiveDep = false,
                flutterVersion = null
            )
                .generate()

        assertTrue(
            generatedCode.contains(
                "static const String images0 = 'assets/images/0.png';"
            )
        )
        assertFalse(generatedCode.contains("imagesA0"))
    }

    @Test
    fun testSnakeNameStyleAppliesToGeneratedNames() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assetsDir)
        assetsDir.children.add(
            AssetNode("my-image.png", "assets/my-image.png", MediaType.IMAGE, null)
        )

        val config =
            createMockConfig(
                mapOf(
                    Constants.KEY_NAME_STYLE to Constants.NAME_STYLE_SNAKE
                )
            )

        val generatedCode =
            DartClassGenerator(
                root,
                config,
                hasSvg = false,
                hasSvgDep = false,
                hasLottie = false,
                hasLottieDep = false,
                hasRive = false,
                hasRiveDep = false,
                flutterVersion = null
            )
                .generate()

        assertTrue(
            generatedCode.contains(
                "static const AssetGenImage my_image_png = AssetGenImage('assets/my-image.png');"
            )
        )
    }

    @Test
    fun testGeneratedFileHeaderIncludesAiDocumentationHint() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = directoryNode("assets", "assets")
        assetsDir.children.add(fileNode("logo", "assets/logo.png", MediaType.IMAGE))
        root.children.add(assetsDir)

        val robustCode = createGenerator(root).generate()
        val legacyCode = createGenerator(root, mapOf("style" to "legacy")).generate()

        assertTrue(
            robustCode.contains(
                "///For AI assistants: read the project usage guide before changing asset access code."
            )
        )
        assertTrue(
            robustCode.contains("///Docs: https://github.com/cr1992/FlutterAssetsGenerator")
        )
        assertTrue(
            legacyCode.contains(
                "///For AI assistants: read the project usage guide before changing asset access code."
            )
        )
        assertTrue(
            legacyCode.contains("///Docs: https://github.com/cr1992/FlutterAssetsGenerator")
        )
    }

    @Test
    fun testRobustDirectoryClassNamesAreUniqueForSameLeafFolderName() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = directoryNode("assets", "assets")
        val iconsDir = directoryNode("icons", "assets/icons")
        val imagesDir = directoryNode("images", "assets/images")
        val iconsFolder = directoryNode("my_folder", "assets/icons/my_folder")
        val imagesFolder = directoryNode("my_folder", "assets/images/my_folder")
        iconsFolder.children.add(fileNode("user", "assets/icons/my_folder/user.png", MediaType.IMAGE))
        imagesFolder.children.add(fileNode("bg", "assets/images/my_folder/bg.png", MediaType.IMAGE))
        iconsDir.children.add(iconsFolder)
        imagesDir.children.add(imagesFolder)
        assetsDir.children.add(iconsDir)
        assetsDir.children.add(imagesDir)
        root.children.add(assetsDir)

        val generatedCode = createGenerator(root).generate()

        assertTrue(generatedCode.contains("static const \$AssetsGenIcons icons = \$AssetsGenIcons();"))
        assertTrue(generatedCode.contains("static const \$AssetsGenImages images = \$AssetsGenImages();"))
        assertTrue(
            generatedCode.contains(
                "final \$AssetsGenIconsMyFolder myFolder = const \$AssetsGenIconsMyFolder();"
            )
        )
        assertTrue(
            generatedCode.contains(
                "final \$AssetsGenImagesMyFolder myFolder = const \$AssetsGenImagesMyFolder();"
            )
        )
        assertTrue(generatedCode.contains("class \$AssetsGenIconsMyFolder {"))
        assertTrue(generatedCode.contains("class \$AssetsGenImagesMyFolder {"))
    }

    @Test
    fun testRobustDirectoryClassNamesAreUniqueForMultipleRootsWithSameTree() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = directoryNode("assets", "assets")
        val brandingDir = directoryNode("branding", "branding")
        val assetsIcons = directoryNode("icons", "assets/icons")
        val brandingAssets = directoryNode("assets", "branding/assets")
        val brandingIcons = directoryNode("icons", "branding/assets/icons")
        val assetsCommon = directoryNode("common", "assets/icons/common")
        val brandingCommon = directoryNode("common", "branding/assets/icons/common")
        assetsCommon.children.add(fileNode("user", "assets/icons/common/user.png", MediaType.IMAGE))
        brandingCommon.children.add(
            fileNode("user", "branding/assets/icons/common/user.png", MediaType.IMAGE)
        )
        assetsIcons.children.add(assetsCommon)
        brandingIcons.children.add(brandingCommon)
        assetsDir.children.add(assetsIcons)
        brandingAssets.children.add(brandingIcons)
        brandingDir.children.add(brandingAssets)
        root.children.add(assetsDir)
        root.children.add(brandingDir)

        val generatedCode = createGenerator(root).generate()

        assertTrue(generatedCode.contains("class \$AssetsGenAssetsIconsCommon {"))
        assertTrue(generatedCode.contains("class \$AssetsGenBrandingAssetsIconsCommon {"))
        assertFalse(generatedCode.contains("class \$AssetsGenCommon {"))
    }

    @Test
    fun testRobustKeepsIntermediateDirectoriesForDeepPath() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = directoryNode("assets", "assets")
        val imagesDir = directoryNode("images", "assets/images")
        val drawerDir = directoryNode("drawer", "assets/images/drawer")
        val backgroundsDir = directoryNode("backgrounds", "assets/images/drawer/backgrounds")
        backgroundsDir.children.add(
            fileNode("bg", "assets/images/drawer/backgrounds/bg.png", MediaType.IMAGE)
        )
        drawerDir.children.add(backgroundsDir)
        imagesDir.children.add(drawerDir)
        assetsDir.children.add(imagesDir)
        root.children.add(assetsDir)

        val generatedCode = createGenerator(root).generate()

        assertTrue(generatedCode.contains("static const \$AssetsGenImages images = \$AssetsGenImages();"))
        assertTrue(
            generatedCode.contains(
                "final \$AssetsGenImagesDrawer drawer = const \$AssetsGenImagesDrawer();"
            )
        )
        assertTrue(
            generatedCode.contains(
                "final \$AssetsGenImagesDrawerBackgrounds backgrounds = const \$AssetsGenImagesDrawerBackgrounds();"
            )
        )
        assertFalse(
            generatedCode.contains(
                "final \$AssetsGenImagesBackgrounds backgrounds = const \$AssetsGenImagesBackgrounds();"
            )
        )
    }

    @Test
    fun testRootDoesNotFlattenWhenSingleRootDirectoryIsNotAssets() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val brandingDir = directoryNode("branding", "branding")
        val iconsDir = directoryNode("icons", "branding/icons")
        iconsDir.children.add(fileNode("logo", "branding/icons/logo.png", MediaType.IMAGE))
        brandingDir.children.add(iconsDir)
        root.children.add(brandingDir)

        val generatedCode = createGenerator(root).generate()

        assertTrue(
            generatedCode.contains(
                "static const \$AssetsGenBranding branding = \$AssetsGenBranding();"
            )
        )
        assertFalse(
            generatedCode.contains(
                "static const \$AssetsGenIcons icons = \$AssetsGenIcons();"
            )
        )
    }

    @Test
    fun testRobustLeafTypeStringGeneratesHierarchicalStringLeaves() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = directoryNode("assets", "assets")
        val iconsDir = directoryNode("icons", "assets/icons")
        iconsDir.children.add(fileNode("user", "assets/icons/user.png", MediaType.IMAGE))
        assetsDir.children.add(iconsDir)
        root.children.add(assetsDir)

        val generatedCode =
            createGenerator(
                root,
                mapOf(Constants.KEY_LEAF_TYPE to Constants.LEAF_TYPE_STRING)
            ).generate()

        assertTrue(
            generatedCode.contains(
                "static const \$AssetsGenIcons icons = \$AssetsGenIcons();"
            )
        )
        assertTrue(generatedCode.contains("final String user = 'assets/icons/user.png';"))
        assertFalse(generatedCode.contains("class AssetGenImage {"))
    }

    @Test
    fun testLegacyStyleIgnoresLeafTypeString() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = directoryNode("assets", "assets")
        assetsDir.children.add(fileNode("logo", "assets/logo.png", MediaType.IMAGE))
        root.children.add(assetsDir)

        val generatedCode =
            createGenerator(
                root,
                mapOf(
                    "style" to "legacy",
                    Constants.KEY_LEAF_TYPE to Constants.LEAF_TYPE_STRING
                )
            ).generate()

        assertTrue(
            generatedCode.contains("static const String logo = 'assets/logo.png';")
        )
        assertFalse(generatedCode.contains("class \$AssetsGenAssets"))
    }

    @Test
    fun testLegacyStyleWithLeafTypeClassGeneratesTypedWrappers() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = directoryNode("assets", "assets")
        assetsDir.children.add(fileNode("logo", "assets/logo.png", MediaType.IMAGE))
        assetsDir.children.add(fileNode("icon", "assets/icon.svg", MediaType.SVG))
        assetsDir.children.add(fileNode("anim", "assets/anim.lottie", MediaType.LOTTIE))
        assetsDir.children.add(fileNode("motion", "assets/motion.riv", MediaType.RIVE))
        assetsDir.children.add(fileNode("readme", "assets/readme.txt", MediaType.UNKNOWN))
        root.children.add(assetsDir)

        val generatedCode =
            createGenerator(
                root,
                mapOf(
                    "style" to "legacy",
                    Constants.KEY_LEAF_TYPE to Constants.LEAF_TYPE_CLASS
                )
            ).generate()

        assertTrue(
            "Should contain AssetGenImage for png",
            generatedCode.contains(
                "static const AssetGenImage logo = AssetGenImage('assets/logo.png');"
            )
        )
        assertTrue(
            "Should contain SvgGenImage for svg",
            generatedCode.contains(
                "static const SvgGenImage icon = SvgGenImage('assets/icon.svg');"
            )
        )
        assertTrue(
            "Should contain LottieGenImage for lottie",
            generatedCode.contains(
                "static const LottieGenImage anim = LottieGenImage('assets/anim.lottie');"
            )
        )
        assertTrue(
            "Should contain RiveGenImage for rive",
            generatedCode.contains(
                "static const RiveGenImage motion = RiveGenImage('assets/motion.riv');"
            )
        )
        assertTrue(
            "Should contain String for unknown type",
            generatedCode.contains(
                "static const String readme = 'assets/readme.txt';"
            )
        )
        assertTrue(
            "Should contain helper class AssetGenImage",
            generatedCode.contains("class AssetGenImage {")
        )
        assertFalse(
            "Should not contain directory classes",
            generatedCode.contains("class \$AssetsGen")
        )
    }

    private fun createMockConfig(pluginConfig: Map<String, Any> = emptyMap()): ModulePubSpecConfig {
        val mockConfig = Mockito.mock(ModulePubSpecConfig::class.java)
        Mockito.`when`(mockConfig.map)
            .thenReturn(
                mapOf(
                    Constants.KEY_CONFIGURATION_MAP to pluginConfig
                )
            )

        return mockConfig
    }

    private fun createGenerator(root: AssetNode, pluginConfig: Map<String, Any> = emptyMap()): DartClassGenerator {
        return DartClassGenerator(
            root,
            createMockConfig(pluginConfig),
            hasSvg = false,
            hasSvgDep = false,
            hasLottie = false,
            hasLottieDep = false,
            hasRive = false,
            hasRiveDep = false,
            flutterVersion = null
        )
    }

    private fun directoryNode(name: String, path: String): AssetNode {
        return AssetNode(name, path, MediaType.DIRECTORY, null)
    }

    private fun fileNode(name: String, path: String, type: MediaType): AssetNode {
        return AssetNode(name, path, type, null)
    }
}
