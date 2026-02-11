package com.crzsc.plugin.utils

object Constants {
    /** 配置map的key */
    const val KEY_CONFIGURATION_MAP = "flutter_assets_generator"

    /** 输出目录的key */
    const val KEY_OUTPUT_DIR = "output_dir"

    /** 输出文件的类名 */
    const val KEY_CLASS_NAME = "class_name"

    /** 是否自动检测 */
    const val KEY_AUTO_DETECTION = "auto_detection"

    /** 是否自动添加依赖 */
    const val KEY_AUTO_ADD_DEPENDENCIES = "auto_add_dependencies"

    /** 是否启用 package 参数 (生成 package: 'package_name') */
    const val KEY_PACKAGE_PARAMETER_ENABLED = "package_parameter_enabled"

    /** 输出的文件名 */
    const val KEY_OUTPUT_FILENAME = "output_filename"

    /** 分割文件的正则 */
    const val FILENAME_SPLIT_PATTERN = "filename_split_pattern"

    /** 忽略的目录 */
    const val PATH_IGNORE = "path_ignore"

    /** 默认目录 */
    const val DEFAULT_OUTPUT_DIR = "generated"
    const val DEFAULT_CLASS_NAME = "Assets"

    const val DEFAULT_FILENAME_SPLIT_PATTERN = "[-_]"
}
