package com.crzsc.plugin.utils

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

object SetupConfigurationHelper {
    private val LOG = Logger.getInstance(SetupConfigurationHelper::class.java)
    private val appPlatformDirs = listOf("android", "ios", "web", "macos", "linux", "windows")
    private const val PROGRAMMATIC_SAVE_SUPPRESSION_WINDOW_MS = 60_000L
    private val programmaticPubspecUpdates = ConcurrentHashMap<String, Long>()

    fun findTargetConfig(
        configs: List<ModulePubSpecConfig>,
        filePath: String?
    ): ModulePubSpecConfig? {
        if (filePath.isNullOrBlank()) {
            return null
        }
        return configs.firstOrNull { filePath == it.pubRoot.path }
    }

    fun findTargetConfig(
        configs: List<ModulePubSpecConfig>,
        filePaths: List<String>
    ): ModulePubSpecConfig? {
        val normalizedPaths = filePaths.filter { it.isNotBlank() }
        if (normalizedPaths.isEmpty()) {
            return null
        }
        return configs
            .filter { config -> normalizedPaths.any { path -> path == config.pubRoot.path } }
            .maxByOrNull { it.pubRoot.path.length }
    }

    fun findTargetConfig(project: Project, candidatePaths: List<String>): ModulePubSpecConfig? {
        val normalizedPaths = candidatePaths.filter { it.isNotBlank() }
        if (normalizedPaths.isEmpty()) {
            LOG.info(
                "[FlutterAssetsGenerator #SetupCurrentModule] project=${project.name} candidates=[] matched=null elapsedMs=0"
            )
            return null
        }

        val matchedRef = arrayOfNulls<ModulePubSpecConfig>(1)
        val elapsedMs =
            measureTimeMillis {
                val configs = FileHelperNew.getAssets(project)
                matchedRef[0] = findTargetConfig(configs, normalizedPaths)
            }
        val matched = matchedRef[0]
        LOG.info(
            "[FlutterAssetsGenerator #SetupCurrentModule] project=${project.name} candidates=$normalizedPaths matched=${matched?.pubRoot?.path ?: "null"} elapsedMs=$elapsedMs"
        )
        return matched
    }

    fun resolveCandidatePaths(e: AnActionEvent): List<String> {
        val candidates = linkedSetOf<String>()

        e.getData(CommonDataKeys.VIRTUAL_FILE)?.path?.let { candidates.add(it) }
        e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            ?.mapNotNull { it?.path }
            ?.forEach { candidates.add(it) }

        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        (psiElement as? PsiFileSystemItem)?.virtualFile?.path?.let { candidates.add(it) }
        psiElement?.containingFile?.virtualFile?.path?.let { candidates.add(it) }

        e.getData(LangDataKeys.IDE_VIEW)
            ?.directories
            ?.map { it.virtualFile.path }
            ?.forEach { candidates.add(it) }

        e.getData(LangDataKeys.MODULE_CONTEXT)
            ?.guessModuleDir()
            ?.path
            ?.let { candidates.add(it) }

        return candidates.toList()
    }

    fun findTargetConfig(project: Project, e: AnActionEvent): ModulePubSpecConfig? {
        return findTargetConfig(project, resolveCandidatePaths(e))
    }

    /**
     * 向 pubspec.yaml 添加默认配置
     * @return true 如果成功添加配置，false 如果配置已存在或写入失败
     */
    fun addDefaultConfiguration(project: Project, config: ModulePubSpecConfig): Boolean {
        return addDefaultConfiguration(project, config, null)
    }

    fun addDefaultConfiguration(
        project: Project,
        config: ModulePubSpecConfig,
        packageParameterEnabledOverride: Boolean?
    ): Boolean {
        val pubspecFile = config.pubRoot.pubspec
        val yamlFile =
            PsiManager.getInstance(project).findFile(pubspecFile) as? YAMLFile ?: return false
        val yamlMapping =
            yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return false

        if (yamlMapping.getKeyValueByKey("flutter_assets_generator") != null) {
            return false
        }

        var added = false
        WriteCommandAction.runWriteCommandAction(project) {
            val generator = YAMLElementGenerator.getInstance(project)
            val configContent =
                buildDefaultConfigurationContent(project, config, packageParameterEnabledOverride)

            val dummyYaml = generator.createDummyYamlWithText(configContent)
            val configMapping =
                PsiTreeUtil.collectElementsOfType(dummyYaml, YAMLMapping::class.java)
                    .firstOrNull()
                    ?: return@runWriteCommandAction
            val configKV = configMapping.keyValues.firstOrNull() ?: return@runWriteCommandAction
            yamlMapping.putKeyValue(configKV)
            added = true
        }

        if (added) {
            programmaticPubspecUpdates[pubspecFile.path] = System.currentTimeMillis()
            FileDocumentManager.getInstance().getDocument(pubspecFile)?.let {
                FileDocumentManager.getInstance().saveDocument(it)
            }
        }

        return added
    }

    fun consumeProgrammaticPubspecUpdate(path: String?): Boolean {
        if (path.isNullOrBlank()) {
            return false
        }
        val markedAt = programmaticPubspecUpdates.remove(path) ?: return false
        return System.currentTimeMillis() - markedAt <= PROGRAMMATIC_SAVE_SUPPRESSION_WINDOW_MS
    }

    internal fun buildDefaultConfigurationContent(
        project: Project,
        config: ModulePubSpecConfig,
        packageParameterEnabledOverride: Boolean? = null
    ): String {
        val packageParameterEnabled =
            packageParameterEnabledOverride
                ?: shouldEnablePackageParameterByDefault(project, config)
        return """
            flutter_assets_generator:
              enable: true
              output_dir: generated/
              output_filename: assets
              class_name: Assets
              auto_detection: true
              # Automatically add missing dependencies (rive, flutter_svg, lottie) default: true
              auto_add_dependencies: true
              # Options: robust (default), legacy (old style)
              style: robust
              # Leaf type: class (typed wrappers, default for robust) or string (raw path, default for legacy)
              leaf_type: class
              # Options: camel (default), snake
              name_style: camel
              package_parameter_enabled: $packageParameterEnabled
              named_with_parent: true
              path_ignore: []
            """.trimIndent()
    }

    internal fun shouldEnablePackageParameterByDefault(
        @Suppress("UNUSED_PARAMETER") project: Project,
        config: ModulePubSpecConfig
    ): Boolean {
        return !isFlutterApp(config) && !isAddToAppModule(config)
    }

    internal fun isFlutterApp(config: ModulePubSpecConfig): Boolean {
        val moduleDir = config.pubRoot.root
        val hasMainEntry = moduleDir.findFileByRelativePath("lib/main.dart") != null
        if (!hasMainEntry) {
            return false
        }
        return appPlatformDirs.any { dir -> moduleDir.findChild(dir)?.isDirectory == true }
    }

    internal fun isAddToAppModule(config: ModulePubSpecConfig): Boolean {
        val moduleDir = config.pubRoot.root
        if (moduleDir.findChild(".android")?.isDirectory == true ||
            moduleDir.findChild(".ios")?.isDirectory == true
        ) {
            return true
        }
        return isAddToAppModule(config.map)
    }

    internal fun isAddToAppModule(pubspecMap: Map<String, Any>): Boolean {
        val flutterMap = pubspecMap["flutter"] as? Map<*, *> ?: return false
        val moduleMap = flutterMap["module"] as? Map<*, *> ?: return false
        return moduleMap.isNotEmpty()
    }
}
