package com.crzsc.plugin.actions

import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelper.shouldActivateFor
import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

class GenerateAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (shouldActivateFor(project!!)) {
            FileGenerator(project).generate()
        } else {
            showNotify("This project is not the flutter project")
        }
    }
}