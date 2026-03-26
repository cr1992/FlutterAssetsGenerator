package com.crzsc.plugin.test

import com.crzsc.plugin.cache.PubspecConfig
import com.crzsc.plugin.listener.PsiTreeListener
import com.crzsc.plugin.listener.PubspecDocumentListener
import com.crzsc.plugin.utils.Constants
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class ListenerLogicTest {

    @Test
    fun testPubspecDocumentListenerTriggerRequiresChangedEnabledConfiguredAutoDetection() {
        val enabledConfig = basePubspecConfig()
        val disabledConfig = enabledConfig.copy(pluginEnabled = false)
        val missingConfig = enabledConfig.copy(hasPluginConfig = false)
        val autoDetectionOff = enabledConfig.copy(autoDetection = false)

        assertTrue(PubspecDocumentListener.shouldTriggerGeneration(enabledConfig, hasChanged = true))
        assertFalse(PubspecDocumentListener.shouldTriggerGeneration(enabledConfig, hasChanged = false))
        assertFalse(PubspecDocumentListener.shouldTriggerGeneration(disabledConfig, hasChanged = true))
        assertFalse(PubspecDocumentListener.shouldTriggerGeneration(missingConfig, hasChanged = true))
        assertFalse(PubspecDocumentListener.shouldTriggerGeneration(autoDetectionOff, hasChanged = true))
    }

    @Test
    fun testPsiTreeListenerConfigAndAssetMatchingLogic() {
        val listener = PsiTreeListener(mockProject())
        val enabledConfig = mockConfig(hasPluginConfig = true, enable = true, autoDetection = true)
        val disabledConfig = mockConfig(hasPluginConfig = true, enable = false, autoDetection = true)

        assertTrue(listener.shouldHandleConfig(enabledConfig))
        assertFalse(listener.shouldHandleConfig(disabledConfig))

        val assetDir = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(assetDir.isDirectory).thenReturn(true)
        Mockito.`when`(enabledConfig.assetVFiles).thenReturn(listOf(assetDir))

        assertTrue(listener.isDirectAssetChild(enabledConfig, assetDir, Mockito.mock(Any::class.java)))
        assertFalse(listener.isDirectAssetChild(enabledConfig, Mockito.mock(VirtualFile::class.java), Mockito.mock(Any::class.java)))
    }

    private fun basePubspecConfig(): PubspecConfig {
        return PubspecConfig(
            assetPaths = listOf("assets/"),
            flutterSvgVersion = null,
            lottieVersion = null,
            flutterVersion = null,
            dartVersion = null,
            hasPluginConfig = true,
            pluginEnabled = true,
            autoDetection = true,
            autoAddDependencies = true,
            outputDir = "generated",
            className = "Assets",
            outputFilename = "assets",
            filenameSplitPattern = "[-_]",
            pathIgnore = emptyList(),
            generationStyle = "robust",
            nameStyle = "camel",
            packageParameterEnabled = false
        )
    }

    private fun mockProject(): Project {
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.name).thenReturn("TestProject")
        return project
    }

    private fun mockConfig(
        hasPluginConfig: Boolean,
        enable: Boolean,
        autoDetection: Boolean
    ): ModulePubSpecConfig {
        val config = Mockito.mock(ModulePubSpecConfig::class.java)
        val map =
            if (hasPluginConfig) {
                mapOf(
                    Constants.KEY_CONFIGURATION_MAP to
                            mapOf(
                                Constants.KEY_ENABLE to enable,
                                Constants.KEY_AUTO_DETECTION to autoDetection
                            )
                )
            } else {
                emptyMap()
            }
        Mockito.`when`(config.map).thenReturn(map)
        Mockito.`when`(config.assetVFiles).thenReturn(emptyList())
        return config
    }
}
