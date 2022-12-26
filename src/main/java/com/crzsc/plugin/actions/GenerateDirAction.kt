package com.crzsc.plugin.actions

import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

/**
 * 同步文件 文件夹到yaml
 */
class GenerateDirAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (FileHelperNew.shouldActivateFor(project!!)) {
            val file = e.getData(PlatformDataKeys.VIRTUAL_FILE)
            if (file != null) {
                FileGenerator(project).buildYaml(file)
            }
        } else {
            showNotify("This project is not the flutter project")
        }
    }
}