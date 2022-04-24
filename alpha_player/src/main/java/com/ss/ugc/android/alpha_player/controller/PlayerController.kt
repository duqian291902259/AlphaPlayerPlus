package com.ss.ugc.android.alpha_player.controller

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process
import android.support.annotation.WorkerThread
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import com.ss.ugc.android.alpha_player.IMonitor
import com.ss.ugc.android.alpha_player.IPlayerAction
import com.ss.ugc.android.alpha_player.model.AlphaVideoViewType
import com.ss.ugc.android.alpha_player.model.Configuration
import com.ss.ugc.android.alpha_player.model.DataSource
import com.ss.ugc.android.alpha_player.player.DefaultSystemPlayer
import com.ss.ugc.android.alpha_player.player.IMediaPlayer
import com.ss.ugc.android.alpha_player.player.PlayerState
import com.ss.ugc.android.alpha_player.render.VideoRenderer
import com.ss.ugc.android.alpha_player.vap.AnimConfig
import com.ss.ugc.android.alpha_player.vap.AnimConfigManager
import com.ss.ugc.android.alpha_player.vap.Constant
import com.ss.ugc.android.alpha_player.vap.FileContainer
import com.ss.ugc.android.alpha_player.vap.inter.IFetchResource
import com.ss.ugc.android.alpha_player.vap.util.ALog
import com.ss.ugc.android.alpha_player.widget.AlphaVideoGLSurfaceView
import com.ss.ugc.android.alpha_player.widget.AlphaVideoGLTextureView
import com.ss.ugc.android.alpha_player.widget.IAlphaVideoView
import org.json.JSONObject
import java.io.File
import java.lang.Exception

/**
 * created by dengzhuoyao on 2020/07/08
 */
class PlayerController(
    val context: Context,
    owner: LifecycleOwner,
    val alphaVideoViewType: AlphaVideoViewType,
    mediaPlayer: IMediaPlayer
) : IPlayerControllerExt, LifecycleObserver, Handler.Callback {

    companion object {
        private const val TAG = "PlayerController"

        const val INIT_MEDIA_PLAYER: Int = 1
        const val SET_DATA_SOURCE: Int = 2
        const val START: Int = 3
        const val PAUSE: Int = 4
        const val RESUME: Int = 5
        const val STOP: Int = 6
        const val DESTROY: Int = 7
        const val SURFACE_PREPARED: Int = 8
        const val RESET: Int = 9

        fun get(configuration: Configuration, mediaPlayer: IMediaPlayer? = null): PlayerController {
            return PlayerController(
                configuration.context, configuration.lifecycleOwner,
                configuration.alphaVideoViewType,
                mediaPlayer ?: DefaultSystemPlayer()
            )
        }
    }

    private var suspendDataSource: DataSource? = null
    var isPlaying: Boolean = false
    var playerState = PlayerState.NOT_PREPARED
    var mMonitor: IMonitor? = null
    private var mPlayerAction: IPlayerAction? = null
    private var mFetchResource: IFetchResource? = null

    // 是否兼容老版本 默认不兼容
    var enableVersion1: Boolean = false

    // 视频模式,默认左右对齐
    var videoMode: Int = Constant.VIDEO_MODE_SPLIT_HORIZONTAL

    private var mVideoRender: VideoRenderer? = null
    var mediaPlayer: IMediaPlayer
    lateinit var alphaVideoView: IAlphaVideoView

    var workHandler: Handler? = null
    val mainHandler: Handler = Handler(Looper.getMainLooper())
    var playThread: HandlerThread? = null

    private val mPreparedListener = object : IMediaPlayer.OnPreparedListener {
        override fun onPrepared() {
            sendMessage(getMessage(START, null))
        }
    }

    private val mErrorListener = object : IMediaPlayer.OnErrorListener {
        override fun onError(what: Int, extra: Int, desc: String) {
            monitor(false, what, extra, "mediaPlayer error, info: $desc")
            emitEndSignal()
        }
    }

    init {
        this.mediaPlayer = mediaPlayer
        init(owner)
        initAlphaView()
        initMediaPlayer()
    }

    private fun init(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(this)
        playThread = HandlerThread("alpha-play-thread", Process.THREAD_PRIORITY_BACKGROUND)
        playThread!!.start()
        workHandler = Handler(playThread!!.looper, this)
    }

    private fun initAlphaView() {
        alphaVideoView = when (alphaVideoViewType) {
            AlphaVideoViewType.GL_SURFACE_VIEW -> AlphaVideoGLSurfaceView(context, null)
            AlphaVideoViewType.GL_TEXTURE_VIEW -> AlphaVideoGLTextureView(context, null)
        }
        alphaVideoView.let {
            val layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            it.setLayoutParams(layoutParams)
            it.setPlayerController(this)
            val renderer = VideoRenderer(it)
            this.mVideoRender = renderer
            it.setVideoRenderer(renderer)
        }
    }

    private fun initMediaPlayer() {
        sendMessage(getMessage(INIT_MEDIA_PLAYER, null))
    }

    override fun setPlayerAction(playerAction: IPlayerAction) {
        this.mPlayerAction = playerAction
    }

    override fun setFetchResource(fetchResource: IFetchResource?) {
        this.mFetchResource = fetchResource
        mVideoRender?.mPluginManager?.getMixAnimPlugin()?.resourceRequest = fetchResource
    }

    override fun setMonitor(monitor: IMonitor) {
        this.mMonitor = monitor
    }

    override fun setVisibility(visibility: Int) {
        alphaVideoView.setVisibility(visibility)
        if (visibility == View.VISIBLE) {
            alphaVideoView.bringToFront()
        }
    }

    override fun attachAlphaView(parentView: ViewGroup) {
        alphaVideoView.addParentView(parentView)
    }

    override fun detachAlphaView(parentView: ViewGroup) {
        alphaVideoView.removeParentView(parentView)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        pause()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        resume()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        stop()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        release()
    }

    private fun sendMessage(msg: Message) {
        playThread?.let {
            if (it.isAlive && !it.isInterrupted) {
                when (workHandler) {
                    null -> workHandler = Handler(it.looper, this)
                }
                workHandler!!.sendMessageDelayed(msg, 0)
            }
        }
    }

    private fun getMessage(what: Int, obj: Any?): Message {
        val message = Message.obtain()
        message.what = what
        message.obj = obj
        return message
    }

    override fun surfacePrepared(surface: Surface) {
        sendMessage(getMessage(SURFACE_PREPARED, surface))
    }

    override fun start(dataSource: DataSource) {
        if (dataSource.isValid()) {
            setVisibility(View.VISIBLE)
            sendMessage(getMessage(SET_DATA_SOURCE, dataSource))
        } else {
            emitEndSignal()
            monitor(false, errorInfo = "dataSource is invalid!")
        }
    }

    override fun pause() {
        sendMessage(getMessage(PAUSE, null))
    }

    override fun resume() {
        sendMessage(getMessage(RESUME, null))
    }

    override fun stop() {
        sendMessage(getMessage(STOP, null))
    }

    override fun reset() {
        sendMessage(getMessage(RESET, null))
    }

    override fun release() {
        sendMessage(getMessage(DESTROY, null))
    }

    override fun getView(): View {
        return alphaVideoView.getView()
    }

    override fun getPlayerType(): String {
        return mediaPlayer.getPlayerType()
    }

    @WorkerThread
    private fun initPlayer() {
        try {
            mediaPlayer.initMediaPlayer()
        } catch (e: Exception) {
            mediaPlayer = DefaultSystemPlayer()
            mediaPlayer.initMediaPlayer()
        }
        mediaPlayer.setScreenOnWhilePlaying(true)
        mediaPlayer.setLooping(false)

        mediaPlayer.setOnFirstFrameListener(object : IMediaPlayer.OnFirstFrameListener {
            override fun onFirstFrame() {
                alphaVideoView.onFirstFrame()
            }
        })
        mediaPlayer.setOnCompletionListener(object : IMediaPlayer.OnCompletionListener {
            override fun onCompletion() {
                alphaVideoView.onCompletion()
                playerState = PlayerState.PAUSED
                monitor(true, errorInfo = "")
                emitEndSignal()
            }
        })
    }

    @WorkerThread
    private fun setDataSource(dataSource: DataSource) {
        try {
            setVideoFromFile(dataSource)
        } catch (e: Exception) {
            e.printStackTrace()
            monitor(
                false,
                errorInfo = "alphaVideoView set dataSource failure: " + Log.getStackTraceString(e)
            )
            emitEndSignal()
        }
    }

    @WorkerThread
    private fun setVideoFromFile(dataSource: DataSource) {
        mediaPlayer.reset()
        playerState = PlayerState.NOT_PREPARED
        val orientation = context.resources.configuration.orientation

        val dataPath = dataSource.getPath(orientation)
        val scaleType = dataSource.getScaleType(orientation)
        if (TextUtils.isEmpty(dataPath) || !File(dataPath).exists()) {
            monitor(false, errorInfo = "dataPath is empty or File is not exists. path = $dataPath")
            emitEndSignal()
            return
        }

        val animConfigManager = AnimConfigManager()
        var config: AnimConfig? = null
        var parseConfig = -1
        //从mp4中解析出json配置 在线程中解析配置
        /*val fileContainer = FileContainer(File(dataPath))
        parseConfig =
            animConfigManager.parseConfig(fileContainer, enableVersion1, videoMode, 24)
        config = animConfigManager.config*/
        if (parseConfig < 0) {
            val json =
                "{\"info\":{\"v\":2,\"f\":211,\"w\":750,\"h\":750,\"fps\":24,\"videoW\":1504,\"videoH\":752,\"aFrame\":[750,0,750,750],\"rgbFrame\":[0,0,750,750],\"isVapx\":1,\"orien\":0},\"src\":[{\"srcId\":\"0\",\"srcType\":\"img\",\"srcTag\":\"tag1\",\"loadType\":\"net\",\"fitType\":\"fitXY\",\"w\":0,\"h\":0},{\"srcId\":\"1\",\"srcType\":\"img\",\"srcTag\":\"tag2\",\"loadType\":\"net\",\"fitType\":\"fitXY\",\"w\":0,\"h\":0},{\"srcId\":\"2\",\"srcType\":\"img\",\"srcTag\":\"tag3\",\"loadType\":\"net\",\"fitType\":\"fitXY\",\"w\":0,\"h\":0},{\"srcId\":\"3\",\"srcType\":\"txt\",\"srcTag\":\"tag4\",\"color\":\"#7D7DFF\",\"style\":\"b\",\"loadType\":\"local\",\"fitType\":\"fitXY\",\"w\":277,\"h\":54}],\"frame\":[{\"i\":117,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[0,742,9,8],\"mFrame\":[752,742,9,8],\"mt\":0}]},{\"i\":182,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[315,261,88,90],\"mFrame\":[1067,261,88,90],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[541,408,61,57],\"mFrame\":[1293,408,61,57],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[187,442,45,44],\"mFrame\":[939,442,45,44],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[192,151,351,69],\"mFrame\":[944,151,351,69],\"mt\":0}]},{\"i\":156,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[495,228,214,201],\"mFrame\":[1247,228,214,201],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[0,346,1,5],\"mFrame\":[752,346,1,5],\"mt\":0}]},{\"i\":169,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[195,60,126,154],\"mFrame\":[947,60,126,154],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[583,335,105,97],\"mFrame\":[1335,335,105,97],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[62,357,66,60],\"mFrame\":[814,357,66,60],\"mt\":0}]},{\"i\":195,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[346,344,70,69],\"mFrame\":[1098,344,70,69],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[515,450,46,46],\"mFrame\":[1267,450,46,46],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[228,489,38,37],\"mFrame\":[980,489,38,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[228,243,298,59],\"mFrame\":[980,243,298,59],\"mt\":0}]},{\"i\":183,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[319,271,87,87],\"mFrame\":[1071,271,87,87],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[538,412,59,56],\"mFrame\":[1290,412,59,56],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[192,447,44,43],\"mFrame\":[944,447,44,43],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[195,158,346,69],\"mFrame\":[947,158,346,69],\"mt\":0}]},{\"i\":144,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[202,740,79,10],\"mFrame\":[954,740,79,10],\"mt\":0}]},{\"i\":170,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[210,81,123,147],\"mFrame\":[962,81,123,147],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[581,341,99,93],\"mFrame\":[1333,341,99,93],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[74,365,65,59],\"mFrame\":[826,365,65,59],\"mt\":0}]},{\"i\":196,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[346,347,70,68],\"mFrame\":[1098,347,70,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[513,452,48,45],\"mFrame\":[1265,452,48,45],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[229,491,37,36],\"mFrame\":[981,491,37,36],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[231,250,293,58],\"mFrame\":[983,250,293,58],\"mt\":0}]},{\"i\":118,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[0,744,1,6],\"mFrame\":[752,744,1,6],\"mt\":0}]},{\"i\":157,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[0,0,16,19],\"mFrame\":[752,0,16,19],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,240,206,192],\"mFrame\":[1264,240,206,192],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[0,283,10,65],\"mFrame\":[752,283,10,65],\"mt\":0}]},{\"i\":184,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[323,280,84,85],\"mFrame\":[1075,280,84,85],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[535,417,58,54],\"mFrame\":[1287,417,58,54],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[197,452,43,42],\"mFrame\":[949,452,43,42],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[198,165,342,68],\"mFrame\":[950,165,342,68],\"mt\":0}]},{\"i\":145,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[168,662,198,88],\"mFrame\":[920,662,198,88],\"mt\":0}]},{\"i\":146,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[189,549,214,201],\"mFrame\":[941,549,214,201],\"mt\":0}]},{\"i\":197,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[347,349,69,68],\"mFrame\":[1099,349,69,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[513,453,47,45],\"mFrame\":[1265,453,47,45],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[230,492,37,36],\"mFrame\":[982,492,37,36],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[234,257,289,57],\"mFrame\":[986,257,289,57],\"mt\":0}]},{\"i\":185,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[327,289,82,82],\"mFrame\":[1079,289,82,82],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[533,421,55,53],\"mFrame\":[1285,421,55,53],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[200,457,44,41],\"mFrame\":[952,457,44,41],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[201,172,338,67],\"mFrame\":[953,172,338,67],\"mt\":0}]},{\"i\":198,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[347,350,69,68],\"mFrame\":[1099,350,69,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,454,47,45],\"mFrame\":[1264,454,47,45],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[230,493,38,37],\"mFrame\":[982,493,38,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[236,264,286,57],\"mFrame\":[988,264,286,57],\"mt\":0}]},{\"i\":147,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[213,421,226,223],\"mFrame\":[965,421,226,223],\"mt\":0}]},{\"i\":186,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[330,297,80,80],\"mFrame\":[1082,297,80,80],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[530,425,55,52],\"mFrame\":[1282,425,55,52],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[205,461,43,41],\"mFrame\":[957,461,43,41],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[203,179,334,66],\"mFrame\":[955,179,334,66],\"mt\":0}]},{\"i\":158,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[0,0,51,35],\"mFrame\":[752,0,51,35],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[528,251,196,182],\"mFrame\":[1280,251,196,182],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[0,275,19,72],\"mFrame\":[752,275,19,72],\"mt\":0}]},{\"i\":171,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[224,101,120,140],\"mFrame\":[976,101,120,140],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[578,347,95,88],\"mFrame\":[1330,347,95,88],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[86,373,63,57],\"mFrame\":[838,373,63,57],\"mt\":0}]},{\"i\":199,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,351,68,68],\"mFrame\":[1100,351,68,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,493,37,37],\"mFrame\":[983,493,37,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[239,271,281,56],\"mFrame\":[991,271,281,56],\"mt\":0}]},{\"i\":148,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[244,299,234,229],\"mFrame\":[996,299,234,229],\"mt\":0}]},{\"i\":187,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[333,305,78,78],\"mFrame\":[1085,305,78,78],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[528,429,53,50],\"mFrame\":[1280,429,53,50],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[209,465,41,41],\"mFrame\":[961,465,41,41],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[206,186,330,66],\"mFrame\":[958,186,330,66],\"mt\":0}]},{\"i\":200,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,352,68,67],\"mFrame\":[1100,352,68,67],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,45],\"mFrame\":[1264,455,46,45],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,493,37,37],\"mFrame\":[983,493,37,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[241,278,278,56],\"mFrame\":[993,278,278,56],\"mt\":0}]},{\"i\":149,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[283,212,238,235],\"mFrame\":[1035,212,238,235],\"mt\":0}]},{\"i\":172,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[236,121,118,133],\"mFrame\":[988,121,118,133],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[575,354,90,83],\"mFrame\":[1327,354,90,83],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[98,380,61,56],\"mFrame\":[850,380,61,56],\"mt\":0}]},{\"i\":159,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[0,0,87,50],\"mFrame\":[752,0,87,50],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[542,262,186,172],\"mFrame\":[1294,262,186,172],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[0,276,30,72],\"mFrame\":[752,276,30,72],\"mt\":0}]},{\"i\":201,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,351,68,68],\"mFrame\":[1100,351,68,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,493,37,37],\"mFrame\":[983,493,37,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[241,278,278,56],\"mFrame\":[993,278,278,56],\"mt\":0}]},{\"i\":188,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[335,311,77,77],\"mFrame\":[1087,311,77,77],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[526,432,52,50],\"mFrame\":[1278,432,52,50],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[212,469,41,40],\"mFrame\":[964,469,41,40],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[209,193,326,65],\"mFrame\":[961,193,326,65],\"mt\":0}]},{\"i\":173,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[248,139,114,127],\"mFrame\":[1000,139,114,127],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[572,360,86,80],\"mFrame\":[1324,360,86,80],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[110,387,59,45],\"mFrame\":[862,387,59,45],\"mt\":0}]},{\"i\":160,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[14,0,108,66],\"mFrame\":[766,0,108,66],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[554,270,177,163],\"mFrame\":[1306,270,177,163],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[0,281,41,72],\"mFrame\":[752,281,41,72],\"mt\":0}]},{\"i\":109,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[15,704,41,46],\"mFrame\":[767,704,41,46],\"mt\":0}]},{\"i\":150,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[323,181,242,237],\"mFrame\":[1075,181,242,237],\"mt\":0}]},{\"i\":174,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[259,156,111,121],\"mFrame\":[1011,156,111,121],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[568,366,82,76],\"mFrame\":[1320,366,82,76],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[122,394,57,53],\"mFrame\":[874,394,57,53],\"mt\":0}]},{\"i\":189,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[337,318,76,75],\"mFrame\":[1089,318,76,75],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[523,436,51,49],\"mFrame\":[1275,436,51,49],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[215,473,40,39],\"mFrame\":[967,473,40,39],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[212,200,321,64],\"mFrame\":[964,200,321,64],\"mt\":0}]},{\"i\":161,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[33,0,122,84],\"mFrame\":[785,0,122,84],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[565,278,167,153],\"mFrame\":[1317,278,167,153],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[0,289,49,71],\"mFrame\":[752,289,49,71],\"mt\":0}]},{\"i\":110,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[2,705,64,45],\"mFrame\":[754,705,64,45],\"mt\":0}]},{\"i\":151,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[363,182,241,235],\"mFrame\":[1115,182,241,235],\"mt\":0}]},{\"i\":175,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[268,172,108,116],\"mFrame\":[1020,172,108,116],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[565,371,78,74],\"mFrame\":[1317,371,78,74],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[133,401,55,51],\"mFrame\":[885,401,55,51],\"mt\":0}]},{\"i\":190,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[339,323,75,74],\"mFrame\":[1091,323,75,74],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[522,439,50,48],\"mFrame\":[1274,439,50,48],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[218,476,40,39],\"mFrame\":[970,476,40,39],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[214,207,318,63],\"mFrame\":[966,207,318,63],\"mt\":0}]},{\"i\":111,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[0,708,56,42],\"mFrame\":[752,708,56,42],\"mt\":0}]},{\"i\":162,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[54,0,130,101],\"mFrame\":[806,0,130,101],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[574,286,157,144],\"mFrame\":[1326,286,157,144],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[0,297,57,70],\"mFrame\":[752,297,57,70],\"mt\":0}]},{\"i\":152,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[398,187,238,231],\"mFrame\":[1150,187,238,231],\"mt\":0}]},{\"i\":176,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[277,187,105,112],\"mFrame\":[1029,187,105,112],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[561,377,76,70],\"mFrame\":[1313,377,76,70],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[143,407,54,51],\"mFrame\":[895,407,54,51],\"mt\":0}]},{\"i\":191,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[341,329,74,72],\"mFrame\":[1093,329,74,72],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[520,442,49,47],\"mFrame\":[1272,442,49,47],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[220,479,40,38],\"mFrame\":[972,479,40,38],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[217,214,314,63],\"mFrame\":[969,214,314,63],\"mt\":0}]},{\"i\":112,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[0,710,45,40],\"mFrame\":[752,710,45,40],\"mt\":0}]},{\"i\":153,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[427,196,235,225],\"mFrame\":[1179,196,235,225],\"mt\":0}]},{\"i\":163,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[76,0,134,118],\"mFrame\":[828,0,134,118],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[579,293,148,136],\"mFrame\":[1331,293,148,136],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[0,306,66,68],\"mFrame\":[752,306,66,68],\"mt\":0}]},{\"i\":177,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[285,202,102,106],\"mFrame\":[1037,202,102,106],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[558,383,72,67],\"mFrame\":[1310,383,72,67],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[152,414,52,48],\"mFrame\":[904,414,52,48],\"mt\":0}]},{\"i\":113,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[0,712,38,38],\"mFrame\":[752,712,38,38],\"mt\":0}]},{\"i\":192,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[342,333,73,72],\"mFrame\":[1094,333,73,72],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[518,444,49,47],\"mFrame\":[1270,444,49,47],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[223,482,38,38],\"mFrame\":[975,482,38,38],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[220,221,310,62],\"mFrame\":[972,221,310,62],\"mt\":0}]},{\"i\":164,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[99,0,134,135],\"mFrame\":[851,0,134,135],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[583,300,140,129],\"mFrame\":[1335,300,140,129],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[1,314,75,68],\"mFrame\":[753,314,75,68],\"mt\":0}]},{\"i\":193,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[344,337,71,72],\"mFrame\":[1096,337,71,72],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[517,447,48,46],\"mFrame\":[1269,447,48,46],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[224,485,39,37],\"mFrame\":[976,485,39,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[223,228,305,61],\"mFrame\":[975,228,305,61],\"mt\":0}]},{\"i\":154,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[453,205,229,218],\"mFrame\":[1205,205,229,218],\"mt\":0}]},{\"i\":114,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[0,715,31,35],\"mFrame\":[752,715,31,35],\"mt\":0}]},{\"i\":178,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[292,215,99,103],\"mFrame\":[1044,215,99,103],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[554,388,70,65],\"mFrame\":[1306,388,70,65],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[161,419,50,48],\"mFrame\":[913,419,50,48],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[181,122,367,73],\"mFrame\":[933,122,367,73],\"mt\":0}]},{\"i\":194,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[345,341,71,70],\"mFrame\":[1097,341,71,70],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[515,449,48,46],\"mFrame\":[1267,449,48,46],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[226,487,38,37],\"mFrame\":[978,487,38,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[225,236,302,60],\"mFrame\":[977,236,302,60],\"mt\":0}]},{\"i\":165,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[121,0,134,152],\"mFrame\":[873,0,134,152],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[585,308,132,120],\"mFrame\":[1337,308,132,120],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[13,323,73,66],\"mFrame\":[765,323,73,66],\"mt\":0}]},{\"i\":155,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[476,216,221,210],\"mFrame\":[1228,216,221,210],\"mt\":0}]},{\"i\":115,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[0,718,24,32],\"mFrame\":[752,718,24,32],\"mt\":0}]},{\"i\":179,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[299,228,96,99],\"mFrame\":[1051,228,96,99],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[551,393,67,64],\"mFrame\":[1303,393,67,64],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[169,426,49,46],\"mFrame\":[921,426,49,46],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[184,129,363,72],\"mFrame\":[936,129,363,72],\"mt\":0}]},{\"i\":166,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[141,0,133,168],\"mFrame\":[893,0,133,168],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[586,314,124,115],\"mFrame\":[1338,314,124,115],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[25,332,72,64],\"mFrame\":[777,332,72,64],\"mt\":0}]},{\"i\":116,\"obj\":[{\"srcId\":\"1\",\"z\":1,\"frame\":[0,736,17,14],\"mFrame\":[752,736,17,14],\"mt\":0}]},{\"i\":180,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[305,240,93,95],\"mFrame\":[1057,240,93,95],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[548,398,64,61],\"mFrame\":[1300,398,64,61],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[175,431,48,46],\"mFrame\":[927,431,48,46],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[187,136,358,72],\"mFrame\":[939,136,358,72],\"mt\":0}]},{\"i\":181,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[310,251,91,92],\"mFrame\":[1062,251,91,92],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[544,403,63,59],\"mFrame\":[1296,403,63,59],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[181,437,47,44],\"mFrame\":[933,437,47,44],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[190,144,354,70],\"mFrame\":[942,144,354,70],\"mt\":0}]},{\"i\":167,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[160,13,132,171],\"mFrame\":[912,13,132,171],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[586,322,117,108],\"mFrame\":[1338,322,117,108],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[38,340,69,63],\"mFrame\":[790,340,69,63],\"mt\":0}]},{\"i\":168,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[178,37,129,162],\"mFrame\":[930,37,129,162],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[585,328,111,103],\"mFrame\":[1337,328,111,103],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[50,349,68,61],\"mFrame\":[802,349,68,61],\"mt\":0}]},{\"i\":202,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,351,68,68],\"mFrame\":[1100,351,68,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,493,37,37],\"mFrame\":[983,493,37,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[241,278,278,56],\"mFrame\":[993,278,278,56],\"mt\":0}]},{\"i\":203,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[347,352,69,67],\"mFrame\":[1099,352,69,67],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,493,37,37],\"mFrame\":[983,493,37,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[241,278,278,56],\"mFrame\":[993,278,278,56],\"mt\":0}]},{\"i\":204,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,351,68,68],\"mFrame\":[1100,351,68,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,493,37,37],\"mFrame\":[983,493,37,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[241,278,278,56],\"mFrame\":[993,278,278,56],\"mt\":0}]},{\"i\":205,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,352,69,67],\"mFrame\":[1100,352,69,67],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,493,37,37],\"mFrame\":[983,493,37,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[241,278,278,56],\"mFrame\":[993,278,278,56],\"mt\":0}]},{\"i\":206,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,351,68,68],\"mFrame\":[1100,351,68,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,493,37,37],\"mFrame\":[983,493,37,37],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[242,278,277,56],\"mFrame\":[994,278,277,56],\"mt\":0}]},{\"i\":207,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,351,68,68],\"mFrame\":[1100,351,68,68],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,494,37,36],\"mFrame\":[983,494,37,36],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[242,278,277,56],\"mFrame\":[994,278,277,56],\"mt\":0}]},{\"i\":208,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,352,68,67],\"mFrame\":[1100,352,68,67],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,494,37,35],\"mFrame\":[983,494,37,35],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[242,278,277,56],\"mFrame\":[994,278,277,56],\"mt\":0}]},{\"i\":209,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,352,68,67],\"mFrame\":[1100,352,68,67],\"mt\":0},{\"srcId\":\"1\",\"z\":1,\"frame\":[512,455,46,44],\"mFrame\":[1264,455,46,44],\"mt\":0},{\"srcId\":\"2\",\"z\":2,\"frame\":[231,494,37,35],\"mFrame\":[983,494,37,35],\"mt\":0},{\"srcId\":\"3\",\"z\":3,\"frame\":[242,278,277,56],\"mFrame\":[994,278,277,56],\"mt\":0}]}]}"
            //"{\"info\":{\"v\":2,\"f\":210,\"w\":750,\"h\":750,\"fps\":24,\"videoW\":1504,\"videoH\":752,\"aFrame\":[750,0,750,750],\"rgbFrame\":[0,0,750,750],\"isVapx\":1,\"orien\":0},\"src\":[{\"srcId\":\"0\",\"srcType\":\"img\",\"srcTag\":\"tag1\",\"loadType\":\"net\",\"fitType\":\"fitXY\",\"w\":133,\"h\":135}],\"frame\":[{\"i\":182,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[316,262,87,88],\"mFrame\":[1068,262,87,88],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":169,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[195,60,126,153],\"mFrame\":[947,60,126,153],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":195,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[347,345,69,68],\"mFrame\":[1099,345,69,68],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":157,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[0,0,14,18],\"mFrame\":[752,0,14,18],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":183,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[320,272,85,86],\"mFrame\":[1072,272,85,86],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":184,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[324,281,83,83],\"mFrame\":[1076,281,83,83],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":170,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[211,82,122,145],\"mFrame\":[963,82,122,145],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":196,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[347,348,69,67],\"mFrame\":[1099,348,69,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":158,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[0,0,50,34],\"mFrame\":[752,0,50,34],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":185,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[328,290,81,81],\"mFrame\":[1080,290,81,81],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":171,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[225,103,119,137],\"mFrame\":[977,103,119,137],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":197,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,350,68,67],\"mFrame\":[1100,350,68,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":159,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[0,0,86,50],\"mFrame\":[752,0,86,50],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":186,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[331,298,79,79],\"mFrame\":[1083,298,79,79],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":172,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[237,122,117,131],\"mFrame\":[989,122,117,131],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":187,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[334,305,77,77],\"mFrame\":[1086,305,77,77],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":160,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[15,0,106,66],\"mFrame\":[767,0,106,66],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":198,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,351,68,67],\"mFrame\":[1100,351,68,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":161,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[35,0,119,83],\"mFrame\":[787,0,119,83],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":199,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[348,352,68,67],\"mFrame\":[1100,352,68,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":200,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":188,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[336,312,76,76],\"mFrame\":[1088,312,76,76],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":173,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[249,140,113,125],\"mFrame\":[1001,140,113,125],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":162,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[55,0,128,100],\"mFrame\":[807,0,128,100],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":189,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[338,319,75,73],\"mFrame\":[1090,319,75,73],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":201,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":163,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[78,0,131,118],\"mFrame\":[830,0,131,118],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":174,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[260,157,109,120],\"mFrame\":[1012,157,109,120],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":190,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[340,324,74,73],\"mFrame\":[1092,324,74,73],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":202,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":203,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":164,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[100,0,133,135],\"mFrame\":[852,0,133,135],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":191,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[342,330,72,71],\"mFrame\":[1094,330,72,71],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":175,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[269,173,107,115],\"mFrame\":[1021,173,107,115],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":204,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":165,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[122,0,132,151],\"mFrame\":[874,0,132,151],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":192,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[343,334,72,71],\"mFrame\":[1095,334,72,71],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":176,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[278,188,104,110],\"mFrame\":[1030,188,104,110],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":205,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":193,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[345,338,70,70],\"mFrame\":[1097,338,70,70],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":177,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[286,203,100,105],\"mFrame\":[1038,203,100,105],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":166,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[142,0,132,168],\"mFrame\":[894,0,132,168],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":206,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":178,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[293,216,98,101],\"mFrame\":[1045,216,98,101],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":194,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[346,342,69,69],\"mFrame\":[1098,342,69,69],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":167,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[161,14,130,169],\"mFrame\":[913,14,130,169],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":207,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":179,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[300,229,95,97],\"mFrame\":[1052,229,95,97],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":208,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":168,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[179,38,128,161],\"mFrame\":[931,38,128,161],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":180,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[306,241,92,94],\"mFrame\":[1058,241,92,94],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":209,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[349,352,67,67],\"mFrame\":[1101,352,67,67],\"mt\":0,\"mAlpha\":0.0}]},{\"i\":181,\"obj\":[{\"srcId\":\"0\",\"z\":0,\"frame\":[311,252,90,91],\"mFrame\":[1063,252,90,91],\"mt\":0,\"mAlpha\":0.0}]}]}"
            //默认1:1的json配置
            // val json = "{\"info\":{\"v\":2,\"f\":210,\"w\":750,\"h\":750,\"fps\":24,\"videoW\":1504,\"videoH\":752,\"aFrame\":[750,0,750,750],\"rgbFrame\":[0,0,750,750],\"isVapx\":0,\"orien\":0}}"
            val jsonObj = JSONObject(json)
            config = AnimConfig()
            val parseResult = config.parse(jsonObj)
            parseConfig = if (parseResult) 0 else -1
            config.jsonConfig = jsonObj
        }
        ALog.d("dq-av", "config parseConfig=$parseConfig, config = $config")

        // 没有配置时，按1:1，原有的alpha视频处理
        if (parseConfig != 0) {
            animConfigManager.config?.isDefaultConfig = true
            animConfigManager.defaultConfig(750, 750)
        }

        scaleType?.let {
            alphaVideoView.setScaleType(it)
        }
        alphaVideoView.setAnimConfig(config)

        mediaPlayer.setLooping(dataSource.isLooping)
        mediaPlayer.setDataSource(dataPath)
        if (alphaVideoView.isSurfaceCreated()) {
            prepareAsync()
        } else {
            suspendDataSource = dataSource
        }
    }

    @WorkerThread
    private fun handleSuspendedEvent() {
        suspendDataSource?.let {
            setVideoFromFile(it)
        }
        suspendDataSource = null
    }


    @WorkerThread
    private fun prepareAsync() {
        mediaPlayer.let {
            if (playerState == PlayerState.NOT_PREPARED || playerState == PlayerState.STOPPED) {
                it.setOnPreparedListener(mPreparedListener)
                it.setOnErrorListener(mErrorListener)
                it.prepareAsync()
            }
        }
    }

    @WorkerThread
    private fun startPlay() {
        when (playerState) {
            PlayerState.PREPARED -> {
                mediaPlayer.start()
                isPlaying = true
                playerState = PlayerState.STARTED
                mainHandler.post {
                    mPlayerAction?.startAction()
                }
            }
            PlayerState.PAUSED -> {
                mediaPlayer.start()
                playerState = PlayerState.STARTED
            }
            PlayerState.NOT_PREPARED, PlayerState.STOPPED -> {
                try {
                    prepareAsync()
                } catch (e: Exception) {
                    e.printStackTrace()
                    monitor(false, errorInfo = "prepare and start MediaPlayer failure!")
                    emitEndSignal()
                }
            }
        }
    }

    @WorkerThread
    private fun parseVideoSize() {
        val videoInfo = mediaPlayer.getVideoInfo()
        alphaVideoView.measureInternal(
            (videoInfo.videoWidth / 2).toFloat(),
            videoInfo.videoHeight.toFloat()
        )

        val scaleType = alphaVideoView.getScaleType()
        mainHandler.post {
            mPlayerAction?.onVideoSizeChanged(
                videoInfo.videoWidth / 2,
                videoInfo.videoHeight,
                scaleType
            )
        }
    }

    override fun handleMessage(msg: Message?): Boolean {
        msg?.let {
            when (msg.what) {
                INIT_MEDIA_PLAYER -> {
                    initPlayer()
                }
                SURFACE_PREPARED -> {
                    val surface = msg.obj as Surface
                    mediaPlayer.setSurface(surface)
                    handleSuspendedEvent()
                }
                SET_DATA_SOURCE -> {
                    val dataSource = msg.obj as DataSource
                    setDataSource(dataSource)
                }
                START -> {
                    try {
                        parseVideoSize()
                        playerState = PlayerState.PREPARED
                        startPlay()
                    } catch (e: Exception) {
                        monitor(
                            false,
                            errorInfo = "start video failure: " + Log.getStackTraceString(e)
                        )
                        emitEndSignal()
                    }
                }
                PAUSE -> {
                    when (playerState) {
                        PlayerState.STARTED -> {
                            mediaPlayer.pause()
                            playerState = PlayerState.PAUSED
                        }
                        else -> {
                        }
                    }
                }
                RESUME -> {
                    if (isPlaying) {
                        startPlay()
                    } else {
                    }
                }
                STOP -> {
                    when (playerState) {
                        PlayerState.STARTED, PlayerState.PAUSED -> {
                            mediaPlayer.pause()
                            playerState = PlayerState.PAUSED
                        }
                        else -> {
                        }
                    }
                }
                DESTROY -> {
                    alphaVideoView.onPause()
                    if (playerState == PlayerState.STARTED) {
                        mediaPlayer.pause()
                        playerState = PlayerState.PAUSED
                    }
                    if (playerState == PlayerState.PAUSED) {
                        mediaPlayer.stop()
                        playerState = PlayerState.STOPPED
                    }
                    mediaPlayer.release()
                    alphaVideoView.release()
                    playerState = PlayerState.RELEASE

                    playThread?.let {
                        it.quit()
                        it.interrupt()
                    }
                }
                RESET -> {
                    mediaPlayer.reset()
                    playerState = PlayerState.NOT_PREPARED
                    isPlaying = false
                }
                else -> {
                }
            }
        }
        return true
    }

    private fun emitEndSignal() {
        isPlaying = false
        mainHandler.post {
            mPlayerAction?.endAction()
        }
    }

    private fun monitor(state: Boolean, what: Int = 0, extra: Int = 0, errorInfo: String) {
        mMonitor?.monitor(state, getPlayerType(), what, extra, errorInfo)
    }
}