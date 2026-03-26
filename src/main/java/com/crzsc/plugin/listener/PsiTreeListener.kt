package com.crzsc.plugin.listener

import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelperNew
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.Alarm
import com.intellij.util.castSafelyTo

class PsiTreeListener(private val project: Project) : PsiTreeChangeListener {
    private val fileGenerator = FileGenerator(project)

    // 使用 IntelliJ Alarm 工具类进行防抖处理，确保在 SWING 线程执行
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

    // 防抖窗口内收集的待处理事件
    internal val pendingEvents = mutableListOf<PsiTreeChangeEvent>()

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {}

    override fun childReplaced(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {}

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {}

    override fun childMoved(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {}

    override fun childAdded(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}

    internal fun shouldHandleConfig(config: com.crzsc.plugin.utils.ModulePubSpecConfig): Boolean {
        return FileHelperNew.hasPluginConfig(config) &&
                FileHelperNew.isPluginEnabled(config) &&
                FileHelperNew.isAutoDetectionEnable(config)
    }

    internal fun isDirectAssetChild(
        config: com.crzsc.plugin.utils.ModulePubSpecConfig,
        parentDir: VirtualFile?,
        changedFile: Any
    ): Boolean {
        if (parentDir == null) return false
        return config.assetVFiles.any { assetFile ->
            if (assetFile.isDirectory) {
                assetFile == parentDir
            } else {
                assetFile == changedFile
            }
        }
    }

    private fun handleEvent(event: PsiTreeChangeEvent) {
        // 收集事件，防抖结束后统一处理，避免每次事件都调用 getAssets()
        synchronized(pendingEvents) {
            pendingEvents.add(event)
        }
        alarm.cancelAllRequests()
        alarm.addRequest(
            {
                if (!project.isDisposed) {
                    processPendingEvents()
                }
            },
            300
        )
    }

    internal fun processPendingEvents() {
        val events: List<PsiTreeChangeEvent>
        synchronized(pendingEvents) {
            events = pendingEvents.toList()
            pendingEvents.clear()
        }

        val assets = FileHelperNew.getAssets(project)
        for (config in assets) {
            if (!shouldHandleConfig(config)) {
                continue
            }

            for (event in events) {
                event.child?.let { changedFile ->
                    val parentDir = changedFile.parent.castSafelyTo<PsiDirectory>()?.virtualFile
                    if (isDirectAssetChild(config, parentDir, changedFile)) {
                        fileGenerator.generateOne(config)
                        return
                    }
                }
            }
        }
    }
}
