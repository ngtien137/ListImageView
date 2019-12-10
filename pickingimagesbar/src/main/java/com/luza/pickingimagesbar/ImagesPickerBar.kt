package com.luza.pickingimagesbar

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


class ImagesPickerBar @JvmOverloads constructor(
    context: Context, var attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val DEF_COLOR_SELECT = Color.parseColor("#44ffffff")
    private val DEF_COLOR_SELECT_FOCUS = Color.parseColor("#88000000")

    private var progress = 0

    private var scroller = Scroller(context,LinearInterpolator())
    private var isAdding = false

    var isInited = false
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

    private var numberImage = 20
    private var imageWidth = 0
    private var imageHeight = 0

    private val rectProgress = RectF()
    private val paintProgress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private var progressPosition = 0f
    private var progressHeight = 0f
    private var progressWidth = 0f
    private var progressCorner = 0f

    private val rectImagesBar = RectF()
    private var cachedBitmap: Bitmap? = null

    private val paintImagesBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private var minCutProgress = 0
    private var minCutDimen = 0f
    private val rectLeftThumb = Rect()
    private val rectRightThumb = Rect()
    private var thumbLeft: Drawable? = null
    private var thumbRight: Drawable? = null
    private val paintThumb = Paint(Paint.ANTI_ALIAS_FLAG)

    private val listSelectProgress = ArrayList<RectF>()
    private val paintSelectProgress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEF_COLOR_SELECT
    }
    private val rectFocusSelect = RectF()
    private val paintSelectProgressFocus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEF_COLOR_SELECT_FOCUS
    }

    var isShowCut = false

    fun showCutThumb(show: Boolean = true) {
        this.isShowCut = show
        invalidate()
    }

    private val extraProgress = 1000

    var listener: IPickBarListener? = null

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ImagesPickerBar)
            flingAble = ta.getBoolean(R.styleable.ImagesPickerBar_ipb_bar_fling_able, false)
            //Video bar
            barHeight = ta.getDimensionPixelSize(R.styleable.ImagesPickerBar_ipb_bar_height, 0)
            numberImage = ta.getInt(R.styleable.ImagesPickerBar_ipb_number_image, 20)

            //Progress
            progressHeight = ta.getDimension(R.styleable.ImagesPickerBar_ipb_thumb_progress_height,0f)
            progressWidth = ta.getDimension(R.styleable.ImagesPickerBar_ipb_thumb_progress_width,0f)
            progressCorner = ta.getDimension(R.styleable.ImagesPickerBar_ipb_thumb_progress_corner,0f)
            paintProgress.color =
                ta.getColor(R.styleable.ImagesPickerBar_ipb_thumb_progress_color, Color.WHITE)
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

            minCutProgress = ta.getInt(R.styleable.ImagesPickerBar_ipb_min_cut, 0)
            thumbLeft = ta.getDrawable(R.styleable.ImagesPickerBar_ipb_thumb_select_left)
                ?: ContextCompat.getDrawable(context, R.drawable.ic_thumb_left)
            thumbRight = ta.getDrawable(R.styleable.ImagesPickerBar_ipb_thumb_select_left)
                ?: ContextCompat.getDrawable(context, R.drawable.ic_thumb_right)

            imageWidth = ta.getDimensionPixelSize(R.styleable.ImagesPickerBar_ipb_image_width, 0)
            imageHeight = ta.getDimensionPixelSize(R.styleable.ImagesPickerBar_ipb_image_height, 0)

            //Select
            paintSelectProgress.color = ta.getColor(R.styleable.ImagesPickerBar_ipb_select_color,DEF_COLOR_SELECT)
            paintSelectProgressFocus.color = ta.getColor(R.styleable.ImagesPickerBar_ipb_select_color_focus,DEF_COLOR_SELECT_FOCUS)
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
        log("Invalidate")
        canvas?.let {
            cachedBitmap?.apply {
                it.drawBitmap(
                    this, progressPosition, rectImagesBar.top//null,rectImagesBar
                    , paintImagesBar
                )
                if (flingAble) {
                    if (scroller.computeScrollOffset()) {
                        scrollXTo(scroller.currX)
                    }
                }
                drawSelectPart(canvas)
                drawSelectPartFocus(canvas)

                drawCenterProgress(canvas)
            }
        }
    }

    private fun drawSelectPartFocus(canvas: Canvas) {
        if (listSelectProgress.isEmpty()||!isAdding)
            return
        val lastRectF = listSelectProgress[listSelectProgress.size-1]
        rectFocusSelect.set(lastRectF)
        canvas.drawRect(rectFocusSelect,paintSelectProgressFocus)
    }

    private fun drawCenterProgress(canvas: Canvas) {
        val leftProgress = progressPosition + scrollX - progressWidth/2f
        val centerBar = rectImagesBar.centerY()
        rectProgress.set(leftProgress,centerBar-progressHeight/2f,leftProgress+progressWidth,centerBar+progressHeight/2f)
        canvas.drawRoundRect(rectProgress,progressCorner,progressCorner,paintProgress)
    }

    private fun drawSelectPart(canvas: Canvas) {
        if (listSelectProgress.isEmpty())
            return
        for (rect in listSelectProgress){
            canvas.drawRect(rect,paintSelectProgress)
        }
    }

    fun startAddingRangeSelect(fromProgress: Int = -1) {
        isAdding = true
        val startPosition =
            if (fromProgress == -1) progress.ToProgressPosition() else fromProgress.ToProgressPosition()
        listSelectProgress.add(RectF().apply {
            set(startPosition,rectImagesBar.top,startPosition,rectImagesBar.bottom)
        })
        invalidate()
    }

    fun stopAddingRangeSelect(add:Boolean=true){
        isAdding = false
        if (add){
            listSelectProgress[listSelectProgress.size-1].apply {
                right = scrollX.toFloat()
            }
        }

    }

    private fun applyListImages() {
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
        if (listImagePaths.size <= numberImage)
            numberImage = listImagePaths.size
        else {
            val frameSkip = listImagePaths.size / numberImage
            val skipList = ArrayList<String>()
            for (i in 0 until numberImage) {
                var index = i * frameSkip
                if (index > listImagePaths.size - 1)
                    index = listImagePaths.size - 1
                skipList.add(listImagePaths[index])
            }
            listImagePaths.clear()
            listImagePaths.addAll(skipList)
        }
        viewWidth = (numberImage * imageWidth + progressPosition).roundToInt()
        minCutDimen =
            (minCutProgress.toFloat() / (listImagePaths.size * extraProgress)) * (viewWidth - progressPosition)
        sync({
            val bmConf = Bitmap.Config.ARGB_8888
            val widthBitmap = viewWidth - progressPosition
            val heightCached = viewHeight - spaceBar * 2
            cachedBitmap =
                Bitmap.createBitmap(widthBitmap.roundToInt(), heightCached.toInt(), bmConf)
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
            invalidate()
            listener?.onInitViewCompleted()
        }, dispatcherOut = Dispatchers.Main)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return gesture.onTouchEvent(event)
    }

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
                scrollXTo(scrollTo)
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

    fun setProgress(progress: Int) {
        var p = progress
        if (progress < 0) {
            p = 0
        } else if (p > (listImagePaths.size - 1) * extraProgress)
            p = (listImagePaths.size - 1) * extraProgress
        this.progress = p
        val currentScroll = p.ToProgressPosition()
        if (isAdding){
            listSelectProgress[listSelectProgress.size-1].apply {
                right = scrollX.toFloat()
            }
        }
        scrollTo(currentScroll, scrollY)
        listener?.onProgressChange(p)
    }

    private fun RectF.set(left: Number, top: Number, right: Number, bottom: Number) {
        set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    fun getProgress() = progress

    fun getMax() = (listImagePaths.size - 1) * extraProgress

    private fun Number.ToProgress(): Int {
        return (this.toFloat() / (viewWidth - progressPosition) * listImagePaths.size).toInt() * extraProgress
    }

    private fun Number.ToProgressPosition(): Int {
        return ((this.toFloat() / extraProgress) / listImagePaths.size * (viewWidth - progressPosition)).toInt()
    }

    private fun scrollXTo(toX: Number, isInitWithListener: Boolean = true) {
        if (isAdding){
            listSelectProgress[listSelectProgress.size-1].apply {
                right = scrollX.toFloat()
            }
        }
        scrollTo(toX.toInt(), scrollY)
        if (isInitWithListener) {
            progress = toX.ToProgress()
            listener?.onProgressChange(progress)
        }
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