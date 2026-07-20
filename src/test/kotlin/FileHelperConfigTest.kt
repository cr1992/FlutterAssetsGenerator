package com.crzsc.plugin.test

import com.crzsc.plugin.utils.Constants
import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.ModulePubSpecConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class FileHelperConfigTest {

    @Test
    fun testMissingPluginConfigIsTreatedAsDisabled() {
        val config = createMockConfig(emptyMap())

        assertFalse(FileHelperNew.hasPluginConfig(config))
        assertFalse(FileHelperNew.isPluginEnabled(config))
        assertFalse(FileHelperNew.isAutoDetectionEnable(config))
    }

    @Test
    fun testPluginConfigDefaultsToEnabled() {
        val config =
            createMockConfig(
                mapOf(
                    Constants.KEY_CONFIGURATION_MAP to emptyMap<String, Any>()
                )
            )

        assertTrue(FileHelperNew.hasPluginConfig(config))
        assertTrue(FileHelperNew.isPluginEnabled(config))
        assertTrue(FileHelperNew.isAutoDetectionEnable(config))
    }

    @Test
    fun testEnableFalseDisablesAutoDetection() {
        val config =
            createMockConfig(
                mapOf(
                    Constants.KEY_CONFIGURATION_MAP to
                            mapOf(
                                Constants.KEY_ENABLE to false,
                                Constants.KEY_AUTO_DETECTION to true
                            )
                )
            )

        assertTrue(FileHelperNew.hasPluginConfig(config))
        assertFalse(FileHelperNew.isPluginEnabled(config))
        assertFalse(FileHelperNew.isAutoDetectionEnable(config))
    }

    @Test
    fun testLeafTypeDefaultsToClassAndSupportsString() {
        val defaultConfig =
            createMockConfig(
                mapOf(
                    Constants.KEY_CONFIGURATION_MAP to emptyMap<String, Any>()
                )
            )
        val stringConfig =
            createMockConfig(
                mapOf(
                    Constants.KEY_CONFIGURATION_MAP to
                            mapOf(Constants.KEY_LEAF_TYPE to Constants.LEAF_TYPE_STRING)
                )
            )

        assertEquals(Constants.LEAF_TYPE_CLASS, FileHelperNew.getLeafType(defaultConfig))
        assertEquals(Constants.LEAF_TYPE_STRING, FileHelperNew.getLeafType(stringConfig))
    }

    @Test
    fun testShouldIncludePubspecExcludesGeneratedFlutterMirrorDirectories() {
        assertFalse(
            FileHelperNew.shouldIncludePubspec(
                "/repo/packages/demo/example/linux/flutter/ephemeral/.plugin_symlinks/path_provider/pubspec.yaml"
            )
        )
        assertFalse(
            FileHelperNew.shouldIncludePubspec(
                "/repo/packages/demo/example/windows/flutter/ephemeral/pubspec.yaml"
            )
        )
        assertFalse(
            FileHelperNew.shouldIncludePubspec(
                "/repo/.dart_tool/package_config/pubspec.yaml"
            )
        )
    }

    @Test
    fun testShouldIncludePubspecKeepsRealMonorepoModuleRoots() {
        assertTrue(
            FileHelperNew.shouldIncludePubspec(
                "/repo/packages/demo/pubspec.yaml"
            )
        )
        assertTrue(
            FileHelperNew.shouldIncludePubspec(
                "/repo/apps/app_main/pubspec.yaml"
            )
        )
    }

    @Test
    fun testIsFlutterPubspecMapAcceptsFlutterSection() {
        assertTrue(
            FileHelperNew.isFlutterPubspecMap(
                mapOf(
                    "name" to "demo",
                    "flutter" to emptyMap<String, Any>()
                )
            )
        )
    }

    @Test
    fun testIsFlutterPubspecMapAcceptsFlutterSdkDependency() {
        assertTrue(
            FileHelperNew.isFlutterPubspecMap(
                mapOf(
                    "name" to "demo",
                    "dependencies" to
                            mapOf(
                                "flutter" to mapOf("sdk" to "flutter")
                            )
                )
            )
        )
    }

    @Test
    fun testIsFlutterPubspecMapRejectsPureDartPackage() {
        assertFalse(
            FileHelperNew.isFlutterPubspecMap(
                mapOf(
                    "name" to "demo",
                    "dependencies" to
                            mapOf(
                                "collection" to "^1.0.0"
                            )
                )
            )
        )
    }

    private fun createMockConfig(map: Map<String, Any>): ModulePubSpecConfig {
        val mockConfig = Mockito.mock(ModulePubSpecConfig::class.java)
        Mockito.`when`(mockConfig.map).thenReturn(map)
        return mockConfig
    }
}
