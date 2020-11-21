package com.crzsc.plugin.provider

import com.crzsc.plugin.utils.PluginUtils.openFile
import com.crzsc.plugin.utils.isImageExtension
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


/**
 * Svgs.dart显示图标在路径左侧
 */
class AssetsLineMarkerProvider : LineMarkerProvider {

    /**
     * Fixme 大图性能优化，添加缓存策略
     */
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.containingFile.name.contains("Assets.dart", true)
                && (element as? LeafPsiElement?)?.elementType?.toString() == "REGULAR_STRING_PART"
        ) {
            return showMakeByType(element)
        }
        return null
    }

    private fun showMakeByType(element: PsiElement): LineMarkerInfo<*>? {
        val assetsPath = element.text
        val anchor = PsiTreeUtil.getDeepestFirst(element)
        val filePath = element.project.basePath + "/" + element.text
        val vFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
        return when {
            assetsPath.isSvgExtension -> showSvgMark(element, anchor, vFile)
            assetsPath.isImageExtension -> showImageMark(element, anchor, vFile)
            else -> null
        }
    }


    private fun showSvgMark(element: PsiElement, anchor: PsiElement,
                            vFile: VirtualFile): LineMarkerInfo<*>? {
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
        return LineMarkerInfo(anchor, anchor.textRange, icon, {
            //悬停，会多次调用
            return@LineMarkerInfo ""
        }, { _, _ -> element.openFile(vFile) }, GutterIconRenderer.Alignment.LEFT)

    }


    private fun showImageMark(element: PsiElement, anchor: PsiElement,
                              vFile: VirtualFile): LineMarkerInfo<*>? {
        val icon = IconUtil.getIcon(vFile, Iconable.ICON_FLAG_VISIBILITY, element.project)
        //其他文件展示文件格式
        return LineMarkerInfo(
                anchor, anchor.textRange,
                icon, {
            //悬停，会多次调用
            return@LineMarkerInfo ""
        }, { _, _ -> element.openFile(vFile) }, GutterIconRenderer.Alignment.LEFT)

    }

}