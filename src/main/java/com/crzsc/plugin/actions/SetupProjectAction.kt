package com.crzsc.plugin.actions

import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.crzsc.plugin.utils.SetupConfigurationHelper
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

/** 一键配置项目 Action 自动在 pubspec.yaml 中添加 flutter_assets_generator 配置块 */
class SetupProjectAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (!FileHelperNew.shouldActivateFor(project!!)) {
            showNotify("This is not a Flutter project")
            return
        }

        // 获取所有 Flutter 模块的 pubspec.yaml
        val assets = FileHelperNew.getAssets(project)
        if (assets.isEmpty()) {
            showNotify("No Flutter modules found in project")
            return
        }

        var configuredCount = 0
        for (config in assets) {
            if (SetupConfigurationHelper.addDefaultConfiguration(project, config)) {
                configuredCount++
            }
        }

        if (configuredCount > 0) {
            showNotify("Successfully configured $configuredCount module(s) with default settings")
        } else {
            showNotify("All modules already have flutter_assets_generator configuration")
        }
    }
}
