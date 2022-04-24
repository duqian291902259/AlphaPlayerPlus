package com.ss.ugc.android.alpha_player.widget

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import com.ss.ugc.android.alpha_player.controller.IPlayerControllerExt
import com.ss.ugc.android.alpha_player.model.ScaleType
import com.ss.ugc.android.alpha_player.render.IRender
import com.ss.ugc.android.alpha_player.vap.AnimConfig

/**
 * created by dengzhuoyao on 2020/07/07
 */
class AlphaVideoGLTextureView @JvmOverloads constructor(
    context: Context,
    attr: AttributeSet? = null
) : GLTextureView(context, attr), IAlphaVideoView {

    private var mAnimConfig: AnimConfig? = null
    private val GL_CONTEXT_VERSION = 2

    @Volatile
    private var isSurfaceCreated: Boolean = false
    override fun isSurfaceCreated(): Boolean {
        return isSurfaceCreated
    }

    private var mVideoWidth: Float = 0f
    private var mVideoHeight: Float = 0f
    private var mScaleType: ScaleType = ScaleType.ScaleAspectFill//ScaleType.ScaleAspectFill

    private var mRenderer: IRender? = null
    var mPlayerController: IPlayerControllerExt? = null
    var mSurface: Surface? = null

    private val mSurfaceListener = object : IRender.SurfaceListener {
        override fun onSurfacePrepared(surface: Surface) {
            mSurface?.release()
            mSurface = surface
            isSurfaceCreated = true
            mPlayerController?.surfacePrepared(surface)
            mPlayerController?.resume()
        }

        override fun onSurfaceDestroyed() {
            mSurface?.release()
            mSurface = null
            isSurfaceCreated = false
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            //Log.d("dq-av", "onFrameAvailable 2")
        }
    }

    init {
        setEGLContextClientVersion(GL_CONTEXT_VERSION)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        addOnSurfacePreparedListener()
        preserveEGLContextOnPause = true
        isOpaque = false
    }

    private fun addOnSurfacePreparedListener() {
        mRenderer?.setSurfaceListener(mSurfaceListener)
    }

    override fun addParentView(parentView: ViewGroup) {
        if (parentView.indexOfChild(this) == -1) {
            this.parent?.let {
                (it as ViewGroup).removeView(this)
            }
            parentView.addView(this)
        }
    }

    override fun removeParentView(parentView: ViewGroup) {
        parentView.removeView(this)
    }

    override fun getView(): View {
        return this
    }

    override fun setPlayerController(playerController: IPlayerControllerExt) {
        this.mPlayerController = playerController
    }

    override fun setVideoRenderer(renderer: IRender) {
        this.mRenderer = renderer
        setRenderer(renderer)
        addOnSurfacePreparedListener()
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun setAnimConfig(animConfig: AnimConfig?) {
        this.mAnimConfig = animConfig
        mRenderer?.setAnimConfig(animConfig)
    }

    override fun setScaleType(scaleType: ScaleType) {
        this.mScaleType = scaleType
        mRenderer?.setScaleType(scaleType)
    }

    override fun getScaleType(): ScaleType {
        return mScaleType
    }

    override fun measureInternal(videoWidth: Float, videoHeight: Float) {
        if (videoWidth > 0 && videoHeight > 0) {
            mVideoWidth = videoWidth
            mVideoHeight = videoHeight
        }
        mRenderer?.let {
            val width = measuredWidth
            val height = measuredHeight
            queueEvent {
                it.measureInternal(width.toFloat(), height.toFloat(), mVideoWidth, mVideoHeight)
            }
        }
    }

    override fun onFirstFrame() {
        mRenderer?.onFirstFrame()
    }

    override fun onCompletion() {
        mRenderer?.onCompletion()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measureInternal(mVideoWidth, mVideoHeight)
    }

    override fun release() {
        mSurfaceListener.onSurfaceDestroyed()
    }
}