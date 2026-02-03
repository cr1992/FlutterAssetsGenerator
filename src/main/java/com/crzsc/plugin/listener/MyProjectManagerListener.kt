package com.crzsc.plugin.listener

import com.crzsc.plugin.cache.PubspecConfigCache
import com.intellij.AppTopics
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection

class MyProjectManagerListener : ProjectManagerListener {
    private val psiListenersMap = mutableMapOf<Project, PsiTreeListener>()
    private val docListenersMap = mutableMapOf<Project, PubspecDocumentListener>()
    private val messageBusConnections = mutableMapOf<Project, MessageBusConnection>()

    override fun projectOpened(project: Project) {
        super.projectOpened(project)

        // 注册 PSI 树监听器(用于监听 assets 文件变更)
        // 使用 Project 作为 parentDisposable，当项目关闭时会自动注销监听器
        val psiListener = PsiTreeListener(project)
        psiListenersMap[project] = psiListener
        PsiManager.getInstance(project).addPsiTreeChangeListener(psiListener, project)

        // 注册文档保存监听器(用于监听 pubspec.yaml 保存)
        val docListener = PubspecDocumentListener(project)
        docListenersMap[project] = docListener

        // 使用消息总线注册文档监听器
        val connection = project.messageBus.connect()
        connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, docListener)
        messageBusConnections[project] = connection
    }

    override fun projectClosing(project: Project) {
        super.projectClosing(project)


        // 断开消息总线连接
        messageBusConnections.remove(project)?.disconnect()

        // 移除文档监听器引用
        docListenersMap.remove(project)

        // 清理配置缓存
        PubspecConfigCache.clearProject(project)
    }

}