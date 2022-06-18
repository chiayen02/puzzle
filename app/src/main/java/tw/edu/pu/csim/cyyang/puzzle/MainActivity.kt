package tw.edu.pu.csim.cyyang.puzzle

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.*
import android.view.animation.Interpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import kotlinx.coroutines.*
import tw.edu.pu.csim.cyyang.puzzle.databinding.ActivityMainBinding
import kotlin.math.min
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding;
    var secondsLeft:Int = 0  //計時
    lateinit var job: Job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main);

        binding.jigsawView.setPicture(BitmapFactory.decodeResource(resources, R.drawable.photo))
        //binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txv.text = secondsLeft.toString() + "秒"
        binding.btnstart.isEnabled = true
        binding.btnstop.isEnabled = false


        binding.btnstart.setOnClickListener(object:View.OnClickListener{
            override fun onClick(p0: View?) {
                job = GlobalScope.launch(Dispatchers.Main) {
                    while(secondsLeft < 100000000000000) {
                        secondsLeft++
                        binding.txv.text = secondsLeft.toString() + "秒"
                        binding.btnstart.isEnabled = false
                        binding.btnstop.isEnabled = true
                        delay(1000)
                    }
                }
            }

        })

        binding.btnstop.setOnClickListener(object:View.OnClickListener{
            override fun onClick(p0: View?) {
                job.cancel()
                binding.btnstart.isEnabled = true
                binding.btnstop.isEnabled = false
            }
        })
    }
    override fun onPause() {
        super.onPause()
        job.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (binding.btnstart.isEnabled == false){
            job = GlobalScope.launch(Dispatchers.Main) {
                while(secondsLeft < 100000000000000) {
                    secondsLeft++
                    binding.txv.text = secondsLeft.toString() + "秒"
                    delay(1000)
                }
            }
        }
    }
}


class JigsawView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GestureDetector.OnGestureListener {

    private var TAG = "TAG";

    //表格大小
    private var tableSize = 3;

    //二维数组，存放图标块
    private val pictureBlock2dMap = Array(tableSize) { Array<PictureBlock?>(tableSize) { null } }


    //手势监听
    private var gestureDetector: GestureDetector = GestureDetector(context, this);

    //是否开始
    private var isStart: Boolean = false;

    //空白点坐标
    private var moveBlockPoint: Point = Point(-1, -1);

    //top偏移
    private var offsetTop: Int = 0;

    //图片大小
    private var gridItemSize = 0;
    private var slideAnimatorDuration: Long = 150;
    private var showSourceBitmap = false;
    //移动步数
    private var step: Int = 0;

    private var itemMovInterpolator:Interpolator=OvershootInterpolator()
    //目标Bitmap
    private lateinit var targetPicture: Bitmap;

    fun setPicture(bitmap: Bitmap) {
        post {
            targetPicture = bitmap.getCenterBitmap();
            parsePicture();
            step = 0;
        }
    }

    //分割图片
    private fun parsePicture() {
        var top = 0;
        var left = 0;
        var postion = 0;
        for (i in pictureBlock2dMap.indices) {
            for (j in pictureBlock2dMap[i].indices) {
                postion++;
                left = j * gridItemSize;
                top = i * gridItemSize;
                pictureBlock2dMap[i][j] =
                    PictureBlock(
                        createBitmap(left, top, gridItemSize),
                        postion,
                        left,
                        top
                    )
            }
        }
        pictureBlock2dMap[tableSize - 1][tableSize - 1]!!.bitmap = createSolidColorBitmap(width)
        isStart = true;
        randomPostion();
        invalidate()

    }

    private fun randomPostion() {
        for (i in 1..pictureBlock2dMap.size * pictureBlock2dMap.size) {
            var srcIndex = Random.nextInt(0, pictureBlock2dMap.size);
            var dstIndex = Random.nextInt(0, pictureBlock2dMap.size);
            var srcIndex1 = Random.nextInt(0, pictureBlock2dMap.size);
            var dstIndex2 = Random.nextInt(0, pictureBlock2dMap.size);
            pictureBlock2dMap[srcIndex][dstIndex]!!.swap(pictureBlock2dMap[srcIndex1][dstIndex2]!!);
        }

        for (i in pictureBlock2dMap.indices) {
            for (j in pictureBlock2dMap[i].indices) {
                var item = pictureBlock2dMap[i][j]!!;
                if (item.postion == tableSize * tableSize) {
                    moveBlockPoint.set(i, j)
                    return
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        offsetTop = (h - w) / 2;
        gridItemSize = w / tableSize;
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var min = min(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(min, min)
    }

    override fun onDraw(canvas: Canvas) {
        if (!isStart) {
            return
        }
        if (showSourceBitmap) {
            var pictureRect = Rect(0, 0, targetPicture.width, targetPicture.height);
            var rect = Rect(0, 0, measuredWidth, measuredHeight);
            canvas.drawBitmap(targetPicture, pictureRect, rect, Paint())
            return
        }
        var left: Int = 0;
        var top: Int = 0;
        for (i in pictureBlock2dMap.indices) {
            for (j in pictureBlock2dMap[i].indices) {
                var item = pictureBlock2dMap[i][j]!!;
                left = item.left;
                top = item.top;
                var bitmap = pictureBlock2dMap[i][j]!!.bitmap;
                var pictureRect = Rect(0, 0, bitmap.width, bitmap.height);
                var rect = Rect(left, top + offsetTop, gridItemSize + left, gridItemSize + top + offsetTop);
                canvas.drawBitmap(bitmap, pictureRect, rect, Paint())
            }
        }

    }

    //交换内容
    private fun PictureBlock.swap(target: PictureBlock) {
        target.postion = this.postion.also {
            this.postion = target.postion;
        }
        target.bitmap = this.bitmap.also {
            this.bitmap = target.bitmap;
        }
    }

    fun Bitmap.getCenterBitmap(): Bitmap {
        //如果图片宽度大于View宽度
        var min = min(this.height, this.width)
        if (min >= measuredWidth) {
            val matrix = Matrix()
            val sx: Float = measuredWidth / min.toFloat()
            matrix.setScale(sx, sx)
            return Bitmap.createBitmap(
                this, 0, (this.height * sx - measuredHeight / 2).toInt(),
                this.width,
                this.width,
                matrix,
                true
            )
        }
        return this;
    }

    fun setTarget(targetPicture: Bitmap) {
        this.targetPicture = targetPicture;
    }


    private fun createSolidColorBitmap(size: Int): Bitmap {
        var bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        return bitmap;
    }

    private fun createBitmap(left: Int, top: Int, size: Int): Bitmap {
        return Bitmap.createBitmap(targetPicture, left, top, size, size)
    }


    private fun List<Int>.isOrder(): Boolean {
        for (i in 1 until this.size) {
            if (this[i] - this[i - 1] != 1) {
                return false
            }
        }
        return true;
    }

    private fun isFinish() {
        var list = mutableListOf<Int>();
        for (i in pictureBlock2dMap.indices) {
            for (j in pictureBlock2dMap[i].indices) {
                var item = pictureBlock2dMap[i][j]!!;
                list.add(item.postion)
            }
        }
        if (list.isOrder()) {
            finish()
        }
    }

    private fun finish() {
        Toast.makeText(context, "Finish", Toast.LENGTH_SHORT).show()
    }

    private fun startAnimator(
        start: Int,
        end: Int,
        srcPoint: Point,
        dstPoint: Point,
        type: Boolean
    ) {
        val handler = object : AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {
            }

            override fun onAnimationEnd(animation: Animator?) {
                pictureBlock2dMap[dstPoint.x][dstPoint.y] =
                    pictureBlock2dMap[srcPoint.x][srcPoint.y].also {
                        pictureBlock2dMap[srcPoint.x][srcPoint.y] =
                            pictureBlock2dMap[dstPoint.x][dstPoint.y]!!;
                    }
                invalidate()
                isFinish()

            }

            override fun onAnimationCancel(animation: Animator?) {
            }

            override fun onAnimationStart(animation: Animator?) {
            }

        }
        var animatorSet = AnimatorSet()
        animatorSet.addListener(handler)
        animatorSet.playTogether(ValueAnimator.ofFloat(start.toFloat(), end.toFloat()).apply {
            duration = slideAnimatorDuration
            interpolator=itemMovInterpolator
            addUpdateListener { animation ->
                var value = animation.animatedValue as Float
                if (type) {
                    pictureBlock2dMap[srcPoint.x][srcPoint.y]!!.left = value.toInt();
                } else {
                    pictureBlock2dMap[srcPoint.x][srcPoint.y]!!.top = value.toInt();

                }
                invalidate()
            }
        }, ValueAnimator.ofFloat(end.toFloat(), start.toFloat()).apply {
            duration = slideAnimatorDuration
            interpolator=itemMovInterpolator
            addUpdateListener { animation ->
                var value = animation.animatedValue as Float
                if (type) {
                    pictureBlock2dMap[dstPoint.x][dstPoint.y]!!.left = value.toInt();
                } else {
                    pictureBlock2dMap[dstPoint.x][dstPoint.y]!!.top = value.toInt();

                }
                invalidate()
            }
        });
        animatorSet.start()

    }


    private fun doMoveTopBottom(direction: Boolean) {
        if ((moveBlockPoint.x == 0 && direction) || (moveBlockPoint.x == tableSize - 1 && !direction)) {
            return;
        }
        step++;
        var value = if (direction) 1 else {
            -1
        }

        var start = moveBlockPoint.x * gridItemSize;
        var end = (moveBlockPoint.x - (value)) * gridItemSize

        startAnimator(
            start, end, Point(moveBlockPoint.x, moveBlockPoint.y),
            Point(moveBlockPoint.x - (value), moveBlockPoint.y),
            false
        )
        moveBlockPoint.x = moveBlockPoint.x - (value);

    }

    private fun doMoveLeftRight(direction: Boolean) {
        if ((moveBlockPoint.y == 0 && direction) || (moveBlockPoint.y == tableSize - 1 && !direction)) {
            return;
        }
        step++
        var value = if (direction) 1 else {
            -1
        }

        var start = moveBlockPoint.y * gridItemSize;
        var end = (moveBlockPoint.y - (value)) * gridItemSize

        startAnimator(
            start, end, Point(moveBlockPoint.x, moveBlockPoint.y),
            Point(moveBlockPoint.x, moveBlockPoint.y - (value)),
            true
        )


        moveBlockPoint.y = moveBlockPoint.y - (value);

    }


    inner class PictureBlock {
        var bitmap: Bitmap;
        var postion: Int = 0
        var left = 0;
        var top = 0;

        constructor(bitmap: Bitmap, postion: Int, left: Int, top: Int) {
            this.bitmap = bitmap
            this.postion = postion
            this.left = left
            this.top = top
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.i(TAG, "onTouchEvent: ")
        if (event.action == MotionEvent.ACTION_UP) {
            Log.i(TAG, "onDown: ACTION_UP")
            showSourceBitmap = false;
            invalidate()
        }
        return gestureDetector.onTouchEvent(event);
    }

    override fun onShowPress(e: MotionEvent?) {
        Log.i(TAG, "onShowPress: ")
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        Log.i(TAG, "onSingleTapUp: ")
        return true;

    }

    override fun onDown(e: MotionEvent): Boolean {
        Log.i(TAG, "onDown: ")

        return true;
    }

    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        var moveXDistance = Math.abs(e1.x - e2.x);
        var moveYDistance = Math.abs(e1.y - e2.y);
        if (moveXDistance > moveYDistance) {
            doMoveLeftRight(e1.x < e2.x)
            return true;
        }
        doMoveTopBottom(e1.y < e2.y)
        return true;
    }

    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return true;
    }

    override fun onLongPress(e: MotionEvent) {
        showSourceBitmap = true;
        invalidate()
        Handler().postDelayed({
            showSourceBitmap = false;
            invalidate()
        }, 5000)
    }
}

