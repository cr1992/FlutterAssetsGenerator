package com.crzsc.plugin.actions;

import com.crzsc.plugin.utils.FileGenerator;
import com.crzsc.plugin.utils.FileHelpers;
import com.crzsc.plugin.utils.PluginUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;


public class GenerateAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (FileHelpers.shouldActivateFor(project)) {
            FileGenerator fileGenerator = new FileGenerator(project);
            fileGenerator.generate();
        } else {
            PluginUtils.showNotify("This project is not the flutter project");
        }
    }

}
