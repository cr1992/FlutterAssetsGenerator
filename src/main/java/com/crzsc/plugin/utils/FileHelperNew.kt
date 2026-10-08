package com.crzsc.plugin.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import io.flutter.pub.PubRoot
import io.flutter.utils.FlutterModuleUtils
import java.util.regex.Pattern
import org.jetbrains.yaml.YAMLLanguage
import org.yaml.snakeyaml.Yaml

/** 基于Module来处理Assets */
object FileHelperNew {

    private val LOG = Logger.getInstance(FileHelperNew::class.java)
    private val excludedPubspecPathSegments =
        setOf(
            ".dart_tool",
            ".pub-cache",
            ".pub",
            ".symlinks",
            ".plugin_symlinks",
            "build",
            ".flutter-plugins-dependencies",
            "ephemeral"
        )

    /**
     * 获取所有可用的Flutter Module的Asset配置 (每次调用都会扫描索引, 需要在读锁内调用)
     */
    @JvmStatic
    fun getAssets(project: Project): List<ModulePubSpecConfig> {
        val folders = mutableListOf<ModulePubSpecConfig>()
        val basePath = project.basePath
        val pubspecFiles = findProjectPubspecFiles(project)
        val filtered =
            pubspecFiles.filter { file -> isEligibleModulePubspecPath(file.path, basePath) }

        for (pubspecFile in filtered) {
            val module = ModuleUtilCore.findModuleForFile(pubspecFile, project)
            if (module == null) {
                LOG.debug("  skipped (no module): ${pubspecFile.path}")
                continue
            }
            val config = getPubSpecConfig(module, pubspecFile)
            if (config == null) {
                LOG.debug("  skipped (not flutter or unreadable): ${pubspecFile.path}")
                continue
            }
            LOG.debug("  accepted: ${pubspecFile.path} [module=${module.name}]")
            folders.add(config)
        }
        LOG.info(
            "[FlutterAssetsGenerator #${project.name}] Found ${pubspecFiles.size} pubspec.yaml, eligible=${filtered.size}, flutterModules=${folders.size}"
        )
        return folders
    }

    /**
     * 项目级缓存的模块配置列表. 只在 pubspec.yaml (YAML PSI) 变化、VFS 结构变化或项目根变化时失效,
     * 供高频调用方 (行标记、Action.update、VFS 监听) 使用, 避免每次都走 FilenameIndex.
     */
    @JvmStatic
    fun getCachedAssets(project: Project): List<ModulePubSpecConfig> {
        return CachedValuesManager.getManager(project).getCachedValue(project, ASSETS_CACHE_KEY, {
            CachedValueProvider.Result.create(
                getAssets(project),
                PsiManager.getInstance(project).modificationTracker.forLanguage(YAMLLanguage.INSTANCE),
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                ProjectRootManager.getInstance(project)
            )
        }, false)
    }

    private val ASSETS_CACHE_KEY =
        com.intellij.openapi.util.Key.create<com.intellij.psi.util.CachedValue<List<ModulePubSpecConfig>>>(
            "FlutterAssetsGenerator.moduleConfigs"
        )

    private fun findProjectPubspecFiles(project: Project): List<VirtualFile> {
        val scope = ProjectScope.getContentScope(project)
        return FilenameIndex.getVirtualFilesByName("pubspec.yaml", scope).toList()
    }

    /**
     * 判断 pubspec 是否位于可识别的模块目录.
     * 只检查相对于项目根目录的路径段, 避免项目本身放在名为 build 之类的目录下时被整体排除.
     */
    internal fun isEligibleModulePubspecPath(path: String, projectBasePath: String? = null): Boolean {
        val relative =
            if (!projectBasePath.isNullOrEmpty() && path.startsWith("$projectBasePath/")) {
                path.substring(projectBasePath.length)
            } else {
                path
            }
        val excluded =
            excludedPubspecPathSegments.firstOrNull { segment -> relative.contains("/$segment/") }
        if (excluded != null) {
            LOG.debug("  excluded (matched /$excluded/): $path")
            return false
        }
        return true
    }

    internal fun shouldIncludePubspec(path: String): Boolean {
        return isEligibleModulePubspecPath(path)
    }

    internal fun isFlutterPubspecMap(pubConfigMap: Map<*, *>): Boolean {
        if (pubConfigMap.containsKey("flutter")) {
            return true
        }
        return hasSdkDependency(pubConfigMap, "dependencies", "flutter", "flutter") ||
                hasSdkDependency(pubConfigMap, "dev_dependencies", "flutter_test", "flutter")
    }

    private fun hasSdkDependency(
        pubConfigMap: Map<*, *>,
        sectionName: String,
        dependencyName: String,
        sdkName: String
    ): Boolean {
        val dependencies = pubConfigMap[sectionName] as? Map<*, *> ?: return false
        val dependency = dependencies[dependencyName] as? Map<*, *> ?: return false
        return dependency["sdk"] == sdkName
    }

    internal fun readPubspecMap(pubspecFile: VirtualFile): Map<String, Any>? {
        return try {
            val document = FileDocumentManager.getInstance().getDocument(pubspecFile)
            val content = document?.text ?: String(pubspecFile.contentsToByteArray())
            @Suppress("UNCHECKED_CAST")
            Yaml().load(content) as? Map<String, Any>
        } catch (e: Exception) {
            LOG.warn("Failed to parse ${pubspecFile.path}: ${e.message}")
            null
        }
    }

    @JvmStatic
    fun shouldActivateFor(project: Project): Boolean {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(
                hasEligibleFlutterPubspec(project),
                PsiModificationTracker.MODIFICATION_COUNT,
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
            )
        }
    }

    private fun hasEligibleFlutterPubspec(project: Project): Boolean {
        val basePath = project.basePath
        return findProjectPubspecFiles(project).any { file ->
            isEligibleModulePubspecPath(file.path, basePath) &&
                    readPubspecMap(file)?.let(::isFlutterPubspecMap) == true
        }
    }

    fun tryGetAssetsList(map: Map<*, *>): MutableList<*>? {
        (map["flutter"] as? Map<*, *>)?.let {
            return it["assets"] as? MutableList<*>
        }
        return null
    }

    @JvmStatic
    fun getPubSpecConfig(module: Module, pubspecFile: VirtualFile): ModulePubSpecConfig? {
        val psiFile = PsiManager.getInstance(module.project).findFile(pubspecFile) ?: return null

        // 除了 pubspec 本身, 还依赖目录结构 (assetVFiles 需要重新解析) 和项目根 (Module 可能被重新导入)
        return CachedValuesManager.getCachedValue(psiFile) {
            CachedValueProvider.Result.create(
                computePubSpecConfig(module, pubspecFile),
                psiFile,
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                ProjectRootManager.getInstance(module.project)
            )
        }
    }

    fun getPubSpecConfigFromMap(
        module: Module,
        pubspecFile: VirtualFile,
        pubConfigMap: Map<String, Any>
    ): ModulePubSpecConfig? {
        try {
            if (!isFlutterPubspecMap(pubConfigMap)) {
                return null
            }
            val moduleDir = pubspecFile.parent
            val pubRoot = PubRoot.forDirectory(moduleDir)
            if (moduleDir != null && pubRoot != null) {
                val assetVFiles = mutableListOf<VirtualFile>()
                (pubConfigMap["flutter"] as? Map<*, *>)?.let { configureMap ->
                    (configureMap["assets"] as? ArrayList<*>)?.let { list ->
                        for (path in list) {
                            moduleDir.findFileByRelativePath(path as String)?.let {
                                if (!assetVFiles.contains(it)) {
                                    assetVFiles.add(it)
                                }
                            }
                        }
                    }
                }
                return ModulePubSpecConfig(
                    module,
                    pubRoot,
                    assetVFiles,
                    pubConfigMap,
                )
            }
        } catch (e: Exception) {
            LOG.warn("Failed to build config for ${pubspecFile.path}", e)
            return null
        }
        return null
    }

    private fun computePubSpecConfig(
        module: Module,
        pubspecFile: VirtualFile
    ): ModulePubSpecConfig? {
        try {
            val moduleDir = pubspecFile.parent
            val pubRoot = PubRoot.forDirectory(moduleDir)
            if (moduleDir != null && pubRoot != null) {
                val pubConfigMap = readPubspecMap(pubRoot.pubspec)
                if (pubConfigMap != null) {
                    return getPubSpecConfigFromMap(module, pubspecFile, pubConfigMap)
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to read config for ${pubspecFile.path}", e)
            return null
        }
        return null
    }

    /** 读取配置 */
    private fun readSetting(config: ModulePubSpecConfig, key: String): Any? {
        getPluginConfigMap(config)?.let { configureMap ->
            return configureMap[key]
        }
        return null
    }

    private fun getPluginConfigMap(config: ModulePubSpecConfig): Map<*, *>? {
        return config.map[Constants.KEY_CONFIGURATION_MAP] as? Map<*, *>
    }

    /** 是否存在 flutter_assets_generator 配置块 */
    fun hasPluginConfig(config: ModulePubSpecConfig): Boolean {
        return getPluginConfigMap(config) != null
    }

    /** 插件能力是否启用 */
    fun isPluginEnabled(config: ModulePubSpecConfig): Boolean {
        val pluginConfig = getPluginConfigMap(config) ?: return false
        return pluginConfig[Constants.KEY_ENABLE] as? Boolean ?: true
    }

    /** 是否开启了自动检测 */
    fun isAutoDetectionEnable(config: ModulePubSpecConfig): Boolean {
        if (!isPluginEnabled(config)) {
            return false
        }
        return readSetting(config, Constants.KEY_AUTO_DETECTION) as Boolean? ?: true
    }

    /** 是否开启了自动添加依赖 */
    fun isAutoAddDependenciesEnable(config: ModulePubSpecConfig): Boolean {
        return readSetting(config, Constants.KEY_AUTO_ADD_DEPENDENCIES) as Boolean? ?: true
    }

    /** 是否开启了 package 参数生成 */
    fun isPackageParameterEnabled(config: ModulePubSpecConfig): Boolean {
        return readSetting(config, Constants.KEY_PACKAGE_PARAMETER_ENABLED) as Boolean? ?: false
    }

    /** 是否在 legacy 样式中使用父目录作为变量名前缀 */
    fun isNamedWithParent(config: ModulePubSpecConfig): Boolean {
        return readSetting(config, Constants.KEY_NAMED_WITH_PARENT) as Boolean? ?: true
    }

    /** 读取命名风格配置 */
    fun getNameStyle(config: ModulePubSpecConfig): String {
        return when (readSetting(config, Constants.KEY_NAME_STYLE) as? String) {
            Constants.NAME_STYLE_SNAKE -> Constants.NAME_STYLE_SNAKE
            else -> Constants.DEFAULT_NAME_STYLE
        }
    }

    /** 读取引用类型配置，未显式配置时根据 style 决定默认值：robust 默认 class，legacy 默认 string */
    fun getLeafType(config: ModulePubSpecConfig): String {
        val explicit = readSetting(config, Constants.KEY_LEAF_TYPE) as? String
        if (explicit != null) {
            return when (explicit) {
                Constants.LEAF_TYPE_STRING -> Constants.LEAF_TYPE_STRING
                Constants.LEAF_TYPE_CLASS -> Constants.LEAF_TYPE_CLASS
                else -> Constants.DEFAULT_LEAF_TYPE
            }
        }
        return if (getGenerationStyle(config) == "legacy") {
            Constants.LEAF_TYPE_STRING
        } else {
            Constants.DEFAULT_LEAF_TYPE
        }
    }

    /** 检查用户是否显式配置了 leaf_type */
    fun isLeafTypeExplicitlySet(config: ModulePubSpecConfig): Boolean {
        return readSetting(config, Constants.KEY_LEAF_TYPE) != null
    }

    /** 读取生成的类名配置 */
    fun getGeneratedClassName(config: ModulePubSpecConfig): String {
        return readSetting(config, Constants.KEY_CLASS_NAME) as String?
            ?: Constants.DEFAULT_CLASS_NAME
    }

    /** 读取文件分割配置 */
    fun getFilenameSplitPattern(config: ModulePubSpecConfig): String {
        return try {
            val pattern =
                readSetting(config, Constants.FILENAME_SPLIT_PATTERN) as String?
                    ?: Constants.DEFAULT_FILENAME_SPLIT_PATTERN
            Pattern.compile(pattern)
            pattern
        } catch (e: Exception) {
            LOG.warn("Invalid filename_split_pattern, falling back to default: ${e.message}")
            Constants.DEFAULT_FILENAME_SPLIT_PATTERN
        }
    }

    /** 读取忽略文件目录 */
    fun getPathIgnore(config: ModulePubSpecConfig): List<String> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val paths = readSetting(config, Constants.PATH_IGNORE) as List<String>? ?: emptyList()
            paths
        } catch (e: Exception) {
            LOG.warn("Invalid path_ignore, ignoring: ${e.message}")
            emptyList()
        }
    }

    /** 获取generated自动生成目录 从yaml中读取 */
    private fun getGeneratedFilePath(config: ModulePubSpecConfig): VirtualFile {
        return resolveGeneratedDirectory(
            config.pubRoot.lib,
            getOutputDir(config),
            createIfMissing = true
        )!!
    }

    fun getOutputDir(config: ModulePubSpecConfig): String {
        return readSetting(config, Constants.KEY_OUTPUT_DIR) as String?
            ?: Constants.DEFAULT_OUTPUT_DIR
    }

    fun findGeneratedFile(config: ModulePubSpecConfig): VirtualFile? {
        return findGeneratedFile(
            config.pubRoot,
            getOutputDir(config),
            getGeneratedFileName(config)
        )
    }

    fun findGeneratedFile(
        pubRoot: PubRoot,
        outputDir: String,
        outputFilename: String
    ): VirtualFile? {
        val generatedDir =
            resolveGeneratedDirectory(pubRoot.lib, outputDir, createIfMissing = false)
                ?: return null
        return generatedDir.findChild("$outputFilename.dart")
    }

    private fun resolveGeneratedDirectory(
        lib: VirtualFile?,
        outputDir: String,
        createIfMissing: Boolean
    ): VirtualFile? {
        val root = lib ?: return null
        var current: VirtualFile = root
        // VFS 路径和 pubspec 里的配置统一使用 '/', 不能用 File.separator (Windows 下是 '\\')
        for (dir in outputDir.split('/', '\\')) {
            if (dir.isEmpty() || dir == ".") {
                continue
            }
            current =
                if (createIfMissing) {
                    current.findOrCreateChildDir(current, dir)
                } else {
                    current.findChild(dir) ?: return null
                }
        }
        return current
    }

    private fun VirtualFile.findOrCreateChildDir(requestor: Any, name: String): VirtualFile {
        val child = findChild(name)
        return child ?: createChildDirectory(requestor, name)
    }

    /** 获取需要生成的文件 如果没有则会创建文件 */
    fun getGeneratedFile(config: ModulePubSpecConfig): VirtualFile {
        return getGeneratedFilePath(config).let {
            val configName = getGeneratedFileName(config)
            return@let it.findOrCreateChildData(it, "$configName.dart")
        }
    }

    fun getGeneratedFileName(config: ModulePubSpecConfig): String =
        readSetting(config, Constants.KEY_OUTPUT_FILENAME) as? String
            ?: Constants.DEFAULT_CLASS_NAME.lowercase()

    /** 读取生成风格配置 return "robust" | "legacy" */
    fun getGenerationStyle(config: ModulePubSpecConfig): String {
        return readSetting(config, "style") as? String ?: "robust"
    }
}

/** 模块Flutter配置信息 */
data class ModulePubSpecConfig(
    val module: Module,
    val pubRoot: PubRoot,
    val assetVFiles: List<VirtualFile>,
    val map: Map<String, Any>,
    val isFlutterModule: Boolean = FlutterModuleUtils.isFlutterModule(module)
)
