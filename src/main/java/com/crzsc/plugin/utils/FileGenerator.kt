package com.crzsc.plugin.utils

import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import io.flutter.utils.FlutterModuleUtils
import java.io.File
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.findModule
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence

class FileGenerator(private val project: Project) {
    companion object {
        private val LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(FileGenerator::class.java)
    }

    /** 为所有模块重新生成 */
    fun generateAll() {
        WriteCommandAction.runWriteCommandAction(project) {
            val assets = FileHelperNew.getAssets(project) as MutableList
            assets.removeAll {
                println("module : ${it.module} assets ${it.assetVFiles}")
                it.assetVFiles.isEmpty()
            }
            if (assets.isEmpty()) {
                showNotify("Please configure your assets path in pubspec.yaml")
                return@runWriteCommandAction
            }
            for (config in assets) {
                generateWithConfig(config)
            }
        }
    }

    /** 生成单个模块文件 */
    fun generateOne(config: ModulePubSpecConfig) {
        WriteCommandAction.runWriteCommandAction(project) { generateWithConfig(config) }
    }

    private fun generateWithConfig(config: ModulePubSpecConfig) {
        val ignorePath = FileHelperNew.getPathIgnore(config)
        val moduleRootPath = config.pubRoot.path

        // 1. 构建资源树 (Build Asset Tree)
        // 根据 pubspec.yaml 配置的 assets 路径，递归扫描构建资源树
        val rootNode = AssetTreeBuilder.build(config.assetVFiles, moduleRootPath, ignorePath)

        // 2. 依赖检查与自动注入 (Dependency Check & Injection)
        // 扫描资源树，判断是否包含 SVG 或 Lottie 文件
        val hasSvg = traverseFindType(rootNode, MediaType.SVG)
        val hasLottie = traverseFindType(rootNode, MediaType.LOTTIE)

        val pubspecFile = config.pubRoot.pubspec
        var hasSvgDep = DependencyHelper.hasDependency(project, pubspecFile, "flutter_svg")
        var hasLottieDep = DependencyHelper.hasDependency(project, pubspecFile, "lottie")

        // 自动添加依赖 (如果开启了 auto_detection)
        if (FileHelperNew.isAutoDetectionEnable(config)) {
            // 检测 Flutter 版本
            val flutterVersion = FlutterVersionHelper.getFlutterVersion(project, pubspecFile)

            val depsToAdd = mutableMapOf<String, String>()

            if (hasSvg) {
                if (!hasSvgDep) {
                    val svgVersion = DependencyVersionSelector.getFlutterSvgVersion(flutterVersion)
                    depsToAdd["flutter_svg"] = svgVersion
                    hasSvgDep = true
                } else {
                    LOG.info(
                        "[FlutterAssetsGenerator #FileGenerator] [generateWithConfig] flutter_svg dependency already exists, skipping addition"
                    )
                }
            }
            if (hasLottie) {
                if (!hasLottieDep) {
                    val lottieVersion = DependencyVersionSelector.getLottieVersion(flutterVersion)
                    depsToAdd["lottie"] = lottieVersion
                    hasLottieDep = true
                } else {
                    LOG.info(
                        "[FlutterAssetsGenerator #FileGenerator] [generateWithConfig] lottie dependency already exists, skipping addition"
                    )
                }
            }

            if (depsToAdd.isNotEmpty()) {
                DependencyHelper.addDependencies(project, pubspecFile, depsToAdd)
            }
        }

        // 3. 生成代码 (Generate Code)
        // 检测 Flutter 版本用于生成兼容的模板
        val flutterVersion = FlutterVersionHelper.getFlutterVersion(project, pubspecFile)
        // 即使没有依赖，也可能生成 path 常量，但不会生成 .svg()/.lottie() 方法
        val content =
            DartClassGenerator(rootNode, config, hasSvgDep, hasLottieDep, flutterVersion)
                .generate()

        // 4. 写入文件
        val psiManager = PsiManager.getInstance(project)
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        FileHelperNew.getGeneratedFile(config).let { generated ->
            psiManager.findFile(generated)?.let { dartFile ->
                psiDocumentManager.getDocument(dartFile)?.let { document ->
                    if (document.text != content) {
                        document.setText(content)
                        psiDocumentManager.commitDocument(document)

                        // 恢复自动格式化
                        // 因为现在的生成过程已经在 invokeLater 中执行,且在 WriteCommandAction 中
                        // 所以可以安全地执行格式化
                        try {
                            CodeStyleManager.getInstance(project).reformat(dartFile)
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
        var module: ModulePubSpecConfig? = null
        for (m in modules) {
            if (file.path.startsWith(m.pubRoot.path)) {
                module = m
                break
            }
        }
        if (module != null && FlutterModuleUtils.isFlutterModule(module.module)) {
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
            list.add("${file.path.removePrefix(rootPath)}/")
            file.children.forEach {
                if (it.isDirectory) {
                    traversalDir(it, rootPath, list)
                }
            }
        }
    }
}
