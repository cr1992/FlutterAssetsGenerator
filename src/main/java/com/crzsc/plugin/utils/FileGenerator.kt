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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import kotlin.system.measureTimeMillis
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.findModule
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
                                allConfigs =
                                    ReadAction.compute<List<ModulePubSpecConfig>, RuntimeException> {
                                        FileHelperNew.getAssets(project)
                                    }
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
        val isStringLeafMode =
            FileHelperNew.getGenerationStyle(config) == "robust" &&
                    FileHelperNew.getLeafType(config) == Constants.LEAF_TYPE_STRING
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

    internal fun deleteGeneratedFile(config: ModulePubSpecConfig): Boolean {
        val generatedFile = FileHelperNew.findGeneratedFile(config)
        if (generatedFile == null || !generatedFile.exists()) {
            LOG.info(
                "[FlutterAssetsGenerator #${project.name}/${config.module.name}] No generated file to delete"
            )
            return false
        }

        generatedFile.delete(this)
        LOG.info(
            "[FlutterAssetsGenerator #${project.name}/${config.module.name}] Deleted generated file: ${generatedFile.path}"
        )
        showNotify("${config.module.name} : generated assets removed because enable=false")
        return true
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
            ReadAction.compute<ReadSnapshot, RuntimeException> {
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
                        flutterVersion
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
        saveChanges()
        val modules = FileHelperNew.getAssets(project)
        val module =
            modules.filter { file.path.startsWith(it.pubRoot.path) }.maxByOrNull {
                it.pubRoot.path.length
            }

        if (module != null) {
            val paths = mutableListOf<String>()
            val rootPath = "${module.pubRoot.path}/"
            if (file.isDirectory) {
                traversalDir(file, rootPath, paths)
            } else {
                paths.add(file.path.removePrefix(rootPath))
            }
            val moduleAssets = FileHelperNew.tryGetAssetsList(module.map)
            if (moduleAssets != null) {
                val moduleDir = file.findModule(project)?.guessModuleDir()
                moduleAssets.removeIf {
                    var parentPath = moduleDir?.path
                    var path = it as String
                    path = path.removeSuffix(File.separator)
                    if (path.contains(File.separator)) {
                        val subIndex = path.lastIndexOf(File.separator)
                        parentPath = "$parentPath${File.separator}${path.take(subIndex + 1)}"
                        path = path.substring(subIndex + 1, path.length)
                    }
                    val asset = File(parentPath, path)
                    !asset.exists()
                }
                paths.removeIf { moduleAssets.contains(it) }
            }
            val yamlFile = module.pubRoot.pubspec.toPsiFile(project) as? YAMLFile
            yamlFile?.let {
                val psiElement =
                    yamlFile.node
                        .getChildren(null)
                        .firstOrNull()
                        ?.psi
                        ?.children
                        ?.firstOrNull()
                        ?.children
                        ?.firstOrNull { it.text.startsWith("flutter:") }
                if (psiElement != null) {
                    val yamlMapping = psiElement.children.first() as YAMLMapping
                    WriteCommandAction.runWriteCommandAction(project) {
                        var assetsValue =
                            yamlMapping.keyValues.firstOrNull { it.keyText == "assets" }
                        val stringBuilder = StringBuilder()
                        moduleAssets?.forEach { stringBuilder.append("    - $it\n") }
                        paths.forEach { stringBuilder.append("    - $it\n") }
                        stringBuilder.removeSuffix("\n")
                        if (assetsValue == null) {
                            assetsValue =
                                YAMLElementGenerator.getInstance(project)
                                    .createYamlKeyValue("assets", stringBuilder.toString())
                            yamlMapping.putKeyValue(assetsValue)
                        } else {
                            val yamlValue =
                                PsiTreeUtil.collectElementsOfType(
                                    YAMLElementGenerator.getInstance(project)
                                        .createDummyYamlWithText(
                                            stringBuilder.toString()
                                        ),
                                    YAMLSequence::class.java
                                )
                                    .iterator()
                                    .next()
                            assetsValue.setValue(yamlValue)
                        }
                    }
                }
            }
            saveChanges()
            showNotify("Flutter: Configuration complete.")
        } else {
            showNotify("This module is not flutter module")
        }
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
