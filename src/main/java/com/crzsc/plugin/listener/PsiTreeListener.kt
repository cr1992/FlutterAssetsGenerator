package com.crzsc.plugin.listener

import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelperNew
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.castSafelyTo
import java.util.*
import kotlin.concurrent.timerTask

class PsiTreeListener(private val project: Project) : PsiTreeChangeListener {
    private val fileGenerator = FileGenerator(project)

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
        val assets = FileHelperNew.getAssets(project)
        for (config in assets) {
            if (FileHelperNew.isAutoDetectionEnable(config)) {
                // 该Module开启了自动检测
                event.child?.let { changedFile ->
                    changedFile.parent.castSafelyTo<PsiDirectory>()?.let { dir ->
                        //assets目录发生改变 这里延迟生成避免报错
                        for (file in config.assetVFiles) {
                            if (dir.virtualFile.path.startsWith(file.path)) {
                                Timer().schedule(timerTask {
                                    fileGenerator.generateOne(config)
                                }, 300)
                                break
                            }
                        }
                    }
                }
            }
        }
    }
}