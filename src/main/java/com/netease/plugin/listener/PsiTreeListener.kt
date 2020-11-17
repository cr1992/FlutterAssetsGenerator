package com.netease.plugin.listener

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.castSafelyTo
import com.netease.plugin.FileGenerator
import com.netease.plugin.setting.PluginSetting
import com.netease.plugin.utils.FileHelpers
import java.util.*
import kotlin.concurrent.timerTask

class PsiTreeListener(private val project: Project) : PsiTreeChangeListener {
    private val fileGenerator = FileGenerator(project)
    private val timer = Timer()
    private var timerTask: TimerTask? = null

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
        if (!PluginSetting.getInstance().autoDetection) {
            return
        }
        event.child?.let { changedFile ->
            changedFile.parent.castSafelyTo<PsiDirectory>()?.let { dir ->
                //assets目录发生改变
                if (dir.virtualFile.path.startsWith(FileHelpers.getAssetsFolder(project).path)) {
                    timerTask?.cancel()
                    timerTask = timerTask {
                        fileGenerator.generate()
                    }
                    timer.schedule(timerTask, 300)
                }
            }
        }
    }
}