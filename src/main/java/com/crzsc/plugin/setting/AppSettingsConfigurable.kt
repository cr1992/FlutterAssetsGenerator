package com.crzsc.plugin.setting

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    override fun getDisplayName(): String {
        return "FlutterAssetsGenerator"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return mySettingsComponent!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = AppSettingsComponent()
        return mySettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val settings = PluginSetting.instance
        var modified = mySettingsComponent!!.getAutoDetection() != settings.autoDetection
        modified = modified or (mySettingsComponent!!.getFileName() != settings.fileName)
        modified = modified or (mySettingsComponent!!.getClassName() != settings.className)
        modified = modified or (mySettingsComponent!!.getFilenameSplitPattern() != settings.filenameSplitPattern)
        modified = modified or (mySettingsComponent!!.getFilePath() != settings.filePath)
        modified = modified or (mySettingsComponent!!.getNamedWithParent() != settings.namedWithParent)
        return modified
    }

    override fun apply() {
        val settings = PluginSetting.instance
        settings.autoDetection = mySettingsComponent!!.getAutoDetection()
        settings.fileName = mySettingsComponent!!.getFileName()
        settings.className = mySettingsComponent!!.getClassName()
        settings.filePath = mySettingsComponent!!.getFilePath()
        settings.filenameSplitPattern = mySettingsComponent!!.getFilenameSplitPattern()
        settings.namedWithParent = mySettingsComponent!!.getNamedWithParent()
    }

    override fun reset() {
        val settings = PluginSetting.instance
        mySettingsComponent!!.setAutoDetection(settings.autoDetection)
        mySettingsComponent!!.setFileName(settings.fileName)
        mySettingsComponent!!.setClassName(settings.className)
        mySettingsComponent!!.setFilePath(settings.filePath)
        mySettingsComponent!!.setFilenameSplitPattern(settings.filenameSplitPattern)
        mySettingsComponent!!.setNamedWithParent(settings.namedWithParent)
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}