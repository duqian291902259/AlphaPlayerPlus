package com.ss.ugc.android.alpha_player.vap.mix

import com.ss.ugc.android.alpha_player.vap.Constant
import com.ss.ugc.android.alpha_player.vap.util.ALog
import com.ss.ugc.android.alpha_player.vap.util.BitmapUtil
import android.graphics.Bitmap
import android.os.SystemClock
import com.ss.ugc.android.alpha_player.vap.AnimConfig
import com.ss.ugc.android.alpha_player.vap.inter.IFetchResource
import com.ss.ugc.android.alpha_player.vap.plugin.IAnimPlugin

class MixAnimPlugin : IAnimPlugin {//(val player: AnimPlayer)

    companion object {
        private const val TAG = "${Constant.TAG}.MixAnimPlugin"
    }

    var resourceRequest: IFetchResource? = null

    //var resourceClickListener: OnResourceClickListener? = null
    var srcMap: SrcMap? = null
    var frameAll: FrameAll? = null
    var curFrameIndex = -1 // 当前帧
    private var resultCbCount = 0 // 回调次数
    private var mixRender: MixRender? = null

    // private val mixTouch by lazy { MixTouch(this) }
    var autoTxtColorFill = true // 是否启动自动文字填充 默认开启

    // 同步锁
    private val lock = Object()
    private var forceStopLock = false

    private var mAnimConfig: AnimConfig? = null

    override fun onConfigCreate(config: AnimConfig): Int {
        if (!config.isMix) return Constant.OK
        if (resourceRequest == null) {
            ALog.i(TAG, "IFetchResource is empty")
            return Constant.REPORT_ERROR_TYPE_CONFIG_PLUGIN_MIX
        }
        this.mAnimConfig = config

        // step 1 parse src
        parseSrc(config)

        // step 2 parse frame
        parseFrame(config)

        // step 3 fetch resource
        fetchResourceSync()

        // step 4 生成文字bitmap
        val result = createBitmap()
        if (!result) {
            return Constant.REPORT_ERROR_TYPE_CONFIG_PLUGIN_MIX
        }

        // step 5 check resource
        ALog.i(TAG, "load resource $resultCbCount")
        srcMap?.map?.values?.forEach {
            if (it.bitmap == null) {
                ALog.e(TAG, "missing src $it")
                return Constant.REPORT_ERROR_TYPE_CONFIG_PLUGIN_MIX
            } else if (it.bitmap?.config == Bitmap.Config.ALPHA_8) {
                ALog.e(TAG, "src $it bitmap must not be ALPHA_8")
                return Constant.REPORT_ERROR_TYPE_CONFIG_PLUGIN_MIX
            } /*else {
                it.srcTextureId = TextureLoadUtil.loadTexture(it.bitmap)
            }*/
        }
        return Constant.OK
    }

    override fun onRenderCreate(textureID: Int) {
        //if (player.configManager.config?.isMix == false) return
        ALog.i(TAG, "mix render init")
        mixRender = MixRender(this)
        mixRender?.init(textureID)
    }

    override fun onRendering(frameIndex: Int) {
        //val config = player.configManager.config ?: return
        //if (!config.isMix) return
        curFrameIndex = frameIndex
        //ALog.i(TAG, "mix render frameIndex=$frameIndex")

        val list = frameAll?.map?.get(frameIndex)?.list ?: return

        list.forEach { frame ->
            val src = srcMap?.map?.get(frame.srcId) ?: return@forEach
            if (mAnimConfig != null) {
                mixRender?.renderFrame(mAnimConfig!!, frame, src)
            }
        }
    }


    override fun onRelease() {
        destroy()
    }

    override fun onDestroy() {
        destroy()
    }

    private fun destroy() {
        // 强制结束等待
        forceStopLockThread()
        //if (player.configManager.config?.isMix == false) return
        val resources = ArrayList<Resource>()
        srcMap?.map?.values?.forEach { src ->
            mixRender?.release(src.srcTextureId)
            when (src.srcType) {
                Src.SrcType.IMG -> resources.add(Resource(src))
                Src.SrcType.TXT -> src.bitmap?.recycle()
                else -> {
                }
            }
        }
        resourceRequest?.releaseResource(resources)

        // 清理
        curFrameIndex = -1
        srcMap?.map?.clear()
        frameAll?.map?.clear()
    }

    private fun parseSrc(config: AnimConfig) {
        config.jsonConfig?.apply {
            srcMap = SrcMap(this)
        }
    }


    private fun parseFrame(config: AnimConfig) {
        config.jsonConfig?.apply {
            frameAll = FrameAll(this)
        }
    }


    private fun fetchResourceSync() {
        synchronized(lock) {
            forceStopLock = false // 开始时不会强制关闭
        }
        val time = SystemClock.elapsedRealtime()
        val totalSrc = srcMap?.map?.size ?: 0
        ALog.i(TAG, "load resource totalSrc = $totalSrc")

        resultCbCount = 0
        srcMap?.map?.values?.forEach { src ->
            if (src.srcType == Src.SrcType.IMG) {
                ALog.i(TAG, "fetch image ${src.srcId}")
                resourceRequest?.fetchImage(Resource(src)) {
                    src.bitmap = if (it == null) {
                        ALog.e(TAG, "fetch image ${src.srcId} bitmap return null")
                        BitmapUtil.createEmptyBitmap()
                    } else it
                    ALog.i(TAG, "fetch image ${src.srcId} finish bitmap is ${it?.hashCode()}")
                    resultCall()
                }
            } else if (src.srcType == Src.SrcType.TXT) {
                ALog.i(TAG, "fetch txt ${src.txt}")
                resourceRequest?.fetchText(Resource(src)) {
                    src.txt = it ?: ""
                    ALog.i(TAG, "fetch text ${src.srcId} finish txt is $it")
                    resultCall()
                }
            }
        }

        // 同步等待所有资源完成
        synchronized(lock) {
            while (resultCbCount < totalSrc && !forceStopLock) {
                lock.wait()
            }
        }
        ALog.i(TAG, "fetchResourceSync cost=${SystemClock.elapsedRealtime() - time}ms")
    }

    private fun forceStopLockThread() {
        synchronized(lock) {
            forceStopLock = true
            lock.notifyAll()
        }
    }

    private fun resultCall() {
        synchronized(lock) {
            resultCbCount++
            lock.notifyAll()
        }
    }

    private fun createBitmap(): Boolean {
        return try {
            srcMap?.map?.values?.forEach { src ->
                if (src.srcType == Src.SrcType.TXT) {
                    try {
                        src.bitmap = BitmapUtil.createTxtBitmap(src)
                    } catch (e: Exception) {
                        ALog.e(TAG, "createBitmap $e")
                    }
                }
            }
            true
        } catch (e: OutOfMemoryError) {
            ALog.e(TAG, "draw text OOM $e", e)
            false
        }
    }
}
