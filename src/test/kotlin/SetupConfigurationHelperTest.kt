package com.crzsc.plugin.test

import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.crzsc.plugin.utils.SetupConfigurationHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.flutter.pub.PubRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class SetupConfigurationHelperTest {

    @Test
    fun testFindTargetConfigRequiresExactModuleRootPath() {
        val rootModule = mockConfig("/project")
        val packageModule = mockConfig("/project/packages/demo")

        val result =
            SetupConfigurationHelper.findTargetConfig(
                listOf(rootModule, packageModule),
                "/project/packages/demo/lib/src/icon.png"
            )

        assertNull(result)
    }

    @Test
    fun testFindTargetConfigReturnsNullForOutsidePath() {
        val config = mockConfig("/project")

        val result =
            SetupConfigurationHelper.findTargetConfig(
                listOf(config),
                "/another/place/file.txt"
            )

        assertNull(result)
    }

    @Test
    fun testFindTargetConfigReturnsExactModuleRoot() {
        val config = mockConfig("/project/packages/demo")

        val result =
            SetupConfigurationHelper.findTargetConfig(
                listOf(config),
                "/project/packages/demo"
            )

        assertEquals(config, result)
    }

    @Test
    fun testFindTargetConfigSupportsMultipleCandidatePaths() {
        val rootModule = mockConfig("/project")
        val packageModule = mockConfig("/project/packages/demo")

        val result =
            SetupConfigurationHelper.findTargetConfig(
                listOf(rootModule, packageModule),
                listOf("/tmp/irrelevant", "/project/packages/demo")
            )

        assertEquals(packageModule, result)
    }

    @Test
    fun testFindTargetConfigIgnoresNestedChildPathInCandidateList() {
        val packageModule = mockConfig("/project/packages/demo")

        val result =
            SetupConfigurationHelper.findTargetConfig(
                listOf(packageModule),
                listOf("/project/packages/demo/lib", "/project/packages/demo/lib/src")
            )

        assertNull(result)
    }

    @Test
    fun testShouldEnablePackageParameterByDefaultForFlutterApp() {
        val project = mockProject("/project")
        val config =
            mockConfig(
                "/project",
                children =
                    setOf(
                        "lib",
                        "lib/main.dart",
                        "android"
                    )
            )

        assertFalse(
            SetupConfigurationHelper.shouldEnablePackageParameterByDefault(project, config)
        )
    }

    @Test
    fun testShouldEnablePackageParameterByDefaultForAddToAppModule() {
        val project = mockProject("/project")
        val config =
            mockConfig(
                "/project/flutter_module",
                pubspecMap =
                    mapOf(
                        "flutter" to mapOf(
                            "module" to mapOf(
                                "androidX" to true,
                                "androidPackage" to "com.example.host",
                                "iosBundleIdentifier" to "com.example.host"
                            )
                        )
                    ),
                children =
                    setOf(
                        ".android",
                        ".ios"
                    )
            )

        assertFalse(
            SetupConfigurationHelper.shouldEnablePackageParameterByDefault(project, config)
        )
    }

    @Test
    fun testShouldEnablePackageParameterByDefaultForFlutterPackage() {
        val project = mockProject("/project")
        val config =
            mockConfig(
                "/project/packages/demo",
                mapOf("flutter" to emptyMap<String, Any>())
            )

        assertTrue(
            SetupConfigurationHelper.shouldEnablePackageParameterByDefault(project, config)
        )
    }

    @Test
    fun testBuildDefaultConfigurationContentEnablesPackageParameterForFlutterPackage() {
        val project = mockProject("/project")
        val config =
            mockConfig(
                "/project/packages/demo",
                mapOf("flutter" to emptyMap<String, Any>())
            )

        val content = SetupConfigurationHelper.buildDefaultConfigurationContent(project, config)

        assertTrue(content.contains("package_parameter_enabled: true"))
    }

    @Test
    fun testBuildDefaultConfigurationContentKeepsPackageParameterDisabledForFlutterApp() {
        val project = mockProject("/project")
        val config =
            mockConfig(
                "/project",
                children =
                    setOf(
                        "lib",
                        "lib/main.dart",
                        "ios"
                    )
            )

        val content = SetupConfigurationHelper.buildDefaultConfigurationContent(project, config)

        assertTrue(content.contains("package_parameter_enabled: false"))
    }

    @Test
    fun testBuildDefaultConfigurationContentUsesExplicitOverrideWhenProvided() {
        val project = mockProject("/project")
        val config = mockConfig("/project")

        val content =
            SetupConfigurationHelper.buildDefaultConfigurationContent(
                project,
                config,
                packageParameterEnabledOverride = true
            )

        assertTrue(content.contains("package_parameter_enabled: true"))
    }

    @Test
    fun testIsFlutterAppDetectsMainEntryAndPlatformDirectory() {
        val config =
            mockConfig(
                "/project",
                children =
                    setOf(
                        "lib",
                        "lib/main.dart",
                        "android"
                    )
            )
        assertTrue(
            SetupConfigurationHelper.isFlutterApp(config)
        )
    }

    @Test
    fun testIsFlutterAppRejectsPackageWithAssetsOnly() {
        val config =
            mockConfig(
                "/project/packages/demo",
                children =
                    setOf(
                        "assets",
                        "assets/icon.png"
                    ),
                pubspecMap =
                    mapOf(
                        "flutter" to mapOf(
                            "assets" to listOf("assets/")
                        )
                    )
            )
        assertFalse(
            SetupConfigurationHelper.isFlutterApp(config)
        )
    }

    @Test
    fun testIsAddToAppModuleAcceptsHiddenPlatformDirectories() {
        val config =
            mockConfig(
                "/project/flutter_module",
                children =
                    setOf(
                        ".android"
                    )
            )
        assertTrue(
            SetupConfigurationHelper.isAddToAppModule(config)
        )
    }

    @Test
    fun testIsAddToAppModuleAcceptsFlutterModuleBlock() {
        val config =
            mockConfig(
                "/project/flutter_module",
                pubspecMap =
                    mapOf(
                        "flutter" to mapOf(
                            "module" to mapOf("androidPackage" to "com.example.host")
                        )
                    )
            )
        assertTrue(
            SetupConfigurationHelper.isAddToAppModule(config)
        )
    }

    private fun mockConfig(
        moduleRootPath: String,
        pubspecMap: Map<String, Any> = emptyMap(),
        children: Set<String> = emptySet()
    ): ModulePubSpecConfig {
        val config = Mockito.mock(ModulePubSpecConfig::class.java)
        val pubRoot = Mockito.mock(PubRoot::class.java)
        val root = mockVirtualDirectory(moduleRootPath, children)
        Mockito.`when`(pubRoot.path).thenReturn(moduleRootPath)
        Mockito.`when`(pubRoot.root).thenReturn(root)
        Mockito.`when`(config.pubRoot).thenReturn(pubRoot)
        Mockito.`when`(config.map).thenReturn(pubspecMap)
        return config
    }

    private fun mockProject(basePath: String): Project {
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.basePath).thenReturn(basePath)
        return project
    }

    private fun mockVirtualDirectory(rootPath: String, children: Set<String>): VirtualFile {
        val root = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(root.path).thenReturn(rootPath)

        children.forEach { relativePath ->
            if (relativePath.isBlank()) {
                return@forEach
            }
            val child = Mockito.mock(VirtualFile::class.java)
            val normalized = relativePath.trim('/')
            val name = normalized.substringAfterLast('/')
            val isDirectory =
                !name.contains(".") || (name.startsWith(".") && !name.substring(1).contains("."))
            Mockito.`when`(child.path).thenReturn("$rootPath/$normalized")
            Mockito.`when`(child.name).thenReturn(name)
            Mockito.`when`(child.isDirectory).thenReturn(isDirectory)
            Mockito.`when`(root.findFileByRelativePath(normalized)).thenReturn(child)
            if (!normalized.contains("/")) {
                Mockito.`when`(root.findChild(name)).thenReturn(child)
            }
        }

        return root
    }
}
