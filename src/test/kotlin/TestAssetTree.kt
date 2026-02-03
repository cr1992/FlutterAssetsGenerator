package com.crzsc.plugin.test

import com.crzsc.plugin.utils.AssetNode
import com.crzsc.plugin.utils.MediaType

fun main() {
    // 模拟资源树结构
    val root = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
    
    val imageDir = AssetNode("image", "assets/image", MediaType.DIRECTORY, null)
    val svgDir = AssetNode("svg", "assets/svg", MediaType.DIRECTORY, null)
    val lottieDir = AssetNode("lottie", "assets/lottie", MediaType.DIRECTORY, null)
    
    root.children.add(imageDir)
    root.children.add(svgDir)
    root.children.add(lottieDir)
    
    // 添加文件
    imageDir.children.add(AssetNode("puro_icon_small.png", "assets/image/puro_icon_small.png", MediaType.IMAGE, null))
    svgDir.children.add(AssetNode("accelerate-svgrepo-com.svg", "assets/svg/accelerate-svgrepo-com.svg", MediaType.SVG, null))
    lottieDir.children.add(AssetNode("animation.json", "assets/lottie/animation.json", MediaType.LOTTIE, null))
    
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
}
