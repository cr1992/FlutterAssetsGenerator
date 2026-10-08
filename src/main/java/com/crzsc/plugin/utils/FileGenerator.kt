package com.crzsc.plugin.utils

import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import kotlin.system.measureTimeMillis
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence

class FileGenerator(private val project: Project) {
    companion object {
        private val LOG = Logger.getInstance(FileGenerator::class.java)
        internal const val SETUP_REQUIRED_MESSAGE =
            "No Flutter modules available for generation. Run 'Setup Project Configuration' first or set enable: true."
    }

    /** 为所有模块重新生成 */
    fun generateAll() {
        ProgressManager.getInstance()
            .run(
                object : Task.Backgroundable(project, "Generating Assets", false) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.text = "Scanning Flutter modules"
                        lateinit var allConfigs: List<ModulePubSpecConfig>
                        val scanElapsedMs =
                            measureTimeMillis {
                                // 后台线程: 等索引就绪再在读锁内扫描, 不用已弃用的 runReadActionInSmartMode
                                allConfigs =
                                    ReadAction.nonBlocking<List<ModulePubSpecConfig>> {
                                        FileHelperNew.getAssets(project)
                                    }
                                        .inSmartMode(project)
                                        .expireWith(project)
                                        .executeSynchronously()
                            }

                        val assets = filterEnabledConfigs(allConfigs)
                        LOG.info(
                            "[FlutterAssetsGenerator #${project.name}] generation scan complete discoveredModules=${allConfigs.size} enabledModules=${assets.size} scanElapsedMs=$scanElapsedMs"
                        )

                        if (shouldShowSetupPrompt(assets)) {
                            logExcludedGenerationCandidates(allConfigs)
                            showNotify(SETUP_REQUIRED_MESSAGE)
                            return
                        }

                        cleanupDisabledConfigs(allConfigs)

                        LOG.info(
                            "[FlutterAssetsGenerator #${project.name}] generation mode=sequential queuedModules=${assets.size}"
                        )

                        var changedModules = 0
                        var unchangedModules = 0
                        val totalElapsedMs =
                            measureTimeMillis {
                                assets.forEachIndexed { index, config ->
                                    indicator.checkCanceled()
                                    val result =
                                        generateOneBlocking(
                                            config = config,
                                            indicator = indicator,
                                            mode = "batch",
                                            moduleIndex = index + 1,
                                            moduleCount = assets.size,
                                            notifyUser = false
                                        )
                                    if (result.completed) {
                                        if (result.wroteContent) {
                                            changedModules++
                                        } else {
                                            unchangedModules++
                                        }
                                    }
                                }
                            }

                        LOG.info(
                            "[FlutterAssetsGenerator #${project.name}] generation batch complete processedModules=${assets.size} changedModules=$changedModules unchangedModules=$unchangedModules totalElapsedMs=$totalElapsedMs"
                        )

                        if (changedModules > 0) {
                            showNotify(
                                "Generated assets updated in $changedModules module(s), $unchangedModules unchanged"
                            )
                        } else {
                            showNotify("All generated assets are up to date ($unchangedModules module(s))")
                        }
                    }
                }
            )
    }

    /** 生成单个模块文件 (异步) */
    fun generateOne(config: ModulePubSpecConfig) {
        if (!FileHelperNew.hasPluginConfig(config) || !FileHelperNew.isPluginEnabled(config)) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Plugin config missing or disabled, skipping generation"
            )
            deleteGeneratedFile(config)
            return
        }

        ProgressManager.getInstance()
            .run(
                object :
                    Task.Backgroundable(
                        project,
                        "Generating Assets for ${config.module.name}",
                        false
                    ) {
                    override fun run(indicator: ProgressIndicator) {
                        generateOneBlocking(config, indicator, mode = "single")
                    }
                }
            )
    }

    internal fun filterEnabledConfigs(configs: List<ModulePubSpecConfig>): List<ModulePubSpecConfig> {
        return configs.filter {
            FileHelperNew.hasPluginConfig(it) &&
                    FileHelperNew.isPluginEnabled(it) &&
                    it.assetVFiles.isNotEmpty()
        }
    }

    internal fun shouldDeleteGeneratedFile(config: ModulePubSpecConfig): Boolean {
        return FileHelperNew.hasPluginConfig(config) && !FileHelperNew.isPluginEnabled(config)
    }

    internal fun shouldShowSetupPrompt(configs: List<ModulePubSpecConfig>): Boolean {
        return configs.isEmpty()
    }

    internal fun shouldAutoAddTypedDependencies(config: ModulePubSpecConfig): Boolean {
        val isStringLeafMode = FileHelperNew.getLeafType(config) == Constants.LEAF_TYPE_STRING
        return !isStringLeafMode &&
                FileHelperNew.isAutoDetectionEnable(config) &&
                FileHelperNew.isAutoAddDependenciesEnable(config)
    }

    private fun logExcludedGenerationCandidates(configs: List<ModulePubSpecConfig>) {
        for (config in configs) {
            val hasPluginConfig = FileHelperNew.hasPluginConfig(config)
            val isEnabled = FileHelperNew.isPluginEnabled(config)
            val assetCount = config.assetVFiles.size
            if (hasPluginConfig && isEnabled && assetCount > 0) {
                continue
            }
            val configuredAssets =
                FileHelperNew.tryGetAssetsList(config.map)
                    ?.joinToString(prefix = "[", postfix = "]") ?: "[]"
            val reason =
                when {
                    !hasPluginConfig -> "missing flutter_assets_generator config"
                    !isEnabled -> "enable=false"
                    assetCount == 0 -> "no resolvable assets"
                    else -> "included"
                }
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}/${config.module.name}] generation candidate check: pubspec=${config.pubRoot.pubspec.path}, hasPluginConfig=$hasPluginConfig, enabled=$isEnabled, assetCount=$assetCount, configuredAssets=$configuredAssets, reason=$reason"
            )
        }
    }

    private fun cleanupDisabledConfigs(configs: List<ModulePubSpecConfig>) {
        configs.filter(::shouldDeleteGeneratedFile).forEach(::deleteGeneratedFile)
    }

    /**
     * 删除已生成的 Dart 文件. VirtualFile.delete 必须在 EDT 的 write action 中执行,
     * 调用方可能来自后台线程 (批量生成 / VFS 监听) 或 EDT (pubspec 保存监听), 这里统一调度.
     */
    internal fun deleteGeneratedFile(config: ModulePubSpecConfig): Boolean {
        val generatedFile = FileHelperNew.findGeneratedFile(config)
        if (generatedFile == null || !generatedFile.exists()) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}/${config.module.name}] No generated file to delete"
            )
            return false
        }

        var deleted = false
        val application = ApplicationManager.getApplication()
        val deleteTask = Runnable {
            if (project.isDisposed || !generatedFile.isValid) {
                return@Runnable
            }
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    generatedFile.delete(this)
                    deleted = true
                    LOG.info(
                        "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Deleted generated file: ${generatedFile.path}"
                    )
                    showNotify("${config.module.name} : generated assets removed because enable=false")
                } catch (e: Exception) {
                    LOG.warn(
                        "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Failed to delete ${generatedFile.path}",
                        e
                    )
                }
            }
        }
        if (application.isDispatchThread) {
            deleteTask.run()
        } else if (application.isReadAccessAllowed) {
            // 持有读锁时 invokeAndWait 会死锁, 退化为异步
            application.invokeLater(deleteTask)
        } else {
            application.invokeAndWait(deleteTask)
        }
        return deleted
    }

    private data class GenerationData(
        val content: String,
        val depsToAdd: Map<String, String>,
        val flutterVersion: SemanticVersion?,
        val assetTreeElapsedMs: Long,
        val dependencyCheckElapsedMs: Long,
        val flutterVersionElapsedMs: Long,
        val dependencySelectionElapsedMs: Long,
        val codegenElapsedMs: Long
    )

    private data class WriteMetrics(
        val dependencyAddElapsedMs: Long,
        val contentWriteElapsedMs: Long,
        val reformatElapsedMs: Long,
        val totalWriteElapsedMs: Long,
        val wroteContent: Boolean
    )

    private data class GenerationResult(
        val wroteContent: Boolean,
        val completed: Boolean
    )

    private data class ReadSnapshot(
        val rootNode: AssetNode,
        val hasSvg: Boolean,
        val hasLottie: Boolean,
        val hasRive: Boolean,
        val hasSvgDep: Boolean,
        val hasLottieDep: Boolean,
        val hasRiveDep: Boolean
    )

    private fun generateOneBlocking(
        config: ModulePubSpecConfig,
        indicator: ProgressIndicator?,
        mode: String,
        moduleIndex: Int? = null,
        moduleCount: Int? = null,
        notifyUser: Boolean = true
    ): GenerationResult {
        val moduleTag = "[FlutterAssetsGenerator #${project.name}/${config.module.name}]"
        val positionTag =
            if (moduleIndex != null && moduleCount != null) {
                " modulePosition=$moduleIndex/$moduleCount"
            } else {
                ""
            }

        var result = GenerationResult(wroteContent = false, completed = false)
        val totalElapsedMs =
            measureTimeMillis {
                indicator?.checkCanceled()
                indicator?.text =
                    if (moduleIndex != null && moduleCount != null) {
                        "Generating module $moduleIndex/$moduleCount: ${config.module.name}"
                    } else {
                        "Preparing asset tree for ${config.module.name}"
                    }

                val prepareElapsedMs: Long
                lateinit var data: GenerationData
                prepareElapsedMs =
                    measureTimeMillis {
                        data = prepareData(config)
                    }
                LOG.info(
                    "$moduleTag mode=$mode$positionTag prepareElapsedMs=$prepareElapsedMs assetTreeElapsedMs=${data.assetTreeElapsedMs} dependencyCheckElapsedMs=${data.dependencyCheckElapsedMs} flutterVersionElapsedMs=${data.flutterVersionElapsedMs} dependencySelectionElapsedMs=${data.dependencySelectionElapsedMs} codegenElapsedMs=${data.codegenElapsedMs} depsToAdd=${data.depsToAdd.keys}"
                )

                indicator?.checkCanceled()
                indicator?.text =
                    if (moduleIndex != null && moduleCount != null) {
                        "Writing module $moduleIndex/$moduleCount: ${config.module.name}"
                    } else {
                        "Writing generated file for ${config.module.name}"
                    }

                val edtDispatchRequestedAt = System.currentTimeMillis()
                var edtWaitElapsedMs = 0L
                var writeMetrics: WriteMetrics? = null
                ApplicationManager.getApplication().invokeAndWait {
                    if (!project.isDisposed) {
                        edtWaitElapsedMs = System.currentTimeMillis() - edtDispatchRequestedAt
                        WriteCommandAction.runWriteCommandAction(project) {
                            writeMetrics = performWrite(config, data, notifyUser)
                        }
                    }
                }

                val resolvedWriteMetrics = writeMetrics
                if (resolvedWriteMetrics == null) {
                    LOG.info("$moduleTag mode=$mode$positionTag writeSkipped=project-disposed")
                    return@measureTimeMillis
                }

                result =
                    GenerationResult(
                        wroteContent = resolvedWriteMetrics.wroteContent,
                        completed = true
                    )
                LOG.info(
                    "$moduleTag mode=$mode$positionTag edtWaitElapsedMs=$edtWaitElapsedMs dependencyAddElapsedMs=${resolvedWriteMetrics.dependencyAddElapsedMs} contentWriteElapsedMs=${resolvedWriteMetrics.contentWriteElapsedMs} reformatElapsedMs=${resolvedWriteMetrics.reformatElapsedMs} totalWriteElapsedMs=${resolvedWriteMetrics.totalWriteElapsedMs} wroteContent=${resolvedWriteMetrics.wroteContent} formatting=skipped-generated-file"
                )
            }

        LOG.info("$moduleTag mode=$mode$positionTag moduleTotalElapsedMs=$totalElapsedMs")
        return result
    }

    /** 准备数据 (后台线程执行) */
    private fun prepareData(config: ModulePubSpecConfig): GenerationData {
        var assetTreeElapsedMs = 0L
        var dependencyCheckElapsedMs = 0L
        val snapshot =
            ReadAction.nonBlocking<ReadSnapshot> {
                val ignorePath = FileHelperNew.getPathIgnore(config)
                val moduleRootPath = config.pubRoot.path

                // 1. 构建资源树 (Build Asset Tree)
                lateinit var rootNode: AssetNode
                assetTreeElapsedMs =
                    measureTimeMillis {
                        rootNode = AssetTreeBuilder.build(config.assetVFiles, moduleRootPath, ignorePath)
                    }

                // 2. 依赖检查
                lateinit var result: ReadSnapshot
                dependencyCheckElapsedMs =
                    measureTimeMillis {
                        val hasSvg = traverseFindType(rootNode, MediaType.SVG)
                        val hasLottie = traverseFindType(rootNode, MediaType.LOTTIE)
                        val hasRive = traverseFindType(rootNode, MediaType.RIVE)

                        val pubspecFile = config.pubRoot.pubspec
                        val hasSvgDep = DependencyHelper.hasDependency(project, pubspecFile, "flutter_svg")
                        val hasLottieDep = DependencyHelper.hasDependency(project, pubspecFile, "lottie")
                        val hasRiveDep = DependencyHelper.hasDependency(project, pubspecFile, "rive")

                        result =
                            ReadSnapshot(
                                rootNode = rootNode,
                                hasSvg = hasSvg,
                                hasLottie = hasLottie,
                                hasRive = hasRive,
                                hasSvgDep = hasSvgDep,
                                hasLottieDep = hasLottieDep,
                                hasRiveDep = hasRiveDep
                            )
                    }

                result
            }
                .expireWith(project)
                .executeSynchronously()

        val pubspecFile = config.pubRoot.pubspec
        val needsFlutterVersion =
            shouldAutoAddTypedDependencies(config) || snapshot.hasSvg

        // 检测 Flutter 版本 (IO / Process)，必须在 ReadAction 外执行，避免长读锁阻塞 EDT 写操作。
        var flutterVersion: SemanticVersion? = null
        val flutterVersionElapsedMs =
            measureTimeMillis {
                if (needsFlutterVersion) {
                    flutterVersion = FlutterVersionHelper.getFlutterVersion(project, pubspecFile)
                }
            }

        var hasSvgDep = snapshot.hasSvgDep
        var hasLottieDep = snapshot.hasLottieDep
        var hasRiveDep = snapshot.hasRiveDep
        val depsToAdd = mutableMapOf<String, String>()

        val dependencySelectionElapsedMs =
            measureTimeMillis {
                if (shouldAutoAddTypedDependencies(config)) {
                    if (snapshot.hasSvg && !hasSvgDep) {
                        val svgVersion = DependencyVersionSelector.getFlutterSvgVersion(flutterVersion)
                        depsToAdd["flutter_svg"] = svgVersion
                        hasSvgDep = true
                    }
                    if (snapshot.hasLottie && !hasLottieDep) {
                        val lottieVersion = DependencyVersionSelector.getLottieVersion(flutterVersion)
                        depsToAdd["lottie"] = lottieVersion
                        hasLottieDep = true
                    }
                    if (snapshot.hasRive && !hasRiveDep) {
                        val riveVersion = DependencyVersionSelector.getRiveVersion(flutterVersion)
                        depsToAdd["rive"] = riveVersion
                        hasRiveDep = true
                    }
                }
            }

        // flutter_svg 模板按项目实际依赖的版本选择; 刚要自动添加的依赖也算
        val flutterSvgVersion =
            depsToAdd["flutter_svg"]
                ?: ((config.map["dependencies"] as? Map<*, *>)?.get("flutter_svg") as? String)

        lateinit var content: String
        val codegenElapsedMs =
            measureTimeMillis {
                content =
                    DartClassGenerator(
                        snapshot.rootNode,
                        config,
                        snapshot.hasSvg,
                        hasSvgDep,
                        snapshot.hasLottie,
                        hasLottieDep,
                        snapshot.hasRive,
                        hasRiveDep,
                        flutterVersion,
                        flutterSvgVersion
                    )
                        .generate()
            }

        return GenerationData(
            content = content,
            depsToAdd = depsToAdd,
            flutterVersion = flutterVersion,
            assetTreeElapsedMs = assetTreeElapsedMs,
            dependencyCheckElapsedMs = dependencyCheckElapsedMs,
            flutterVersionElapsedMs = flutterVersionElapsedMs,
            dependencySelectionElapsedMs = dependencySelectionElapsedMs,
            codegenElapsedMs = codegenElapsedMs
        )
    }

    /** 执行写入 (EDT 执行) */
    private fun performWrite(
        config: ModulePubSpecConfig,
        data: GenerationData,
        notifyUser: Boolean
    ): WriteMetrics {
        val pubspecFile = config.pubRoot.pubspec
        val moduleTag = "[FlutterAssetsGenerator #${project.name}/${config.module.name}]"
        var dependencyAddElapsedMs = 0L
        var contentWriteElapsedMs = 0L
        var wroteContent = false

        val totalWriteElapsedMs =
            measureTimeMillis {
                if (data.depsToAdd.isNotEmpty()) {
                    LOG.info("$moduleTag addingMissingDependencies=${data.depsToAdd.keys}")
                    dependencyAddElapsedMs =
                        measureTimeMillis {
                            DependencyHelper.addDependencies(project, pubspecFile, data.depsToAdd)
                        }
                }

                val psiManager = PsiManager.getInstance(project)
                val psiDocumentManager = PsiDocumentManager.getInstance(project)
                FileHelperNew.getGeneratedFile(config).let { generated ->
                    psiManager.findFile(generated)?.let { dartFile ->
                        psiDocumentManager.getDocument(dartFile)?.let { document ->
                            if (document.text != data.content) {
                                wroteContent = true
                                contentWriteElapsedMs =
                                    measureTimeMillis {
                                        document.setText(data.content)
                                        psiDocumentManager.commitDocument(document)
                                    }
                                LOG.info(
                                    "$moduleTag writeElapsedMs=$contentWriteElapsedMs file=${generated.path}"
                                )

                                if (notifyUser) {
                                    showNotify("${config.module.name} : assets generate succeed")
                                }
                            } else {
                                LOG.info("$moduleTag writeSkipped=no-content-change file=${generated.path}")
                                if (notifyUser) {
                                    showNotify("${config.module.name} : nothing changed")
                                }
                            }
                        } ?: LOG.warn("$moduleTag documentUnavailable file=${generated.path}")
                    } ?: LOG.warn("$moduleTag psiFileUnavailable file=${generated.path}")
                }
            }

        return WriteMetrics(
            dependencyAddElapsedMs = dependencyAddElapsedMs,
            contentWriteElapsedMs = contentWriteElapsedMs,
            reformatElapsedMs = 0L,
            totalWriteElapsedMs = totalWriteElapsedMs,
            wroteContent = wroteContent
        )
    }

    /** 将所选择目录及子目录添加到yaml配置 */
    fun buildYaml(file: VirtualFile) {
        if (DumbService.isDumb(project)) {
            showNotify("Indexing in progress, please retry after indexing finishes")
            return
        }
        saveChanges()
        val modules = FileHelperNew.getAssets(project)
        val module =
            modules.filter { file.path.startsWith("${it.pubRoot.path}/") || file.path == it.pubRoot.path }
                .maxByOrNull { it.pubRoot.path.length }

        if (module == null) {
            showNotify("This module is not flutter module")
            return
        }

        val rootPath = "${module.pubRoot.path}/"
        val paths = mutableListOf<String>()
        if (file.isDirectory) {
            traversalDir(file, rootPath, paths)
        } else {
            paths.add(file.path.removePrefix(rootPath))
        }

        // 注意: tryGetAssetsList 返回的是缓存的配置对象, 必须复制后再修改, 否则会污染缓存
        val rawAssets = FileHelperNew.tryGetAssetsList(module.map)
        if (rawAssets != null && rawAssets.any { it !is String }) {
            // Flutter 3.16+ 支持 `- path: x/` + flavors/transformers 这种 map 条目, 整体重写会丢掉它们
            showNotify(
                "pubspec assets contains map-style entries (path:/flavors:), " +
                        "please add these paths manually: ${paths.joinToString(", ")}"
            )
            return
        }
        val existingAssets =
            rawAssets
                ?.mapNotNull { it as? String }
                ?.filter { configured ->
                    // 存在性检查必须相对于 pub 包根目录, 而不是 IntelliJ module 目录 (monorepo 下两者可能不同)
                    File(module.pubRoot.path, configured.trimEnd('/')).exists()
                }
                ?.toMutableList()
        if (existingAssets != null) {
            paths.removeIf { existingAssets.contains(it) }
        }

        val yamlFile = PsiManager.getInstance(project).findFile(module.pubRoot.pubspec) as? YAMLFile
        val flutterMapping =
            yamlFile
                ?.documents
                ?.firstOrNull()
                ?.topLevelValue
                ?.let { it as? YAMLMapping }
                ?.getKeyValueByKey("flutter")
                ?.value as? YAMLMapping
        if (flutterMapping == null) {
            showNotify("No 'flutter:' section found in ${module.pubRoot.pubspec.path}")
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val assetsValue = flutterMapping.getKeyValueByKey("assets")
            val stringBuilder = StringBuilder()
            existingAssets?.forEach { stringBuilder.append("    - $it\n") }
            paths.forEach { stringBuilder.append("    - $it\n") }
            val content = stringBuilder.toString().removeSuffix("\n")
            val generator = YAMLElementGenerator.getInstance(project)
            if (assetsValue == null) {
                flutterMapping.putKeyValue(generator.createYamlKeyValue("assets", content))
            } else {
                val yamlValue =
                    PsiTreeUtil.collectElementsOfType(
                        generator.createDummyYamlWithText(content),
                        YAMLSequence::class.java
                    )
                        .firstOrNull()
                if (yamlValue != null) {
                    assetsValue.setValue(yamlValue)
                }
            }
        }
        saveChanges()
        showNotify("Flutter: Configuration complete.")
    }

    // 递归遍历判断资源树中是否包含指定类型的文件
    private fun traverseFindType(node: AssetNode, type: MediaType): Boolean {
        if (node.type == type) return true
        for (child in node.children) {
            if (traverseFindType(child, type)) return true
        }
        return false
    }

    private fun saveChanges() {
        FileDocumentManager.getInstance().saveAllDocuments()
        PsiDocumentManager.getInstance(project).commitAllDocumentsUnderProgress()
    }

    private fun traversalDir(file: VirtualFile, rootPath: String, list: MutableList<String>) {
        if (file.isDirectory) {
            val name = file.name
            // 过滤分辨率相关的目录: 1.5x, 2.0x, 3.0x 等
            // 正则匹配: 数字开头 + 可选(.数字) + x结尾
            val isResolutionDir = name.matches(Regex("^[0-9]+(\\.[0-9]+)?x$"))
            if (!isResolutionDir) {
                list.add("${file.path.removePrefix(rootPath)}/")
            }
            file.children.forEach {
                if (it.isDirectory) {
                    traversalDir(it, rootPath, list)
                }
            }
        }
    }
}
