package com.crzsc.plugin.listener

import com.crzsc.plugin.cache.PubspecConfigCache
import com.intellij.AppTopics
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection

class MyProjectManagerListener : ProjectManagerListener {
    private val vfsListenersMap = mutableMapOf<Project, VfsAssetListener>()
    private val docListenersMap = mutableMapOf<Project, PubspecDocumentListener>()
    private val messageBusConnections = mutableMapOf<Project, MessageBusConnection>()

    override fun projectOpened(project: Project) {
        super.projectOpened(project)

        val vfsListener = VfsAssetListener(project)
        vfsListenersMap[project] = vfsListener

        // 注册文档保存监听器(用于监听 pubspec.yaml 保存)
        val docListener = PubspecDocumentListener(project)
        docListenersMap[project] = docListener

        // 使用消息总线注册 VFS 监听器和文档监听器
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, vfsListener)
        connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, docListener)
        messageBusConnections[project] = connection
    }

    override fun projectClosing(project: Project) {
        super.projectClosing(project)

        // 断开消息总线连接
        messageBusConnections.remove(project)?.disconnect()

        // 移除文档监听器引用
        docListenersMap.remove(project)
        vfsListenersMap.remove(project)

        // 清理配置缓存
        PubspecConfigCache.clearProject(project)
    }
}
