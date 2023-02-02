package edu.mailman.weatherapplication

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!isLocationEnabled()) {
            Toast.makeText(this,
            "Turn on location provider", Toast.LENGTH_LONG).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Toast.makeText(this,
                "Location provider is on", Toast.LENGTH_LONG).show()
        }
    }

    private fun isLocationEnabled() : Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)  ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

}