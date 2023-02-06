package edu.mailman.weatherapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import edu.mailman.weatherapplication.databinding.ActivityMainBinding
import edu.mailman.weatherapplication.models.WeatherResponse
import edu.mailman.weatherapplication.network.WeatherService
import retrofit.Callback
import retrofit.GsonConverterFactory
import retrofit.Response
import retrofit.Retrofit
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var progressDialog: Dialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(this,
            "Turn on location provider", Toast.LENGTH_LONG).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread().check()
        }
    }

    private fun isLocationEnabled() : Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)  ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {
            val retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create(WeatherService::class.java)

            val listCall = service.getWeather(
                latitude, longitude, Constants.UNITS, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse> {
                override fun onResponse(
                    response: Response<WeatherResponse>?,
                    retrofit: Retrofit?) {

                    hideProgressDialog()
                    if (response!!.isSuccess) {
                        val weatherList = response.body()
                        setupUI(weatherList)
                        Log.i("Response Result", "$weatherList")
                    } else {
                        when (val rc = response.code()) {
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            } else -> {
                                Log.e("Error", "generic error $rc")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    Log.e("Error", t!!.message.toString())
                    hideProgressDialog()
                }
            })

        } else {
            Toast.makeText(
                this@MainActivity,
                "No Internet connection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. " +
                    "It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val locationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setWaitForAccurateLocation(false)
                .setMaxUpdates(1)
                .build()


        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude: Double? = mLastLocation?.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude: Double? = mLastLocation?.longitude
            Log.i("Current Longitude", "$longitude")

            getLocationWeatherDetails(latitude!!, longitude!!)
        }
    }

    private fun showCustomProgressDialog() {
        progressDialog = Dialog(this)
        progressDialog!!.setContentView(R.layout.dialog_custom_progress)
        progressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            } else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog!!.dismiss()
        }
    }

    private fun setupUI(weatherList: WeatherResponse) {
        Log.i("Weather Name", weatherList.toString())

        for (i in weatherList.weather.indices) {
            val countryCode = weatherList.sys.country
            val unitDegrees = getTemperatureUnit(countryCode)

            binding?.tvMain?.text = weatherList.weather[i].main
            binding?.tvMainDescription?.text = weatherList.weather[i].description
            binding?.tvTemperature?.text = String.format("Temperature  %.0f%s",
                weatherList.main.temp, unitDegrees)
            binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
            binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)
            binding?.tvSpeed?.text = String.format("Wind Speed  %.0f mph", weatherList.wind.speed)
            binding?.tvName?.text = weatherList.name
            binding?.tvCountry?.text = countryCode
            binding?.tvHumidity?.text = String.format("Humidity  %d%%", weatherList.main.humidity)
            binding?.tvMaxMin?.text = String.format("Max: %.0f%s / Min: %.0f%s",
                weatherList.main.temp_max, unitDegrees, weatherList.main.temp_min, unitDegrees)

            when (weatherList.weather[i].icon) {
                "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "50n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
            }
        }
    }

    private fun getTemperatureUnit(countryCode: String): String {
        return if (
            countryCode == "US" ||
            countryCode == "LR" ||
            countryCode == "MM") {
            "°F"
        } else {
            "°C"
        }
    }

    private fun unixTime(timex: Long) : String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}