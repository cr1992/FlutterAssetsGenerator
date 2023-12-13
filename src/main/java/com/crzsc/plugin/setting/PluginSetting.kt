package com.crzsc.plugin.setting

import com.crzsc.plugin.utils.Constants
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Supports storing the application settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(name = "com.crzsc.plugin.setting.PluginSetting", storages = [Storage("FlutterAssetsGenerator.xml")])
class PluginSetting : PersistentStateComponent<PluginSetting> {
    var className: String? = Constants.DEFAULT_CLASS_NAME
    var fileName: String? = Constants.DEFAULT_CLASS_NAME.lowercase()
    var filePath: String? = Constants.DEFAULT_OUTPUT_DIR
    var filenameSplitPattern: String? = Constants.DEFAULT_FILENAME_SPLIT_PATTERN
    var namedWithParent = true
    var autoDetection = true
    var leadingWithPackageName = true;
    override fun getState(): PluginSetting {
        return this
    }

    override fun loadState(state: PluginSetting) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: PluginSetting
            get() = ApplicationManager.getApplication().getService(PluginSetting::class.java)
    }
}