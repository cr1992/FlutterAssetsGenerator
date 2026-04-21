package com.crzsc.plugin.utils

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import io.flutter.pub.PubRoot
import io.flutter.utils.FlutterModuleUtils
import java.util.*
import java.util.regex.Pattern
import org.jetbrains.kotlin.idea.util.projectStructure.getModule
import org.jetbrains.kotlin.konan.file.File
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

    /** 获取所有可用的Flutter Module的Asset配置 */
    @JvmStatic
    fun getAssets(project: Project): List<ModulePubSpecConfig> {
        val folders = mutableListOf<ModulePubSpecConfig>()
        val pubspecFiles = findProjectPubspecFiles(project)
        val filtered = pubspecFiles.filter { file -> isEligibleModulePubspecPath(file.path) }
        LOG.info("Found ${pubspecFiles.size} pubspec.yaml, after filtering: ${filtered.size}")

        for (pubspecFile in filtered) {
            val module = pubspecFile.getModule(project)
            if (module == null) {
                LOG.info("  skipped (no module): ${pubspecFile.path}")
                continue
            }
            val config = getPubSpecConfig(module, pubspecFile)
            if (config == null) {
                LOG.info("  skipped (not flutter or unreadable): ${pubspecFile.path}")
                continue
            }
            LOG.info("  accepted: ${pubspecFile.path} [module=${module.name}]")
            folders.add(config)
        }
        LOG.info("Resolved ${folders.size} Flutter module config(s)")
        return folders
    }

    private fun findProjectPubspecFiles(project: Project): List<VirtualFile> {
        val pubspecFiles = mutableListOf<VirtualFile>()
        val scope = ProjectScope.getContentScope(project)
        FilenameIndex.processFilesByName(
            "pubspec.yaml",
            false,
            { file: PsiFileSystemItem ->
                file.virtualFile?.let { pubspecFiles.add(it) }
                true
            },
            scope,
            project
        )
        return pubspecFiles
    }

    internal fun isEligibleModulePubspecPath(path: String): Boolean {
        val excluded =
            excludedPubspecPathSegments.firstOrNull { segment -> path.contains("/$segment/") }
        if (excluded != null) {
            LOG.info("  excluded (matched /$excluded/): $path")
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
            val document =
                com.intellij
                    .openapi
                    .fileEditor
                    .FileDocumentManager
                    .getInstance()
                    .getDocument(pubspecFile)
            val content = document?.text ?: String(pubspecFile.contentsToByteArray())
            Yaml().load(content) as? Map<String, Any>
        } catch (e: Exception) {
            e.printStackTrace()
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
        val scope = ProjectScope.getContentScope(project)
        var found = false
        FilenameIndex.processFilesByName(
            "pubspec.yaml",
            false,
            { file: PsiFileSystemItem ->
                val virtualFile = file.virtualFile ?: return@processFilesByName true
                if (!isEligibleModulePubspecPath(virtualFile.path)) {
                    return@processFilesByName true
                }
                if (readPubspecMap(virtualFile)?.let(::isFlutterPubspecMap) == true) {
                    found = true
                    return@processFilesByName false
                }
                true
            },
            scope,
            project
        )
        return found
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

        return CachedValuesManager.getCachedValue(psiFile) {
            CachedValueProvider.Result.create(computePubSpecConfig(module, pubspecFile), psiFile)
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
            e.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
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
        if (!outputDir.contains(File.separator)) {
            return if (createIfMissing) {
                root.findOrCreateChildDir(root, outputDir)
            } else {
                root.findChild(outputDir)
            }
        }

        var current: VirtualFile = root
        for (dir in outputDir.split(File.separator)) {
            if (dir.isEmpty()) {
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
