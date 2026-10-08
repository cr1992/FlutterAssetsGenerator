package com.crzsc.plugin.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.flutter.sdk.FlutterSdk
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import org.yaml.snakeyaml.Yaml

/** Flutter 版本检测工具 */
object FlutterVersionHelper {
    private val LOG = Logger.getInstance(FlutterVersionHelper::class.java)
    private const val TAG = "[FlutterAssetsGenerator #FlutterVersionHelper]"
    private const val COMMAND_TIMEOUT_MS = 20_000

    // 版本缓存: Key = SDK路径, Value = 版本号
    private val versionCache = ConcurrentHashMap<String, SemanticVersion>()
    // 进行中的版本探测，避免多模块并发时重复执行慢路径
    private val versionTasks = ConcurrentHashMap<String, FutureTask<SemanticVersion?>>()
    // 探测失败的 SDK, 本次会话内不再重试 (否则 generateAll 会对每个模块各等一次超时)
    private val failedSdks = ConcurrentHashMap.newKeySet<String>()

    /**
     * 从 Flutter SDK 或 pubspec.yaml 获取 Flutter 版本
     * @return Flutter 版本,如果无法解析则返回 null
     */
    @RequiresBackgroundThread
    fun getFlutterVersion(project: Project, pubspecFile: VirtualFile): SemanticVersion? {
        getFlutterVersionFromSdk(project)?.let {
            return it
        }
        return getFlutterVersionFromPubspec(pubspecFile)
    }

    /** 从 Flutter SDK 获取版本 优先级: 缓存 > bin/cache/flutter.version.json > version 文件 > 命令执行 */
    private fun getFlutterVersionFromSdk(project: Project): SemanticVersion? {
        val sdk = FlutterSdk.getFlutterSdk(project)
        val sdkPath = sdk?.homePath ?: return null

        versionCache[sdkPath]?.let {
            return it
        }
        if (failedSdks.contains(sdkPath)) {
            return null
        }

        val newTask =
            FutureTask<SemanticVersion?> {
                val version =
                    readVersionFromSdkFiles(sdkPath) ?: getFlutterVersionFromCommand(sdkPath)
                if (version != null) {
                    versionCache[sdkPath] = version
                } else {
                    LOG.warn("$TAG Could not detect Flutter version for $sdkPath, will not retry this session")
                    failedSdks.add(sdkPath)
                }
                version
            }

        val task = versionTasks.putIfAbsent(sdkPath, newTask) ?: newTask
        val isOwner = task === newTask
        if (isOwner) {
            LOG.info("$TAG Starting version detection for $sdkPath")
            task.run()
        } else {
            LOG.info("$TAG Waiting for in-flight version detection for $sdkPath")
        }

        return try {
            task.get()
        } catch (e: Exception) {
            LOG.warn("$TAG Failed to resolve Flutter version for $sdkPath", e)
            null
        } finally {
            if (isOwner) {
                versionTasks.remove(sdkPath, task)
            }
        }
    }

    /**
     * 从 SDK 自带的文件读取版本.
     * 新版 SDK (3.2x+) 只有 bin/cache/flutter.version.json, 旧版才有根目录 version 文件, 两种都尝试.
     */
    internal fun readVersionFromSdkFiles(sdkPath: String): SemanticVersion? {
        readVersionJson(File(sdkPath, "bin/cache/flutter.version.json"))?.let { return it }
        readLegacyVersionFile(File(sdkPath, "version"))?.let { return it }
        LOG.info("$TAG No version file found under $sdkPath")
        return null
    }

    internal fun readVersionJson(file: File): SemanticVersion? {
        if (!file.isFile) return null
        return try {
            // 文件很小, 用正则直接提取 frameworkVersion, 避免引入 JSON 依赖
            val text = file.readText()
            val match = Regex("\"frameworkVersion\"\\s*:\\s*\"([^\"]+)\"").find(text)
            val version = match?.groupValues?.get(1)?.let { parseVersion(it) }
            if (version != null) {
                LOG.info("$TAG Detected Flutter version from flutter.version.json: $version")
            }
            version
        } catch (e: Exception) {
            LOG.info("$TAG Failed to read ${file.path}: ${e.message}")
            null
        }
    }

    internal fun readLegacyVersionFile(file: File): SemanticVersion? {
        if (!file.isFile) return null
        return try {
            val version = parseVersion(file.readText().trim())
            if (version != null) {
                LOG.info("$TAG Detected Flutter version from version file: $version")
            }
            version
        } catch (e: Exception) {
            LOG.info("$TAG Failed to read ${file.path}: ${e.message}")
            null
        }
    }

    /** 通过执行 flutter --version 命令获取版本 (最慢, 带超时, Windows 下使用 flutter.bat) */
    @RequiresBackgroundThread
    private fun getFlutterVersionFromCommand(sdkPath: String): SemanticVersion? {
        try {
            val executable = if (SystemInfo.isWindows) "flutter.bat" else "flutter"
            val flutterCommand = File(sdkPath, "bin/$executable")
            if (!flutterCommand.exists()) {
                LOG.info("$TAG Flutter executable not found: ${flutterCommand.path}")
                return null
            }

            val commandLine =
                GeneralCommandLine(flutterCommand.path, "--version", "--no-version-check")
                    .withWorkDirectory(sdkPath)
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            val output = CapturingProcessHandler(commandLine).runProcess(COMMAND_TIMEOUT_MS)
            if (output.isTimeout) {
                LOG.warn("$TAG flutter --version timed out after ${COMMAND_TIMEOUT_MS}ms")
                return null
            }

            return parseVersionFromCommandOutput(output.stdout)?.also {
                LOG.info("$TAG Detected Flutter version from command: $it")
            }
        } catch (e: Exception) {
            LOG.info("$TAG Failed to get Flutter version from command: ${e.message}")
        }
        return null
    }

    /** 解析输出, 格式类似: "Flutter 3.10.0 • channel stable • ..." */
    internal fun parseVersionFromCommandOutput(output: String): SemanticVersion? {
        val match = Regex("Flutter\\s+(\\d+\\.\\d+\\.\\d+)").find(output) ?: return null
        return parseVersion(match.groupValues[1])
    }

    /** 从 pubspec.yaml 获取 Flutter SDK 版本约束 (下界, 仅作兜底) */
    private fun getFlutterVersionFromPubspec(pubspecFile: VirtualFile): SemanticVersion? {
        try {
            val data = Yaml().load<Map<*, *>>(String(pubspecFile.contentsToByteArray()))
            val environment = data?.get("environment") as? Map<*, *>
            val flutterConstraint = environment?.get("flutter") as? String
            if (flutterConstraint != null) {
                LOG.info("$TAG Found Flutter constraint in pubspec: $flutterConstraint")
                return parseVersionConstraint(flutterConstraint)
            }
        } catch (e: Exception) {
            LOG.warn("$TAG Failed to read Flutter version from pubspec", e)
        }
        return null
    }

    /** 解析版本约束字符串 支持格式: ">=3.0.0", "^3.0.0", "3.0.0", ">=3.0.0 <4.0.0" */
    internal fun parseVersionConstraint(constraint: String): SemanticVersion? {
        val match = Regex("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?").find(constraint) ?: return null
        return parseVersion(match.value)
    }

    /** 解析版本号字符串 格式: "3.0.0" 或 "3.0", 允许带 "-pre" 之类后缀 */
    internal fun parseVersion(versionString: String): SemanticVersion? {
        val core = versionString.trim().substringBefore('-').substringBefore('+')
        val parts = core.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return SemanticVersion(major, minor, patch)
    }
}

/** 语义化版本号 */
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) :
    Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"
}
