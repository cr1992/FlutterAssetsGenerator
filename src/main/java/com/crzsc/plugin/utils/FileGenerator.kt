package com.crzsc.plugin.utils

import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.findModule
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence

class FileGenerator(private val project: Project) {
    companion object {
        private val LOG = Logger.getInstance(FileGenerator::class.java)
    }

    /** 为所有模块重新生成 */
    /** 为所有模块重新生成 */
    fun generateAll() {
        val assets = FileHelperNew.getAssets(project) as MutableList
        assets.removeAll {
            println("module : ${it.module} assets ${it.assetVFiles}")
            it.assetVFiles.isEmpty()
        }
        if (assets.isEmpty()) {
            showNotify("Please configure your assets path in pubspec.yaml")
            return
        }
        for (config in assets) {
            generateOne(config)
        }
    }

    /** 生成单个模块文件 (异步) */
    fun generateOne(config: ModulePubSpecConfig) {
        ProgressManager.getInstance()
            .run(
                object :
                    Task.Backgroundable(
                        project,
                        "Generating Assets for ${config.module.name}",
                        false
                    ) {
                    override fun run(indicator: ProgressIndicator) {
                        val data = prepareData(config)

                        // Switch to EDT for writing
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed) {
                                WriteCommandAction.runWriteCommandAction(project) {
                                    performWrite(config, data)
                                }
                            }
                        }
                    }
                }
            )
    }

    private data class GenerationData(
        val content: String,
        val depsToAdd: Map<String, String>,
        val flutterVersion: SemanticVersion?
    )

    /** 准备数据 (后台线程执行) */
    private fun prepareData(config: ModulePubSpecConfig): GenerationData {
        return ReadAction.compute<GenerationData, RuntimeException> {
            val ignorePath = FileHelperNew.getPathIgnore(config)
            val moduleRootPath = config.pubRoot.path

            // 1. 构建资源树 (Build Asset Tree)
            val rootNode = AssetTreeBuilder.build(config.assetVFiles, moduleRootPath, ignorePath)

            // 2. 依赖检查
            val hasSvg = traverseFindType(rootNode, MediaType.SVG)
            val hasLottie = traverseFindType(rootNode, MediaType.LOTTIE)

            val pubspecFile = config.pubRoot.pubspec
            var hasSvgDep = DependencyHelper.hasDependency(project, pubspecFile, "flutter_svg")
            var hasLottieDep = DependencyHelper.hasDependency(project, pubspecFile, "lottie")

            // 检测 Flutter 版本 (IO / Process)
            val flutterVersion = FlutterVersionHelper.getFlutterVersion(project, pubspecFile)

            val depsToAdd = mutableMapOf<String, String>()

            if (FileHelperNew.isAutoDetectionEnable(config)) {
                if (hasSvg && !hasSvgDep) {
                    val svgVersion = DependencyVersionSelector.getFlutterSvgVersion(flutterVersion)
                    depsToAdd["flutter_svg"] = svgVersion
                    hasSvgDep = true
                }
                if (hasLottie && !hasLottieDep) {
                    val lottieVersion = DependencyVersionSelector.getLottieVersion(flutterVersion)
                    depsToAdd["lottie"] = lottieVersion
                    hasLottieDep = true
                }
            }

            // 3. 生成代码 (Generate Code)
            val content =
                DartClassGenerator(rootNode, config, hasSvgDep, hasLottieDep, flutterVersion)
                    .generate()

            GenerationData(content, depsToAdd, flutterVersion)
        }
    }

    /** 执行写入 (EDT 执行) */
    private fun performWrite(config: ModulePubSpecConfig, data: GenerationData) {
        val pubspecFile = config.pubRoot.pubspec

        // 添加依赖
        if (data.depsToAdd.isNotEmpty()) {
            DependencyHelper.addDependencies(project, pubspecFile, data.depsToAdd)
        }

        // 写入文件
        val psiManager = PsiManager.getInstance(project)
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        FileHelperNew.getGeneratedFile(config).let { generated ->
            psiManager.findFile(generated)?.let { dartFile ->
                psiDocumentManager.getDocument(dartFile)?.let { document ->
                    if (document.text != data.content) {
                        document.setText(data.content)
                        psiDocumentManager.commitDocument(document)

                        try {
                            ReformatCodeProcessor(project, dartFile, null, false).run()
                        } catch (e: Exception) {
                            LOG.warn("Failed to reformat file: ${dartFile.name}", e)
                        }

                        showNotify("${config.module.name} : assets generate succeed")
                    } else {
                        showNotify("${config.module.name} : nothing changed")
                    }
                }
            }
        }
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
        ApplicationManager.getApplication().saveAll()
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
