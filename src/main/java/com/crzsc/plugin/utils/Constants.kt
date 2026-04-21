package com.crzsc.plugin.utils

object Constants {
    /** 配置map的key */
    const val KEY_CONFIGURATION_MAP = "flutter_assets_generator"

    /** 是否启用插件能力 */
    const val KEY_ENABLE = "enable"

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

    /** 是否在 legacy 样式中使用父目录作为变量名前缀 (默认 true) */
    const val KEY_NAMED_WITH_PARENT = "named_with_parent"

    /** 命名风格 */
    const val KEY_NAME_STYLE = "name_style"

    /** 引用类型：class（包装类）或 string（原始路径） */
    const val KEY_LEAF_TYPE = "leaf_type"

    /** 输出的文件名 */
    const val KEY_OUTPUT_FILENAME = "output_filename"

    /** 分割文件的正则 */
    const val FILENAME_SPLIT_PATTERN = "filename_split_pattern"

    /** 忽略的目录 */
    const val PATH_IGNORE = "path_ignore"

    /** 默认目录 */
    const val DEFAULT_OUTPUT_DIR = "generated"
    const val DEFAULT_CLASS_NAME = "Assets"
    const val DEFAULT_NAME_STYLE = "camel"
    const val DEFAULT_LEAF_TYPE = "class"
    const val NAME_STYLE_CAMEL = "camel"
    const val NAME_STYLE_SNAKE = "snake"
    const val LEAF_TYPE_CLASS = "class"
    const val LEAF_TYPE_STRING = "string"

    const val DEFAULT_FILENAME_SPLIT_PATTERN = "[-_]"
}
