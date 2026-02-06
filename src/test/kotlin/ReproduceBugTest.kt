package com.crzsc.plugin.test

import com.crzsc.plugin.utils.AssetTreeBuilder
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito

class ReproduceBugTest {

    @Test
    fun testNonRecursiveScanning() {
        // Scenario:
        // Configured: assets-b/button-item/
        // Contains: bg.png
        // Contains Subdir: nested/ -> nested.png
        // Expected: nested/nested.png should be ignored.

        val buttonItemDir = createMockDir("button-item", "/project/assets-b/button-item")
        val bgPng = createMockFile("bg.png", "/project/assets-b/button-item/bg.png", "png")

        val nestedDir = createMockDir("nested", "/project/assets-b/button-item/nested")
        val nestedPng =
                createMockFile(
                        "nested.png",
                        "/project/assets-b/button-item/nested/nested.png",
                        "png"
                )

        // Setup hierarchy
        // button-item contains bg.png AND nested dir
        Mockito.`when`(buttonItemDir.children).thenReturn(arrayOf(bgPng, nestedDir))
        Mockito.`when`(nestedDir.children).thenReturn(arrayOf(nestedPng))
        Mockito.`when`(nestedDir.parent).thenReturn(buttonItemDir)

        val rootNode = AssetTreeBuilder.build(listOf(buttonItemDir), "/project", emptyList())

        printTree(rootNode)

        val assetsBNode = rootNode.children.find { it.name == "assets-b" }
        if (assetsBNode == null) fail("assets-b missing")
        val buttonItemNode = assetsBNode!!.children.find { it.name == "button-item" }
        if (buttonItemNode == null) fail("button-item missing")

        // Verify bg.png exists
        val bgNode = buttonItemNode.children.find { it.name == "bg" }
        if (bgNode == null) fail("bg.png should be present")

        // Verify nested dir does NOT exist
        val nestedNode = buttonItemNode.children.find { it.name == "nested" }
        if (nestedNode != null) {
            fail("Nested directory 'nested' should be IGNORED in non-recursive scanning!")
        }
    }

    private fun createMockFile(name: String, path: String, extension: String): VirtualFile {
        val file = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(file.name).thenReturn(name)
        Mockito.`when`(file.nameWithoutExtension).thenReturn(name.substringBeforeLast("."))
        Mockito.`when`(file.path).thenReturn(path)
        Mockito.`when`(file.isValid).thenReturn(true)
        Mockito.`when`(file.isDirectory).thenReturn(false)
        Mockito.`when`(file.extension).thenReturn(extension)
        return file
    }

    private fun createMockDir(name: String, path: String): VirtualFile {
        val file = Mockito.mock(VirtualFile::class.java)
        Mockito.`when`(file.name).thenReturn(name)
        Mockito.`when`(file.path).thenReturn(path)
        Mockito.`when`(file.isValid).thenReturn(true)
        Mockito.`when`(file.isDirectory).thenReturn(true)
        Mockito.`when`(file.children).thenReturn(arrayOf()) // Default empty
        return file
    }

    private fun printTree(node: com.crzsc.plugin.utils.AssetNode, depth: Int = 0) {
        val indent = "  ".repeat(depth)
        println("$indent- ${node.name} (${node.path})")
        node.children.forEach { printTree(it, depth + 1) }
    }
}
