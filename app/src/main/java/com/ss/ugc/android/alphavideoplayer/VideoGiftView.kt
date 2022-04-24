package com.ss.ugc.android.alphavideoplayer

import android.arch.lifecycle.LifecycleOwner
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.Toast
import com.ss.ugc.android.alpha_player.IMonitor
import com.ss.ugc.android.alpha_player.IPlayerAction
import com.ss.ugc.android.alpha_player.controller.IPlayerController
import com.ss.ugc.android.alpha_player.controller.PlayerController
import com.ss.ugc.android.alpha_player.model.AlphaVideoViewType
import com.ss.ugc.android.alpha_player.model.Configuration
import com.ss.ugc.android.alpha_player.model.DataSource
import com.ss.ugc.android.alpha_player.vap.inter.IFetchResource
import com.ss.ugc.android.alpha_player.vap.player.AnimPlayer
import com.ss.ugc.android.alphavideoplayer.player.ExoPlayerImpl
import com.ss.ugc.android.alphavideoplayer.utils.JsonUtil

/**
 * created by dengzhuoyao on 2020/07/08
 */
class VideoGiftView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val TAG = "VideoGiftView"
    }

    private val mVideoContainer: RelativeLayout
    private var mPlayerController: IPlayerController? = null

    init {
        LayoutInflater.from(context).inflate(getResourceLayout(), this)
        mVideoContainer = findViewById(R.id.video_view)
    }

    private fun getResourceLayout(): Int {
        return R.layout.view_video_gift
    }

    fun initPlayerController(
        context: Context,
        owner: LifecycleOwner,
        playerAction: IPlayerAction,
        fetchResource: IFetchResource,
        monitor: IMonitor
    ) {
        val configuration = Configuration(context, owner)
        //  GLTextureView supports custom display layer, but GLSurfaceView has better performance, and the GLSurfaceView is default.
        configuration.alphaVideoViewType = AlphaVideoViewType.GL_TEXTURE_VIEW
        //  You can implement your IMediaPlayer, here we use ExoPlayerImpl that implemented by ExoPlayer, and
        //  we support DefaultSystemPlayer as default player.
        mPlayerController = PlayerController.get(configuration, ExoPlayerImpl(context))
        //mPlayerController = PlayerController.get(configuration, AnimPlayer(context))
        mPlayerController?.let {
            it.setPlayerAction(playerAction)
            it.setFetchResource(fetchResource)
            it.setMonitor(monitor)
        }
    }

    fun startVideoGift(filePath: String) {
        if (TextUtils.isEmpty(filePath)) {
            return
        }
        val configModel = JsonUtil.parseConfigModel(filePath)
        val dataSource = DataSource()
            .setBaseDir(filePath)
            .setPortraitPath(configModel.portraitItem!!.path!!, configModel.portraitItem!!.alignMode)
            .setLandscapePath(configModel.landscapeItem!!.path!!, configModel.landscapeItem!!.alignMode)
            .setLooping(false)//不能设置循坏播放，会导致无法知道遮罩的对应那一帧
        startDataSource(dataSource)
    }

    private fun startDataSource(dataSource: DataSource) {
        if (!dataSource.isValid()) {
            Log.e(TAG, "startDataSource: dataSource is invalid.")
        }
        mPlayerController?.start(dataSource)
    }

    fun attachView() {
        mPlayerController?.attachAlphaView(mVideoContainer)
        //Toast.makeText(context, "attach alphaVideoView", Toast.LENGTH_SHORT).show()
    }

    fun detachView() {
        mPlayerController?.detachAlphaView(mVideoContainer)
        //Toast.makeText(context, "detach alphaVideoView", Toast.LENGTH_SHORT).show()
    }

    fun releasePlayerController() {
        mPlayerController?.let {
            it.detachAlphaView(mVideoContainer)
            it.release()
        }
    }

    fun reset(){
        mPlayerController?.reset()
    }
}