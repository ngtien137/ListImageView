package com.luza.pickingvideosectiondemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.luza.pickingimagesbar.ImagesPickerBar
import com.luza.pickingimagesbar.sync
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_loading.*
import java.io.File

class MainActivity : AppCompatActivity(),View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }

    private fun getListFile(): ArrayList<String> {
        val listImages = ArrayList<String>()
        val dir =Environment.getExternalStorageDirectory().absolutePath+"/TempGif"
        val listFile = File(dir).listFiles()
        listFile?.let {
            var size = listFile.size
            if (size>200){
                size = 200
            }
            for (index in 1..size){
                listImages.add("$dir/temp_image$index.jpg")
            }
        }
        return listImages
    }

    private fun init() {


        pickerBar.listImagePaths = getListFile()
        pickerBar.listener = object : ImagesPickerBar.IPickBarListener{
            override fun onInitStart() {
                llLoading.visibility = View.VISIBLE
            }

            override fun onInitProgress(percent: Int) {
                tvLoading.text = "Loading $percent"
            }

            override fun onInitViewCompleted() {
                llLoading.visibility = View.GONE
            }
        }
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btnRefresh->{
                pickerBar.listImagePaths = getListFile()
            }
        }
    }
}
