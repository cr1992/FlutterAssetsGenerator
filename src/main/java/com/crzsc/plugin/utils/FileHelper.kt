package com.crzsc.plugin.utils

import com.crzsc.plugin.setting.PluginSetting
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.search.FilenameIndex
import io.flutter.pub.PubRoot
import io.flutter.utils.FlutterModuleUtils
import org.yaml.snakeyaml.Yaml
import java.io.FileInputStream


object FileHelper {
    var assetsPath: String? = null

    /**
     * 获取资源路径
     */
    @JvmStatic
    fun getAssetsFolder(project: Project): VirtualFile? {
        val guessProjectDir = project.guessProjectDir()
        getPubSpecConfig(project)?.let { pubSpecConfig ->
            (pubSpecConfig.map["flutter"] as? Map<*, *>)?.let { configureMap ->
                (configureMap["assets"] as? ArrayList<*>)?.let { list ->
                    val path = list[0] as String
                    val index = path.indexOf("/")
                    assetsPath = if (index == -1) {
                        path
                    } else {
                        path.substring(0, index)
                    }
                }
            }
        }
        if (assetsPath == null) {
            return null
        }
        return guessProjectDir?.findChild(assetsPath!!)
            ?: guessProjectDir!!.createChildDirectory(this, assetsPath!!)
    }

    /**
     * 获取资源路径
     */
    @JvmStatic
    fun getAssetsFiles(assetsFile: VirtualFile): List<VirtualFile>? {
        val list = mutableListOf<VirtualFile>()
        checkAddDir(list,assetsFile)
        return list.takeIf { it.isNotEmpty() }
    }

    ///递归添加文件夹
    private fun checkAddDir(list:MutableList<VirtualFile>,virtualFile: VirtualFile){
        if (virtualFile.isDirectory) {
            //不全是文件
            if (virtualFile.children.any { c -> !c.isDirectory }) {
                list.add(virtualFile)
            }
            virtualFile.children.forEach {
                checkAddDir(list,it)
            }
        }
    }

    /**
     * 获取generated自动生成目录
     */
    private fun getGeneratedFilePath(project: Project): VirtualFile {
        return PubRoot.forFile(getProjectIdeaFile(project))?.lib?.let { lib ->
            var filePath = PluginSetting.getInstance().filePath
            if (filePath.isNullOrEmpty()) {
                return@let lib
            }
            filePath = filePath.trim()
            if (!filePath.contains("/")) {
                return@let lib.findChild(filePath)
                    ?: lib.createChildDirectory(lib, filePath)
            } else {
                var file = lib
                filePath.split("/").forEach { dir ->
                    if (dir.isNotEmpty()) {
                        file = file.findChild(dir)
                            ?: file.createChildDirectory(file, dir)
                    }
                }
                return@let file
            }
        }!!
    }

    fun getGeneratedFile(project: Project): VirtualFile {
        return getGeneratedFilePath(project).let {
            return@let (it.findChild("assets.dart") ?: it.createChildData(this, "assets.dart"))
        }
    }

    /**
     * 获取项目.idea目录的一个文件
     */
    private fun getProjectIdeaFile(project: Project): VirtualFile? {
        val ideaFile = project.projectFile ?: project.workspaceFile ?: project.guessProjectDir()?.children?.first()
        if (ideaFile == null) {
            PluginUtils.showNotify("Missing .idea/misc.xml or .idea/workspace.xml file")
        }
        return ideaFile
    }

    /**
     * 判断项目中是否包含这个file
     */
    fun containsProjectFile(project: Project, fileName: String): Boolean {
        return FilenameIndex.getAllFilesByExt(project, "dart").firstOrNull {
            it.path.endsWith(fileName)
        } != null
    }

    /**
     * 判断Directory中是否包含这个file
     */
    fun containsDirectoryFile(directory: PsiDirectory, fileName: String): Boolean {
        return directory.files.filter { it.name.endsWith(".dart") }
            .firstOrNull { it.name.contains(fileName) } != null
    }

    @Suppress("DuplicatedCode")
    @JvmStatic
    fun getPubSpecConfig(project: Project): PubSpecConfig? {
        PubRoot.forFile(getProjectIdeaFile(project))?.let { pubRoot ->
            FileInputStream(pubRoot.pubspec.path).use { inputStream ->
                (Yaml().load(inputStream) as? Map<String, Any>)?.let { map ->
                    return PubSpecConfig(project, pubRoot, map)
                }
            }
        }
        return null
    }

    @Suppress("DuplicatedCode")
    @JvmStatic
    fun shouldActivateFor(project: Project): Boolean = shouldActivateWith(getPubSpecConfig(project))

    @Suppress("DuplicatedCode")
    @JvmStatic
    fun shouldActivateWith(pubSpecConfig: PubSpecConfig?): Boolean {
        pubSpecConfig?.let {
            // Did the user deactivate for this project?
            // Automatically activated for Flutter projects.
            return it.pubRoot.declaresFlutter()
        }
        return pubSpecConfig?.pubRoot?.declaresFlutter() ?: false
    }

    private const val PROJECT_NAME = "name"

    data class PubSpecConfig(
        val project: Project,
        val pubRoot: PubRoot,
        val map: Map<String, Any>,
        //项目名称,导包需要
        val name: String = ((if (map[PROJECT_NAME] == "null") null else map[PROJECT_NAME])
            ?: project.name).toString(),
        val isFlutterModule: Boolean = FlutterModuleUtils.hasFlutterModule(project)
    )
}
