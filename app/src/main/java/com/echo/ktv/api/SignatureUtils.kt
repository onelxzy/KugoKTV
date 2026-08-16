package com.echo.ktv.api

import java.util.TreeMap

object SignatureUtils {

    // KuGou Concept Lite (概念版) configuration matching EchoMusic 1-to-1
    const val LITE_APP_ID = "3116"
    const val LITE_CLIENT_VER = "11440"
    const val LITE_ANDROID_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
    const val LITE_SIGN_KEY_SALT = "185672dd44712f60bb1736df5a377e82"

    // KuGou Standard configuration
    const val STD_APP_ID = "1005"
    const val STD_CLIENT_VER = "20489"
    const val STD_ANDROID_SALT = "OIlwieks28dk2k092lksi2UIkp"
    const val STD_SIGN_KEY_SALT = "57ae12eb6890223e355ccfcb74edf70d"

    fun signatureAndroidParams(params: Map<String, String>, data: String = "", isLite: Boolean = true): String {
        val salt = if (isLite) LITE_ANDROID_SALT else STD_ANDROID_SALT
        val sortedMap = TreeMap(params)
        val builder = StringBuilder()
        for ((key, value) in sortedMap) {
            builder.append(key).append("=").append(value)
        }
        val paramsString = builder.toString()
        val rawInput = "$salt$paramsString$data$salt"
        return CryptoUtils.md5(rawInput)
    }

    fun signKey(hash: String, mid: String = "undefined", userId: String = "0", isLite: Boolean = true): String {
        val salt = if (isLite) LITE_SIGN_KEY_SALT else STD_SIGN_KEY_SALT
        val appId = if (isLite) LITE_APP_ID else STD_APP_ID
        val rawInput = "$hash$salt$appId$mid$userId"
        return CryptoUtils.md5(rawInput)
    }
}
