package com.crzsc.plugin.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile

enum class MediaType {
    IMAGE,
    SVG,
    LOTTIE,
    UNKNOWN,
    DIRECTORY
}

data class AssetNode(
    val name: String,
    val path: String, // Relative path from module root
    val type: MediaType,
    val virtualFile: VirtualFile?,
    val children: MutableList<AssetNode> = mutableListOf()
)

object AssetTreeBuilder {
    private val LOG = Logger.getInstance(AssetTreeBuilder::class.java)

    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "wbmp", "ico")
    private val resolutionDirPattern = Regex("^\\d+(\\.\\d+)?x$")

    /**
     * 构建资源树
     * @param files pubspec.yaml 中配置的资源文件列表
     * @param moduleRootPath 模块根路径
     * @param ignorePath 忽略路径列表
     */
    fun build(
        files: List<VirtualFile>,
        moduleRootPath: String,
        ignorePath: List<String>
    ): AssetNode {
        LOG.info(
            "[FlutterAssetsGenerator #AssetTreeBuilder] [AssetTreeBuilder] Building asset tree from ${files.size} files"
        )
        // 创建根节点
        val rootNode = AssetNode("Assets", "", MediaType.DIRECTORY, null)

        for (file in files) {
            LOG.info(
                "[FlutterAssetsGenerator #AssetTreeBuilder] [AssetTreeBuilder] Processing file: ${file.path}"
            )
            processFile(file, rootNode, moduleRootPath, ignorePath)
        }

        LOG.info(
            "[FlutterAssetsGenerator #AssetTreeBuilder] [AssetTreeBuilder] Asset tree built successfully"
        )

        pruneEmptyDirectories(rootNode)

        return rootNode
    }

    /** 递归移除空的目录节点 */
    private fun pruneEmptyDirectories(node: AssetNode): Boolean {
        if (node.type != MediaType.DIRECTORY) return false

        val iterator = node.children.iterator()
        while (iterator.hasNext()) {
            val child = iterator.next()
            val isEmptyDir = pruneEmptyDirectories(child)
            if (isEmptyDir) {
                LOG.info(
                    "[FlutterAssetsGenerator #AssetTreeBuilder] [prune] Removing empty directory: ${child.name}"
                )
                iterator.remove()
            }
        }

        return node.children.isEmpty()
    }

    /** 递归处理文件或目录 */
    private fun processFile(
        file: VirtualFile,
        parentNode: AssetNode,
        moduleRootPath: String,
        ignorePath: List<String>
    ) {
        if (shouldIgnore(file, ignorePath)) return
        LOG.info(
            "[FlutterAssetsGenerator #AssetTreeBuilder] [processFile] Processing: ${file.path}, isDirectory: ${file.isDirectory}"
        )

        if (file.isDirectory) {
            // 如果是目录,为该目录创建节点(如果不存在)
            val relativePath = file.path.removePrefix("$moduleRootPath/")
            LOG.info(
                "[FlutterAssetsGenerator #AssetTreeBuilder] [processFile] Directory relativePath: $relativePath"
            )

            // 查找或创建当前目录节点
            val dirName = file.name
            var dirNode =
                parentNode.children.find {
                    it.name == dirName && it.type == MediaType.DIRECTORY
                }
            if (dirNode == null) {
                LOG.info(
                    "[FlutterAssetsGenerator #AssetTreeBuilder] [processFile] Creating directory node: $dirName (path: $relativePath)"
                )
                dirNode = AssetNode(dirName, relativePath, MediaType.DIRECTORY, null)
                parentNode.children.add(dirNode)
            } else {
                LOG.info(
                    "[FlutterAssetsGenerator #AssetTreeBuilder] [processFile] Found existing directory node: $dirName"
                )
            }

            // 递归处理子文件,使用刚创建/找到的目录节点作为父节点
            file.children.forEach { child ->
                processFile(child, dirNode, moduleRootPath, ignorePath)
            }
        } else {
            // 如果是文件,直接添加到父节点
            val mediaType = getMediaType(file)
            val name = file.nameWithoutExtension
            val relativePath = file.path.removePrefix("$moduleRootPath/")

            LOG.info(
                "[FlutterAssetsGenerator #AssetTreeBuilder] [processFile] Adding file: $name (path: $relativePath, type: $mediaType)"
            )

            // 避免重复添加
            if (parentNode.children.none { it.path == relativePath }) {
                parentNode.children.add(AssetNode(name, relativePath, mediaType, file))
            }
        }
    }

    /** 根据目录路径查找对应的节点，如果不存在则创建 这是为了处理 pubspec 可能配置了深层目录的情况 */
    private fun getNodeForPath(
        directory: VirtualFile,
        root: AssetNode,
        moduleRootPath: String
    ): AssetNode {
        val relativePath = directory.path.removePrefix("$moduleRootPath/")
        LOG.info(
            "[FlutterAssetsGenerator #AssetTreeBuilder] [getNodeForPath] directory: ${directory.path}, relativePath: $relativePath"
        )
        if (relativePath.isEmpty()) return root // 根目录

        val parts = relativePath.split("/")
        var currentNode = root
        val pathParts = mutableListOf<String>()

        for (part in parts) {
            if (part.isEmpty()) continue

            pathParts.add(part)
            val currentPath = pathParts.joinToString("/")

            // 查找当前层级是否已存在对应目录节点(只按名称和类型匹配)
            var child =
                currentNode.children.find { it.name == part && it.type == MediaType.DIRECTORY }
            if (child == null) {
                // 不存在则创建,使用完整的相对路径
                LOG.info(
                    "[FlutterAssetsGenerator #AssetTreeBuilder] [getNodeForPath] Creating new directory node: $part (path: $currentPath)"
                )
                child = AssetNode(part, currentPath, MediaType.DIRECTORY, null)
                currentNode.children.add(child)
            } else {
                LOG.info(
                    "[FlutterAssetsGenerator #AssetTreeBuilder] [getNodeForPath] Found existing directory node: $part"
                )
            }
            currentNode = child
        }
        return currentNode
    }

    private fun shouldIgnore(file: VirtualFile, ignorePath: List<String>): Boolean {
        if (file.name.startsWith(".")) {
            LOG.info(
                "[FlutterAssetsGenerator #AssetTreeBuilder] [shouldIgnore] Ignoring dot file: ${file.name}"
            )
            return true
        }

        if (file.isDirectory && resolutionDirPattern.matches(file.name)) {
            LOG.info(
                "[FlutterAssetsGenerator #AssetTreeBuilder] [shouldIgnore] Ignoring resolution variant directory: ${file.name}"
            )
            return true
        }
        // 忽略配置列表中的文件
        for (ignore in ignorePath) {
            if (file.path.contains(ignore, ignoreCase = true)) {
                LOG.info(
                    "[FlutterAssetsGenerator #AssetTreeBuilder] [shouldIgnore] Ignoring file matching '$ignore': ${file.path}"
                )
                return true
            }
        }
        return false
    }

    /** 根据文件扩展名或内容判断资源类型 */
    private fun getMediaType(file: VirtualFile): MediaType {
        val ext = file.extension?.lowercase()
        return when {
            imageExtensions.contains(ext) -> MediaType.IMAGE
            ext == "svg" -> MediaType.SVG
            ext == "lottie" -> MediaType.LOTTIE
            ext == "json" -> if (isLottieFile(file)) MediaType.LOTTIE else MediaType.UNKNOWN
            else -> MediaType.UNKNOWN
        }
    }

    /** 简单判断是否为 Lottie 动画文件 检查 JSON 中是否包含 "v" (版本) 和 "layers" 或 "ip" 等关键字 */
    private fun isLottieFile(file: VirtualFile): Boolean {
        return try {
            if (file.length > 5 * 1024 * 1024) {
                LOG.info(
                    "[FlutterAssetsGenerator #AssetTreeBuilder] [isLottieFile] Skipping large file (>5MB): ${file.name} (${file.length} bytes)"
                )
                return false // 跳过大于 5MB 的文件
            }
            val content = String(file.inputStream.readNBytes(200.coerceAtMost(file.length.toInt())))
            content.contains("\"v\"") &&
                    (content.contains("\"layers\"") || content.contains("\"ip\""))
        } catch (e: Exception) {
            LOG.warn(
                "[FlutterAssetsGenerator #AssetTreeBuilder] [isLottieFile] Error reading file: ${file.path}",
                e
            )
            false
        }
    }
}
