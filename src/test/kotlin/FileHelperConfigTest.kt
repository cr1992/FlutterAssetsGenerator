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

    private fun createMockConfig(map: Map<String, Any>): ModulePubSpecConfig {
        val mockConfig = Mockito.mock(ModulePubSpecConfig::class.java)
        Mockito.`when`(mockConfig.map).thenReturn(map)
        return mockConfig
    }
}
