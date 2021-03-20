package com.gsixacademy.android.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.gsixacademy.android.weatherapp.R.*
import com.gsixacademy.android.weatherapp.models.WeatherResponse
import com.gsixacademy.android.weatherapp.network.WeatherService
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

// Variabla za spoena lokacija na klientot sto mu dava na korisnikot da ja dobie momentalnata lokacija.
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

// Variabla za Progress Dialog.
    private var mProgressDialog: Dialog? = null

// Variabla za momentalna - Current Latitude.
    private var mLatitude: Double = 0.0

// Variabla za momentalna - Current Longitude.
    private var mLongitude: Double = 0.0

// TODO (1) Variabla za SharedPreferences
    private lateinit var mSharedPreferences: SharedPreferences



    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)


        // Custom Toolbar - implementacija
        setSupportActionBar(myToolbar)


// Inicijalizacija za povrzana lokacija so clientot - FusedLocationClient!
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

// TODO (2) Inicijalizacija za SharedPreferences varijabla.
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

// TODO (7) Povikuvanje na metodda UI za da se popolnat podatocite vo UI sto se prethodno zacuvani vo SharedPreferences.
        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT).show()

// Prenasochuvanje i setiranje od kade sto treba da se vkluci davatelot na lokacijata - (Location Provider)
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {

                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(this@MainActivity,
                                "You have denied location permission. Please enable them as it is mandatory for the app to work",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?,
                    ) {
                        showRationalDialogForPermissions()
                    }
                      }).onSameThread()
                        .check()
        }
    }


    // Funkcija za baranje na momentalnata lokacija. Koristenje na klientot za dobienata lokacija.
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }
// Objekt za povraten povik na lokacija,na klientot za dobienata lokacija
// kade sto ke gi dobieme momentalnite detali za lokacijata.
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            val mLastLocation: Location = locationResult!!.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "&latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            mLatitude = latitude
            mLongitude = longitude
            getLocationWeatherDetails(latitude, longitude)
        }
    }
// Funkcija koja se koristi za da se dobijat vremenskite detali od momentalnata lokacija bazirani na Latitude-Longitude.
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {
// Retrofit
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

// Metodi za povraten povik koj se izvrsuvaat so pomosh na Retrofit.
            listCall.clone().enqueue(object : Callback<WeatherResponse> {
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>,
                ) {
                    if (response.isSuccessful) {

                        hideProgressDialog()

                        val weatherList: WeatherResponse? = response.body()
                        Log.i("Response Result", "$weatherList")

// TODO (4) Pretvaranje na objektot vo String i go zacuvuvame Stringot vo SharedPreference!
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

// Ne sum siguren dali treba - if (weatherList != null) {}
                        if (weatherList != null) {
// TODO (5) Metoda SetupUI. Start - End!
                        setupUI()
                        }

                        Log.i("Response Result", "$weatherList")
                    } else {
// Proverka (else) ako odgovorot ne e uspeshen se proveruva kodot i ni dava nekoj od ovie rezultati (400, 404 isl).
                        when (response.code()) {
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrrrr", t.message.toString())
                    hideProgressDialog()
                }
            })

        } else {
            Toast.makeText(
                this@MainActivity,
                "No internet connection available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

// Funkcija sto se koristi za prikazuvanje na predupreduvanje koga se odbivaat dozvolite-permissions
// i treba da se dozvoli od setingot na aplikacijata. za ovie informacii.
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature. it can be enabled underApplication Settings")
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
            .setNegativeButton("Cancel") {
                    dialog,
                    _,
                ->
                dialog.dismiss()
            }.show()

    }

// Funkcija sto se koristi za da se potvrdi dali (Lokacijata i GPS) e ovozmozena ili ne, na uredot na korisnikot.
    private fun isLocationEnabled(): Boolean {

        // Ova obezbeduva pristap do uslugite za sistemska lokacija.
        val locationManager: LocationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(layout.dialog_custom_progress)

        mProgressDialog!!.show()
    }

    // Dodaden Button za Refresh - no ne se pojavuva na ekran !!!
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // Fun za Button - Refresh !!!
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            id.action_refresh -> {
                getLocationWeatherDetails(mLatitude, mLongitude)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
// Funkcija se koristi da gi setira rezultatite vo UI elements.
    private fun setupUI() {

// TODO (6) Vaka go dobivame zacuvaniot odgovor (Stored Response) od SharedPreferences
//  i povtorno se pretvara vo objekt na podatoci, za da gi napolnime podatocite vo UI.

        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()) {

            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

// For loop za da gi dobieme potrebnite podatoci i site se naseleni vo UI.
            for (i in weatherList.weather.indices) {
                Log.i("Weather Name", weatherList.weather.toString())

                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
                tv_min.text = weatherList.main.temp_min.toString() + " min"
                tv_max.text = weatherList.main.temp_max.toString() + " max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country

                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text = unixTime(weatherList.sys.sunset)

// Update na glavnite iconi.
                when (weatherList.weather[i].icon) {
                    "01d" -> iv_main.setImageResource(drawable.sunny)
                    "02d" -> iv_main.setImageResource(drawable.cloud)
                    "03d" -> iv_main.setImageResource(drawable.cloud)
                    "04d" -> iv_main.setImageResource(drawable.cloud)
                    "05d" -> iv_main.setImageResource(drawable.cloud)
                    "10d" -> iv_main.setImageResource(drawable.rain)
                    "11d" -> iv_main.setImageResource(drawable.storm)
                    "13d" -> iv_main.setImageResource(drawable.snowflake)
                    "01n" -> iv_main.setImageResource(drawable.cloud)
                    "02n" -> iv_main.setImageResource(drawable.cloud)
                    "03n" -> iv_main.setImageResource(drawable.cloud)
                    "10n" -> iv_main.setImageResource(drawable.cloud)
                    "11n" -> iv_main.setImageResource(drawable.rain)
                    "13n" -> iv_main.setImageResource(drawable.snowflake)
                }
            }
        }
    }

// Funkcija sto se koristi za da se dobie vrednosta na edinicta za temperatura.
    private fun getUnit(value: String): String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }
// Funkcija sto se koristi za da se dobie formatirano vreme, vrz formatot na lokacijata sto mu ja prenesuvame.
    private fun unixTime(timex: Int): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}