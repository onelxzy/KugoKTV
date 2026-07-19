package com.echo.ktv.api

import java.util.TreeMap

object SignatureUtils {

    const val APP_ID = "1005"
    const val CLIENT_VER = "20489"
    const val ANDROID_SALT = "OIlwieks28dk2k092lksi2UIkp"
    const val SIGN_KEY_SALT = "57ae12eb6890223e355ccfcb74edf70d"
    const val PARAMS_KEY_SALT = "OIlwieks28dk2k092lksi2UIkp"

    fun signatureAndroidParams(params: Map<String, String>, data: String = ""): String {
        // Sort keys alphabetically
        val sortedMap = TreeMap(params)
        val builder = StringBuilder()
        for ((key, value) in sortedMap) {
            builder.append(key).append("=").append(value)
        }
        val paramsString = builder.toString()
        val rawInput = "$ANDROID_SALT$paramsString$data$ANDROID_SALT"
        return CryptoUtils.md5(rawInput)
    }

    fun signKey(hash: String, mid: String, userId: String = "0"): String {
        val rawInput = "$hash$SIGN_KEY_SALT$APP_ID$mid$userId"
        return CryptoUtils.md5(rawInput)
    }

    fun signParamsKey(data: String): String {
        val rawInput = "$APP_ID$PARAMS_KEY_SALT$CLIENT_VER$data"
        return CryptoUtils.md5(rawInput)
    }
}
