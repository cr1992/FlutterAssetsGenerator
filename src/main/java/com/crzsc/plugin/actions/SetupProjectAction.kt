package com.crzsc.plugin.actions

import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * 一键配置项目 Action
 * 自动在 pubspec.yaml 中添加 flutter_assets_generator 配置块
 */
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
            if (addDefaultConfiguration(project, config)) {
                configuredCount++
            }
        }

        if (configuredCount > 0) {
            showNotify("Successfully configured $configuredCount module(s) with default settings")
        } else {
            showNotify("All modules already have flutter_assets_generator configuration")
        }
    }

    /**
     * 向 pubspec.yaml 添加默认配置
     * @return true 如果成功添加配置，false 如果配置已存在
     */
    private fun addDefaultConfiguration(project: com.intellij.openapi.project.Project, config: com.crzsc.plugin.utils.ModulePubSpecConfig): Boolean {
        val pubspecFile = config.pubRoot.pubspec
        val yamlFile = PsiManager.getInstance(project).findFile(pubspecFile) as? YAMLFile ?: return false
        val yamlMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return false

        // 检查是否已存在配置
        if (yamlMapping.getKeyValueByKey("flutter_assets_generator") != null) {
            return false
        }

        // 添加默认配置
        WriteCommandAction.runWriteCommandAction(project) {
            val generator = YAMLElementGenerator.getInstance(project)
            
            // 创建配置内容
            val configContent = """
                flutter_assets_generator:
                  output_dir: generated/
                  output_filename: assets
                  class_name: Assets
                  auto_detection: true
                  # Options: robust (default), camel_case (legacy)
                  style: robust
                  path_ignore: []
            """.trimIndent()

            try {
                // 解析配置为 YAML 结构
                val dummyYaml = generator.createDummyYamlWithText(configContent)
                val configMapping = PsiTreeUtil.collectElementsOfType(dummyYaml, YAMLMapping::class.java).firstOrNull()
                
                if (configMapping != null) {
                    val configKV = configMapping.keyValues.firstOrNull()
                    if (configKV != null) {
                        yamlMapping.putKeyValue(configKV)
                    }
                }
            } catch (e: Exception) {
                showNotify("Failed to add configuration: ${e.message}")
            }
        }

        return true
    }
}
