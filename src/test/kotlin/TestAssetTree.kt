package com.crzsc.plugin.test

import com.crzsc.plugin.utils.AssetNode
import com.crzsc.plugin.utils.MediaType
import org.junit.Test

class TestAssetTree {
    @Test
    fun test() {
        // 模拟资源树结构
        val root = AssetNode("assets", "assets", MediaType.DIRECTORY, null)

        val imageDir = AssetNode("image", "assets/image", MediaType.DIRECTORY, null)
        val svgDir = AssetNode("svg", "assets/svg", MediaType.DIRECTORY, null)
        val lottieDir = AssetNode("lottie", "assets/lottie", MediaType.DIRECTORY, null)

        root.children.add(imageDir)
        root.children.add(svgDir)
        root.children.add(lottieDir)

        // 添加文件
        imageDir.children.add(
                AssetNode(
                        "puro_icon_small.png",
                        "assets/image/puro_icon_small.png",
                        MediaType.IMAGE,
                        null
                )
        )
        svgDir.children.add(
                AssetNode(
                        "accelerate-svgrepo-com.svg",
                        "assets/svg/accelerate-svgrepo-com.svg",
                        MediaType.SVG,
                        null
                )
        )
        lottieDir.children.add(
                AssetNode("animation.json", "assets/lottie/animation.json", MediaType.LOTTIE, null)
        )

        // 测试类名生成
        println("Root node: ${root.name}, path: ${root.path}")
        println("Image dir: ${imageDir.name}, path: ${imageDir.path}")
        println("SVG dir: ${svgDir.name}, path: ${svgDir.path}")
        println("Lottie dir: ${lottieDir.name}, path: ${lottieDir.path}")

        // 测试文件路径
        println("\nFile paths:")
        imageDir.children.forEach { println("  ${it.name}: ${it.path}") }
        svgDir.children.forEach { println("  ${it.name}: ${it.path}") }
        lottieDir.children.forEach { println("  ${it.name}: ${it.path}") }
        lottieDir.children.forEach { println("  ${it.name}: ${it.path}") }

        testResolutionRegex()
    }
}

fun testResolutionRegex() {
    println("\n--- Testing Resolution Regex ---")
    val resolutionDirPattern = Regex("^\\d+(\\.\\d+)?x$")
    val testCases =
            mapOf(
                    "2.0x" to true,
                    "3.0x" to true,
                    "1.5x" to true,
                    "10x" to true,
                    "assets" to false,
                    "image" to false,
                    "2.0" to false, // missing x
                    "x" to false,
                    "2x.png" to
                            false // assuming check is done on directory name via file.isDirectory
                    // check in logic
                    )

    var allPassed = true
    testCases.forEach { (input, expected) ->
        val result = resolutionDirPattern.matches(input)
        if (result != expected) {
            println("FAILED: '$input' -> expected $expected, but got $result")
            allPassed = false
        } else {
            println("PASSED: '$input' -> $result")
        }
    }

    if (allPassed) {
        println("All resolution regex tests passed!")
    } else {
        println("Some resolution regex tests failed.")
        throw RuntimeException("Resolution regex tests failed")
    }
}
