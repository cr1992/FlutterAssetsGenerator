package com.crzsc.plugin.utils

import com.intellij.openapi.diagnostic.Logger

/**
 * 依赖版本选择器
 * 根据 Flutter SDK 版本选择兼容的依赖版本
 */
object DependencyVersionSelector {
    private val LOG = Logger.getInstance(DependencyVersionSelector::class.java)
    
    /**
     * 获取推荐的 flutter_svg 版本
     * @param flutterVersion Flutter SDK 版本,如果为 null 则返回保守版本
     * @return 版本约束字符串,例如 "^2.0.10"
     */
    fun getFlutterSvgVersion(flutterVersion: SemanticVersion?): String {
        if (flutterVersion == null) {
            LOG.info("[FlutterAssetsGenerator #DependencyVersionSelector] No Flutter version, using conservative flutter_svg version")
            return "^2.0.10" // 保守策略:使用稳定且广泛兼容的版本
        }
        
        val version = when {
            // Flutter >= 3.32: 使用最新的 2.2.3 版本
            flutterVersion >= SemanticVersion(3, 32, 0) -> "^2.2.3"
            
            // Flutter >= 2.5.0: 使用 2.0.x 系列(稳定且广泛使用)
            flutterVersion >= SemanticVersion(2, 5, 0) -> "^2.0.10"
            
            // Flutter >= 2.0.0: 使用 1.1.x 系列
            flutterVersion >= SemanticVersion(2, 0, 0) -> "^1.1.6"
            
            // 更旧的版本:使用 1.0.x 系列
            else -> "^1.0.3"
        }
        
        LOG.info("[FlutterAssetsGenerator #DependencyVersionSelector] Flutter $flutterVersion -> flutter_svg $version")
        return version
    }
    
    /**
     * 获取推荐的 lottie 版本
     * @param flutterVersion Flutter SDK 版本,如果为 null 则返回保守版本
     * @return 版本约束字符串,例如 "^2.7.0"
     */
    fun getLottieVersion(flutterVersion: SemanticVersion?): String {
        if (flutterVersion == null) {
            LOG.info("[FlutterAssetsGenerator #DependencyVersionSelector] No Flutter version, using conservative lottie version")
            return "^2.7.0" // 保守策略:使用稳定且广泛兼容的版本
        }
        
        val version = when {
            // Flutter >= 3.35: 使用最新的 3.3.2 版本
            flutterVersion >= SemanticVersion(3, 35, 0) -> "^3.3.2"
            
            // Flutter >= 3.27: 使用 3.3.0 版本
            flutterVersion >= SemanticVersion(3, 27, 0) -> "^3.3.0"
            
            // Flutter >= 3.10.0: 使用 3.1.x 系列(较新但稳定)
            flutterVersion >= SemanticVersion(3, 10, 0) -> "^3.1.0"
            
            // Flutter >= 3.0.0: 使用 2.7.x 系列(稳定且广泛使用)
            flutterVersion >= SemanticVersion(3, 0, 0) -> "^2.7.0"
            
            // Flutter >= 2.5.0: 使用 2.3.x 系列
            flutterVersion >= SemanticVersion(2, 5, 0) -> "^2.3.0"
            
            // 更旧的版本:使用 2.0.x 系列
            else -> "^2.0.0"
        }
        
        LOG.info("[FlutterAssetsGenerator #DependencyVersionSelector] Flutter $flutterVersion -> lottie $version")
        return version
    }
}
