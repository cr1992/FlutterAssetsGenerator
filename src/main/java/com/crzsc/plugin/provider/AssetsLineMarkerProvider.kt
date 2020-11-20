package com.crzsc.plugin.provider

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IconUtil
import com.intellij.util.SVGLoader
import java.io.File
import javax.swing.Icon
import javax.swing.ImageIcon
import kotlin.math.max


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
            val anchor = PsiTreeUtil.getDeepestFirst(element)
            val filePath = element.project.basePath + "/" + element.text
            val vFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
            //svg则展示缩小的icon
            if (element.text.endsWith(".svg", true)) {
                var icon: Icon = ImageIcon(
                        SVGLoader.load(File(element.project.basePath + "/" + element.text).toURL(), 1.0F)
                )
                if (icon.iconWidth > 16) {
                    icon = IconUtil.scale(icon, 16.0 / max(icon.iconWidth, icon.iconHeight))
                }
                return LineMarkerInfo(
                        anchor, anchor.textRange,
                        icon, {
                    //悬停，会多次调用
                    element.showPop(vFile)
                    return@LineMarkerInfo ""
                }, { _, _ -> element.openFile(vFile) }, GutterIconRenderer.Alignment.LEFT)
            }
            //其他文件展示文件格式
            return LineMarkerInfo(
                    anchor, anchor.textRange,
                    IconUtil.getIcon(vFile, Iconable.ICON_FLAG_VISIBILITY, element.project), {
                //悬停，会多次调用
                element.showPop(vFile)
                return@LineMarkerInfo ""
            }, { _, _ -> element.openFile(vFile) }, GutterIconRenderer.Alignment.LEFT)
        }
        return null
    }

    /**
     * TODO 显示pop
     */
    private fun PsiElement.showPop(vFile: VirtualFile) {
        println("tooltipProvider:${text}")

    }

    /**
     * 新窗口打开文件
     */
    private fun PsiElement.openFile(vFile: VirtualFile) {
        FileEditorManager.getInstance(project)
                .openTextEditor(OpenFileDescriptor(project, vFile), true)
    }


}