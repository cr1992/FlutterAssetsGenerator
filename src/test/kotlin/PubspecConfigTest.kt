package com.crzsc.plugin.test

import com.crzsc.plugin.cache.PubspecConfig
import com.crzsc.plugin.cache.PubspecConfigCache
import com.intellij.openapi.project.Project
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class PubspecConfigTest {

    @Test
    fun testFromMap() {
        val map =
                mapOf(
                        "flutter" to mapOf("assets" to listOf("assets/images/", "assets/icons/")),
                        "dependencies" to mapOf("flutter_svg" to "^1.0.0", "lottie" to "^2.0.0"),
                        "environment" to mapOf("flutter" to ">=3.0.0", "sdk" to ">=2.17.0"),
                        "flutter_assets_generator" to
                                mapOf(
                                        "auto_detection" to true,
                                        "output_dir" to "lib/gen",
                                        "class_name" to "MyAssets",
                                        "output_filename" to "my_assets",
                                        "filename_split_pattern" to "_",
                                        "path_ignore" to listOf("ignore_me"),
                                        "style" to "legacy"
                                )
                )

        val config = PubspecConfig.fromMap(map)

        // Verify assets
        assertEquals(2, config.assetPaths.size)
        assertEquals("assets/images/", config.assetPaths[0])

        // Verify dependencies
        assertEquals("^1.0.0", config.flutterSvgVersion)
        assertEquals("^2.0.0", config.lottieVersion)

        // Verify environment
        assertEquals(">=3.0.0", config.flutterVersion)
        assertEquals(">=2.17.0", config.dartVersion)

        // Verify plugin config
        assertTrue(config.autoDetection)
        assertEquals("lib/gen", config.outputDir)
        assertEquals("MyAssets", config.className)
        assertEquals("my_assets", config.outputFilename)
        assertEquals("_", config.filenameSplitPattern)
        assertEquals(1, config.pathIgnore.size)
        assertEquals("ignore_me", config.pathIgnore[0])
        assertEquals("legacy", config.generationStyle)
    }

    @Test
    fun testFromMapDefaults() {
        val map = emptyMap<String, Any>()
        val config = PubspecConfig.fromMap(map)

        assertTrue(config.assetPaths.isEmpty())
        assertNull(config.flutterSvgVersion)
        assertNull(config.lottieVersion)
        assertFalse(config.autoDetection)
        assertEquals("generated", config.outputDir)
        assertEquals("Assets", config.className)
        assertEquals("assets", config.outputFilename)
        assertEquals("[-_]", config.filenameSplitPattern)
        assertEquals("robust", config.generationStyle)
    }
    @Test
    fun testHasChangedDepsIgnored() {
        // Mock Project
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.locationHash).thenReturn("test_project_hash")
        Mockito.`when`(project.name).thenReturn("TestProject")

        val modulePath = "/path/to/module"

        // Base config
        val baseConfig = createBaseConfig()

        // 1. Initial put
        PubspecConfigCache.put(project, modulePath, baseConfig)

        // 2. New config with ONLY dependency changes
        val newConfigDepsChanged =
                baseConfig.copy(
                        flutterSvgVersion = "^1.1.0", // Changed
                        lottieVersion = "^2.1.0" // Changed
                )

        // Should return FALSE because dependency changes are ignored
        assertFalse(
                "Dependency version changes should be ignored",
                PubspecConfigCache.hasChanged(project, modulePath, newConfigDepsChanged)
        )
    }

    @Test
    fun testHasChangedContentChanged() {
        // Mock Project
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.locationHash).thenReturn("test_project_hash")
        Mockito.`when`(project.name).thenReturn("TestProject")

        val modulePath = "/path/to/module"

        // Base config
        val baseConfig = createBaseConfig()
        PubspecConfigCache.put(project, modulePath, baseConfig)

        // 1. Asset paths changed -> TRUE
        assertTrue(
                PubspecConfigCache.hasChanged(
                        project,
                        modulePath,
                        baseConfig.copy(assetPaths = listOf("assets/new/"))
                )
        )

        // 2. Class name changed -> TRUE
        assertTrue(
                PubspecConfigCache.hasChanged(
                        project,
                        modulePath,
                        baseConfig.copy(className = "NewAssets")
                )
        )

        // 3. Auto detection changed -> TRUE
        assertTrue(
                PubspecConfigCache.hasChanged(
                        project,
                        modulePath,
                        baseConfig.copy(autoDetection = !baseConfig.autoDetection)
                )
        )
    }

    private fun createBaseConfig(): PubspecConfig {
        return PubspecConfig(
                assetPaths = listOf("assets/"),
                flutterSvgVersion = "^1.0.0",
                lottieVersion = "^2.0.0",
                flutterVersion = ">=3.0.0",
                dartVersion = ">=2.17.0",
                autoDetection = true,
                outputDir = "generated",
                className = "Assets",
                outputFilename = "assets",
                filenameSplitPattern = "[-_]",
                pathIgnore = emptyList(),
                generationStyle = "robust"
        )
    }
}
