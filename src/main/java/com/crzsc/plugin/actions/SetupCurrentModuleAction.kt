package com.crzsc.plugin.actions

import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.crzsc.plugin.utils.SetupConfigurationHelper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbService

/**
 * 仅为当前右键选中的 Flutter 模块添加默认配置
 */
class SetupCurrentModuleAction : AnAction() {
    companion object {
        private val LOG = Logger.getInstance(SetupCurrentModuleAction::class.java)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (project == null || !FileHelperNew.shouldActivateFor(project)) {
            showNotify("This is not a Flutter project")
            return
        }
        if (DumbService.isDumb(project)) {
            showNotify("Indexing in progress, please retry after indexing finishes")
            return
        }

        val candidatePaths = SetupConfigurationHelper.resolveCandidatePaths(e)
        LOG.info(
            "[FlutterAssetsGenerator #SetupCurrentModule] actionPerformed project=${project.name} candidates=$candidatePaths"
        )
        val config = SetupConfigurationHelper.findTargetConfig(project, candidatePaths)
        if (config == null) {
            showNotify("Please right-click the root directory of a Flutter module")
            return
        }

        val packageParameterEnabled =
            SetupConfigurationHelper.shouldEnablePackageParameterByDefault(project, config)
        val configured = SetupConfigurationHelper.addDefaultConfiguration(project, config)
        if (configured) {
            showNotify(
                "Configured module ${config.module.name} with package_parameter_enabled=$packageParameterEnabled"
            )
        } else {
            showNotify("Module ${config.module.name} already has flutter_assets_generator configuration")
        }
    }

    override fun update(e: AnActionEvent) {
        // update() 在每次弹出菜单时都会调用, 这里只能用缓存, 不能触发索引扫描
        val project = e.getData(PlatformDataKeys.PROJECT)
        val visible =
            project != null &&
                    !DumbService.isDumb(project) &&
                    FileHelperNew.shouldActivateFor(project) &&
                    SetupConfigurationHelper.findTargetConfig(
                        FileHelperNew.getCachedAssets(project),
                        SetupConfigurationHelper.resolveCandidatePaths(e)
                    ) != null
        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible
    }
}
