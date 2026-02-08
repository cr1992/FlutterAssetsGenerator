package com.crzsc.plugin.test

import com.crzsc.plugin.utils.AssetNode
import com.crzsc.plugin.utils.DartClassGenerator
import com.crzsc.plugin.utils.MediaType
import com.crzsc.plugin.utils.ModulePubSpecConfig
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class DartClassGeneratorTest {

        @Test
        fun testSpecialCharFieldNames() {
                // 构建测试资源树
                val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
                val assetsDir = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
                root.children.add(assetsDir)

                // 添加特殊命名的文件
                // 1. 数字开头: 3.0.0.1.txt -> a3001
                assetsDir.children.add(
                        AssetNode("3.0.0.1.txt", "assets/3.0.0.1.txt", MediaType.UNKNOWN, null)
                )
                // 2. 关键词: class.png -> class_
                assetsDir.children.add(
                        AssetNode("class.png", "assets/class.png", MediaType.IMAGE, null)
                )
                // 3. 特殊字符: my-image.png -> myImage
                assetsDir.children.add(
                        AssetNode("my-image.png", "assets/my-image.png", MediaType.IMAGE, null)
                )
                // 4. 多重扩展名: archive.tar.gz -> archiveTarGz (注意: AssetTreeBuilder 可能会把 name 设置为
                // archive.tar, 但这里模拟最原始的情况)
                // 假设 AssetTreeBuilder.processFile 中的 name = file.nameWithoutExtension
                // 对于 archive.tar.gz -> name 可能是 archive.tar
                assetsDir.children.add(
                        AssetNode("archive.tar", "assets/archive.tar.gz", MediaType.UNKNOWN, null)
                )
                // 5. 仅数字: 123.png -> a123
                assetsDir.children.add(AssetNode("123", "assets/123.png", MediaType.IMAGE, null))
                // 6. 特殊符号开头: _private.png -> private (trim logic)
                assetsDir.children.add(
                        AssetNode("_private", "assets/_private.png", MediaType.IMAGE, null)
                )

                // 生成代码
                val config = createMockConfig()
                val generator =
                        DartClassGenerator(
                                root,
                                config,
                                hasSvg = false,
                                hasLottie = false,
                                flutterVersion = null
                        )
                val generatedCode = generator.generate()

                println(generatedCode)

                // 验证
                // 3.0.0.1.txt 应转换为 a3001
                assertTrue(
                        "Should contain 'a3001Txt'",
                        generatedCode.contains(
                                "static const String a3001Txt = 'assets/3.0.0.1.txt';"
                        )
                )

                // class.png 应转换为 classPng
                assertTrue(
                        "Should contain 'classPng'",
                        generatedCode.contains(
                                "static const AssetGenImage classPng = AssetGenImage('assets/class.png');"
                        )
                )
                // my-image.png 应转换为 myImagePng
                assertTrue(
                        "Should contain 'myImagePng'",
                        generatedCode.contains(
                                "static const AssetGenImage myImagePng = AssetGenImage('assets/my-image.png');"
                        )
                )
                // archive.tar.gz (name=archive.tar) -> archiveTar
                // 如果我们移除 substringBeforeLast('.')，那么 archive.tar -> archive.tar -> archiveTar
                assertTrue(
                        "archive.tar should be archiveTar",
                        generatedCode.contains(
                                "static const String archiveTar = 'assets/archive.tar.gz';"
                        )
                )

                // 123.png (name=123) -> a123
                assertTrue(
                        "123.png should be a123",
                        generatedCode.contains(
                                "static const AssetGenImage a123 = AssetGenImage('assets/123.png');"
                        )
                )

                // _private.png (name=_private) -> private
                assertTrue(
                        "_private.png should be private",
                        generatedCode.contains(
                                "static const AssetGenImage private = AssetGenImage('assets/_private.png');"
                        )
                )
        }

        private fun createMockConfig(): ModulePubSpecConfig {
                // Mock ModulePubSpecConfig
                val mockConfig = Mockito.mock(ModulePubSpecConfig::class.java)

                return mockConfig
        }
}
