package com.echo.ktv.server

import java.net.NetworkInterface
import java.util.Collections

object IpUtils {
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val host = address.hostAddress ?: ""
                        val isIPv4 = host.indexOf(':') < 0
                        if (isIPv4) {
                            return host
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "127.0.0.1"
    }
}
