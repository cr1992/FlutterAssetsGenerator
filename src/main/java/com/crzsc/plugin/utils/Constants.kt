package com.crzsc.plugin.utils

object Constants {
    /**
     * 配置map的key
     */
    const val KEY_CONFIGURATION_MAP = "flutter_assets_generator"
    /**
     * 输出目录的key
     */
    const val KEY_OUTPUT_DIR = "output_dir"

    /**
     * 输出文件的类名
     */
    const val KEY_CLASS_NAME = "class_name"

    /**
     * 是否自动检测
     */
    const val KEY_AUTO_DETECTION = "auto_detection"

    /**
     * 命名是否根据上级目录决定
     */
    const val KEY_NAMED_WITH_PARENT = "named_with_parent"

    const val KEY_LEADING_WITH_PACKAGE_NAME = "leading_with_package_name"

    /**
     * 输出的文件名
     */
    const val KEY_OUTPUT_FILENAME = "output_filename"

    /**
     * 分割文件的正则
     */
    const val FILENAME_SPLIT_PATTERN = "filename_split_pattern"

    /**
     * 忽略的目录
     */
    const val PATH_IGNORE = "path_ignore"

    /**
     * 默认目录
     */
    const val DEFAULT_OUTPUT_DIR = "generated"
    const val DEFAULT_CLASS_NAME = "Assets"

    const val DEFAULT_FILENAME_SPLIT_PATTERN = "[-_]"
}