package com.crzsc.plugin.listener

import com.crzsc.plugin.cache.PubspecConfig
import com.crzsc.plugin.cache.PubspecConfigCache
import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelperNew
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.yaml.snakeyaml.Yaml

/** pubspec.yaml 文档保存监听器 只在保存时检测配置变更并触发生成 */
class PubspecDocumentListener(private val project: Project) : FileDocumentManagerListener {
    private val fileGenerator = FileGenerator(project)

    companion object {
        private val LOG = Logger.getInstance(PubspecDocumentListener::class.java)
        private const val TAG = "[FAG-PUBSPEC]"

        internal fun shouldTriggerGeneration(newConfig: PubspecConfig, hasChanged: Boolean): Boolean {
            return hasChanged &&
                    newConfig.hasPluginConfig &&
                    newConfig.pluginEnabled &&
                    newConfig.autoDetection
        }
    }

    override fun beforeDocumentSaving(document: Document) {
        val file =
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(document)

        // 只处理 pubspec.yaml 文件
        if (file?.name != "pubspec.yaml") {
            return
        }

        handlePubspecSave(file, document)
    }

    private fun handlePubspecSave(pubspecFile: VirtualFile, document: Document) {
        LOG.info(
            "$TAG project=${project.name} saved=${pubspecFile.path}"
        )

        val config =
            FileHelperNew.getAssets(project).firstOrNull { it.pubRoot.pubspec == pubspecFile } ?: return
        val modulePath = config.pubRoot.pubspec.parent.path

        // 使用 invokeLater 延迟读取配置和生成,确保读取到保存后的最新内容
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                try {
                    val content = document.text
                    val yaml = Yaml()
                    val data = yaml.load<Map<*, *>>(content) ?: return@invokeLater

                    val newConfig = PubspecConfig.fromMap(data)
                    val hasChanged = PubspecConfigCache.hasChanged(project, modulePath, newConfig)
                    LOG.info(
                        "$TAG module=${config.module.name} assetPaths=${newConfig.assetPaths} hasPluginConfig=${newConfig.hasPluginConfig} pluginEnabled=${newConfig.pluginEnabled} autoDetection=${newConfig.autoDetection} hasChanged=$hasChanged"
                    )

                    if (!hasChanged) {
                        LOG.info(
                            "$TAG module=${config.module.name} config-unchanged skip-generation"
                        )
                        return@invokeLater
                    }

                    PubspecConfigCache.put(project, modulePath, newConfig)

                    if (!shouldTriggerGeneration(newConfig, hasChanged = true)) {
                        LOG.info(
                            "$TAG module=${config.module.name} skip-generation reason=config-disabled-or-missing"
                        )
                        return@invokeLater
                    }

                    LOG.info(
                        "$TAG module=${config.module.name} config-changed trigger-generation"
                    )

                    val updatedConfig =
                        FileHelperNew.getPubSpecConfigFromMap(
                            config.module,
                            config.pubRoot.pubspec,
                            data as Map<String, Any>
                        )

                    if (updatedConfig != null) {
                        LOG.info(
                            "$TAG module=${config.module.name} reloaded-assetVFiles=${updatedConfig.assetVFiles.map { it.path }}"
                        )
                        fileGenerator.generateOne(updatedConfig)
                    } else {
                        LOG.error(
                            "$TAG module=${config.module.name} failed-to-reload-config-for-generation"
                        )
                    }
                } catch (e: Exception) {
                    LOG.error(
                        "$TAG module=${config.module.name} processing-failed",
                        e
                    )
                }
            }
        }
    }
}
