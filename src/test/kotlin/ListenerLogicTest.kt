package com.crzsc.plugin.test

import com.crzsc.plugin.cache.PubspecConfig
import com.crzsc.plugin.listener.PubspecDocumentListener
import com.crzsc.plugin.listener.VfsAssetListener
import com.crzsc.plugin.utils.Constants
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.junit.Assert.assertEquals
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
    fun testVfsAssetListenerConfigAndAssetMatchingLogic() {
        val listener = VfsAssetListener(mockProject())
        val enabledConfig = mockConfig(hasPluginConfig = true, enable = true, autoDetection = true)
        val disabledConfig = mockConfig(hasPluginConfig = true, enable = false, autoDetection = true)

        assertTrue(listener.shouldHandleConfig(enabledConfig))
        assertFalse(listener.shouldHandleConfig(disabledConfig))

        val moduleRoot = mockVirtualFile("/project", true, null)
        val assetDir = mockVirtualFile("/project/assets", true, moduleRoot)
        val envFile = mockVirtualFile("/project/.test.env", false, moduleRoot)
        Mockito.`when`(enabledConfig.assetVFiles).thenReturn(listOf(assetDir, envFile))

        assertTrue(listener.isAssetChange(enabledConfig, "/project/assets"))
        assertTrue(listener.isAssetChange(enabledConfig, "/project/assets/icons/home.png"))
        assertTrue(listener.isAssetChange(enabledConfig, "/project/.test.env"))
        assertFalse(listener.isAssetChange(enabledConfig, "/project/lib/main.dart"))
    }

    @Test
    fun testCollectCandidatePathsUsesEventPath() {
        val listener = VfsAssetListener(mockProject())
        val event = Mockito.mock(VFileEvent::class.java)
        Mockito.`when`(event.path).thenReturn("/project/assets/icons/home.png")
        Mockito.`when`(event.file).thenReturn(null)

        val candidates = listener.collectCandidatePaths(event)
        assertEquals(listOf("/project/assets/icons/home.png"), candidates)
    }

    @Test
    fun testPendingEventsCollectedCorrectly() {
        val listener = VfsAssetListener(mockProject())

        // 模拟多个事件加入 pendingEvents
        val event1 = Mockito.mock(VFileEvent::class.java)
        val event2 = Mockito.mock(VFileEvent::class.java)
        val event3 = Mockito.mock(VFileEvent::class.java)

        listener.pendingEvents.add(event1)
        listener.pendingEvents.add(event2)
        listener.pendingEvents.add(event3)

        assertEquals(3, listener.pendingEvents.size)

        // 验证事件按顺序收集
        assertTrue(listener.pendingEvents[0] === event1)
        assertTrue(listener.pendingEvents[2] === event3)
    }

    @Test
    fun testPendingEventsClearAndReuse() {
        val listener = VfsAssetListener(mockProject())

        // 加入一批事件
        repeat(10) {
            listener.pendingEvents.add(Mockito.mock(VFileEvent::class.java))
        }
        assertEquals(10, listener.pendingEvents.size)

        // 模拟 processPendingEvents 中的 toList + clear 逻辑
        val snapshot: List<VFileEvent>
        synchronized(listener.pendingEvents) {
            snapshot = listener.pendingEvents.toList()
            listener.pendingEvents.clear()
        }
        assertEquals(10, snapshot.size)
        assertEquals(0, listener.pendingEvents.size)

        // 清空后新事件仍可正常收集
        listener.pendingEvents.add(Mockito.mock(VFileEvent::class.java))
        assertEquals(1, listener.pendingEvents.size)
    }

    @Test
    fun testCollectCandidatePathsForRenameIncludesOldAndNewPath() {
        val listener = VfsAssetListener(mockProject())
        val parent = mockVirtualFile("/project/assets/icons", true, null)
        val file = mockVirtualFile("/project/assets/icons/new.png", false, parent)
        val event = Mockito.mock(VFilePropertyChangeEvent::class.java)

        Mockito.`when`(event.path).thenReturn("/project/assets/icons/new.png")
        Mockito.`when`(event.file).thenReturn(file)
        Mockito.`when`(event.propertyName).thenReturn(VirtualFile.PROP_NAME)
        Mockito.`when`(event.oldValue).thenReturn("old.png")
        Mockito.`when`(event.newValue).thenReturn("new.png")

        val candidates = listener.collectCandidatePaths(event)
        assertTrue(candidates.contains("/project/assets/icons/old.png"))
        assertTrue(candidates.contains("/project/assets/icons/new.png"))
    }

    @Test
    fun testCollectCandidatePathsForMoveIncludesOldAndNewPath() {
        val listener = VfsAssetListener(mockProject())
        val oldParent = mockVirtualFile("/project/assets/icons", true, null)
        val newParent = mockVirtualFile("/project/assets/widget", true, null)
        val file = mockVirtualFile("/project/assets/widget/moved.png", false, newParent)
        val event = Mockito.mock(VFileMoveEvent::class.java)

        Mockito.`when`(event.path).thenReturn("/project/assets/widget/moved.png")
        Mockito.`when`(event.file).thenReturn(file)
        Mockito.`when`(event.oldParent).thenReturn(oldParent)
        Mockito.`when`(file.name).thenReturn("moved.png")

        val candidates = listener.collectCandidatePaths(event)
        assertTrue(candidates.contains("/project/assets/widget/moved.png"))
        assertTrue(candidates.contains("/project/assets/icons/moved.png"))
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
            leafType = "class",
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
        val module = Mockito.mock(Module::class.java)
        Mockito.`when`(module.name).thenReturn("TestModule")
        Mockito.`when`(config.module).thenReturn(module)
        Mockito.`when`(config.assetVFiles).thenReturn(emptyList())
        return config
    }

    private fun mockVirtualFile(
        path: String,
        isDirectory: Boolean,
        parent: VirtualFile?
    ): VirtualFile {
        val file = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(file.path).thenReturn(path)
        Mockito.`when`(file.isDirectory).thenReturn(isDirectory)
        Mockito.`when`(file.parent).thenReturn(parent)
        return file
    }
}
