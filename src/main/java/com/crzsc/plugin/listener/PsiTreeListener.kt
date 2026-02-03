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
        val file = event.file ?: event.child?.containingFile
        val virtualFile = file?.virtualFile ?: return

        // 1. 监听 pubspec.yaml 变更
        // 当配置文件发生改变（如修改了 auto_detection 配置，或者增删了 flutter_svg 依赖），触发重新生成
        if (virtualFile.name == "pubspec.yaml") {
            val assets = FileHelperNew.getAssets(project)
            for (config in assets) {
                if (config.pubRoot.pubspec == virtualFile) {
                    if (FileHelperNew.isAutoDetectionEnable(config)) {
                        // 使用 Alarm 取消之前的请求，重新延迟执行，实现防抖 (1秒)
                        alarm.cancelAllRequests()
                        alarm.addRequest({
                            if (!project.isDisposed) {
                                fileGenerator.generateOne(config)
                            }
                        }, 1000)
                    }
                    return
                }
            }
        }

        // 2. 监听 assets 目录下的文件变更
        val assets = FileHelperNew.getAssets(project)
        for (config in assets) {
            if (FileHelperNew.isAutoDetectionEnable(config)) {
                event.child?.let { changedFile ->
                    val parentDir = changedFile.parent.castSafelyTo<PsiDirectory>()?.virtualFile
                    if (parentDir != null) {
                        for (assetFile in config.assetVFiles) {
                            // 判断变更文件是否位于配置的 assets 目录下
                            if (parentDir.path.startsWith(assetFile.path)) {
                                // 资源文件变更响应较快，防抖时间设置为 300ms
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