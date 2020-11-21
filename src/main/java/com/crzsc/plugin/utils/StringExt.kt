package com.crzsc.plugin.utils

/**
 * 是svg拓展名
 */
val String.isSvgExtension: Boolean
    get() = endsWith(".svg", true)

/**
 * 是Image拓展名
 */
val String.isImageExtension: Boolean
    get() = endsWith(".png", true)
            || endsWith(".jpg", true)
            || endsWith(".webp", true)
