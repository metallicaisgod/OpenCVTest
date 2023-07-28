package com.example.opencvtest

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.OpenCVFrameConverter
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.dnn.Dnn.readNetFromCaffe
import org.opencv.imgproc.Imgproc
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context = getApplication<Application>().applicationContext

    private val _resultUri = MutableLiveData<Uri?>(null)
    val resultUri: LiveData<Uri?> = _resultUri

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun videoProcessing(videoUri: Uri) {

        var outputUri: Uri? = null
        val job = coroutineScope.launch {

            val inputStream = context.contentResolver.openInputStream(videoUri)
            val grabber = FFmpegFrameGrabber(inputStream)

            val proto: String = getPath("MobileNetSSD_deploy.prototxt", context)
            val weights: String = getPath("MobileNetSSD_deploy.caffemodel", context)
            val net = readNetFromCaffe(proto, weights)

            val converterToMat = OpenCVFrameConverter.ToOrgOpenCvCoreMat()
            // grabber.format = "mp4"
            grabber.start()

            val filePrefix = "processed"
            val fileExtn = ".mkv"
            val valuesvideos = ContentValues()
            valuesvideos.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + "Folder")
            valuesvideos.put(MediaStore.Video.Media.TITLE, filePrefix + System.currentTimeMillis())
            valuesvideos.put(
                MediaStore.Video.Media.DISPLAY_NAME,
                filePrefix + System.currentTimeMillis() + fileExtn
            )
            valuesvideos.put(MediaStore.Video.Media.MIME_TYPE, "video/mkv")
            valuesvideos.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            valuesvideos.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            outputUri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                valuesvideos
            )

            val outputStream = context.contentResolver.openOutputStream(
                outputUri ?: throw RuntimeException("outputUri is null")
            )

            val frameRecorder = FFmpegFrameRecorder(
                outputStream,
                grabber.imageWidth,
                grabber.imageHeight,
                grabber.audioChannels
            )
            frameRecorder.format = "matroska"
            frameRecorder.frameRate = 30.0
            frameRecorder.start()

            val resultBitmap: Bitmap = Bitmap.createBitmap(
                grabber.imageWidth,
                grabber.imageHeight, Bitmap.Config.ARGB_8888
            )

            var detections: Mat = Mat()

            for (i in 0 until (grabber.lengthInVideoFrames - 1)) {
                val nthFrame = grabber.grabImage()
                val frame = converterToMat.convert(nthFrame)
                if (i % 3 == 0) {
                    Log.d("Viewmodel", "processing #$i frame")
                    val aspRatio = frame.width().toDouble() / frame.height().toDouble()
                    val inWidth = 240.0
                    val inHeight = inWidth / aspRatio
                    val inScaleFactor = 0.007843
                    val meanVal = 127.5
                    Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2RGB)
                    val blob = Dnn.blobFromImage(
                        frame, inScaleFactor,
                        Size(inWidth, inHeight),
                        Scalar(meanVal, meanVal, meanVal), false, false
                    )
                    net.setInput(blob)
                    detections = net.forward()
                    detections = detections.reshape(1, detections.total().toInt() / 7)
                }
                val cols = frame.cols()
                val rows = frame.rows()
                val threshold = 0.5
                for (i in 0 until detections.rows()) {
                    val confidence = detections[i, 2][0]
                    if (confidence > threshold) {
                        val classId = detections[i, 1][0].toInt()
                        val left = (detections[i, 3][0] * cols)
                        val top = (detections[i, 4][0] * rows)
                        val right = (detections[i, 5][0] * cols)
                        val bottom = (detections[i, 6][0] * rows)
                        Imgproc.rectangle(
                            frame, Point(left, top), Point(right, bottom),
                            Scalar(0.0, 255.0, 0.0), 2
                        )
                        if (classId >= 0 && classId < classNames.size) {
                            val label = classNames[classId] + ": " + confidence
                            val baseLine = IntArray(1)
                            val labelSize: Size =
                                Imgproc.getTextSize(
                                    label,
                                    Imgproc.FONT_HERSHEY_SIMPLEX,
                                    1.0,
                                    1,
                                    baseLine
                                )
                            Imgproc.rectangle(
                                frame, Point(left, top - labelSize.height),
                                Point(left + labelSize.width, top + baseLine[0]),
                                Scalar(255.0, 255.0, 255.0), Imgproc.FILLED
                            )
                            Imgproc.putText(
                                frame, label, Point(left, top),
                                Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, Scalar(0.0, 0.0, 0.0)
                            )
                        }
                    }
                }

                Log.d(TAG, "timestamp is ${grabber.timestamp}")
                frameRecorder.timestamp = grabber.timestamp
                val resultFrame = converterToMat.convert(frame)
                frameRecorder.record(resultFrame)


            }

            frameRecorder.close()
            grabber.close()

            inputStream?.close()
            outputStream?.close()

        }
        viewModelScope.launch {
            job.join()
            _resultUri.value = outputUri
        }
    }

    private fun getPath(file: String, context: Context): String {
        val assetManager: AssetManager = context.assets
        var inputStream: BufferedInputStream? = null
        try {
// Read data from assets.
            inputStream = BufferedInputStream(assetManager.open(file))
            val data = ByteArray(inputStream.available())
            inputStream.read(data)
            inputStream.close()
            // Create copy file in storage.
            val outFile = File(context.filesDir, file)
            val os = FileOutputStream(outFile)
            os.write(data)
            os.close()
            // Return a path to file which may be read in common way.
            return outFile.absolutePath
        } catch (ex: IOException) {
            Log.i(TAG, "Failed to upload a file")
        }
        return ""
    }

    override fun onCleared() {
        super.onCleared()
        coroutineScope.cancel()
    }

    companion object {

        private val classNames = listOf(
            "background",
            "aeroplane", "bicycle", "bird", "boat",
            "bottle", "bus", "car", "cat", "chair",
            "cow", "diningtable", "dog", "horse",
            "motorbike", "person", "pottedplant",
            "sheep", "sofa", "train", "tvmonitor"
        )
        val TAG = MainViewModel::class.java.simpleName
    }
}