package com.crzsc.plugin.setting

import com.crzsc.plugin.utils.message
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class AppSettingsComponent {
    val panel: JPanel
    private val filePath = JBTextField()
    private val fileName = JBTextField()
    private val className = JBTextField()
    private val filenameSplitPattern = JBTextField()
    private val autoDetection = JBCheckBox(message("settingsAutoDetection"))
    private val namedWithParent = JBCheckBox(message("settingsNamed"))
    private val leadingWithPackageName = JBCheckBox(message("settingsLeadingWithPackageName"))

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(message("settingsFilePath")), filePath, 1, false)
            .addLabeledComponent(JBLabel(message("settingsFileName")), fileName, 1, false)
            .addLabeledComponent(JBLabel(message("settingsClassName")), className, 1, false)
            .addLabeledComponent(JBLabel(message("settingsSplitPattern")), filenameSplitPattern, 1, false)
            .addComponent(autoDetection, 1)
            .addComponent(namedWithParent, 1)
            .addComponent(leadingWithPackageName, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    val preferredFocusedComponent: JComponent
        get() = filePath

    fun getFilePath(): String {
        return filePath.text
    }

    fun setFilePath(text: String?) {
        filePath.text = text
    }

    fun getFileName(): String {
        return fileName.text
    }


    fun setFileName(text: String?) {
        fileName.text = text
    }

    fun getClassName(): String {
        return className.text
    }

    fun setClassName(text: String?) {
        className.text = text
    }

    fun getFilenameSplitPattern(): String {
        return filenameSplitPattern.text
    }

    fun setFilenameSplitPattern(text: String?) {
        filenameSplitPattern.text = text
    }

    fun getAutoDetection(): Boolean {
        return autoDetection.isSelected
    }

    fun setAutoDetection(newStatus: Boolean) {
        autoDetection.isSelected = newStatus
    }

    fun getNamedWithParent(): Boolean {
        return namedWithParent.isSelected
    }

    fun setNamedWithParent(newStatus: Boolean) {
        namedWithParent.isSelected = newStatus
    }

    fun getLeadingWithPackageName(): Boolean {
        return leadingWithPackageName.isSelected
    }

    fun setLeadingWithPackageName(newStatus: Boolean) {
        leadingWithPackageName.isSelected = newStatus
    }
}
