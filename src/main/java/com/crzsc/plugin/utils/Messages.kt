package com.crzsc.plugin.utils

import java.util.*


fun message(key: String): String {
    return ResourceBundle.getBundle("messages.MessagesBundle").getString(key)
}

