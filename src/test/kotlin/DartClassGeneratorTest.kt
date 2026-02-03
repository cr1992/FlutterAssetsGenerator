package com.crzsc.plugin.test

import com.crzsc.plugin.utils.AssetNode
import com.crzsc.plugin.utils.MediaType
import com.crzsc.plugin.utils.DartClassGenerator
import org.junit.Test
import org.junit.Assert.*

/**
 * 测试 DartClassGenerator 生成的代码结构
 */
class DartClassGeneratorTest {
    
    @Test
    fun testNoD uplicateClasses() {
        // 构建测试资源树
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assetsDir)
        
        // 添加子目录
        val imageDir = AssetNode("image", "assets/image", MediaType.DIRECTORY, null)
        val svgDir = AssetNode("svg", "assets/svg", MediaType.DIRECTORY, null)
        val lottieDir = AssetNode("lottie", "assets/lottie", MediaType.DIRECTORY, null)
        
        assetsDir.children.add(imageDir)
        assetsDir.children.add(svgDir)
        assetsDir.children.add(lottieDir)
        
        // 添加文件
        imageDir.children.add(AssetNode("test.png", "assets/image/test.png", MediaType.IMAGE, null))
        svgDir.children.add(AssetNode("test.svg", "assets/svg/test.svg", MediaType.SVG, null))
        lottieDir.children.add(AssetNode("test.json", "assets/lottie/test.json", MediaType.LOTTIE, null))
        
        // 生成代码
        val config = createMockConfig()
        val generator = DartClassGenerator(root, config, hasSvg = true, hasLottie = true)
        val generatedCode = generator.generate()
        
        // 验证:检查类定义数量
        val imageGenCount = generatedCode.split("class \$AssetsImageGen").size - 1
        val svgGenCount = generatedCode.split("class \$AssetsSvgGen").size - 1
        val lottieGenCount = generatedCode.split("class \$AssetsLottieGen").size - 1
        val assetsGenCount = generatedCode.split("class \$AssetsAssetsGen").size - 1
        
        // 每个类应该只出现一次
        assertEquals("$AssetsImageGen should appear exactly once", 1, imageGenCount)
        assertEquals("$AssetsSvgGen should appear exactly once", 1, svgGenCount)
        assertEquals("$AssetsLottieGen should appear exactly once", 1, lottieGenCount)
        assertEquals("$AssetsAssetsGen should not appear", 0, assetsGenCount)
        
        // 验证:根类应该包含三个字段
        assertTrue("Root class should contain image field", generatedCode.contains("static const \$AssetsImageGen image"))
        assertTrue("Root class should contain svg field", generatedCode.contains("static const \$AssetsSvgGen svg"))
        assertTrue("Root class should contain lottie field", generatedCode.contains("static const \$AssetsLottieGen lottie"))
        
        println("Generated code structure is correct!")
        println(generatedCode)
    }
    
    private fun createMockConfig(): Any {
        // 创建模拟配置对象
        // 这里需要根据实际的 ModulePubSpecConfig 结构来实现
        return object {
            fun getLeadingWithPackageNameIfChecked() = ""
        }
    }
}
