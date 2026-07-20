package com.crzsc.plugin.cache

import com.crzsc.plugin.utils.Constants
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import org.yaml.snakeyaml.Yaml

/** pubspec.yaml 配置快照 用于检测配置是否发生变化 */
data class PubspecConfig(
    // Flutter 资源配置
    val assetPaths: List<String>,

    // 依赖版本
    val flutterSvgVersion: String?,
    val lottieVersion: String?,

    // 环境配置
    val flutterVersion: String?,
    val dartVersion: String?,

    // 插件自定义配置
    val hasPluginConfig: Boolean,
    val pluginEnabled: Boolean,
    val autoDetection: Boolean,
    val autoAddDependencies: Boolean,
    val outputDir: String,
    val className: String,
    val outputFilename: String,
    val filenameSplitPattern: String,
    val pathIgnore: List<String>,
    val generationStyle: String, // "robust" (default), "legacy", "snake_case"
    val nameStyle: String,
    val leafType: String,
    val packageParameterEnabled: Boolean
) {
    companion object {
        private val LOG = Logger.getInstance(PubspecConfig::class.java)

        /** 从 pubspec.yaml 读取配置 */
        /** 从 Map 解析配置 */
        fun fromMap(data: Map<*, *>): PubspecConfig {
            // 读取 flutter.assets
            val flutterMap = data["flutter"] as? Map<*, *>
            val assetPaths =
                (flutterMap?.get("assets") as? List<*>)?.mapNotNull { it as? String }
                    ?: emptyList()

            // 读取依赖版本
            val dependencies = data["dependencies"] as? Map<*, *>
            val flutterSvgVersion = dependencies?.get("flutter_svg") as? String
            val lottieVersion = dependencies?.get("lottie") as? String

            // 读取环境配置
            val environment = data["environment"] as? Map<*, *>
            val flutterVersion = environment?.get("flutter") as? String
            val dartVersion = environment?.get("sdk") as? String

            // 读取插件配置
            val pluginConfig = data[Constants.KEY_CONFIGURATION_MAP] as? Map<*, *>
            val hasPluginConfig = pluginConfig != null
            val pluginEnabled =
                if (pluginConfig == null) {
                    false
                } else {
                    pluginConfig[Constants.KEY_ENABLE] as? Boolean ?: true
                }
            val autoDetection =
                pluginConfig?.get(Constants.KEY_AUTO_DETECTION) as? Boolean ?: false
            val autoAddDependencies =
                pluginConfig?.get(Constants.KEY_AUTO_ADD_DEPENDENCIES) as? Boolean ?: true
            val outputDir =
                pluginConfig?.get(Constants.KEY_OUTPUT_DIR) as? String ?: Constants.DEFAULT_OUTPUT_DIR
            val className =
                pluginConfig?.get(Constants.KEY_CLASS_NAME) as? String ?: Constants.DEFAULT_CLASS_NAME
            val outputFilename =
                pluginConfig?.get(Constants.KEY_OUTPUT_FILENAME) as? String ?: "assets"
            val filenameSplitPattern =
                pluginConfig?.get(Constants.FILENAME_SPLIT_PATTERN) as? String
                    ?: Constants.DEFAULT_FILENAME_SPLIT_PATTERN
            val pathIgnore =
                (pluginConfig?.get(Constants.PATH_IGNORE) as? List<*>)?.mapNotNull {
                    it as? String
                }
                    ?: emptyList()

            // 读取生成风格配置: 'robust' (默认,新版), 'legacy' (旧版兼容)
            val generationStyle = pluginConfig?.get("style") as? String ?: "robust"
            val nameStyle = when (pluginConfig?.get(Constants.KEY_NAME_STYLE) as? String) {
                Constants.NAME_STYLE_SNAKE -> Constants.NAME_STYLE_SNAKE
                else -> Constants.DEFAULT_NAME_STYLE
            }
            val explicitLeafType = pluginConfig?.get(Constants.KEY_LEAF_TYPE) as? String
            val leafType =
                when (explicitLeafType) {
                    Constants.LEAF_TYPE_STRING -> Constants.LEAF_TYPE_STRING
                    Constants.LEAF_TYPE_CLASS -> Constants.LEAF_TYPE_CLASS
                    null ->
                        if (generationStyle == "legacy") {
                            Constants.LEAF_TYPE_STRING
                        } else {
                            Constants.DEFAULT_LEAF_TYPE
                        }
                    else -> Constants.DEFAULT_LEAF_TYPE
                }
            val packageParameterEnabled =
                pluginConfig?.get(Constants.KEY_PACKAGE_PARAMETER_ENABLED) as? Boolean ?: false

            return PubspecConfig(
                assetPaths = assetPaths,
                flutterSvgVersion = flutterSvgVersion,
                lottieVersion = lottieVersion,
                flutterVersion = flutterVersion,
                dartVersion = dartVersion,
                hasPluginConfig = hasPluginConfig,
                pluginEnabled = pluginEnabled,
                autoDetection = autoDetection,
                autoAddDependencies = autoAddDependencies,
                outputDir = outputDir,
                className = className,
                outputFilename = outputFilename,
                filenameSplitPattern = filenameSplitPattern,
                pathIgnore = pathIgnore,
                generationStyle = generationStyle,
                nameStyle = nameStyle,
                leafType = leafType,
                packageParameterEnabled = packageParameterEnabled
            )
        }

        /** 从 pubspec.yaml 读取配置 */
        fun fromPubspec(project: Project, pubspecFile: VirtualFile): PubspecConfig? {
            try {
                // 优先从 Document 读取(内存中的最新内容),如果获取不到则从磁盘读取
                val document =
                    com.intellij
                        .openapi
                        .fileEditor
                        .FileDocumentManager
                        .getInstance()
                        .getDocument(pubspecFile)
                val content = document?.text ?: String(pubspecFile.contentsToByteArray())

                val yaml = Yaml()
                val data = yaml.load<Map<*, *>>(content) ?: return null

                return fromMap(data)
            } catch (e: Exception) {
                LOG.warn(
                    "[FlutterAssetsGenerator #${project.name}] Failed to read pubspec config",
                    e
                )
                return null
            }
        }
    }
}

/** 项目+模块的唯一标识 */
data class ProjectModuleKey(
    val projectId: String, // project.locationHash
    val modulePath: String
) {
    companion object {
        fun create(project: Project, modulePath: String): ProjectModuleKey {
            return ProjectModuleKey(projectId = project.locationHash, modulePath = modulePath)
        }
    }
}

/** pubspec.yaml 配置缓存 支持多项目隔离 */
object PubspecConfigCache {
    private val LOG = Logger.getInstance(PubspecConfigCache::class.java)
    private val cache = ConcurrentHashMap<ProjectModuleKey, PubspecConfig>()

    /** 获取缓存的配置 */
    fun get(project: Project, modulePath: String): PubspecConfig? {
        val key = ProjectModuleKey.create(project, modulePath)
        return cache[key]
    }

    /** 保存配置到缓存 */
    fun put(project: Project, modulePath: String, config: PubspecConfig) {
        val key = ProjectModuleKey.create(project, modulePath)
        cache[key] = config
        LOG.info("[FlutterAssetsGenerator #${project.name}] Cached config for module: $modulePath")
    }

    /**
     * 检查配置是否发生变化
     * @return true 如果配置改变,需要重新生成
     */
    fun hasChanged(project: Project, modulePath: String, newConfig: PubspecConfig): Boolean {
        val key = ProjectModuleKey.create(project, modulePath)
        val oldConfig = cache[key]

        if (oldConfig == null) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] No cached config for $modulePath, treating as changed"
            )
            return true
        }

        // 自定义比较逻辑:忽略依赖版本的变化
        // 依赖版本由插件自动管理,不应触发代码重新生成
        val changed = isConfigChanged(oldConfig, newConfig)
        if (changed) {
            LOG.info("[FlutterAssetsGenerator #${project.name}] Config changed for $modulePath")
            logConfigDiff(project, oldConfig, newConfig)
        } else {
            // 记录被忽略的依赖版本变化
            if (oldConfig.flutterSvgVersion != newConfig.flutterSvgVersion ||
                oldConfig.lottieVersion != newConfig.lottieVersion
            ) {
                LOG.info(
                    "[FlutterAssetsGenerator #${project.name}] Dependency versions changed but ignored (no regeneration needed)"
                )
            }
        }

        return changed
    }

    /** 比较配置是否发生实质性变化(忽略依赖版本) */
    private fun isConfigChanged(old: PubspecConfig, new: PubspecConfig): Boolean {
        return old.assetPaths != new.assetPaths ||
                old.hasPluginConfig != new.hasPluginConfig ||
                old.pluginEnabled != new.pluginEnabled ||
                old.autoDetection != new.autoDetection ||
                old.autoAddDependencies != new.autoAddDependencies ||
                old.outputDir != new.outputDir ||
                old.className != new.className ||
                old.outputFilename != new.outputFilename ||
                old.filenameSplitPattern != new.filenameSplitPattern ||
                old.pathIgnore != new.pathIgnore ||
                old.generationStyle != new.generationStyle ||
                old.nameStyle != new.nameStyle ||
                old.leafType != new.leafType ||
                old.packageParameterEnabled != new.packageParameterEnabled
        // 注意:故意忽略 flutterSvgVersion, lottieVersion, flutterVersion, dartVersion
    }

    /** 清理项目的所有缓存 */
    fun clearProject(project: Project) {
        val projectId = project.locationHash
        val keysToRemove = cache.keys.filter { it.projectId == projectId }
        keysToRemove.forEach { cache.remove(it) }
        LOG.info(
            "[FlutterAssetsGenerator #${project.name}] Cleared ${keysToRemove.size} cached configs"
        )
    }

    /** 记录配置差异 */
    private fun logConfigDiff(project: Project, old: PubspecConfig, new: PubspecConfig) {
        if (old.assetPaths != new.assetPaths) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] Asset paths changed: ${old.assetPaths} -> ${new.assetPaths}"
            )
        }
        if (old.flutterSvgVersion != new.flutterSvgVersion) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] flutter_svg version changed: ${old.flutterSvgVersion} -> ${new.flutterSvgVersion}"
            )
        }
        if (old.lottieVersion != new.lottieVersion) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] lottie version changed: ${old.lottieVersion} -> ${new.lottieVersion}"
            )
        }
        if (old.flutterVersion != new.flutterVersion) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] Flutter version changed: ${old.flutterVersion} -> ${new.flutterVersion}"
            )
        }
        if (old.autoDetection != new.autoDetection) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] auto_detection changed: ${old.autoDetection} -> ${new.autoDetection}"
            )
        }
        if (old.hasPluginConfig != new.hasPluginConfig) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] has_plugin_config changed: ${old.hasPluginConfig} -> ${new.hasPluginConfig}"
            )
        }
        if (old.pluginEnabled != new.pluginEnabled) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] plugin_enabled changed: ${old.pluginEnabled} -> ${new.pluginEnabled}"
            )
        }
        if (old.autoAddDependencies != new.autoAddDependencies) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] auto_add_dependencies changed: ${old.autoAddDependencies} -> ${new.autoAddDependencies}"
            )
        }
        if (old.generationStyle != new.generationStyle) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] generation_style changed: ${old.generationStyle} -> ${new.generationStyle}"
            )
        }
        if (old.nameStyle != new.nameStyle) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] name_style changed: ${old.nameStyle} -> ${new.nameStyle}"
            )
        }
        if (old.leafType != new.leafType) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] leaf_type changed: ${old.leafType} -> ${new.leafType}"
            )
        }
        if (old.packageParameterEnabled != new.packageParameterEnabled) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] package_parameter_enabled changed: ${old.packageParameterEnabled} -> ${new.packageParameterEnabled}"
            )
        }
        if (old.outputFilename != new.outputFilename) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}] output_filename changed: ${old.outputFilename} -> ${new.outputFilename}"
            )
        }
    }
}
