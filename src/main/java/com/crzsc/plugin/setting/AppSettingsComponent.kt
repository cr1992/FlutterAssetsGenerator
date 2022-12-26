package com.crzsc.plugin.setting

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
    private val autoDetection = JBCheckBox("Enable auto-detection")
    private val namedWithParent = JBCheckBox("Named with parent")

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Enter generated file path: "), filePath, 1, false)
            .addLabeledComponent(JBLabel("Enter generated file name: "), fileName, 1, false)
            .addLabeledComponent(JBLabel("Enter generated class name: "), className, 1, false)
            .addLabeledComponent(JBLabel("Enter filename split pattern: "), filenameSplitPattern, 1, false)
            .addComponent(autoDetection, 1)
            .addComponent(namedWithParent, 1)
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
}