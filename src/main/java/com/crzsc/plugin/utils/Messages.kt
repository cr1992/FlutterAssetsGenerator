package com.crzsc.plugin.utils

import java.util.*


fun message(key: String): String {
    val l = if (Locale.getDefault().language.equals("us") or Locale.getDefault().language.equals("en")) "" else "_" + Locale.getDefault().language
    return ResourceBundle.getBundle("messages.MessagesBundle$l").getString(key)
}

