package com.crzsc.plugin.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.flutter.sdk.FlutterSdk
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import org.yaml.snakeyaml.Yaml

/** Flutter 版本检测工具 */
object FlutterVersionHelper {
    private val LOG = Logger.getInstance(FlutterVersionHelper::class.java)

    // 版本缓存: Key = SDK路径, Value = 版本号
    private val versionCache = ConcurrentHashMap<String, SemanticVersion>()

    /**
     * 从 pubspec.yaml 或 Flutter SDK 获取 Flutter 版本
     * @param project 项目实例
     * @param pubspecFile pubspec.yaml 文件
     * @return Flutter 版本,如果无法解析则返回 null
     */
    @RequiresBackgroundThread
    fun getFlutterVersion(project: Project, pubspecFile: VirtualFile): SemanticVersion? {
        // 优先尝试从 Flutter SDK 获取实际版本
        getFlutterVersionFromSdk(project)?.let {
            return it
        }

        // 降级到从 pubspec.yaml 读取约束
        return getFlutterVersionFromPubspec(pubspecFile)
    }

    /** 从 Flutter SDK 获取版本 优先级: 缓存 > version 文件 > 命令执行 */
    private fun getFlutterVersionFromSdk(project: Project): SemanticVersion? {
        val sdk = FlutterSdk.getFlutterSdk(project)
        val sdkPath = sdk?.homePath ?: return null

        // 1. 先从缓存读取
        versionCache[sdkPath]?.let {
            LOG.info(
                "[FlutterAssetsGenerator #FlutterVersionHelper] Using cached version for $sdkPath: $it"
            )
            return it
        }

        // 2. 尝试从 SDK 的 version 文件读取 (最快)
        getFlutterVersionFromFile(sdkPath)?.let { version ->
            versionCache[sdkPath] = version
            LOG.info(
                "[FlutterAssetsGenerator #FlutterVersionHelper] Read version from file: $version"
            )
            return version
        }

        // 3. 降级到执行命令 (最慢,但最可靠)
        getFlutterVersionFromCommand(project, sdkPath)?.let { version ->
            versionCache[sdkPath] = version
            return version
        }

        return null
    }

    /** 从 Flutter SDK 的 version 文件读取版本 文件位置: ${sdkPath}/version */
    private fun getFlutterVersionFromFile(sdkPath: String): SemanticVersion? {
        try {
            val versionFile = File(sdkPath, "version")
            if (!versionFile.exists()) {
                LOG.info(
                    "[FlutterAssetsGenerator #FlutterVersionHelper] Version file not found: ${versionFile.absolutePath}"
                )
                return null
            }

            val versionString = versionFile.readText().trim()
            val version = parseVersion(versionString)

            if (version != null) {
                LOG.info(
                    "[FlutterAssetsGenerator #FlutterVersionHelper] Detected Flutter version from file: $version"
                )
            }

            return version
        } catch (e: Exception) {
            LOG.info(
                "[FlutterAssetsGenerator #FlutterVersionHelper] Failed to read version file: ${e.message}"
            )
            return null
        }
    }

    /** 通过执行 flutter --version 命令获取 Flutter SDK 版本 */
    @RequiresBackgroundThread
    private fun getFlutterVersionFromCommand(project: Project, sdkPath: String): SemanticVersion? {
        try {
            val flutterCommand = "$sdkPath/bin/flutter"

            LOG.info(
                "[FlutterAssetsGenerator #FlutterVersionHelper] Using flutter command: $flutterCommand"
            )

            val process = Runtime.getRuntime().exec("$flutterCommand --version")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // 解析输出,格式类似: "Flutter 3.10.0 • channel stable • ..."
            val versionRegex = Regex("Flutter\\s+(\\d+\\.\\d+\\.\\d+)")
            val matchResult = versionRegex.find(output)

            if (matchResult != null) {
                val versionString = matchResult.groupValues[1]
                val version = parseVersion(versionString)
                LOG.info(
                    "[FlutterAssetsGenerator #FlutterVersionHelper] Detected Flutter version from command: $version"
                )
                return version
            }
        } catch (e: Exception) {
            LOG.info(
                "[FlutterAssetsGenerator #FlutterVersionHelper] Failed to get Flutter version from command: ${e.message}"
            )
        }

        return null
    }

    /** 从 pubspec.yaml 获取 Flutter SDK 版本约束 */
    private fun getFlutterVersionFromPubspec(pubspecFile: VirtualFile): SemanticVersion? {
        try {
            val yaml = Yaml()
            val data = yaml.load<Map<*, *>>(FileInputStream(pubspecFile.path))

            // 读取 environment.flutter 约束
            val environment = data["environment"] as? Map<*, *>
            val flutterConstraint = environment?.get("flutter") as? String

            if (flutterConstraint != null) {
                LOG.info(
                    "[FlutterAssetsGenerator #FlutterVersionHelper] Found Flutter constraint in pubspec: $flutterConstraint"
                )
                return parseVersionConstraint(flutterConstraint)
            }

            LOG.info(
                "[FlutterAssetsGenerator #FlutterVersionHelper] No Flutter version constraint found in pubspec.yaml"
            )
        } catch (e: Exception) {
            LOG.warn(
                "[FlutterAssetsGenerator #FlutterVersionHelper] Failed to read Flutter version from pubspec",
                e
            )
        }

        return null
    }

    /** 解析版本约束字符串 支持格式: ">=3.0.0", "^3.0.0", "3.0.0" */
    private fun parseVersionConstraint(constraint: String): SemanticVersion? {
        try {
            // 移除约束符号,提取版本号
            val versionString =
                constraint
                    .replace(">=", "")
                    .replace("^", "")
                    .replace("<", "")
                    .trim()
                    .split(" ")[0] // 取第一个版本号

            return parseVersion(versionString)
        } catch (e: Exception) {
            LOG.warn(
                "[FlutterAssetsGenerator #FlutterVersionHelper] Failed to parse version constraint: $constraint",
                e
            )
            return null
        }
    }

    /** 解析版本号字符串 格式: "3.0.0" 或 "3.0" */
    private fun parseVersion(versionString: String): SemanticVersion? {
        try {
            val parts = versionString.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            return SemanticVersion(major, minor, patch)
        } catch (e: Exception) {
            LOG.warn(
                "[FlutterAssetsGenerator #FlutterVersionHelper] Failed to parse version: $versionString",
                e
            )
            return null
        }
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
