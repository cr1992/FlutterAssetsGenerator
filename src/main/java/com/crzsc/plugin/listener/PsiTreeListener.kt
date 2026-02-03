package com.crzsc.plugin.listener

import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelperNew
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.castSafelyTo
import com.intellij.util.Alarm

class PsiTreeListener(private val project: Project) : PsiTreeChangeListener {
    private val fileGenerator = FileGenerator(project)
    
    // 使用 IntelliJ Alarm 工具类进行防抖处理，确保在 SWING 线程执行
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
        handleEvent(event)
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
    }

    private fun handleEvent(event: PsiTreeChangeEvent) {
        // 注意: pubspec.yaml 的变更现在由 PubspecDocumentListener 处理
        // 这里只监听 assets 目录下的文件变更
        
        val assets = FileHelperNew.getAssets(project)
        for (config in assets) {
            if (FileHelperNew.isAutoDetectionEnable(config)) {
                event.child?.let { changedFile ->
                    val parentDir = changedFile.parent.castSafelyTo<PsiDirectory>()?.virtualFile
                    if (parentDir != null) {
                        for (assetFile in config.assetVFiles) {
                            // 判断变更文件是否位于配置的 assets 目录下
                            if (parentDir.path.startsWith(assetFile.path)) {
                                // 资源文件变更响应较快,防抖时间设置为 300ms
                                alarm.cancelAllRequests()
                                alarm.addRequest({
                                    if (!project.isDisposed) {
                                        fileGenerator.generateOne(config)
                                    }
                                }, 300)
                                return
                            }
                        }
                    }
                }
            }
        }
    }
}