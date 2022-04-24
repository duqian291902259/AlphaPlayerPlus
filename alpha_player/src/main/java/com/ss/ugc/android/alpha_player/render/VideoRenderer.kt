package com.ss.ugc.android.alpha_player.render

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.util.Log
import android.view.Surface
import com.ss.ugc.android.alpha_player.BuildConfig
import com.ss.ugc.android.alpha_player.model.ScaleType
import com.ss.ugc.android.alpha_player.vap.*
import com.ss.ugc.android.alpha_player.vap.plugin.AnimPluginManager
import com.ss.ugc.android.alpha_player.vap.util.GlFloatArray
import com.ss.ugc.android.alpha_player.widget.IAlphaVideoView
import com.ss.ugc.android.alpha_player.vap.util.ShaderUtil
import com.ss.ugc.android.alpha_player.vap.util.VertexUtil
import java.lang.Exception
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * created by dengzhuoyao on 2020/07/07
 */
class VideoRenderer(val alphaVideoView: IAlphaVideoView) : IRender {

    companion object {
        private const val TAG = "VideoRender"
        const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
    }

    private val mVPMatrix = FloatArray(16)
    private val sTMatrix = FloatArray(16)
    private var programID: Int = 0
    private var textureID: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var uSTMatrixHandle: Int = 0
    private val canDraw = AtomicBoolean(false)
    private val updateSurface = AtomicBoolean(false)
    private lateinit var surfaceTexture: SurfaceTexture
    private var surfaceListener: IRender.SurfaceListener? = null
    private var scaleType = ScaleType.ScaleAspectFill

    //vap
    private val vertexArray = GlFloatArray()
    private val alphaArray = GlFloatArray()
    private val rgbArray = GlFloatArray()
    private val eglUtil: EGLUtil = EGLUtil()
    private var uTextureLocation: Int = 0
    private var aPositionLocation: Int = 0
    private var aTextureAlphaLocation: Int = 0
    private var aTextureRgbLocation: Int = 0
    private var surfaceSizeChanged = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var mAnimConfig: AnimConfig? = null
    val mPluginManager = AnimPluginManager()

    init {
        Matrix.setIdentityM(sTMatrix, 0)
    }

    override fun setScaleType(scaleType: ScaleType) {
        this.scaleType = scaleType
    }

    override fun setAnimConfig(animConfig: AnimConfig?) {
        this.mAnimConfig = animConfig
        mPluginManager.reset()
        animConfig?.apply {
            mPluginManager.onConfigCreate(animConfig)
            //animConfig = testConfig()
            initByConfig(animConfig)
        }
        mCountDraw = 0
    }

    private fun testConfig(): AnimConfig {
        val config = AnimConfig()
        config.width = 672
        config.height = 1504
        config.videoWidth = 1104
        config.videoHeight = 1504

        config.rgbPointRect = PointRect(
            4,
            0,
            672,
            1504
        )
        config.alphaPointRect = PointRect(
            684,
            4,
            336,
            752
        )
        return config
    }

    private fun initByConfig(config: AnimConfig) {
        mPluginManager.totalFrame = config.totalFrames
        //AnimConfig(version=2, totalFrames=240, width=672, height=1504, videoWidth=1104, videoHeight=1504, orien=0, fps=20, isMix=true,
        // alphaPointRect=PointRect(x=684, y=4, w=336, h=752), rgbPointRect=PointRect(x=4, y=0, w=672, h=1504), isDefaultConfig=false)
        setVertexBuf(config)
        setTexCoords(config)
    }

    private fun setVertexBuf(config: AnimConfig) {
        vertexArray.setArray(
            VertexUtil.create(
                config.width,
                config.height,
                PointRect(0, 0, config.width, config.height),
                vertexArray.array
            )
        )
    }

    private fun setTexCoords(config: AnimConfig) {
        val alpha = TexCoordsUtil.create(
            config.videoWidth,
            config.videoHeight,
            config.alphaPointRect,
            alphaArray.array
        )
        val rgb = TexCoordsUtil.create(
            config.videoWidth,
            config.videoHeight,
            config.rgbPointRect,
            rgbArray.array
        )
        alphaArray.setArray(alpha)
        rgbArray.setArray(rgb)
    }

    override fun measureInternal(
        viewWidth: Float,
        viewHeight: Float,
        videoWidth: Float,
        videoHeight: Float
    ) {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return
        }
        // TODO: 2021/2/26 横竖屏切换，顶点坐标变化
        Log.d(
            "dq-av",
            "viewWidth=$viewWidth,viewHeight=$viewHeight,videoWidth=$videoWidth,videoHeight=$videoHeight,"
        )
    }

    override fun setSurfaceListener(surfaceListener: IRender.SurfaceListener) {
        this.surfaceListener = surfaceListener
    }

    override fun onDrawFrame(glUnused: GL10) {
        if (updateSurface.compareAndSet(true, false)) {
            try {
                surfaceTexture.updateTexImage()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            surfaceTexture.getTransformMatrix(sTMatrix)
        }

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        if (!canDraw.get()) {
            mPluginManager.reset()
            GLES20.glFinish()
            return
        }
        if (BuildConfig.DEBUG && mCountDraw >= 209) return
        draw()

        //插件渲染：文字，遮罩图片
        mPluginManager.onRendering()

        mCountDraw++
    }


    private fun draw() {
        GLES20.glUseProgram(programID)
        // 设置顶点坐标
        vertexArray.setVertexAttribPointer(aPositionLocation)
        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureID)
        GLES20.glUniform1i(uTextureLocation, 0)

        // 设置纹理坐标
        // alpha 通道坐标
        alphaArray.setVertexAttribPointer(aTextureAlphaLocation)
        // rgb 通道坐标
        rgbArray.setVertexAttribPointer(aTextureRgbLocation)

        Matrix.setIdentityM(mVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, sTMatrix, 0)
        checkGlError("setVertexAttribPointer")

        // draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun onSurfaceChanged(glUnused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceWidth = width
        surfaceHeight = height
        updateViewPort(width, height)
    }

    /**
     * 显示区域大小变化
     */
    private fun updateViewPort(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        surfaceSizeChanged = true
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun onSurfaceCreated(glUnused: GL10, config: EGLConfig) {
        programID =
            ShaderUtil.createProgram(RenderConstant.VERTEX_SHADER, RenderConstant.FRAGMENT_SHADER)
        if (programID == 0) {
            return
        }

        uTextureLocation = GLES20.glGetUniformLocation(programID, "texture")
        checkGlError("glGetUniformLocation")
        if (uTextureLocation == -1) {
            throw RuntimeException("Could not get location for uTextureLocation")
        }
        aPositionLocation = GLES20.glGetAttribLocation(programID, "vPosition")
        aTextureAlphaLocation = GLES20.glGetAttribLocation(programID, "vTexCoordinateAlpha")
        aTextureRgbLocation = GLES20.glGetAttribLocation(programID, "vTexCoordinateRgb")


        uMVPMatrixHandle = GLES20.glGetUniformLocation(programID, "uMVPMatrix")
        uSTMatrixHandle = GLES20.glGetUniformLocation(programID, "uSTMatrix")
        checkGlError("glGetUniformLocation uSTMatrix")
        /*if (uSTMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uSTMatrix")
        }*/

        prepareSurface()

        mPluginManager.onRenderCreate(textureID)
    }

    override fun onSurfaceDestroyed(gl: GL10?) {
        surfaceListener?.onSurfaceDestroyed()
        clearFrame()
        mPluginManager.onRelease()
        mPluginManager.onDestroy()
    }

    private var mCountDraw = 0

    private fun clearFrame() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        eglUtil.swapBuffers()
        mCountDraw = 0
    }

    private fun prepareSurface() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        textureID = textures[0]
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureID)
        checkGlError("glBindTexture textureID")

        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )

        surfaceTexture = SurfaceTexture(textureID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            surfaceTexture.setDefaultBufferSize(
                alphaVideoView.getMeasuredWidth(),
                alphaVideoView.getMeasuredHeight()
            )
        }
        surfaceTexture.setOnFrameAvailableListener(this)

        val surface = Surface(this.surfaceTexture)
        surfaceListener?.onSurfacePrepared(surface)
        updateSurface.compareAndSet(true, false)
    }

    override fun onFrameAvailable(surface: SurfaceTexture) {
        updateSurface.compareAndSet(false, true)
        alphaVideoView.requestRender()
        surfaceListener?.onFrameAvailable(surface)
    }

    override fun onFirstFrame() {
        mPluginManager.reset()
        canDraw.compareAndSet(false, true)
        Log.i(TAG, "onFirstFrame:    canDraw = " + canDraw.get())
        alphaVideoView.requestRender()
    }

    override fun onCompletion() {
        mPluginManager.reset()
        canDraw.compareAndSet(true, false)
        Log.i(TAG, "onCompletion:   canDraw = " + canDraw.get())
        alphaVideoView.requestRender()
        clearFrame()
    }


    private fun checkGlError(op: String) {
        val error: Int = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
            // TODO: 2018/4/25 端监控 用于监控礼物播放成功状态
        }
    }
}