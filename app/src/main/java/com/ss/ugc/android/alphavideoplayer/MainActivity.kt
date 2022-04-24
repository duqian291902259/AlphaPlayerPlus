package com.ss.ugc.android.alphavideoplayer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import com.ss.ugc.android.alpha_player.IMonitor
import com.ss.ugc.android.alpha_player.IPlayerAction
import com.ss.ugc.android.alpha_player.model.ScaleType
import com.ss.ugc.android.alpha_player.vap.mix.Resource
import com.ss.ugc.android.alpha_player.vap.inter.IFetchResource
import com.ss.ugc.android.alpha_player.vap.util.ALog
import com.ss.ugc.android.alpha_player.vap.util.IALog
import com.ss.ugc.android.alphavideoplayer.utils.PermissionUtils
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

/**
 * created by dengzhuoyao on 2020/07/08
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    val basePath = Environment.getExternalStorageDirectory().absolutePath

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        //window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main)
        PermissionUtils.verifyStoragePermissions(this)
        initVideoGiftView()
        initLog()
    }

    private fun initLog() {
        ALog.isDebug = BuildConfig.DEBUG
        ALog.log = object : IALog {
            override fun i(tag: String, msg: String) {
                Log.i(tag, msg)
            }

            override fun d(tag: String, msg: String) {
                Log.d(tag, msg)
            }

            override fun e(tag: String, msg: String) {
                Log.e(tag, msg)
            }

            override fun e(tag: String, msg: String, tr: Throwable) {
                Log.e(tag, msg, tr)
            }
        }
    }


    private fun initVideoGiftView() {
        video_gift_view.initPlayerController(this, this, playerAction, fetchResource, monitor)
        video_gift_view.post {
            video_gift_view.attachView()
            startPlay()
        }
    }

    private var mVideoWidth = 0
    private var mVideoHeight = 0
    private val playerAction = object : IPlayerAction {
        override fun onVideoSizeChanged(videoWidth: Int, videoHeight: Int, scaleType: ScaleType) {
            Log.i(
                TAG,
                "call onVideoSizeChanged(), videoWidth = $videoWidth, videoHeight = $videoHeight, scaleType = $scaleType"
            )

            mVideoWidth = videoWidth
            mVideoHeight = videoHeight
            //updateVideoViewLayoutParams()
        }

        override fun startAction() {
            Log.i(TAG, "call startAction()")
        }

        override fun endAction() {
            Log.i(TAG, "call endAction")
        }
    }

    private fun updateVideoViewLayoutParams() {// TODO: 2021/2/22 横竖屏切换的高度问题
        val lp = video_gift_view.layoutParams as RelativeLayout.LayoutParams
        //lp.width = mVideoWidth//if(mIsLand) mVideoHeight else mVideoWidth
        //lp.height = mVideoHeight//if(mIsLand) mVideoWidth else mVideoHeight
        val w = window.decorView.width
        lp.width = if (w == 0) dp2px(this, 400f).toInt() else w
        lp.height = (w * mVideoHeight * 1f / mVideoWidth).toInt()
        video_gift_view.layoutParams = lp
        video_gift_view.requestLayout()
    }

    private fun dp2px(context: Context, dp: Float): Float {
        val scale = context.resources.displayMetrics.density
        return dp * scale + 0.5f
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        mIsLand = newConfig?.orientation == Configuration.ORIENTATION_LANDSCAPE

        //updateVideoViewLayoutParams()
    }

    private var head1Img = true
    private var mIsLand = false

    private val fetchResource = object : IFetchResource {
        /**
         * 获取图片资源
         * 无论图片是否获取成功都必须回调 result 否则会无限等待资源
         */
        override fun fetchImage(resource: Resource, result: (Bitmap?) -> Unit) {
            /**
             * srcTag是素材中的一个标记，在制作素材时定义
             * 解析时由业务读取tag决定需要播放的内容是什么
             * 比如：一个素材里需要显示多个头像，则需要定义多个不同的tag，表示不同位置，需要显示不同的头像，文字类似
             */
            val srcTag = resource.tag
            val drawableId = if (head1Img) R.mipmap.dq else R.mipmap.avator1

            //if (srcTag == "[sImg1]") {
            if (srcTag.contains("tag")) { // 此tag是已经写入到动画配置中的tag
                head1Img = !head1Img
                val options = BitmapFactory.Options()
                options.inScaled = false
                result(BitmapFactory.decodeResource(resources, drawableId, options))
            } else {
                result(null)
            }
        }

        /**
         * 获取文字资源
         */
        override fun fetchText(resource: Resource, result: (String?) -> Unit) {
            //val str = "恭喜 No.${1000 + Random().nextInt(8999)} 杜小菜 升神"
            val str = "杜小菜升级了"
            val srcTag = resource.tag

            //if (srcTag == "[sTxt1]") { // 此tag是已经写入到动画配置中的tag
            //if (srcTag == "tag2") {
            if (srcTag.contains("tag")) {
                result(str)
            } else {
                result(null)
            }
        }

        /**
         * 播放完毕后的资源回收
         */
        override fun releaseResource(resources: List<Resource>) {
            resources.forEach {
                it.bitmap?.recycle()
            }
        }
    }

    private val monitor = object : IMonitor {
        override fun monitor(
            state: Boolean,
            playType: String,
            what: Int,
            extra: Int,
            errorInfo: String
        ) {
            Log.i(
                TAG,
                "call monitor(), state: $state, playType = $playType, what = $what, extra = $extra, errorInfo = $errorInfo"
            )
        }
    }

    fun attachView(v: View) {
        video_gift_view.attachView()
    }

    fun detachView(v: View) {
        video_gift_view.detachView()
    }

    fun playGift(v: View) {
        // TODO-dq: 3/7/21 修复重新播放遮罩纹理没有完全融合的问题
        /*video_gift_view.detachView()
         video_gift_view.attachView()
         val end = System.currentTimeMillis()
         video_gift_view.reset()
         */
        startPlay()
    }

    private fun startPlay() {
        val testPath = getResourcePath()
        Log.i("dzy", "play gift file path : $testPath")
        if ("" == testPath) {
            Toast.makeText(
                this,
                "please run 'gift_install.sh gift/demoRes' for load alphaVideo resource.",
                Toast.LENGTH_SHORT
            )
                .show()
        }
        video_gift_view.startVideoGift(testPath)
    }

    private fun getResourcePath(): String {
        val dirPath = basePath + File.separator + "alphaVideoGift" + File.separator
        val dirFile = File(dirPath)
        if (dirFile.exists() && dirFile.listFiles() != null && dirFile.listFiles().isNotEmpty()) {
            return dirPath;//dirFile.listFiles()[0].absolutePath
        }
        return ""
    }

    override fun onDestroy() {
        super.onDestroy()
        video_gift_view.releasePlayerController()
    }
}
