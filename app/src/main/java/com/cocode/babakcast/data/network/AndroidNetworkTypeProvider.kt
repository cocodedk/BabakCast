package com.cocode.babakcast.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cocode.babakcast.domain.network.NetworkType
import com.cocode.babakcast.domain.network.NetworkTypeProvider

class AndroidNetworkTypeProvider(private val context: Context) : NetworkTypeProvider {

    override fun current(): NetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkType.OFFLINE
        val active = cm.activeNetwork ?: return NetworkType.OFFLINE
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkType.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkType.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OTHER
        }
    }
}
