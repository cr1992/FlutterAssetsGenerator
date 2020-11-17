package com.netease.plugin.setting;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;

public class AppSettingsComponent {

    private final JPanel myMainPanel;
    private final JBTextField assetsPathText = new JBTextField();
    private final JBTextField filePath = new JBTextField();
    private final JBTextField className = new JBTextField();
    private final JBCheckBox autoDetection = new JBCheckBox("Enable auto-detection");

    public AppSettingsComponent() {
        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Enter assets path: "), assetsPathText, 1, false)
                .addLabeledComponent(new JBLabel("Enter generate path(based on lib/): "), filePath, 1, false)
                .addLabeledComponent(new JBLabel("Enter generate class name: "), className, 1, false)
                .addComponent(autoDetection, 1)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return myMainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return assetsPathText;
    }


    public String getAssetsPath() {
        return assetsPathText.getText();
    }

    public void setAssetsPath(String newText) {
        assetsPathText.setText(newText);
    }

    public String getFilePath() {
        return filePath.getText();
    }

    public void setFilePath(String text) {
        filePath.setText(text);
    }

    public String getClassName() {
        return className.getText();
    }

    public void setClassName(String text) {
        className.setText(text);
    }

    public boolean getAutoDetection() {
        return autoDetection.isSelected();
    }

    public void setAutoDetection(boolean newStatus) {
        autoDetection.setSelected(newStatus);
    }

}
