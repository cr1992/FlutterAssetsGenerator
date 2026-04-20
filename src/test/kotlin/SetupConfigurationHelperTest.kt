package com.crzsc.plugin.test

import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.crzsc.plugin.utils.SetupConfigurationHelper
import io.flutter.pub.PubRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun mockConfig(moduleRootPath: String): ModulePubSpecConfig {
        val config = Mockito.mock(ModulePubSpecConfig::class.java)
        val pubRoot = Mockito.mock(PubRoot::class.java)
        Mockito.`when`(pubRoot.path).thenReturn(moduleRootPath)
        Mockito.`when`(config.pubRoot).thenReturn(pubRoot)
        return config
    }
}
