import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class ConnectivityReceiver(private val listener: ConnectivityListener) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        val isConnected = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        listener.onNetworkConnectionChanged(isConnected)
    }

    interface ConnectivityListener {
        fun onNetworkConnectionChanged(isConnected: Boolean)
    }
}
