package com.luza.pickingvideosectiondemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import com.luza.pickingimagesbar.ImagesPickerBar
import com.luza.pickingimagesbar.log
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_loading.*
import java.io.File
import java.lang.Exception

class MainActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }

    private fun getListFile(): ArrayList<String> {
        val listImages = ArrayList<String>()
        val dir = Environment.getExternalStorageDirectory().absolutePath + "/TempGif"
        val listFile = File(dir).listFiles()
        listFile?.let {
            var size = listFile.size
            if (size > 200) {
                size = 200
            }
            for (index in 1..size) {
                listImages.add("$dir/temp_image$index.jpg")
            }
        }
        return listImages
    }

    private fun init() {
        pickerBar.listImagePaths = getListFile()
        pickerBar.listener = object : ImagesPickerBar.IPickBarListener {
            override fun onInitStart() {
                llLoading.visibility = View.VISIBLE
            }

            override fun onInitProgress(percent: Int) {
                tvLoading.text = "Loading $percent"
            }

            override fun onInitViewCompleted() {
                llLoading.visibility = View.GONE
            }

            override fun onProgressChange(progress: Int) {
                //log("Progress: $progress")
            }
        }

        tvResult.movementMethod = ScrollingMovementMethod()
        edtIndexPart.setOnEditorActionListener{tv,actionId,event->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (edtIndexPart.text.isNotEmpty()) {
                    val index = edtIndexPart.text.toString().toInt()
                    pickerBar.setEdit(index)
                    val list = pickerBar.getSelectPartImageIndexs(index)
                    val sList = "Result: " + list.joinToString(",")
                    tvResult.text = sList
                }
                true
            }
            false
        }
    }

    val runPlay = Runnable {
        try {
            while (true) {
                Handler(Looper.getMainLooper()).post {
                    if (!pickerBar.isInteracted) {
                        if (pickerBar.getProgress() >= pickerBar.getMax())
                            pickerBar.setProgress(0)
                        else
                            pickerBar.setProgress(pickerBar.getProgress() + 100)
                    }
                }
                Thread.sleep(100)
            }
        } catch (e: Exception) {
        }
    }
    var thPlay: Thread? = null

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnRefresh -> {
                pickerBar.listImagePaths = getListFile()
            }
            R.id.btnPlay -> {
                if (!pickerBar.isEditting()&&!pickerBar.isAdding) {
                    pickerBar.startAddingRangeSelect()
                }
                thPlay?.interrupt()
                thPlay = null
                thPlay = Thread(runPlay)
                thPlay?.start()
            }
            R.id.btnStop -> {
                pickerBar.stopAddingRangeSelect()
                thPlay?.interrupt()
                thPlay = null
            }
        }
    }
}
