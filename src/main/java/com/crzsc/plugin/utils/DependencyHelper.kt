package com.crzsc.plugin.utils

import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import java.io.File
import java.nio.charset.Charset
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

object DependencyHelper {
    private val LOG =
        com.intellij.openapi.diagnostic.Logger.getInstance(DependencyHelper::class.java)

    /** 检查 pubspec.yaml 中是否存在指定依赖 */
    fun hasDependency(project: Project, pubspecFile: VirtualFile, dependencyName: String): Boolean {
        val yamlFile =
            PsiManager.getInstance(project).findFile(pubspecFile) as? YAMLFile ?: return false
        val yamlMapping =
            yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return false
        val dependencies = yamlMapping.getKeyValueByKey("dependencies")?.value as? YAMLMapping
        return dependencies?.getKeyValueByKey(dependencyName) != null
    }

    /** 批量添加依赖 */
    fun addDependencies(
        project: Project,
        pubspecFile: VirtualFile,
        dependenciesToAdd: Map<String, String>
    ) {
        if (dependenciesToAdd.isEmpty()) return

        LOG.info(
            "[FlutterAssetsGenerator #DependencyHelper] Adding dependencies to ${pubspecFile.path}: $dependenciesToAdd"
        )

        var hasChanges = false
        WriteCommandAction.runWriteCommandAction(project) {
            val yamlFile =
                PsiManager.getInstance(project).findFile(pubspecFile) as? YAMLFile
                    ?: return@runWriteCommandAction
            val yamlMapping =
                yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping
                    ?: return@runWriteCommandAction

            val dependenciesKV = yamlMapping.getKeyValueByKey("dependencies")
            val generator = YAMLElementGenerator.getInstance(project)

            if (dependenciesKV == null) {
                val content = StringBuilder("\n")
                dependenciesToAdd.forEach { (name, version) ->
                    content.append("  $name: $version\n")
                }
                val newDep = generator.createYamlKeyValue("dependencies", content.toString())
                yamlMapping.putKeyValue(newDep)
                hasChanges = true
            } else {
                val dependenciesMapping = dependenciesKV.value as? YAMLMapping
                if (dependenciesMapping != null) {
                    dependenciesToAdd.forEach { (name, version) ->
                        if (dependenciesMapping.getKeyValueByKey(name) == null) {
                            val newDep = generator.createYamlKeyValue(name, version)
                            dependenciesMapping.putKeyValue(newDep)
                            hasChanges = true
                        }
                    }
                }
            }
        }

        if (hasChanges) {
            val document =
                FileDocumentManager
                    .getInstance()
                    .getDocument(pubspecFile)
            if (document != null) {
                FileDocumentManager
                    .getInstance()
                    .saveDocument(document)
            }
            runFlutterPubGet(project, pubspecFile)
        }
    }

    /** 执行 flutter pub get 命令 */
    fun runFlutterPubGet(project: Project, pubspecFile: VirtualFile) {
        LOG.info(
            "[FlutterAssetsGenerator #DependencyHelper] Requesting pub get for ${pubspecFile.path}"
        )

        val sdk = FlutterSdk.getFlutterSdk(project)
        // 优先尝试从当前文件目录获取 PubRoot，确保是当前模块
        val moduleDir = pubspecFile.parent
        val root = if (moduleDir != null) PubRoot.forDirectory(moduleDir) else null

        LOG.info(
            "[FlutterAssetsGenerator #DependencyHelper] Resolved SDK: ${sdk?.homePath}, Root: ${root?.path}, ModuleDir: ${moduleDir?.path}"
        )

        // 核心校验：确保找到的 PubRoot 确实是当前 pubspecFile 所在的目录
        // 如果 PubRoot 解析到了上层目录（例如主工程），则视为不匹配
        val isRootMatch = root != null && root.pubspec == pubspecFile

        if (sdk != null && isRootMatch) {
            // 方案A: 使用 Flutter 插件原生方法 (有 UI，且路径匹配)
            LOG.info("[FlutterAssetsGenerator #DependencyHelper] Executing via Flutter Plugin API")
            sdk.startPubGet(root!!, project)
        } else {
            // 方案B: 降级到 CLI 执行 (无 Flutter 插件支持 或 路径不匹配时的兼容方案)
            // 显式指定工作目录为 pubspecFile 所在的目录
            LOG.info(
                "[FlutterAssetsGenerator #DependencyHelper] Executing via CLI fallback (Explicit WorkDir)"
            )
            val projectDirPath = moduleDir?.path ?: return
            val flutterPath = sdk?.homePath?.let { "$it/bin/flutter" } ?: "flutter"

            ProgressManager.getInstance()
                .run(
                    object :
                        Task.Backgroundable(project, "Running flutter pub get", false) {
                        override fun run(indicator: ProgressIndicator) {
                            try {
                                val commandLine = GeneralCommandLine()
                                commandLine.exePath = flutterPath
                                commandLine.addParameters("pub", "get")
                                commandLine.workDirectory = File(projectDirPath)
                                commandLine.charset = Charset.forName("UTF-8")
                                commandLine.withParentEnvironmentType(
                                    GeneralCommandLine.ParentEnvironmentType.CONSOLE
                                )

                                val handler = CapturingProcessHandler(commandLine)
                                val output = handler.runProcess()

                                if (output.exitCode == 0) {
                                    showNotify("Flutter pub get completed successfully")
                                } else {
                                    val errorMsg = output.stderr.ifEmpty { output.stdout }
                                    LOG.warn(
                                        "[FlutterAssetsGenerator #DependencyHelper] CLI failed: $errorMsg"
                                    )
                                    showNotify(
                                        "Flutter pub get failed (${output.exitCode}): $errorMsg"
                                    )
                                }
                            } catch (e: Exception) {
                                LOG.warn(
                                    "[FlutterAssetsGenerator #DependencyHelper] CLI execution error",
                                    e
                                )
                                showNotify("Failed to start flutter pub get: ${e.message}")
                            }
                        }
                    }
                )
        }
    }
}
