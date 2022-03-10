package com.crzsc.plugin.utils

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

object PluginUtils {

    @JvmStatic
    fun showNotify(message: String?) {
        val notificationGroup = NotificationGroup("FlutterAssetsGenerator", NotificationDisplayType.BALLOON, true)
        ApplicationManager.getApplication().invokeLater {
            val notification = notificationGroup.createNotification(message!!, NotificationType.INFORMATION)
            Notifications.Bus.notify(notification)
        }
    }

    @JvmStatic
    fun showError(message: String?) {
        val notificationGroup = NotificationGroup("FlutterAssetsGenerator", NotificationDisplayType.BALLOON, true)
        ApplicationManager.getApplication().invokeLater {
            val notification = notificationGroup.createNotification(message!!, NotificationType.ERROR)
            Notifications.Bus.notify(notification)
        }
    }

    /**
     * 转换小写驼峰式
     */
    fun String.toLowCamelCase(): String {
        return if (this.isEmpty()) {
            this
        } else {
            val split = this.split("_")
            val sb = StringBuilder()
            for (i in split.indices) {
                if (i == 0) {
                    sb.append(split[i])
                } else {
                    sb.append(split[i].toUpperCaseFirst())
                }
            }
            return sb.toString()
        }
    }

    /**
     * 新窗口打开文件
     */
    fun PsiElement.openFile(vFile: VirtualFile) {
        FileEditorManager.getInstance(project)
            .openTextEditor(OpenFileDescriptor(project, vFile), true)
    }

    /**
     * 首字母大写
     */
    fun String.toUpperCaseFirst(): String {
        return if (this.isEmpty()) {
            this
        } else {
            "${this[0].toUpperCase()}${this.subSequence(1, this.length)}"
        }
    }
}