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
import kotlin.math.abs
import kotlin.math.roundToInt


class ImagesPickerBar @JvmOverloads constructor(
    context: Context, var attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val DEF_COLOR_SELECT = Color.parseColor("#440000ff")
    private val DEF_COLOR_SELECT_FOCUS = Color.parseColor("#88ff00ff")

    private var progress = 0

    private var scroller = Scroller(context,LinearInterpolator())
    var isAdding = false
        private set
    private var indexEdit = -1
    private var currentThumb=Thumb.NONE

    var isInited = false
    var isInteracted = false
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

    private var minRangeProgress = 0 
    private var minRangeDimen = 0f
    private var thumbRangeWidth = 0
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
    private var maxDuration = 200

    var listener: IPickBarListener? = null

    fun isEditting() = indexEdit!=-1

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

            minRangeProgress = ta.getInt(R.styleable.ImagesPickerBar_ipb_min_range, 0)
            thumbRangeWidth = ta.getDimensionPixelSize(R.styleable.ImagesPickerBar_ipb_thumb_range_width, 0)
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
        canvas?.let {
            cachedBitmap?.apply {
                it.drawBitmap(
                    this, null,rectImagesBar
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
                drawCutThumb(canvas)
            }
        }
    }

    private fun drawCutThumb(canvas: Canvas) {
        if (indexEdit==-1)
            return
        val focusRect = listSelectProgress[indexEdit]
        if (thumbRangeWidth==0)
            thumbRangeWidth = (thumbLeft!!.intrinsicWidth.toFloat()/thumbLeft!!.intrinsicHeight * (abs(focusRect.bottom-focusRect.top))).roundToInt()
        thumbLeft!!.apply {
            bounds.set(focusRect.left-thumbRangeWidth,focusRect.top,focusRect.left,focusRect.bottom)
            draw(canvas)
        }
        thumbRight!!.apply {
            bounds.set(focusRect.right,focusRect.top,focusRect.right+thumbRangeWidth,focusRect.bottom)
            draw(canvas)
        }
    }

    private fun drawSelectPartFocus(canvas: Canvas) {
        if (listSelectProgress.isEmpty()||(!isAdding&&indexEdit==-1))
            return
        val focusRect = if (indexEdit==-1) listSelectProgress[listSelectProgress.size-1]
        else listSelectProgress[indexEdit]
//        val right = if (indexEdit==-1) focusRect.right
//        else (progressPosition + scrollX - progressWidth/2f)
        rectFocusSelect.set(focusRect.left,focusRect.top,focusRect.right,focusRect.bottom)
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
            (if (fromProgress == -1) progress.ToProgressPosition() else fromProgress.ToProgressPosition())+progressPosition
        listSelectProgress.add(RectF().apply {
            set(startPosition,rectImagesBar.top,startPosition,rectImagesBar.bottom)
        })
        invalidate()
    }

    fun stopAddingRangeSelect(add:Boolean=true){
        if (add){
            if(isAdding) {
                isAdding = false
                listSelectProgress[listSelectProgress.size - 1].apply {
                    right = scrollX.toFloat() + progressPosition
                }
            }
        }
        invalidate()
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

    fun getMinCutDimen(): Float {
        minRangeDimen =
            (minRangeProgress.toFloat() / (maxDuration * extraProgress)) * (viewWidth - progressPosition)
        return minRangeDimen
    }

    fun setMaxDuration(maxDuration:Int){
        this.maxDuration = maxDuration
        invalidate()
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
        rectImagesBar.left = progressPosition
        rectImagesBar.right = rectImagesBar.left+viewWidth
        getMinCutDimen()
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
        return when(event?.actionMasked){
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
                isInteracted = false
                currentThumb = Thumb.NONE
                false
            }
            else->{
                gesture.onTouchEvent(event)
            }
        }
    }

    private var gesture =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent?,
                distanceX: Float, distanceY: Float
            ): Boolean {
                if(currentThumb==Thumb.NONE||indexEdit==-1) {
                    var scrollTo = distanceX.toInt() + scrollX
                    val minLeft = when {
                        isAdding -> {
                            (listSelectProgress[listSelectProgress.lastIndex].left - progressPosition)
                                .roundToInt()
                        }
//                        indexEdit != -1 -> {
//                            (listSelectProgress[indexEdit].left - progressPosition)
//                                .roundToInt()
//                        }
                        else -> 0
                    }
                    if (scrollTo < minLeft)
                        scrollTo = minLeft
                    else if (scrollTo > (viewWidth - progressPosition) && widthScreen < viewWidth) {
                        scrollTo = ((viewWidth - progressPosition).toInt())
                    }
                    scrollXTo(scrollTo)
                }else{
                    val rect = listSelectProgress[indexEdit]
                    val disX = distanceX.toInt()
                    if(currentThumb==Thumb.LEFT){
                        val minLeft = rectImagesBar.left
                        val maxRight = rect.right
                        when {
                            rect.left-disX<=minLeft -> rect.left = minLeft
                            rect.left-disX>=maxRight -> rect.left = maxRight
                            else -> rect.left -= disX
                        }
                    }else{
                        val minLeft = rect.left
                        val maxRight = rectImagesBar.right
                        when {
                            rect.right-disX<=minLeft -> rect.right = minLeft
                            rect.right-disX>=maxRight -> rect.right = maxRight
                            else -> rect.right -= disX
                        }
                    }
                    invalidate()
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                //only support fling for fun. If u want to use, need more code
                if (flingAble) {
                    val maxX =
                        when {
                            isAdding -> listSelectProgress[listSelectProgress.lastIndex].left.roundToInt()
                            viewWidth > widthScreen -> (viewWidth - progressPosition).roundToInt()
                            else -> 0
                        }
                    scroller.fling(
                        scrollX, scrollY,
                        (-velocityX).toInt(), 0, 0, maxX, 0, viewHeight
                    )
                    invalidate()
                }
                return true
            }

            override fun onDown(e: MotionEvent?): Boolean {
                isInteracted = true
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                }
                if (isEditting()){
                    currentThumb = Thumb.NONE
                    e?.let {
                        val rectFocus = listSelectProgress[indexEdit]
                        currentThumb = when {
                            e.x+scrollX in rectFocus.left-thumbRangeWidth..rectFocus.left -> {
                                Thumb.LEFT
                            }
                            e.x+scrollX in rectFocus.right..rectFocus.right+thumbRangeWidth -> {
                                Thumb.RIGHT
                            }
                            else -> Thumb.NONE
                        }
                    }
                    log(currentThumb.toString())
                }
                return true
            }
        })

    fun setProgress(progress: Int) {
        var p = progress
        if (progress < 0) {
            p = 0
        } else if (p > maxDuration * extraProgress)
            p = maxDuration * extraProgress
        this.progress = p
        val currentScroll = p.ToProgressPosition()
        if (isAdding){
            listSelectProgress[listSelectProgress.size-1].apply {
                right = scrollX.toFloat() + progressPosition
            }
        }
        scrollTo(currentScroll, scrollY)
        listener?.onProgressChange(p)
    }

    fun setEdit(index:Int){
        indexEdit = index
        invalidate()
    }

    private fun RectF.set(left: Number, top: Number, right: Number, bottom: Number) {
        set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    private fun Rect.set(left: Number, top: Number, right: Number, bottom: Number) {
        set(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    fun getProgress() = progress

    fun getMax() = maxDuration*extraProgress

    private fun Number.ToProgress(): Int {
        return (this.toFloat() / (viewWidth - progressPosition) * maxDuration*extraProgress).toInt()
    }

    private fun Number.ToProgressPosition(): Int {
        return ((this.toFloat() / extraProgress) / maxDuration * (viewWidth - progressPosition)).roundToInt()
    }

    private fun scrollXTo(toX: Number, isInitWithListener: Boolean = true) {
        scrollTo(toX.toInt(), scrollY)
        if (isAdding){
            listSelectProgress[listSelectProgress.size-1].apply {
                right = scrollX.toFloat() + progressPosition
            }
        }
//        else if (indexEdit!=-1 && listSelectProgress[indexEdit].right > scrollX.toFloat() + progressPosition){
//            indexEdit = -1
//        }
        if (isInitWithListener) {
            progress = toX.ToProgress()
            listener?.onProgressChange(progress)
        }
    }

    fun getListSelectSize() = listSelectProgress.size

    fun getSelectPartImageIndexs(indexSelectPart:Int): ArrayList<Int> {
        if (indexSelectPart>=getListSelectSize()||indexSelectPart<0)
            return ArrayList()
        val list = ArrayList<Int>()
        val rect = listSelectProgress[indexSelectPart]
        val minIndex = (rect.left-progressPosition).ToProgress()/extraProgress
        var maxIndex = (rect.right-progressPosition).ToProgress()/extraProgress
        if (maxIndex==maxDuration)
            maxIndex = maxDuration-1
        for (index in minIndex..maxIndex){
            list.add(index)
        }
        return list
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

    enum class Thumb{
        LEFT,RIGHT,NONE
    }

}