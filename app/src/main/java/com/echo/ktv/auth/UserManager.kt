package com.echo.ktv.auth

import android.content.Context
import android.content.SharedPreferences
import com.echo.ktv.api.CryptoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigInteger
import java.util.UUID

data class UserProfile(
    val userId: Long,
    val token: String,
    val nickname: String,
    val avatarUrl: String,
    val vipType: Int,
    val vipToken: String,
    val isVip: Boolean
)

object UserManager {
    private const val PREFS_NAME = "kugo_ktv_user_prefs"
    private const val KEY_DEVICE_ID = "key_persistent_device_id"
    private const val KEY_DFID = "key_persistent_dfid"
    private const val KEY_MID = "key_persistent_mid"
    
    private const val KEY_USER_ID = "key_user_id"
    private const val KEY_TOKEN = "key_token"
    private const val KEY_NICKNAME = "key_nickname"
    private const val KEY_AVATAR = "key_avatar"
    private const val KEY_VIP_TYPE = "key_vip_type"
    private const val KEY_VIP_TOKEN = "key_vip_token"
    private const val KEY_IS_VIP = "key_is_vip"

    private var prefs: SharedPreferences? = null

    // Persistent Device Fingerprints (Anti-Risk Control)
    var deviceId: String = ""
        private set
    var dfid: String = "-"
        private set
    var mid: String = "0"
        private set

    // User Profile Reactive State
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        // 1. Initialize & Persist Fixed Device Fingerprint (Prevents Multi-Device Anti-Fraud Trigger)
        var savedDeviceId = sp.getString(KEY_DEVICE_ID, null)
        if (savedDeviceId.isNullOrEmpty()) {
            savedDeviceId = UUID.randomUUID().toString()
            val computedMid = calculateMid(savedDeviceId)
            val computedDfid = CryptoUtils.md5(savedDeviceId).take(24)
            sp.edit()
                .putString(KEY_DEVICE_ID, savedDeviceId)
                .putString(KEY_MID, computedMid)
                .putString(KEY_DFID, computedDfid)
                .apply()
            deviceId = savedDeviceId
            mid = computedMid
            dfid = computedDfid
        } else {
            deviceId = savedDeviceId
            mid = sp.getString(KEY_MID, calculateMid(savedDeviceId)) ?: "0"
            dfid = sp.getString(KEY_DFID, "-") ?: "-"
        }

        // 2. Restore User Login Session
        val savedUserId = sp.getLong(KEY_USER_ID, 0L)
        val savedToken = sp.getString(KEY_TOKEN, null)
        if (savedUserId > 0L && !savedToken.isNullOrEmpty()) {
            val nickname = sp.getString(KEY_NICKNAME, "酷狗概念版用户") ?: "酷狗概念版用户"
            val avatar = sp.getString(KEY_AVATAR, "") ?: ""
            val vipType = sp.getInt(KEY_VIP_TYPE, 0)
            val vipToken = sp.getString(KEY_VIP_TOKEN, "") ?: ""
            val isVip = sp.getBoolean(KEY_IS_VIP, vipType > 0 || vipToken.isNotEmpty())

            _userProfile.value = UserProfile(
                userId = savedUserId,
                token = savedToken,
                nickname = nickname,
                avatarUrl = avatar,
                vipType = vipType,
                vipToken = vipToken,
                isVip = isVip
            )
        }
    }

    fun saveLogin(
        userId: Long,
        token: String,
        nickname: String,
        avatarUrl: String,
        vipType: Int = 0,
        vipToken: String = "",
        isVip: Boolean = false
    ) {
        val finalIsVip = isVip || vipType > 0 || vipToken.isNotEmpty()
        prefs?.edit()
            ?.putLong(KEY_USER_ID, userId)
            ?.putString(KEY_TOKEN, token)
            ?.putString(KEY_NICKNAME, nickname)
            ?.putString(KEY_AVATAR, avatarUrl)
            ?.putInt(KEY_VIP_TYPE, vipType)
            ?.putString(KEY_VIP_TOKEN, vipToken)
            ?.putBoolean(KEY_IS_VIP, finalIsVip)
            ?.apply()

        _userProfile.value = UserProfile(
            userId = userId,
            token = token,
            nickname = nickname,
            avatarUrl = avatarUrl,
            vipType = vipType,
            vipToken = vipToken,
            isVip = finalIsVip
        )
    }

    fun logout() {
        prefs?.edit()
            ?.remove(KEY_USER_ID)
            ?.remove(KEY_TOKEN)
            ?.remove(KEY_NICKNAME)
            ?.remove(KEY_AVATAR)
            ?.remove(KEY_VIP_TYPE)
            ?.remove(KEY_VIP_TOKEN)
            ?.remove(KEY_IS_VIP)
            ?.apply()

        _userProfile.value = null
    }

    val isLoggedIn: Boolean
        get() = _userProfile.value != null

    val isVip: Boolean
        get() = _userProfile.value?.isVip == true

    val userId: String
        get() = _userProfile.value?.userId?.toString() ?: "0"

    val token: String
        get() = _userProfile.value?.token ?: ""

    /**
     * Standard KuGou MID Algorithm:
     * Hashes device UUID using MD5, interprets the 32-char hex string as a base-16 BigInteger, and returns decimal string.
     */
    private fun calculateMid(uuidStr: String): String {
        return try {
            val md5Hex = CryptoUtils.md5(uuidStr)
            var bigInt = BigInteger.ZERO
            val base16 = BigInteger.valueOf(16)
            val len = md5Hex.length
            for (i in 0 until len) {
                val digit = Character.digit(md5Hex[i], 16)
                if (digit >= 0) {
                    val charVal = BigInteger.valueOf(digit.toLong())
                    val powerVal = base16.pow(len - 1 - i)
                    bigInt = bigInt.add(charVal.multiply(powerVal))
                }
            }
            bigInt.toString()
        } catch (e: Exception) {
            "0"
        }
    }
}
