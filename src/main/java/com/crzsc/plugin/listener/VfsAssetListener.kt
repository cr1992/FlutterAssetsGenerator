package com.crzsc.plugin.listener

import com.crzsc.plugin.utils.FileGenerator
import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import kotlin.system.measureTimeMillis

/**
 * 监听 VFS 变化, 命中资源目录时重新生成对应模块.
 * 通过 plugin.xml 的 projectListeners 注册, 每个项目一个实例.
 */
class VfsAssetListener(private val project: Project) : BulkFileListener {
    companion object {
        private val LOG = Logger.getInstance(VfsAssetListener::class.java)
        private const val TAG = "[FAG-LISTENER]"
        private const val DEBOUNCE_MS = 300

        /**
         * 这些目录下的变化永远不可能是 pubspec 里声明的资源, 直接丢弃.
         * 注意不包含 build / ephemeral: 它们可能是用户资源目录的名字, 真正的排除交给精确匹配.
         */
        private val ignoredSegments =
            setOf(".dart_tool", ".git", ".idea", ".gradle", ".pub-cache", ".symlinks", ".plugin_symlinks")

        /** 这些扩展名的文件不会被当作资源, 典型的是插件自己写出的 .dart */
        private val ignoredExtensions = setOf("dart", "iml", "lock")

        /**
         * 粗筛: 事件路径是否可能影响资源.
         * 不读任何配置, 只做字符串判断, 用来避免每次编辑 .dart 文件都触发全量扫描.
         * 不按项目根目录过滤: 模块的 content root 可能在项目目录之外 (symlink / 外部包).
         */
        internal fun mayAffectAssets(path: String): Boolean {
            if (path.isBlank()) return false
            if (ignoredSegments.any { path.contains("/$it/") }) {
                return false
            }
            val name = path.substringAfterLast('/')
            val ext = name.substringAfterLast('.', "")
            if (ext.isNotEmpty() && ext in ignoredExtensions) {
                return false
            }
            return true
        }
    }

    private val fileGenerator = FileGenerator(project)
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    internal val pendingEvents = mutableListOf<VFileEvent>()

    override fun after(events: List<VFileEvent>) {
        if (events.isEmpty() || project.isDisposed) {
            return
        }

        val relevant = events.filter { event -> collectCandidatePaths(event).any { mayAffectAssets(it) } }
        if (relevant.isEmpty()) {
            return
        }

        synchronized(pendingEvents) {
            pendingEvents.addAll(relevant)
        }

        alarm.cancelAllRequests()
        alarm.addRequest(
            {
                if (!project.isDisposed) {
                    processPendingEvents()
                }
            },
            DEBOUNCE_MS
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

    /**
     * 在池线程上执行: 用可取消、等待索引就绪的读操作拿到模块列表, 再在读锁外做路径匹配并触发生成.
     * 不直接用 ReadAction.compute, 避免索引期间抛 IndexNotReadyException 以及长读锁阻塞 EDT 写操作.
     */
    internal fun processPendingEvents() {
        val events: List<VFileEvent>
        synchronized(pendingEvents) {
            events = pendingEvents.toList()
            pendingEvents.clear()
        }
        if (events.isEmpty()) {
            return
        }

        val startedAt = System.currentTimeMillis()
        ReadAction.nonBlocking<List<ModulePubSpecConfig>> { FileHelperNew.getCachedAssets(project) }
            .inSmartMode(project)
            .expireWith(project)
            // onSuccess 会在读锁内回调; 改在 EDT 上收尾, 匹配是纯字符串比较, generateOne 只是提交后台任务
            .finishOnUiThread(ModalityState.defaultModalityState()) { assets ->
                if (project.isDisposed) return@finishOnUiThread
                val scanElapsedMs = System.currentTimeMillis() - startedAt
                LOG.info(
                    "$TAG project=${project.name} pendingEvents=${events.size} discoveredModules=${assets.size} scanElapsedMs=$scanElapsedMs"
                )
                val matched = matchModules(assets, events)
                for (config in matched) {
                    fileGenerator.generateOne(config)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
            .onError { error ->
                // 项目关闭 / 读操作被写操作打断后取消, 属于正常情况
                if (error is ProcessCanceledException || error is java.util.concurrent.CancellationException) {
                    LOG.debug("$TAG project=${project.name} module scan cancelled")
                } else {
                    LOG.warn("$TAG project=${project.name} failed to resolve modules", error)
                }
            }
    }

    /** 返回所有受影响的模块 (同一批事件可能涉及多个模块, 不能只处理第一个) */
    internal fun matchModules(
        assets: List<ModulePubSpecConfig>,
        events: List<VFileEvent>
    ): List<ModulePubSpecConfig> {
        val matched = linkedSetOf<ModulePubSpecConfig>()
        val matchingElapsedMs =
            measureTimeMillis {
                val candidatePathsPerEvent = events.map { collectCandidatePaths(it) }
                for (config in assets) {
                    if (!shouldHandleConfig(config)) {
                        continue
                    }
                    events.forEachIndexed { index, event ->
                        if (matched.contains(config)) return@forEachIndexed
                        for (candidatePath in candidatePathsPerEvent[index]) {
                            val matchedAsset = findMatchingAsset(config, candidatePath)
                            if (matchedAsset != null) {
                                LOG.info(
                                    "$TAG trigger module=${config.module.name} source=${event.javaClass.simpleName} changed=${normalizePath(candidatePath)} matchedAsset=${normalizePath(matchedAsset.path)}"
                                )
                                matched.add(config)
                                break
                            }
                        }
                    }
                }
            }
        LOG.info(
            "$TAG project=${project.name} pendingEvents=${events.size} matchedModules=${matched.size} matchingElapsedMs=$matchingElapsedMs"
        )
        return matched.toList()
    }

    private fun normalizePath(path: String): String {
        return path.removeSuffix("/")
    }
}
