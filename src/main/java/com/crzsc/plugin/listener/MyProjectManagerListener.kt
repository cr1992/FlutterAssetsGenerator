package com.crzsc.plugin.listener

import com.crzsc.plugin.cache.PubspecConfigCache
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * 项目关闭时清理配置缓存.
 * VFS / 文档保存监听器改为在 plugin.xml 的 projectListeners 中声明式注册 (按需懒加载),
 * 不再使用已废弃的 projectOpened.
 */
class MyProjectManagerListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        PubspecConfigCache.clearProject(project)
    }
}
