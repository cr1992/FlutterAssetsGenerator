package com.crzsc.plugin.test

import com.crzsc.plugin.utils.Constants
import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class FileGeneratorLogicTest {

    @Test
    fun testFilterEnabledConfigsOnlyKeepsConfiguredEnabledModulesWithAssets() {
        val generator = FileGenerator(mockProject())
        val enabled = mockConfig(hasPluginConfig = true, enable = true, withAssets = true)
        val disabled = mockConfig(hasPluginConfig = true, enable = false, withAssets = true)
        val missingConfig = mockConfig(hasPluginConfig = false, enable = false, withAssets = true)
        val emptyAssets = mockConfig(hasPluginConfig = true, enable = true, withAssets = false)

        val result =
            generator.filterEnabledConfigs(
                listOf(enabled, disabled, missingConfig, emptyAssets)
            )

        assertEquals(1, result.size)
        assertTrue(result.contains(enabled))
    }

    @Test
    fun testShouldShowSetupPromptWhenNoEnabledConfigsRemain() {
        val generator = FileGenerator(mockProject())

        assertTrue(generator.shouldShowSetupPrompt(emptyList()))
        assertFalse(generator.shouldShowSetupPrompt(listOf(mockConfig(true, true, true))))
    }

    private fun mockProject(): Project {
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.name).thenReturn("TestProject")
        return project
    }

    private fun mockConfig(
        hasPluginConfig: Boolean,
        enable: Boolean,
        withAssets: Boolean
    ): ModulePubSpecConfig {
        val config = Mockito.mock(ModulePubSpecConfig::class.java)
        val map =
            if (hasPluginConfig) {
                mapOf(
                    Constants.KEY_CONFIGURATION_MAP to
                            mapOf(Constants.KEY_ENABLE to enable)
                )
            } else {
                emptyMap()
            }
        Mockito.`when`(config.map).thenReturn(map)
        Mockito.`when`(config.assetVFiles)
            .thenReturn(
                if (withAssets) {
                    listOf(Mockito.mock(VirtualFile::class.java))
                } else {
                    emptyList()
                }
            )
        return config
    }
}
