package com.crzsc.plugin.actions

import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelperNew.shouldActivateFor
import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.crzsc.plugin.utils.message
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

/**
 * 生成资源文件 Action
 * 触发所有 Flutter 模块的资源代码生成
 */
class GenerateAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (shouldActivateFor(project!!)) {
            FileGenerator(project).generateAll()
        } else {
            showNotify(message("notFlutterProject"))
        }
    }
}
