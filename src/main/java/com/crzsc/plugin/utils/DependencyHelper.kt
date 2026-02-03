package com.crzsc.plugin.utils

import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import java.io.File

object DependencyHelper {

    /**
     * 检查 pubspec.yaml 中是否存在指定依赖
     */
    fun hasDependency(project: Project, pubspecFile: VirtualFile, dependencyName: String): Boolean {
        val yamlFile = PsiManager.getInstance(project).findFile(pubspecFile) as? YAMLFile ?: return false
        val yamlMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return false
        val dependencies = yamlMapping.getKeyValueByKey("dependencies")?.value as? YAMLMapping
        return dependencies?.getKeyValueByKey(dependencyName) != null
    }

    /**
     * 向 pubspec.yaml 添加依赖
     * @param version 依赖版本，例如 "^1.0.0"
     */
    fun addDependency(project: Project, pubspecFile: VirtualFile, dependencyName: String, version: String) {
        if (hasDependency(project, pubspecFile, dependencyName)) return

        WriteCommandAction.runWriteCommandAction(project) {
            val yamlFile = PsiManager.getInstance(project).findFile(pubspecFile) as? YAMLFile ?: return@runWriteCommandAction
            val yamlMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return@runWriteCommandAction
            
            val dependenciesKV = yamlMapping.getKeyValueByKey("dependencies")
            val generator = YAMLElementGenerator.getInstance(project)

            if (dependenciesKV == null) {
                // 如果 dependencies 节点不存在（虽然在 Flutter 项目中不太可能），则创建一个新的
                 val newDep = generator.createYamlKeyValue("dependencies", "\n  $dependencyName: $version")
                 yamlMapping.putKeyValue(newDep)
            } else {
                val dependenciesMapping = dependenciesKV.value as? YAMLMapping
                if (dependenciesMapping != null) {
                    // 在现有的 dependencies 下添加新的键值对
                    val newDep = generator.createYamlKeyValue(dependencyName, version)
                    dependenciesMapping.putKeyValue(newDep)
                } else {
                    // dependencies 存在但结构异常
                }
            }
        }
        
        // 添加依赖后执行 flutter pub get
        runFlutterPubGet(project, pubspecFile)
    }
    
    /**
     * 执行 flutter pub get 命令
     */
    private fun runFlutterPubGet(project: Project, pubspecFile: VirtualFile) {
        val projectDir = pubspecFile.parent?.path ?: return
        
        ApplicationManager.getApplication().invokeLater {
            try {
                val processBuilder = ProcessBuilder("flutter", "pub", "get")
                processBuilder.directory(File(projectDir))
                processBuilder.redirectErrorStream(true)
                
                val process = processBuilder.start()
                
                // 在后台线程中等待命令完成
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        val exitCode = process.waitFor()
                        if (exitCode == 0) {
                            showNotify("Flutter pub get completed successfully")
                        } else {
                            showNotify("Flutter pub get failed with exit code: $exitCode")
                        }
                    } catch (e: Exception) {
                        showNotify("Failed to run flutter pub get: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                showNotify("Failed to start flutter pub get: ${e.message}")
            }
        }
    }
}
