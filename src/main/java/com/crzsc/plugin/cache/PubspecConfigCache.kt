package com.crzsc.plugin.cache

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.yaml.snakeyaml.Yaml
import java.util.concurrent.ConcurrentHashMap

/**
 * pubspec.yaml 配置快照
 * 用于检测配置是否发生变化
 */
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
    val autoDetection: Boolean,
    val outputDir: String,
    val className: String,
    val filenameSplitPattern: String,
    val pathIgnore: List<String>,
    val generationStyle: String // "robust" (default), "camel_case", "snake_case"
) {
    companion object {
        private val LOG = Logger.getInstance(PubspecConfig::class.java)
        
        /**
         * 从 pubspec.yaml 读取配置
         */
        fun fromPubspec(project: Project, pubspecFile: VirtualFile): PubspecConfig? {
            try {
                // 优先从 Document 读取(内存中的最新内容),如果获取不到则从磁盘读取
                val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(pubspecFile)
                val content = document?.text ?: String(pubspecFile.contentsToByteArray())
                
                val yaml = Yaml()
                val data = yaml.load<Map<*, *>>(content)
                
                // 读取 flutter.assets
                val flutterMap = data["flutter"] as? Map<*, *>
                val assetPaths = (flutterMap?.get("assets") as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList()
                
                // 读取依赖版本
                val dependencies = data["dependencies"] as? Map<*, *>
                val flutterSvgVersion = dependencies?.get("flutter_svg") as? String
                val lottieVersion = dependencies?.get("lottie") as? String
                
                // 读取环境配置
                val environment = data["environment"] as? Map<*, *>
                val flutterVersion = environment?.get("flutter") as? String
                val dartVersion = environment?.get("sdk") as? String
                
                // 读取插件配置
                val pluginConfig = data["flutter_assets_generator"] as? Map<*, *>
                val autoDetection = pluginConfig?.get("auto_detection") as? Boolean ?: false
                val outputDir = pluginConfig?.get("output_dir") as? String ?: "generated"
                val className = pluginConfig?.get("class_name") as? String ?: "Assets"
                val filenameSplitPattern = pluginConfig?.get("filename_split_pattern") as? String ?: "[-_]"
                val pathIgnore = (pluginConfig?.get("path_ignore") as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList()
                    
                // 读取生成风格配置: 'robust' (默认,新版), 'camel_case' (旧版兼容)
                val generationStyle = pluginConfig?.get("style") as? String ?: "robust"
                
                return PubspecConfig(
                    assetPaths = assetPaths,
                    flutterSvgVersion = flutterSvgVersion,
                    lottieVersion = lottieVersion,
                    flutterVersion = flutterVersion,
                    dartVersion = dartVersion,
                    autoDetection = autoDetection,
                    outputDir = outputDir,
                    className = className,
                    filenameSplitPattern = filenameSplitPattern,
                    pathIgnore = pathIgnore,
                    generationStyle = generationStyle
                )
            } catch (e: Exception) {
                LOG.warn("[FlutterAssetsGenerator #${project.name}] Failed to read pubspec config", e)
                return null
            }
        }
    }
}

/**
 * 项目+模块的唯一标识
 */
data class ProjectModuleKey(
    val projectId: String,  // project.locationHash
    val modulePath: String
) {
    companion object {
        fun create(project: Project, modulePath: String): ProjectModuleKey {
            return ProjectModuleKey(
                projectId = project.locationHash,
                modulePath = modulePath
            )
        }
    }
}

/**
 * pubspec.yaml 配置缓存
 * 支持多项目隔离
 */
object PubspecConfigCache {
    private val LOG = Logger.getInstance(PubspecConfigCache::class.java)
    private val cache = ConcurrentHashMap<ProjectModuleKey, PubspecConfig>()
    
    /**
     * 获取缓存的配置
     */
    fun get(project: Project, modulePath: String): PubspecConfig? {
        val key = ProjectModuleKey.create(project, modulePath)
        return cache[key]
    }
    
    /**
     * 保存配置到缓存
     */
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
            LOG.info("[FlutterAssetsGenerator #${project.name}] No cached config for $modulePath, treating as changed")
            return true
        }
        
        val changed = oldConfig != newConfig
        if (changed) {
            LOG.info("[FlutterAssetsGenerator #${project.name}] Config changed for $modulePath")
            logConfigDiff(project, oldConfig, newConfig)
        }
        
        return changed
    }
    
    /**
     * 清理项目的所有缓存
     */
    fun clearProject(project: Project) {
        val projectId = project.locationHash
        val keysToRemove = cache.keys.filter { it.projectId == projectId }
        keysToRemove.forEach { cache.remove(it) }
        LOG.info("[FlutterAssetsGenerator #${project.name}] Cleared ${keysToRemove.size} cached configs")
    }
    
    /**
     * 记录配置差异
     */
    private fun logConfigDiff(project: Project, old: PubspecConfig, new: PubspecConfig) {
        if (old.assetPaths != new.assetPaths) {
            LOG.info("[FlutterAssetsGenerator #${project.name}] Asset paths changed: ${old.assetPaths} -> ${new.assetPaths}")
        }
        if (old.flutterSvgVersion != new.flutterSvgVersion) {
            LOG.info("[FlutterAssetsGenerator #${project.name}] flutter_svg version changed: ${old.flutterSvgVersion} -> ${new.flutterSvgVersion}")
        }
        if (old.lottieVersion != new.lottieVersion) {
            LOG.info("[FlutterAssetsGenerator #${project.name}] lottie version changed: ${old.lottieVersion} -> ${new.lottieVersion}")
        }
        if (old.flutterVersion != new.flutterVersion) {
            LOG.info("[FlutterAssetsGenerator #${project.name}] Flutter version changed: ${old.flutterVersion} -> ${new.flutterVersion}")
        }
        if (old.autoDetection != new.autoDetection) {
            LOG.info("[FlutterAssetsGenerator #${project.name}] auto_detection changed: ${old.autoDetection} -> ${new.autoDetection}")
        }
        if (old.generationStyle != new.generationStyle) {
            LOG.info("[FlutterAssetsGenerator #${project.name}] generation_style changed: ${old.generationStyle} -> ${new.generationStyle}")
        }
    }
}
