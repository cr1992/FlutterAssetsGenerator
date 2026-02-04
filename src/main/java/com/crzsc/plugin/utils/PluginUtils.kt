package com.crzsc.plugin.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

object PluginUtils {

    // 从 NotificationGroupManager 获取通知组
    private fun getNotificationGroup() =
            NotificationGroupManager.getInstance().getNotificationGroup("FlutterAssetsGenerator")

    @JvmStatic
    fun showNotify(message: String?) {
        if (message.isNullOrEmpty()) return
        ApplicationManager.getApplication().invokeLater {
            val notification =
                    getNotificationGroup().createNotification(message, NotificationType.INFORMATION)
            Notifications.Bus.notify(notification)
        }
    }

    @JvmStatic
    fun showError(message: String?) {
        if (message.isNullOrEmpty()) return
        ApplicationManager.getApplication().invokeLater {
            val notification =
                    getNotificationGroup().createNotification(message, NotificationType.ERROR)
            Notifications.Bus.notify(notification)
        }
    }

    /** 转换小写驼峰式 */
    fun String.toLowCamelCase(regex: Regex): String {
        return if (this.isEmpty()) {
            this
        } else {
            val newStr = this.replace(Regex("[@]"), "")
            val split = newStr.split(regex)
            val sb = StringBuilder()
            for (i in split.indices) {
                if (i == 0) {
                    sb.append(split[i].lowerCaseFirst())
                } else {
                    sb.append(split[i].upperCaseFirst())
                }
            }
            return sb.toString()
        }
    }

    /** 新窗口打开文件 */
    fun PsiElement.openFile(vFile: VirtualFile) {
        FileEditorManager.getInstance(project)
                .openTextEditor(OpenFileDescriptor(project, vFile), true)
    }

    fun String.lowerCaseFirst(): String {
        return if (this.isEmpty()) {
            this
        } else {
            "${this[0].lowercase()}${this.subSequence(1, this.length)}"
        }
    }

    /** 首字母大写 */
    fun String.upperCaseFirst(): String {
        return if (this.isEmpty()) {
            this
        } else {
            "${this[0].uppercase()}${this.subSequence(1, this.length)}"
        }
    }
}
