package com.crzsc.plugin.listener

import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.Alarm
import kotlin.system.measureTimeMillis

class VfsAssetListener(private val project: Project) : BulkFileListener {
    companion object {
        private val LOG = Logger.getInstance(VfsAssetListener::class.java)
        private const val TAG = "[FAG-LISTENER]"
    }

    private val fileGenerator = FileGenerator(project)
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    internal val pendingEvents = mutableListOf<VFileEvent>()

    override fun after(events: List<VFileEvent>) {
        if (events.isEmpty()) {
            return
        }

        synchronized(pendingEvents) {
            pendingEvents.addAll(events)
        }

        alarm.cancelAllRequests()
        alarm.addRequest(
            {
                if (!project.isDisposed) {
                    processPendingEvents()
                }
            },
            300
        )
    }

    internal fun shouldHandleConfig(config: ModulePubSpecConfig): Boolean {
        return FileHelperNew.hasPluginConfig(config) &&
                FileHelperNew.isPluginEnabled(config) &&
                FileHelperNew.isAutoDetectionEnable(config)
    }

    internal fun collectCandidatePaths(event: VFileEvent): List<String> {
        val candidates = linkedSetOf<String>()

        val eventPath = event.path
        if (eventPath.isNotBlank()) {
            candidates.add(eventPath)
        }

        event.file?.path?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }

        if (event is VFileMoveEvent) {
            candidates.add("${event.oldParent.path}/${event.file.name}")
        }

        if (event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME) {
            event.file.parent?.path?.let { parentPath ->
                (event.oldValue as? String)?.let { oldName ->
                    candidates.add("$parentPath/$oldName")
                }
                (event.newValue as? String)?.let { newName ->
                    candidates.add("$parentPath/$newName")
                }
            }
        }

        return candidates.toList()
    }

    internal fun isAssetChange(config: ModulePubSpecConfig, changedPath: String?): Boolean {
        return findMatchingAsset(config, changedPath) != null
    }

    private fun findMatchingAsset(config: ModulePubSpecConfig, changedPath: String?): VirtualFile? {
        if (changedPath.isNullOrBlank()) {
            return null
        }

        val normalizedChangedPath = normalizePath(changedPath)
        for (assetFile in config.assetVFiles) {
            val assetPath = normalizePath(assetFile.path)
            val matched =
                if (assetFile.isDirectory) {
                    normalizedChangedPath == assetPath ||
                            normalizedChangedPath.startsWith("$assetPath/")
                } else {
                    normalizedChangedPath == assetPath
                }
            if (matched) {
                return assetFile
            }
        }
        return null
    }

    internal fun processPendingEvents() {
        val events: List<VFileEvent>
        synchronized(pendingEvents) {
            events = pendingEvents.toList()
            pendingEvents.clear()
        }

        lateinit var assets: List<ModulePubSpecConfig>
        val scanElapsedMs =
            measureTimeMillis {
                assets =
                    ReadAction.compute<List<ModulePubSpecConfig>, RuntimeException> {
                        FileHelperNew.getAssets(project)
                    }
            }
        LOG.info(
            "$TAG project=${project.name} pendingEvents=${events.size} discoveredModules=${assets.size} scanElapsedMs=$scanElapsedMs"
        )

        val matchingElapsedMs =
            measureTimeMillis {
                for (config in assets) {
                    if (!shouldHandleConfig(config)) {
                        continue
                    }

                    for (event in events) {
                        val candidatePaths = collectCandidatePaths(event)
                        if (candidatePaths.isEmpty()) {
                            LOG.info(
                                "$TAG skip module=${config.module.name} source=${event.javaClass.simpleName} reason=no-candidate-path path=${event.path} file=${pathOf(event.file)}"
                            )
                            continue
                        }

                        var matchedAsset: VirtualFile? = null
                        var matchedPath: String? = null
                        for (candidatePath in candidatePaths) {
                            matchedAsset = findMatchingAsset(config, candidatePath)
                            if (matchedAsset != null) {
                                matchedPath = normalizePath(candidatePath)
                                break
                            }
                        }

                        if (matchedAsset != null) {
                            LOG.info(
                                "$TAG trigger module=${config.module.name} source=${event.javaClass.simpleName} changed=$matchedPath matchedAsset=${normalizePath(matchedAsset.path)} candidates=$candidatePaths"
                            )
                            fileGenerator.generateOne(config)
                            return
                        }

                        LOG.info(
                            "$TAG skip module=${config.module.name} source=${event.javaClass.simpleName} candidates=$candidatePaths reason=no-asset-match"
                        )
                    }
                }
            }
        LOG.info(
            "$TAG project=${project.name} pendingEvents=${events.size} matchingElapsedMs=$matchingElapsedMs"
        )
    }

    private fun normalizePath(path: String): String {
        return path.removeSuffix("/")
    }
    private fun pathOf(file: VirtualFile?): String = file?.path ?: "<null>"
}
