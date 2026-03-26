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
            "[FlutterAssetsGenerator #${project.name}] pubspec.yaml saved: ${pubspecFile.path}"
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

                    if (!hasChanged) {
                        LOG.info(
                            "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Config unchanged, skipping generation"
                        )
                        return@invokeLater
                    }

                    PubspecConfigCache.put(project, modulePath, newConfig)

                    if (!newConfig.hasPluginConfig || !newConfig.pluginEnabled || !newConfig.autoDetection) {
                        LOG.info(
                            "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Plugin config missing, disabled, or auto_detection off; skipping generation"
                        )
                        return@invokeLater
                    }

                    LOG.info(
                        "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Config changed, triggering generation"
                    )

                    val updatedConfig =
                        FileHelperNew.getPubSpecConfigFromMap(
                            config.module,
                            config.pubRoot.pubspec,
                            data as Map<String, Any>
                        )

                    if (updatedConfig != null) {
                        fileGenerator.generateOne(updatedConfig)
                    } else {
                        LOG.error(
                            "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Failed to reload config for generation"
                        )
                    }
                } catch (e: Exception) {
                    LOG.error(
                        "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Processing failed",
                        e
                    )
                }
            }
        }
    }
}
