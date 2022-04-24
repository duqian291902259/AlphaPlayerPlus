/*
 * Tencent is pleased to support the open source community by making vap available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ss.ugc.android.alpha_player.vap.player

import android.content.Context
import android.view.Surface
import com.ss.ugc.android.alpha_player.model.VideoInfo
import com.ss.ugc.android.alpha_player.player.AbsPlayer
import com.ss.ugc.android.alpha_player.vap.AnimConfigManager
import com.ss.ugc.android.alpha_player.vap.Constant
import com.ss.ugc.android.alpha_player.vap.FileContainer
import com.ss.ugc.android.alpha_player.vap.inter.IAnimListener
import com.ss.ugc.android.alpha_player.vap.plugin.AnimPluginManager
import java.io.File

class AnimPlayer(context: Context? = null) : AbsPlayer(context) {

    companion object {
        private const val TAG = "dq.AnimPlayer"
    }

    var animListener: IAnimListener? = null
    var decoder: Decoder? = null
    var audioPlayer: AudioPlayer? = null
    var fps: Int = 0
        set(value) {
            decoder?.fps = value
            field = value
        }
    var playLoop: Int = 0
        set(value) {
            decoder?.playLoop = value
            audioPlayer?.playLoop = value
            field = value
        }
    var supportMaskBoolean: Boolean = false
    var maskEdgeBlurBoolean: Boolean = false

    // 是否兼容老版本 默认不兼容
    var enableVersion1: Boolean = false

    // 视频模式
    var videoMode: Int = Constant.VIDEO_MODE_SPLIT_HORIZONTAL
    var isDetachedFromWindow = false
    var isSurfaceAvailable = true
    var startRunnable: Runnable? = null
    var isStartRunning = false // 启动时运行状态

    val configManager = AnimConfigManager()
    val pluginManager = AnimPluginManager()

    /*fun onSurfaceTextureDestroyed() {
        isSurfaceAvailable = false
        decoder?.destroy()
        audioPlayer?.destroy()
    }

    fun onSurfaceTextureAvailable(width: Int, height: Int) {
        isSurfaceAvailable = true
        startRunnable?.run()
        startRunnable = null
    }


    fun onSurfaceTextureSizeChanged(width: Int, height: Int) {
        decoder?.onSurfaceSizeChanged(width, height)
    }*/

    fun startPlay(fileContainer: FileContainer) {
        isStartRunning = true
        prepareDecoder()
        /*if (decoder?.prepareThread() == false) {
            decoder?.onFailed(
                Constant.REPORT_ERROR_TYPE_CREATE_THREAD,
                Constant.ERROR_MSG_CREATE_THREAD
            )
            isStartRunning = false
            return
        }
        // 在线程中解析配置
        decoder?.renderThread?.handler?.post {
            val result = configManager.parseConfig(fileContainer, enableVersion1, videoMode, fps)
            if (result != Constant.OK) {
                decoder?.onFailed(result, Constant.getErrorMsg(result))
                isStartRunning = false
                return@post
            }
            ALog.i(TAG, "parse ${configManager.config}")
            val config = configManager.config
            // 如果是默认配置，因为信息不完整onVideoConfigReady不会被调用
            if (config != null && (config.isDefaultConfig || animListener?.onVideoConfigReady(config) == true)) {*/
        innerStartPlay(fileContainer)
        /*} else {
            ALog.i(TAG, "onVideoConfigReady return false")
        }
    }*/
    }

    private fun innerStartPlay(fileContainer: FileContainer) {
        synchronized(AnimPlayer::class.java) {
            if (isSurfaceAvailable) {
                isStartRunning = false
                decoder?.start(fileContainer)
                audioPlayer?.start(fileContainer)
            } else {
                startRunnable = Runnable {
                    innerStartPlay(fileContainer)
                }
                //animView.prepareTextureView()
            }
        }
    }

    fun stopPlay() {
        decoder?.stop()
        audioPlayer?.stop()
    }

    fun isRunning(): Boolean {
        return isStartRunning // 启动过程运行状态
                || (decoder?.isRunning ?: false) // 解码过程运行状态

    }

    private fun prepareDecoder() {
        if (decoder?.prepareThread() == false) {
            decoder?.onFailed(
                Constant.REPORT_ERROR_TYPE_CREATE_THREAD,
                Constant.ERROR_MSG_CREATE_THREAD
            )
            isStartRunning = false
            return
        }
        if (decoder == null) {
            decoder = HardDecoder(this).apply {
                playLoop = this@AnimPlayer.playLoop
                fps = this@AnimPlayer.fps
            }
        }

        if (audioPlayer == null) {
            audioPlayer = AudioPlayer(this).apply {
                playLoop = this@AnimPlayer.playLoop
            }
        }
    }

    override fun initMediaPlayer() {
        prepareDecoder()
    }

    override fun setSurface(surface: Surface) {
        decoder?.surface = surface
    }

    private var mFileContainer: FileContainer? = null

    override fun setDataSource(dataPath: String) {
        mFileContainer = FileContainer(File(dataPath))
    }

    override fun prepareAsync() {
    }

    override fun start() {
        isStartRunning = true
        if (mFileContainer != null) {
            innerStartPlay(mFileContainer!!)
        }
    }

    override fun pause() {

    }

    override fun stop() {
        stopPlay()
    }

    override fun reset() {

    }

    override fun release() {
        isSurfaceAvailable = false
        decoder?.destroy()
        audioPlayer?.destroy()
    }

    override fun setLooping(looping: Boolean) {
        decoder?.playLoop = Int.MAX_VALUE
        audioPlayer?.playLoop = Int.MAX_VALUE
    }

    override fun setScreenOnWhilePlaying(onWhilePlaying: Boolean) {

    }

    override fun getVideoInfo(): VideoInfo {
        return VideoInfo(decoder?.mVideoWidth ?: 0, decoder?.mVideoHeight ?: 0)
    }

    override fun getPlayerType(): String {
        return "HardDecoder"
    }

    /*fun updateMaskConfig(maskConfig: MaskConfig?) {
        configManager.config?.maskConfig = configManager.config?.maskConfig ?: MaskConfig()
        configManager.config?.maskConfig?.safeSetMaskBitmapAndReleasePre(maskConfig?.alphaMaskBitmap)
        configManager.config?.maskConfig?.maskPositionPair = maskConfig?.maskPositionPair
        configManager.config?.maskConfig?.maskTexPair = maskConfig?.maskTexPair
    }*/

}