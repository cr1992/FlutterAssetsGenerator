package com.crzsc.plugin.actions

import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.crzsc.plugin.utils.PluginUtils.showNotify
import com.crzsc.plugin.utils.SetupConfigurationHelper
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import kotlin.system.measureTimeMillis

/** 一键配置项目 Action 自动在 pubspec.yaml 中添加 flutter_assets_generator 配置块 */
class SetupProjectAction : AnAction() {
    companion object {
        private val LOG = Logger.getInstance(SetupProjectAction::class.java)
    }

    private data class SetupModuleTiming(
        val config: ModulePubSpecConfig,
        val added: Boolean,
        val elapsedMs: Long
    )

    private data class GenerationReadiness(
        val generatable: List<ModulePubSpecConfig>,
        val missingPluginConfig: List<ModulePubSpecConfig>,
        val disabled: List<ModulePubSpecConfig>,
        val noResolvableAssets: List<ModulePubSpecConfig>
    )

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        if (!FileHelperNew.shouldActivateFor(project!!)) {
            showNotify("This is not a Flutter project")
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Setting up Flutter Assets Generator", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Scanning Flutter modules"
                    lateinit var assets: List<ModulePubSpecConfig>
                    val scanElapsedMs =
                        measureTimeMillis {
                            assets =
                                ReadAction.compute<List<ModulePubSpecConfig>, RuntimeException> {
                                    FileHelperNew.getAssets(project)
                                }
                        }

                    LOG.info(
                        "[FlutterAssetsGenerator #SetupProject] project=${project.name} discoveredModules=${assets.size} scanElapsedMs=$scanElapsedMs"
                    )

                    if (assets.isEmpty()) {
                        showNotify("No Flutter modules found in project")
                        return
                    }

                    var configuredCount = 0
                    var setupModuleTimings = emptyList<SetupModuleTiming>()
                    val setupElapsedMs =
                        measureTimeMillis {
                            val timings = mutableListOf<SetupModuleTiming>()
                            ApplicationManager.getApplication().invokeAndWait {
                                assets.forEachIndexed { index, config ->
                                    indicator.checkCanceled()
                                    indicator.text =
                                        "Configuring module ${index + 1}/${assets.size}: ${config.module.name}"
                                    var added = false
                                    val moduleElapsedMs =
                                        measureTimeMillis {
                                            added =
                                                SetupConfigurationHelper.addDefaultConfiguration(
                                                    project,
                                                    config
                                                )
                                        }
                                    if (added) {
                                        configuredCount++
                                    }
                                    timings.add(
                                        SetupModuleTiming(
                                            config = config,
                                            added = added,
                                            elapsedMs = moduleElapsedMs
                                        )
                                    )
                                }
                            }
                            setupModuleTimings = timings.toList()
                        }

                    setupModuleTimings.forEach { timing ->
                        LOG.info(
                            "[FlutterAssetsGenerator #SetupProject] project=${project.name} module=${timing.config.module.name} pubspec=${timing.config.pubRoot.pubspec.path} setupModuleElapsedMs=${timing.elapsedMs} result=${if (timing.added) "added" else "skipped-existing"}"
                        )
                    }

                    LOG.info(
                        "[FlutterAssetsGenerator #SetupProject] project=${project.name} configuredCount=$configuredCount skippedCount=${assets.size - configuredCount} totalModules=${assets.size} setupElapsedMs=$setupElapsedMs avgSetupModuleElapsedMs=${averageElapsedMs(setupModuleTimings)}"
                    )

                    indicator.text = "Refreshing module configs"
                    lateinit var refreshedAssets: List<ModulePubSpecConfig>
                    val refreshElapsedMs =
                        measureTimeMillis {
                            refreshedAssets =
                                ReadAction.compute<List<ModulePubSpecConfig>, RuntimeException> {
                                    FileHelperNew.getAssets(project)
                                }
                        }
                    val readiness: GenerationReadiness
                    val readinessElapsedMs =
                        measureTimeMillis {
                            readiness = analyzeGenerationReadiness(project, refreshedAssets)
                        }
                    LOG.info(
                        "[FlutterAssetsGenerator #SetupProject] project=${project.name} refreshedModules=${refreshedAssets.size} refreshElapsedMs=$refreshElapsedMs readinessElapsedMs=$readinessElapsedMs generationReadiness generatable=${readiness.generatable.size} missingPluginConfig=${readiness.missingPluginConfig.size} disabled=${readiness.disabled.size} noResolvableAssets=${readiness.noResolvableAssets.size}"
                    )
                    logModulePaths("generatable", readiness.generatable)
                    logModulePaths("missingPluginConfig", readiness.missingPluginConfig)
                    logModulePaths("disabled", readiness.disabled)
                    logModulePaths("noResolvableAssets", readiness.noResolvableAssets)

                    if (configuredCount > 0) {
                        showNotify("Successfully configured $configuredCount module(s) with default settings")
                    } else {
                        showNotify("All modules already have flutter_assets_generator configuration")
                    }
                }
            }
        )
    }

    private fun analyzeGenerationReadiness(
        project: com.intellij.openapi.project.Project,
        configs: List<ModulePubSpecConfig>
    ): GenerationReadiness {
        if (configs.isEmpty()) {
            return GenerationReadiness(
                generatable = emptyList(),
                missingPluginConfig = emptyList(),
                disabled = emptyList(),
                noResolvableAssets = emptyList()
            )
        }
        val fileGenerator = FileGenerator(project = project)
        val generatable = mutableListOf<ModulePubSpecConfig>()
        val missingPluginConfig = mutableListOf<ModulePubSpecConfig>()
        val disabled = mutableListOf<ModulePubSpecConfig>()
        val noResolvableAssets = mutableListOf<ModulePubSpecConfig>()

        configs.forEach { config ->
            when {
                !FileHelperNew.hasPluginConfig(config) -> missingPluginConfig.add(config)
                !FileHelperNew.isPluginEnabled(config) -> disabled.add(config)
                config.assetVFiles.isEmpty() -> noResolvableAssets.add(config)
                fileGenerator.filterEnabledConfigs(listOf(config)).isNotEmpty() -> generatable.add(config)
                else -> noResolvableAssets.add(config)
            }
        }

        return GenerationReadiness(
            generatable = generatable,
            missingPluginConfig = missingPluginConfig,
            disabled = disabled,
            noResolvableAssets = noResolvableAssets
        )
    }

    private fun averageElapsedMs(timings: List<SetupModuleTiming>): Long {
        if (timings.isEmpty()) {
            return 0L
        }
        val totalElapsedMs = timings.fold(0L) { acc, timing -> acc + timing.elapsedMs }
        return totalElapsedMs / timings.size
    }

    private fun logModulePaths(label: String, configs: List<ModulePubSpecConfig>) {
        if (configs.isEmpty()) {
            return
        }
        val modules =
            configs.joinToString(separator = "; ") {
                "${it.module.name}:${it.pubRoot.pubspec.path}"
            }
        LOG.info(
            "[FlutterAssetsGenerator #SetupProject] $label modules=$modules"
        )
    }
}
