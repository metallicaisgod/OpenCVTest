package com.example.opencvtest

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.MediaController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.opencvtest.databinding.ActivityMainBinding
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader


class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val viewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private val progressDialog by lazy {

        AlertDialog.Builder(this)
            .setView(R.layout.progress_dialog)
            .create()
    }

    private val loaderCallback = object : BaseLoaderCallback(this) {

        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
                }

                else -> super.onManagerConnected(status)
            }
        }
    }

    private var getPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val videoUri = data.data
                videoUri?.let {

                    progressDialog.show()
                    viewModel.videoProcessing(videoUri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (!OpenCVLoader.initDebug()) {
            Log.d(
                TAG,
                "Internal OpenCV library not found. Using OpenCV Manager for initialization"
            )
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, loaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }

        binding.getVideoButton.setOnClickListener {
            chooseVideoFromGallery()
        }

        viewModel.resultUri.observe(this) { outputUri ->
            outputUri?.let {
                progressDialog.dismiss()

                with(binding) {
                    videoView.setVideoURI(it)
                    videoView.setMediaController(MediaController(this@MainActivity))
                    videoView.requestFocus(0)
                    videoView.start()

                }
            }
        }
    }

    public override fun onResume() {
        super.onResume()

    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun chooseVideoFromGallery() {

        val galleryIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        if (galleryIntent.resolveActivity(packageManager) != null) {
            getPhotoLauncher.launch(galleryIntent)
        }

    }


    companion object {
        val TAG = MainActivity::class.java.simpleName
    }
}