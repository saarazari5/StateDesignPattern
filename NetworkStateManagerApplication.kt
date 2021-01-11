package com.example.tasteit_alpha

import android.app.Application
import com.example.tasteit_alpha.Utils.NetworkMonitor
import kotlin.properties.Delegates

class NetworkStateManagerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NetworkMonitor(this).startNetworkCallback()
    }

    override fun onTerminate() {
        super.onTerminate()
        NetworkMonitor(this).stopNetworkCallback()
    }

}






//global variables to check connection state
object ConnectionVariables {
    var isNetworkConnected: Boolean by Delegates.observable(false) { property, oldValue, newValue ->
    }
}