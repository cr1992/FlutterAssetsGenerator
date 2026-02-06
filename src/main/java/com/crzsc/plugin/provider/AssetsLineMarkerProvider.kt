package com.crzsc.plugin.provider

import com.crzsc.plugin.utils.FileHelperNew
import com.crzsc.plugin.utils.PluginUtils.openFile
import com.crzsc.plugin.utils.isSvgExtension
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.SVGLoader
import javax.swing.Icon
import javax.swing.ImageIcon

/** Svgs.dart显示图标在路径左侧 */
class AssetsLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val elementType = (element as? LeafPsiElement?)?.elementType?.toString()
        if (elementType == "REGULAR_STRING_PART") {
            // 这里会被多次调用 尽量减少调用次数
            var assetName: String? = null
            var leadingWithPackageName: String? = null
            val vFile = element.containingFile.virtualFile
            if (vFile != null) {
                val bestConfig =
                    FileHelperNew.getAssets(element.project)
                        .filter { vFile.path.startsWith(it.pubRoot.path) }
                        .maxByOrNull { it.pubRoot.path.length }

                if (bestConfig != null) {
                    assetName = FileHelperNew.getGeneratedFileName(bestConfig)
                    leadingWithPackageName = bestConfig.getLeadingWithPackageNameIfChecked()
                }
            }

            // 检查当前文件名是否匹配配置的生成文件名
            // getGeneratedFileName 返回不带扩展名的文件名,需要添加 .dart 后缀
            val filenameCorrect =
                assetName != null && element.containingFile.name.equals("$assetName.dart", true)
            if (filenameCorrect) {
                //                println("filenameCorrect showMakeByType : $element")
                return showMakeByType(element, leadingWithPackageName)
            }
        }
        return null
    }

    private fun showMakeByType(
        element: PsiElement,
        leadingWithPackageName: String?
    ): LineMarkerInfo<*>? {
        val assetsPath = element.text
        val anchor = PsiTreeUtil.getDeepestFirst(element)
        // 先用默认的path找文件
        var filePath = element.project.basePath + "/" + element.text
        if (!leadingWithPackageName.isNullOrEmpty()) {
            filePath = filePath.replace(leadingWithPackageName, "")
        }
        var vFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        if (vFile == null) {
            // 如果没找到，尝试根据当前文件向上查找
            var file = element.containingFile.viewProvider.virtualFile
            while (file.name != "lib" && file.parent != null) {
                file = file.parent
            }
            filePath = file.parent.path + "/" + element.text
            if (!leadingWithPackageName.isNullOrEmpty()) {
                filePath = filePath.replace(leadingWithPackageName, "")
            }
            vFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        }
        //        println("showMakeByType assetsPath $assetsPath")
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
    ): LineMarkerInfo<*> {
        val icon: Icon =
            ImageIcon(
                SVGLoader.load(
                    null,
                    vFile.inputStream,
                    ScaleContext.createIdentity(),
                    16.0,
                    16.0
                )
            )
        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            icon,
            {
                // 悬停，会多次调用
                return@LineMarkerInfo ""
            },
            { _, _ -> element.openFile(vFile) },
            GutterIconRenderer.Alignment.LEFT
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
            GutterIconRenderer.Alignment.LEFT
        )
    }
}
