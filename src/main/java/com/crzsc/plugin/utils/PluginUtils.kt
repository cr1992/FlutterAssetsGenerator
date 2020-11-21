package com.crzsc.plugin.utils

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager

object PluginUtils {
    @JvmStatic
    fun showNotify(message: String?) {
        val notificationGroup = NotificationGroup("Assets generator", NotificationDisplayType.BALLOON, true)
        ApplicationManager.getApplication().invokeLater {
            val notification = notificationGroup.createNotification(message!!, NotificationType.INFORMATION)
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