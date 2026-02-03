package com.crzsc.plugin.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.yaml.snakeyaml.Yaml
import java.io.FileInputStream

/**
 * Flutter 版本检测工具
 */
object FlutterVersionHelper {
    private val LOG = Logger.getInstance(FlutterVersionHelper::class.java)
    
    /**
     * 从 pubspec.yaml 或 Flutter SDK 获取 Flutter 版本
     * @param pubspecFile pubspec.yaml 文件
     * @return Flutter 版本,如果无法解析则返回 null
     */
    fun getFlutterVersion(pubspecFile: VirtualFile): SemanticVersion? {
        // 优先尝试从 Flutter SDK 命令获取实际版本
        getFlutterVersionFromCommand()?.let { return it }
        
        // 降级到从 pubspec.yaml 读取约束
        return getFlutterVersionFromPubspec(pubspecFile)
    }
    
    /**
     * 通过执行 flutter --version 命令获取 Flutter SDK 版本
     */
    private fun getFlutterVersionFromCommand(): SemanticVersion? {
        try {
            val process = Runtime.getRuntime().exec("flutter --version")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            // 解析输出,格式类似: "Flutter 3.10.0 • channel stable • ..."
            val versionRegex = Regex("Flutter\\s+(\\d+\\.\\d+\\.\\d+)")
            val matchResult = versionRegex.find(output)
            
            if (matchResult != null) {
                val versionString = matchResult.groupValues[1]
                val version = parseVersion(versionString)
                LOG.info("[FlutterAssetsGenerator #FlutterVersionHelper] Detected Flutter version from SDK: $version")
                return version
            }
        } catch (e: Exception) {
            LOG.info("[FlutterAssetsGenerator #FlutterVersionHelper] Failed to get Flutter version from command: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 从 pubspec.yaml 获取 Flutter SDK 版本约束
     */
    private fun getFlutterVersionFromPubspec(pubspecFile: VirtualFile): SemanticVersion? {
        try {
            val yaml = Yaml()
            val data = yaml.load<Map<*, *>>(FileInputStream(pubspecFile.path))
            
            // 读取 environment.flutter 约束
            val environment = data["environment"] as? Map<*, *>
            val flutterConstraint = environment?.get("flutter") as? String
            
            if (flutterConstraint != null) {
                LOG.info("[FlutterAssetsGenerator #FlutterVersionHelper] Found Flutter constraint in pubspec: $flutterConstraint")
                return parseVersionConstraint(flutterConstraint)
            }
            
            LOG.info("[FlutterAssetsGenerator #FlutterVersionHelper] No Flutter version constraint found in pubspec.yaml")
        } catch (e: Exception) {
            LOG.warn("[FlutterAssetsGenerator #FlutterVersionHelper] Failed to read Flutter version from pubspec", e)
        }
        
        return null
    }
    
    /**
     * 解析版本约束字符串
     * 支持格式: ">=3.0.0", "^3.0.0", "3.0.0"
     */
    private fun parseVersionConstraint(constraint: String): SemanticVersion? {
        try {
            // 移除约束符号,提取版本号
            val versionString = constraint
                .replace(">=", "")
                .replace("^", "")
                .replace("<", "")
                .trim()
                .split(" ")[0] // 取第一个版本号
            
            return parseVersion(versionString)
        } catch (e: Exception) {
            LOG.warn("[FlutterAssetsGenerator #FlutterVersionHelper] Failed to parse version constraint: $constraint", e)
            return null
        }
    }
    
    /**
     * 解析版本号字符串
     * 格式: "3.0.0" 或 "3.0"
     */
    private fun parseVersion(versionString: String): SemanticVersion? {
        try {
            val parts = versionString.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            
            return SemanticVersion(major, minor, patch)
        } catch (e: Exception) {
            LOG.warn("[FlutterAssetsGenerator #FlutterVersionHelper] Failed to parse version: $versionString", e)
            return null
        }
    }
}

/**
 * 语义化版本号
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }
    
    override fun toString(): String = "$major.$minor.$patch"
}
