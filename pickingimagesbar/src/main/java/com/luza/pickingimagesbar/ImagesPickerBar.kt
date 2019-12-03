package com.luza.pickingimagesbar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Scroller
import androidx.core.graphics.applyCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


class ImagesPickerBar @JvmOverloads constructor(
    context: Context, var attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isInited = false
    var isLoading = false
    var listImagePaths = ArrayList<String>()
        set(value) {
            field = value
            applyListImages()
        }

    //private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var flingAble = false

    val widthScreen = context.resources.displayMetrics.widthPixels
    private var viewWidth = 0
    private var viewHeight = 0
    private var barHeight = 0
    var spaceBar = 0f

    private var numberImage = 10
    private var imageWidth = 0
    private var imageHeight = 0

    private val rectProgress = RectF()
    private val paintProgress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
    }
    private var progressPosition = 0f

    private val rectImagesBar = RectF()
    private var cachedBitmap: Bitmap? = null

    private val paintImagesBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    var listener: IPickBarListener? = null

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ImagesPickerBar)
            flingAble = ta.getBoolean(R.styleable.ImagesPickerBar_ipb_bar_fling_able, false)
            //Video bar
            barHeight = ta.getDimensionPixelSize(R.styleable.ImagesPickerBar_ipb_bar_height, 0)
            numberImage = ta.getInt(R.styleable.ImagesPickerBar_ipb_number_image,20)

            //Progress
            paintProgress.color =
                ta.getColor(R.styleable.ImagesPickerBar_ipb_thumb_progress_color, Color.BLACK)
            try {
                progressPosition =
                    ta.getDimensionPixelSize(
                        R.styleable.ImagesPickerBar_ipb_thumb_progress_position,
                        0
                    )
                        .toFloat()
            } catch (e: UnsupportedOperationException) {
                progressPosition =
                    ta.getInt(R.styleable.ImagesPickerBar_ipb_thumb_progress_position, -1).toFloat()
                when (progressPosition) {
                    Position.CENTER.id.toFloat() -> {
                        progressPosition = widthScreen / 2f
                    }
                    Position.LEFT.id.toFloat() -> {
                        progressPosition = 0f
                    }
                }
            }

            imageWidth = ta.getDimensionPixelSize(R.styleable.ImagesPickerBar_ipb_image_width, 0)
            imageHeight = ta.getDimensionPixelSize(R.styleable.ImagesPickerBar_ipb_image_height, 0)
            ta.recycle()
        }
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        isInited = true
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (barHeight == 0)
            barHeight = viewHeight
        spaceBar = (viewHeight - barHeight) / 2f
        val videoBarTop = 0f + spaceBar

        rectImagesBar.set(progressPosition, videoBarTop, viewWidth, videoBarTop + barHeight)
        setMeasuredDimension(widthSize, viewHeight)
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.let {
            if (!isLoading) {
                cachedBitmap?.apply {
                    it.drawBitmap(
                        this, progressPosition, rectImagesBar.top//null,rectImagesBar
                        , paintImagesBar
                    )
                    if (flingAble) {
                        if (scroller.computeScrollOffset()) {
                            scrollTo(scroller.currX, scrollY)
                            log("Progress: ${scroller.currX.ToProgress()}")
                        }
                    }
                }
            }
        }


    }

    private fun RectF.set(left: Number, top: Number, right: Number, bottom: Number) {
        set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    private fun applyListImages() {
        isLoading = true
        if (!isInited) {
            viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    applyListImages(true)
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                }

            })
        } else {
            applyListImages(true)
        }
    }

    fun applyListImages(apply: Boolean) {
        listener?.onInitStart()
        if (listImagePaths.size<=numberImage)
            numberImage = listImagePaths.size
        else{
            val frameSkip = listImagePaths.size/numberImage
            val skipList = ArrayList<String>()
            log("Old list size: "+listImagePaths.size.toString())
            for (i in 0..numberImage){
                var index = i*frameSkip
                if (index>listImagePaths.size-1)
                    index = listImagePaths.size-1
                skipList.add(listImagePaths[index])
            }
            listImagePaths.clear()
            listImagePaths.addAll(skipList)
            log("New list size: "+listImagePaths.size.toString())
        }
        viewWidth = (numberImage * imageWidth + progressPosition).roundToInt()
        sync({
            val bmConf = Bitmap.Config.ARGB_8888
            val widthBitmap = viewWidth - progressPosition
            val heightCached = viewHeight - spaceBar * 2
            cachedBitmap = Bitmap.createBitmap(widthBitmap.roundToInt(), heightCached.toInt(), bmConf)
            var offset = 0f
            for (path in listImagePaths) {
                val bitmapOptions = BitmapFactory.Options()
                val bm = BitmapFactory.decodeFile(path, bitmapOptions)
                val bitmap = Bitmap.createScaledBitmap(bm, imageWidth, heightCached.toInt(), true)
                cachedBitmap?.applyCanvas {
                    this.drawBitmap(bitmap, offset, 0f, paintImagesBar)
                }
                offset += imageWidth
                GlobalScope.launch(Dispatchers.Main) {
                    listener?.onInitProgress(((listImagePaths.indexOf(path) + 1f) / listImagePaths.size * 100).toInt())
                }
            }
        }, {
            isLoading = false
            invalidate()
            listener?.onInitViewCompleted()
        }, dispatcherOut = Dispatchers.Main)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return gesture.onTouchEvent(event)
    }

    private var scroller = Scroller(context)
    private var gesture =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent?,
                distanceX: Float, distanceY: Float
            ): Boolean {
                var scrollTo = distanceX.toInt() + scrollX
                if (scrollTo < 0)
                    scrollTo = 0
                else if (scrollTo > (viewWidth - progressPosition) && widthScreen < viewWidth) {
                    scrollTo = ((viewWidth - progressPosition).toInt())
                }
                scrollTo(scrollTo, scrollY)
                log("Progress: ${scrollTo.ToProgress()}")
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (flingAble) {
                    val maxX =
                        if (viewWidth > widthScreen) (viewWidth - progressPosition).roundToInt() else 0
                    scroller.fling(
                        scrollX, scrollY,
                        (-velocityX).toInt(), 0, 0, maxX, 0, viewHeight
                    )
                    invalidate()
                }
                return true
            }

            override fun onDown(e: MotionEvent?): Boolean {
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                }
                return true
            }
        })

    private fun Number.ToProgress(): Int {
        return (this.toFloat()/(viewWidth-progressPosition)*listImagePaths.size).toInt()
    }

    interface IPickBarListener {
        fun onInitStart() {}
        fun onInitProgress(percent: Int) {}
        fun onInitViewCompleted() {}
        fun onProgressChange(progress: Int) {}

    }

    enum class Position(var id: Int) {
        CENTER(0), LEFT(-1)
    }

}