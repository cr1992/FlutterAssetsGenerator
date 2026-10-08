package com.crzsc.plugin.provider

import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.ModulePubSpecConfig
import com.crzsc.plugin.utils.PluginUtils.openFile
import com.crzsc.plugin.utils.isSvgExtension
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IconUtil
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.Icon
import javax.swing.ImageIcon

/** Svgs.dart显示图标在路径左侧 */
class AssetsLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // 这里会对每个 Dart 文件里的每个字符串片段调用, 必须先做零成本的过滤, 再去查模块配置
        val elementType = (element as? LeafPsiElement)?.elementType?.toString()
        if (elementType != "REGULAR_STRING_PART") {
            return null
        }
        val containingFile = element.containingFile ?: return null
        val vFile = containingFile.virtualFile ?: return null
        if (!containingFile.name.endsWith(".dart", ignoreCase = true)) {
            return null
        }

        // 项目级缓存, 不会每个字符串都扫描 FilenameIndex
        val bestConfig =
            FileHelperNew.getCachedAssets(element.project)
                .filter { vFile.path.startsWith("${it.pubRoot.path}/") }
                .maxByOrNull { it.pubRoot.path.length }
                ?: return null

        val expectedName = "${FileHelperNew.getGeneratedFileName(bestConfig)}.dart"
        if (!containingFile.name.equals(expectedName, ignoreCase = true)) {
            return null
        }
        return showMakeByType(element, bestConfig)
    }

    private fun showMakeByType(element: PsiElement, config: ModulePubSpecConfig): LineMarkerInfo<*>? {
        val assetsPath = element.text
        val anchor = PsiTreeUtil.getDeepestFirst(element)

        // package_parameter_enabled 时字符串是 packages/<pkg>/<path>, 去掉前缀后再定位文件
        val packageName = config.map["name"] as? String
        val relativePath =
            if (packageName != null && assetsPath.startsWith("packages/$packageName/")) {
                assetsPath.removePrefix("packages/$packageName/")
            } else {
                assetsPath
            }

        // 优先相对于资源所属的 pub 包根目录查找, 找不到再退回项目根目录
        var vFile = config.pubRoot.root.findFileByRelativePath(relativePath)
        if (vFile == null) {
            vFile = LocalFileSystem.getInstance().findFileByPath(element.project.basePath + "/" + relativePath)
        }
        if (vFile != null) {
            return when {
                assetsPath.isSvgExtension -> showSvgMark(element, anchor, vFile)
                else -> showIconMark(element, anchor, vFile)
            }
        }
        return null
    }

    private fun showSvgMark(
        element: PsiElement,
        anchor: PsiElement,
        vFile: VirtualFile
    ): LineMarkerInfo<*>? {
        val icon: Icon? =
            try {
                val url = File(vFile.path).toURI().toURL()
                val baseIcon = IconLoader.findIcon(url)
                if (baseIcon != null) {
                    val width = baseIcon.iconWidth
                    val height = baseIcon.iconHeight
                    if (width > 0 && height > 0) {
                        // 使用 BufferedImage 进行栅格化，确保 IDE 拿到的永远是 16x16 的静态位图
                        // 这样可以避免 IconLoader 异步加载过程中的尺寸变化导致的 UI 闪烁/占位过大问题
                        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                        val g = image.createGraphics()
                        // 始终按比例缩放，确保长宽最大不超过 16
                        val scale = 16.0 / kotlin.math.max(width, height)
                        g.scale(scale, scale)
                        baseIcon.paintIcon(null, g, 0, 0)
                        g.dispose()
                        ImageIcon(image)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        if (icon == null) return null // Or some default icon

        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            icon,
            {
                // 悬停，会多次调用
                return@LineMarkerInfo ""
            },
            { _, _ -> element.openFile(vFile) },
            GutterIconRenderer.Alignment.LEFT,
            { "SVG Asset" }
        )
    }

    private fun showIconMark(
        element: PsiElement,
        anchor: PsiElement,
        vFile: VirtualFile
    ): LineMarkerInfo<*> {
        val icon = IconUtil.getIcon(vFile, Iconable.ICON_FLAG_VISIBILITY, element.project)
        // 其他文件展示文件格式
        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            icon,
            {
                // 悬停，会多次调用
                return@LineMarkerInfo ""
            },
            { _, _ -> element.openFile(vFile) },
            GutterIconRenderer.Alignment.LEFT,
            { "Asset File" }
        )
    }
}
