package com.example.tasteit_alpha.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.tasteit_alpha.Activities.MainActivity
import com.example.tasteit_alpha.R
import com.example.tasteit_alpha.Services.LocationService
import com.example.tasteit_alpha.Utils.AppUtils.*
import com.example.tasteit_alpha.Utils.PermissionUtilities
import com.example.tasteit_alpha.ui.Adapters.ImageDataAdapter
import com.example.tasteit_alpha.ui.Adapters.SeekBarChangeAdapter
import com.example.tasteit_alpha.ui.Adapters.TextWatcherAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesProvider


class HomeFragment : Fragment() {
    private var distanceProgress = 0
    private val fusedLocationClient: FusedLocationProviderClient by lazy{
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    lateinit var homeRecycler : RecyclerView
     private var locality: String? = null
     private var currentUserLong : Double = DEFAULT_LANG_LAT
     private var currentUserLat : Double = DEFAULT_LANG_LAT



    private val mReceiver : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            locality= intent.getStringExtra(CITY_EXTRA)
            currentUserLong = intent.getDoubleExtra(LONG_EXTRA, DEFAULT_LANG_LAT)
            currentUserLat = intent.getDoubleExtra(LAT_EXTRA, DEFAULT_LANG_LAT)
            checkQueryStateAndFetchData()
        }
    }


    private var queryState : QueryStates = QueryStates.LOCALITY

    private lateinit var homeViewModel: HomeViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bottomedLayout:ConstraintLayout = view.findViewById(R.id.home_bottom_sheet)
        homeRecycler = view.findViewById(R.id.home_recycler)
        val bmsBehaviour=BottomSheetBehavior.from(bottomedLayout)
        initBottomSheet(view,bmsBehaviour)
        //get the live data from the view model:
        val mSelectedImage = homeViewModel.selectedImage
            mSelectedImage.observe(viewLifecycleOwner, Observer {
                if (it == null) return@Observer
                val args = Bundle()
                args.putParcelable(IMAGE_ARGS, it)
                findNavController().navigate(R.id.action_navigation_home_to_detailsFragment,args)
                mSelectedImage.value = null
            })

        homeViewModel.mImages.observe(viewLifecycleOwner, Observer {
            homeRecycler.layoutManager = StaggeredGridLayoutManager(2,
                StaggeredGridLayoutManager.VERTICAL)
            homeRecycler.adapter =
                ImageDataAdapter(it, currentUserLong, currentUserLat, context, mSelectedImage)
            homeRecycler.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = bmsBehaviour.peekHeight
            }
        })
    }

    private fun initBottomSheet(view: View, bmsBehaviour: BottomSheetBehavior<ConstraintLayout>) {
        initBehaviour(bmsBehaviour)
        val textMeasure:TextView=view.findViewById(R.id.tv_measure_system)
        textMeasure.visibility=View.INVISIBLE
        val etDistance = view.findViewById<EditText>(R.id.et_distance)
        etDistance.visibility=View.INVISIBLE
        val seekBar=view.findViewById<SeekBar>(R.id.seek_distance)
        seekBar.isEnabled=false
        seekBar.setOnSeekBarChangeListener(object : SeekBarChangeAdapter() {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, fromUser: Boolean) {
                distanceProgress=seekBar.progress
                if (fromUser) {
                    etDistance.setText(seekBar.progress.toString())
                }
            }
        })

        etDistance.addTextChangedListener(object : TextWatcherAdapter() {
            @SuppressLint("SetTextI18n")
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if (etDistance.text.toString() == "") {
                    etDistance.setText("0")
                }
                if (etDistance.text.toString().toInt() > 150) {
                    etDistance.setText("150")
                }
                seekBar.progress = etDistance.text.toString().toInt()
                distanceProgress=seekBar.progress
            }

        })

        view.findViewById<RadioGroup>(R.id.search_alg).setOnCheckedChangeListener { radioGroup, i ->
            val radioButton: View = radioGroup!!.findViewById(i)
            when (radioGroup.indexOfChild(radioButton)){
                0 -> {
                    etDistance.visibility = View.INVISIBLE
                    textMeasure.visibility = View.INVISIBLE
                    seekBar.isEnabled = false
                    queryState = QueryStates.LOCALITY
                    deinitCurrentUserLocation()
                }
                1 -> {
                    etDistance.visibility = View.VISIBLE
                    textMeasure.visibility = View.VISIBLE
                    seekBar.isEnabled = true
                    queryState = QueryStates.DISTANCE
                    deinitCurrentUserLocation()

                }
            }
        }
    }

    private fun deinitCurrentUserLocation() {
        currentUserLat= DEFAULT_LANG_LAT
        currentUserLong= DEFAULT_LANG_LAT
    }


    private fun initBehaviour(bmsBehaviour: BottomSheetBehavior<ConstraintLayout>) {
        bmsBehaviour.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED){
                  getCurrentLocation()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val margin =
                    bmsBehaviour.peekHeight + (bottomSheet.height - bmsBehaviour.peekHeight) * slideOffset
                homeRecycler.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = margin.toInt()
                }
            }
        })
    }

    private fun checkQueryStateAndFetchData() {
        when (queryState){
            QueryStates.DISTANCE->{
               homeViewModel.fetchDataByDistance(0,currentUserLong , currentUserLat)
            }

            QueryStates.LOCALITY->{
                locality.let {
                    homeViewModel.fetchDataByLocality(it)
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        getCurrentLocation()
        activity?.registerReceiver(mReceiver , IntentFilter(ACTION_GET_ADDRESS))
    }

    override fun onPause() {
        super.onPause()
        homeRecycler.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        activity?.unregisterReceiver(mReceiver)
    }


    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
             if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){
                 buildPermRequestDialog()
             }else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) , RC_LOCATION)
             }
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if(location == null){
                if(!checkLocSetting(context))
                     showLocationDialog(this)
                return@addOnSuccessListener

            }
            if(queryState!= QueryStates.DISTANCE &&
                currentUserLat!= DEFAULT_LANG_LAT && currentUserLong!= DEFAULT_LANG_LAT &&
                getDistanceBetweenTwoPoints(currentUserLat,currentUserLong,location.latitude,location.longitude)<100)return@addOnSuccessListener
            //get lon and lat and start the service
            val geoIntent = Intent(requireContext() , LocationService::class.java)
            geoIntent.putExtra(LOC_KEY, LatLng(location.latitude, location.longitude))
            requireActivity().startService(geoIntent)
        }



    }

    //permissions and location services methods
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_LOCATION && grantResults[0] == PackageManager.PERMISSION_GRANTED){
           getCurrentLocation()
        }
    }



    private fun buildPermRequestDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setCancelable(false)
        builder.setMessage(R.string.request_dialog_message)
            .setPositiveButton("enable permission") { dialog, _ ->
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), RC_LOCATION)
                dialog.cancel()
            }
            .setNegativeButton("cancel") { dialog, _ ->
                Toast.makeText(context, "please give location permission to use this app", Toast.LENGTH_LONG).show()
                moveToSearch()
                dialog.cancel()
            }
        builder.show()
    }

    private fun moveToSearch() {
        //todo : replace this method with setEmptyViewWithErrorCapturing and a snack bar
        (requireActivity() as MainActivity).mainVp.setCurrentItem(1 , true)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LocationGooglePlayServicesProvider.REQUEST_CHECK_SETTINGS) {
            if (resultCode != AppCompatActivity.RESULT_OK) {
                Toast.makeText(context,
                    "please enable location service if you want to use the home feature",
                    Toast.LENGTH_LONG).show()
                moveToSearch()
            }else{
                getCurrentLocation()
            }
        }
    }

    enum class QueryStates {
        DISTANCE , LOCALITY
    }

}


