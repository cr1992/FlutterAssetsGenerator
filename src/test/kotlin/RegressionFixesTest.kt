package com.crzsc.plugin.test

import com.crzsc.plugin.cache.PubspecConfig
import com.crzsc.plugin.cache.PubspecConfigCache
import com.crzsc.plugin.listener.VfsAssetListener
import com.crzsc.plugin.utils.AssetNode
import com.crzsc.plugin.utils.Constants
import com.crzsc.plugin.utils.DartClassGenerator
import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.FlutterVersionHelper
import com.crzsc.plugin.utils.MediaType
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.crzsc.plugin.utils.SemanticVersion
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/** 3.3.1 修复的回归测试 */
class RegressionFixesTest {

    // ---------- Dart 字面量转义 ----------

    @Test
    fun testDartLiteralEscapesInterpolationQuoteAndBackslash() {
        assertEquals("'assets/a.png'", DartClassGenerator.dartLiteral("assets/a.png"))
        assertEquals("'assets/price\\\$1.png'", DartClassGenerator.dartLiteral("assets/price\$1.png"))
        assertEquals("'assets/it\\'s.png'", DartClassGenerator.dartLiteral("assets/it's.png"))
        assertEquals("'assets\\\\win.png'", DartClassGenerator.dartLiteral("assets\\win.png"))
    }

    @Test
    fun testGeneratedCodeEscapesDollarInAssetPath() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assets = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assets)
        assets.children.add(AssetNode("price\$1", "assets/price\$1.png", MediaType.IMAGE, null))

        val code = generator(root).generate()
        assertTrue(code, code.contains("AssetGenImage('assets/price\\\$1.png')"))
    }

    // ---------- 未知类型路径在两种风格下一致 ----------

    @Test
    fun testUnknownTypeUsesPackagePrefixedPathInRobustAndLegacy() {
        val pluginConfig = mapOf<String, Any>(Constants.KEY_PACKAGE_PARAMETER_ENABLED to true)

        val robustRoot = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val robustAssets = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        robustRoot.children.add(robustAssets)
        robustAssets.children.add(AssetNode("readme", "assets/readme.txt", MediaType.UNKNOWN, null))
        val robust = generator(robustRoot, pluginConfig, packageName = "my_pkg").generate()
        assertTrue(robust, robust.contains("static const String readme = 'packages/my_pkg/assets/readme.txt';"))

        val legacyRoot = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val legacyAssets = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        legacyRoot.children.add(legacyAssets)
        legacyAssets.children.add(AssetNode("readme", "assets/readme.txt", MediaType.UNKNOWN, null))
        val legacy =
            generator(legacyRoot, pluginConfig + ("style" to "legacy"), packageName = "my_pkg").generate()
        assertTrue(legacy, legacy.contains("= 'packages/my_pkg/assets/readme.txt';"))
    }

    // ---------- flutter_svg 模板选择 ----------

    @Test
    fun testFlutterSvgTemplateSelectionPrefersDependencyVersion() {
        // 项目锁在 1.x: 即使 SDK 很新也用 1.x 模板
        assertFalse(DartClassGenerator.shouldUseFlutterSvg2("^1.1.6", SemanticVersion(3, 44, 0)))
        assertTrue(DartClassGenerator.shouldUseFlutterSvg2("^2.0.10", SemanticVersion(2, 0, 0)))
        assertTrue(DartClassGenerator.shouldUseFlutterSvg2(">=2.0.0 <3.0.0", null))
        // 没有依赖信息时回退到 SDK 版本
        assertFalse(DartClassGenerator.shouldUseFlutterSvg2(null, SemanticVersion(2, 0, 0)))
        assertTrue(DartClassGenerator.shouldUseFlutterSvg2(null, SemanticVersion(3, 0, 0)))
        // 什么都拿不到: 默认 2.x (与自动添加的依赖一致)
        assertTrue(DartClassGenerator.shouldUseFlutterSvg2(null, null))
    }

    @Test
    fun testSvg2TemplateHasColorFilterAndNoDeprecatedColor() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assets = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assets)
        assets.children.add(AssetNode("logo", "assets/logo.svg", MediaType.SVG, null))

        val code =
            DartClassGenerator(
                root, config(emptyMap()), hasSvg = true, hasSvgDep = true, hasLottie = false,
                hasLottieDep = false, hasRive = false, hasRiveDep = false,
                flutterVersion = null, flutterSvgVersion = "^2.2.3"
            ).generate()
        assertTrue(code, code.contains("ColorFilter? colorFilter,"))
        assertTrue(code, code.contains("colorFilter: colorFilter,"))
        assertFalse(code, code.contains("cacheColorFilter"))
    }

    @Test
    fun testGeneratedCodeDoesNotImportLottieInternals() {
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assets = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assets)
        assets.children.add(AssetNode("anim", "assets/anim.json", MediaType.LOTTIE, null))

        val code =
            DartClassGenerator(
                root, config(emptyMap()), hasSvg = false, hasSvgDep = false, hasLottie = true,
                hasLottieDep = true, hasRive = false, hasRiveDep = false, flutterVersion = null
            ).generate()
        assertTrue(code.contains("import 'package:lottie/lottie.dart';"))
        assertFalse(code, code.contains("package:lottie/src/"))
        assertFalse(code, code.contains("implementation_imports"))
        // 这两个 typedef 没有从 lottie.dart 导出, 模板里不能直接用名字
        assertFalse(code, code.contains("LottieFrameBuilder"))
        assertFalse(code, code.contains("WarningCallback"))
        assertTrue(code, code.contains("Widget Function(BuildContext context, Widget child, LottieComposition? composition)? frameBuilder,"))
        assertTrue(code, code.contains("void Function(String)? onWarning,"))
    }

    // ---------- Flutter 版本解析 ----------

    @Test
    fun testParseVersionAndConstraint() {
        assertEquals(SemanticVersion(3, 44, 9), FlutterVersionHelper.parseVersion("3.44.9"))
        assertEquals(SemanticVersion(3, 45, 0), FlutterVersionHelper.parseVersion("3.45.0-0.1.pre"))
        assertEquals(SemanticVersion(3, 0, 0), FlutterVersionHelper.parseVersionConstraint(">=3.0.0 <4.0.0"))
        assertEquals(SemanticVersion(2, 5, 0), FlutterVersionHelper.parseVersionConstraint("^2.5.0"))
        assertNull(FlutterVersionHelper.parseVersionConstraint("any"))
        assertEquals(
            SemanticVersion(3, 10, 0),
            FlutterVersionHelper.parseVersionFromCommandOutput("Flutter 3.10.0 • channel stable • https://...")
        )
    }

    @Test
    fun testReadVersionFromFlutterVersionJson() {
        val sdk = File.createTempFile("flutter-sdk", "").apply { delete(); mkdirs() }
        try {
            val json = File(sdk, "bin/cache/flutter.version.json")
            json.parentFile.mkdirs()
            json.writeText(
                """{"frameworkVersion": "3.44.9", "channel": "stable", "engineRevision": "abc"}"""
            )
            assertEquals(SemanticVersion(3, 44, 9), FlutterVersionHelper.readVersionFromSdkFiles(sdk.path))

            // 旧版 SDK 只有根目录 version 文件
            json.delete()
            File(sdk, "version").writeText("3.3.10\n")
            assertEquals(SemanticVersion(3, 3, 10), FlutterVersionHelper.readVersionFromSdkFiles(sdk.path))
        } finally {
            sdk.deleteRecursively()
        }
    }

    // ---------- 配置一致性 ----------

    @Test
    fun testAutoDetectionDefaultsToTrueWhenPluginBlockExists() {
        val config =
            PubspecConfig.fromMap(
                mapOf(Constants.KEY_CONFIGURATION_MAP to emptyMap<String, Any>())
            )
        assertTrue(config.hasPluginConfig)
        assertTrue(config.pluginEnabled)
        assertTrue("must match FileHelperNew.isAutoDetectionEnable default", config.autoDetection)
        assertTrue(config.namedWithParent)

        val disabled =
            PubspecConfig.fromMap(
                mapOf(Constants.KEY_CONFIGURATION_MAP to mapOf(Constants.KEY_ENABLE to false))
            )
        assertFalse(disabled.autoDetection)
    }

    @Test
    fun testNamedWithParentChangeTriggersRegeneration() {
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.locationHash).thenReturn("regression_named_with_parent")
        Mockito.`when`(project.name).thenReturn("TestProject")
        val modulePath = "/path/to/module"

        val base =
            PubspecConfig.fromMap(
                mapOf(Constants.KEY_CONFIGURATION_MAP to mapOf("style" to "legacy"))
            )
        PubspecConfigCache.put(project, modulePath, base)
        assertTrue(PubspecConfigCache.hasChanged(project, modulePath, base.copy(namedWithParent = false)))
        assertFalse(PubspecConfigCache.hasChanged(project, modulePath, base.copy()))
    }

    // ---------- 模块路径过滤相对项目根 ----------

    @Test
    fun testPubspecExclusionIsRelativeToProjectRoot() {
        val base = "/Users/me/build/my_app"
        assertTrue(FileHelperNew.isEligibleModulePubspecPath("$base/pubspec.yaml", base))
        assertTrue(FileHelperNew.isEligibleModulePubspecPath("$base/packages/ui/pubspec.yaml", base))
        assertFalse(FileHelperNew.isEligibleModulePubspecPath("$base/build/pubspec.yaml", base))
        assertFalse(FileHelperNew.isEligibleModulePubspecPath("$base/.dart_tool/x/pubspec.yaml", base))
        // 没有项目根信息时保持旧行为
        assertFalse(FileHelperNew.isEligibleModulePubspecPath("$base/pubspec.yaml", null))
    }

    // ---------- VFS 粗筛与多模块匹配 ----------

    @Test
    fun testMayAffectAssetsFiltersIrrelevantEvents() {
        assertTrue(VfsAssetListener.mayAffectAssets("/project/assets/a.png"))
        assertTrue(VfsAssetListener.mayAffectAssets("/project/assets/icons"))
        assertTrue("no extension is fine", VfsAssetListener.mayAffectAssets("/project/assets/LICENSE"))
        assertTrue("content roots may live outside the project dir", VfsAssetListener.mayAffectAssets("/other/pkg/assets/a.png"))
        assertTrue("build may be a legit asset folder name", VfsAssetListener.mayAffectAssets("/project/assets/build/a.png"))
        assertFalse(VfsAssetListener.mayAffectAssets("/project/lib/main.dart"))
        assertFalse(VfsAssetListener.mayAffectAssets("/project/lib/generated/assets.dart"))
        assertFalse(VfsAssetListener.mayAffectAssets("/project/.dart_tool/x.png"))
        assertFalse(VfsAssetListener.mayAffectAssets("/project/.git/objects/ab"))
        assertFalse(VfsAssetListener.mayAffectAssets(""))
    }

    @Test
    fun testMatchModulesReturnsEveryAffectedModule() {
        val listener = VfsAssetListener(mockProject())
        val moduleA = listenerConfig("A", "/project/a/assets")
        val moduleB = listenerConfig("B", "/project/b/assets")
        val moduleC = listenerConfig("C", "/project/c/assets")

        val eventA = Mockito.mock(VFileEvent::class.java)
        Mockito.`when`(eventA.path).thenReturn("/project/a/assets/x.png")
        val eventB = Mockito.mock(VFileEvent::class.java)
        Mockito.`when`(eventB.path).thenReturn("/project/b/assets/y.png")

        val matched = listener.matchModules(listOf(moduleA, moduleB, moduleC), listOf(eventA, eventB))
        assertEquals(listOf("A", "B"), matched.map { it.module.name })
    }

    // ---------- helpers ----------

    private fun config(pluginConfig: Map<String, Any>, packageName: String? = null): ModulePubSpecConfig {
        val mock = Mockito.mock(ModulePubSpecConfig::class.java)
        val map = mutableMapOf<String, Any>(Constants.KEY_CONFIGURATION_MAP to pluginConfig)
        if (packageName != null) map["name"] = packageName
        Mockito.`when`(mock.map).thenReturn(map)
        return mock
    }

    private fun generator(
        root: AssetNode,
        pluginConfig: Map<String, Any> = emptyMap(),
        packageName: String? = null
    ): DartClassGenerator {
        return DartClassGenerator(
            root, config(pluginConfig, packageName), hasSvg = false, hasSvgDep = false,
            hasLottie = false, hasLottieDep = false, hasRive = false, hasRiveDep = false,
            flutterVersion = null
        )
    }

    private fun mockProject(): Project {
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.name).thenReturn("TestProject")
        return project
    }

    private fun listenerConfig(moduleName: String, assetDirPath: String): ModulePubSpecConfig {
        val config = Mockito.mock(ModulePubSpecConfig::class.java)
        Mockito.`when`(config.map)
            .thenReturn(
                mapOf(Constants.KEY_CONFIGURATION_MAP to mapOf(Constants.KEY_ENABLE to true))
            )
        val module = Mockito.mock(Module::class.java)
        Mockito.`when`(module.name).thenReturn(moduleName)
        Mockito.`when`(config.module).thenReturn(module)
        val dir = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(dir.path).thenReturn(assetDirPath)
        Mockito.`when`(dir.isDirectory).thenReturn(true)
        Mockito.`when`(config.assetVFiles).thenReturn(listOf(dir))
        return config
    }
}
