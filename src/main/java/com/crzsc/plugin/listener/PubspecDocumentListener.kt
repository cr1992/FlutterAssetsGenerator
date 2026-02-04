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

/**
 * pubspec.yaml 文档保存监听器
 * 只在保存时检测配置变更并触发生成
 */
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

        handlePubspecSave(file)
    }

    private fun handlePubspecSave(pubspecFile: VirtualFile) {
        LOG.info("[FlutterAssetsGenerator #${project.name}] pubspec.yaml saved: ${pubspecFile.path}")

        // 获取项目中所有 Flutter 模块的配置
        val assets = FileHelperNew.getAssets(project)

        for (config in assets) {
            // 检查是否是当前保存的 pubspec.yaml
            if (config.pubRoot.pubspec != pubspecFile) {
                continue
            }

            // 检查是否启用了自动检测
            if (!FileHelperNew.isAutoDetectionEnable(config)) {
                LOG.info("[FlutterAssetsGenerator #${project.name}/${config.module.name}] auto_detection disabled, skipping")
                continue
            }

            val modulePath = config.pubRoot.pubspec.parent.path

            // 使用 invokeLater 延迟读取配置和生成,确保读取到保存后的最新内容
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    try {
                        // 在这里读取配置,确保文件已经保存到磁盘
                        val newConfig = PubspecConfig.fromPubspec(project, pubspecFile)
                        if (newConfig == null) {
                            LOG.warn("[FlutterAssetsGenerator #${project.name}/${config.module.name}] Failed to read pubspec config")
                            return@invokeLater
                        }

                        // 检查配置是否改变
                        val hasChanged =
                            PubspecConfigCache.hasChanged(project, modulePath, newConfig)

                        if (hasChanged) {
                            LOG.info("[FlutterAssetsGenerator #${project.name}/${config.module.name}] Config changed, triggering generation")

                            // 更新缓存
                            PubspecConfigCache.put(project, modulePath, newConfig)

                            // 使用最新的配置重新加载 ModulePubSpecConfig 对象
                            // 必须这样做,因为原本的 config 对象包含的是旧的配置信息(如 className 等)
                            val updatedConfig = FileHelperNew.getPubSpecConfig(config.module)
                            if (updatedConfig != null) {
                                // 触发生成
                                fileGenerator.generateOne(updatedConfig)
                            } else {
                                LOG.error("[FlutterAssetsGenerator #${project.name}/${config.module.name}] Failed to reload config for generation")
                            }
                        } else {
                            LOG.info("[FlutterAssetsGenerator #${project.name}/${config.module.name}] Config unchanged, skipping generation")
                        }
                    } catch (e: Exception) {
                        LOG.error(
                            "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Processing failed",
                            e
                        )
                    }
                }
            }

            return
        }
    }
}
