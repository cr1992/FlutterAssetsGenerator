package com.crzsc.plugin.test

import com.crzsc.plugin.utils.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class PackageConfigTest {

    @Test
    fun testPackageParameterEnabled() {
        // 1. Setup Assets
        val root = AssetNode("Assets", "", MediaType.DIRECTORY, null)
        val assetsDir = AssetNode("assets", "assets", MediaType.DIRECTORY, null)
        root.children.add(assetsDir)
        assetsDir.children.add(
            AssetNode("logo.png", "assets/logo.png", MediaType.IMAGE, null)
        )

        // 2. Setup Config
        // Construct a map that enables package_parameter_enabled
        val configMap =
            mapOf(
                "name" to "my_awesome_package",
                Constants.KEY_CONFIGURATION_MAP to
                        mapOf(Constants.KEY_PACKAGE_PARAMETER_ENABLED to true)
            )

        val mockConfig = Mockito.mock(ModulePubSpecConfig::class.java)
        Mockito.`when`(mockConfig.map).thenReturn(configMap)
        // Even if this returns something, it should be ignored by the new logic

        // 3. Generate
        val generator =
            DartClassGenerator(
                root,
                mockConfig,
                hasSvg = true,
                hasLottie = true,
                flutterVersion = null
            )
        val code = generator.generate()

        // 4. Verify
        println(code)

        // Verify Helper Classes contain package declaration
        assertTrue(
            "AssetGenImage should contain package declaration",
            code.contains("static const String package = 'my_awesome_package';")
        )

        // Verify methods use the package
        assertTrue(
            "Image.asset should use package: package",
            code.contains("package: package,")
        )

        // Verify path getter includes package prefix
        assertTrue(
            "path getter should include package prefix",
            code.contains("'packages/my_awesome_package/\$_assetName'")
        )

        // Verify fields do NOT contain prefix in the constructor (because it's handled by
        // path
        // getter / Image.asset)
        // The generator uses prefix + child.path.
        // If package_parameter_enabled is true, prefix should be forced to empty string.
        // child.path is "assets/logo.png"
        // So expected: AssetGenImage('assets/logo.png')
        // NOT: AssetGenImage('packages/my_awesome_package/assets/logo.png')
        assertTrue(
            "Field constructor should use relative path",
            code.contains("AssetGenImage('assets/logo.png')")
        )

        // Ensure SvgGenImage also has it
        assertTrue(
            "SvgGenImage should contain package declaration",
            code.contains("class SvgGenImage") &&
                    code.contains("static const String package = 'my_awesome_package';")
        )

        // Ensure SvgGenImage path getter is correct (Strict check)
        val svgClassContent =
            code.substringAfter("class SvgGenImage")
                .substringBefore("class LottieGenImage")
        assertTrue(
            "SvgGenImage path getter should include package prefix",
            svgClassContent.contains(
                "String get path => 'packages/my_awesome_package/\$_assetName';"
            )
        )
    }
}
