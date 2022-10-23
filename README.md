# AlphaPlayerPlus
一个有趣且很有创意的视频特效项目。

[https://github.com/duqian291902259/AlphaPlayerPlus](https://github.com/duqian291902259/AlphaPlayerPlus)

### 前言
直播产品，需要更多炫酷的礼物特效，比如飞机特效，跑车特效，生日蛋糕融特效等，融合了直播流画面的特效。所以在字节开源的alphaPlayer库和特效VAP库的基础上进行改造，实现融合特效渲染。
支持将用户的头像、昵称、当前直播间的直播视频流等元素动态融合进视频特效中，增强用户的交互感和体验感。

### Android实现

已经将原有的alphaPlayer进行升级改造，支持将用户的头像、昵称、当前直播间的直播视频流等元素动态融合进视频特效中，增强用户的交互感和体验感。

### 融合特效的技术原理
自研特效sdk，负责渲染视频特效。
美术负责制作特效视频，制作时将特效的颜色信息通过等分的视频区域存储，视频的左半边存放了特效原始的RGB信息，视频的右半边存放了特效的透明度和遮罩的透明度等信息。

#### 透明视频的渲染
特效渲染时，读取左半部分纹理的RGB值，作为原始视频的主体颜色，右半部分纹理的R通道值作为Alpha值，得到视频纹理的rgba值，进行着色渲染，得到带透明度的特效纹理，如下图所示：
![基于RGBA通道的透明视频融合特效渲染.png](https://upload-images.jianshu.io/upload_images/2001922-e7b284813145443f.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

#### 视频融合遮罩素材
通常特效中的融合区域，会随着场景的变换，产生近大远小的透视3D效果，而不是维持标准的矩形形状。在图形渲染技术中，对于渲染的顶点坐标组成非平行四边形形状，GPU默认对组成四边形的两个三角形分别采用线性纹理插值的方式，渲染的结果可以看到两个三角形虽然在接缝处是连续的，单其导数（切线和双线向量）在此处不连续，效果无法达到预期，如图2所示。为解决这一问题，需要使用投影插值的方式，将融合纹理渲染的2D顶点坐标，通过透视变换的到贴合特效的形状，从而模拟3D的视觉效果。可以将这一过程抽象成将一个矩形，经过透视变换，得到一个非平行四边形的过程。

将带透明度的特效纹理，与融合元素的纹理，根据资源右侧部分的G值进行融合渲染。融合渲染的RGB= (特效纹理RGB)*(1-右侧G)+(融合纹理RGB)*(右侧G)，融合渲染的Alpha值=(右侧R)*(1-右侧G)+(右侧G)*(右侧G)。得到融合后的特效纹理，最终渲染到设备屏幕上。

#### 融合特效的数据管理
原始视频与遮罩素材的融合过程以及而遮罩的渲染，是在原有右边灰色视频的基础上，采用G通道值表示遮罩的区域，后续遮罩渲染需要根据G通道的值作为融合的透明度。
因视频数据帧比较多，里面的遮罩位置、个数、文字内容、文字颜色、图片展示形式，千差万别，渲染时直接处理，有很多东西是程序未知的，所以需要对素材预处理，提前得到这些信息，生成一份渲染专用的json配置文件。这份文件要么放在视频中，要么服务器下发（目前我们这两种方式都会采用，遮罩相关的信息放视频中，与用户相关的信息接口提供）。

#### 1，预处理工具
负责获取特效渲染所需的资源数据和遮罩数据。遮罩素材，需要专门导出一个视频（右侧部分）用于识别遮罩的区域和色值。


#### 2，Mp4播放渲染。
其中对遮罩的识别、坐标位置的确定、变换矩阵的生成，由工具识别素材视频，最终导出json格式，写入进mp4容器的自定义box中。
我们端上需要解析出json，然后封装成对应的实体类，方便渲染时读取遮罩信息，并替换成实际的文字、图片、直播帧数据。

### 融合特效渲染步骤
1，原有的特效视频渲染，支持带alpha通道的mp4视频。我们可以得到视频纹理id：mVideoTextureId，包含左右灰色和透明区域。
2，使用FBO，对文字，图片，直播流的数据进行二次处理，确定渲染的顶点位置和纹理坐标，渲染成屏幕大小的纹理：fboTextureId
3，将mVideoTextureId和fboTextureId传入最终融合的着色器处理，得到最终的展示效果。
以上三个步骤，面涉及到很多细节，需要了解OpenGL的坐标体系，绘制步骤和基本原理。具体可以参看项目代码。


### 流程图
![融合渲染流程图.png](https://upload-images.jianshu.io/upload_images/2001922-37dee33d7ad7a8c1.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

### 实现的效果
![画中画效果.png](https://upload-images.jianshu.io/upload_images/2001922-58eff57ae48de0f1.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
![图片和文字渲染](https://upload-images.jianshu.io/upload_images/2001922-5e5e01c66f8178bf.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

### 接入说明
可以参看demo项目，暂未开放。demo只提供图片和文字融合效果的实现。
1，可以实现图片、视频流融合效果
2，图片+文字的融合效果
3，配置入口：PlayerController.get(configuration, mediaPlayer)
4，VideoGiftView，只是封装了PlayerController和播放容器。
5，音视频播放器可以自定义，也可以用现有的实现。AlphaMediaPlayer便于控制解码、渲染次数。
6，其他的播放器（ DefaultSystemPlayer、ExoPlayerImpl）播放的话，渲染遮罩的帧索引需要将json中的index-1，否则无法对齐原始素材效果
7，ALog：内部log输出接口，外部可以自行实现打印。
8，IFetchResource：资源接口：外部传入文本、图片等资源给遮罩帧使用。

### Android API说明
#### 初始化配置信息、PlayerController
```kotlin
val configuration = Configuration(context,owner)
        //  GLTextureView supports custom display layer, but GLSurfaceView has better performance, and the GLSurfaceView is default.
        configuration.alphaVideoViewType = AlphaVideoViewType.GL_SURFACE_VIEW
        // AlphaMediaPlayer便于控制解码、渲染次数，其他的播放器，渲染帧索引需要将json中的数字-1，否则无法对齐
        //val mediaPlayer = DefaultSystemPlayer()
        //val mediaPlayer = ExoPlayerImpl(context)
        val mediaPlayer = AlphaMediaPlayer(context)
        mPlayerController = PlayerController.get(configuration, mediaPlayer)
        .//设置监听
        mPlayerController?.let {
            it.setPlayerAction(playerAction)
            it.setFetchResource(fetchResource)
            //it.setMonitor(monitor)
        }
```
#### 设置流数据监听
```java
    mPlayerController.setOnRenderLiveFrameListener(() -> {
        //通知外部，有遮罩需要用到视频流画面，开始渲染直播画面，自定实现监听流，本demo无流数据来源，暂不提供实现。
        // 然后将流里面的直播画面的数据共享到surfaceTexture，通过以下调用传回给sdk内部渲染：
    });
 
```
#### IPlayerAction接口
```kotlin
private val playerAction = object : IPlayerAction {
        override fun onVideoSizeChanged(videoWidth: Int, videoHeight: Int, scaleType: ScaleType) {
            Log.i(TAG, "视频实际的宽高改变，用户改变渲染容器size：onVideoSizeChanged,videoWidth=$videoWidth, videoHeight=$videoHeight,scaleType=$scaleType")
        }
        override fun startAction() {
            Log.i(TAG, "开始播放")
        }
        override fun endAction(status: Int, mVideoPath: String?) {
            Log.i(TAG, "结束播放，错误码和出错的url，方便做重新播放逻辑")
        }
    }
```

#### 资源接口说明
```kotlin
interface IFetchResource {
    // 获取图片 (暂时不支持Bitmap.Config.ALPHA_8 主要是因为一些机型opengl兼容问题)
    fun fetchImage(resource: Resource, result: (Bitmap?) -> Unit)
    // 获取文字
    fun fetchText(resource: Resource, result: (String?) -> Unit)
    // 资源释放通知
    fun releaseResource(resources: List<Resource>)
}
```
#### 自定义Log
回调中使用自己的log实现
```kotlin
private fun initLog() {
        ALog.isDebug = BuildConfig.DEBUG
        ALog.log = ALogImp()

class ALogImp : IALog {
        override fun i(tag: String, msg: String) {
            Log.i(tag, msg)
        }
        override fun d(tag: String, msg: String) {
            Log.d(tag, msg)
        }
        override fun e(tag: String, msg: String) {
            Log.e(tag, msg)
        }
    }
```
### 测试demo，资源的路径
1，进入gift/demoRes/目录.
2，adb push demoRes /sdcard/alphaVideoGift/
3，build-->run.

### 注意事项：
demo的可以配置外部json
dataSource.configJsonPath = "/sdcard/alphaVideoGift/xxx.json"
也可以将json数据写入mp4视频，自定义一个box，播放视频前解析出json即可以不配置外部json

### 技术实现，部分代码
#### 1，顶点、纹理坐标array
```kotlin
 val array = FloatArray(8)
private var floatBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(array.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(array)
            
fun setVertexAttribPointer(attributeLocation: Int) {
        if (attributeLocation < 0) {
            EGLUtil.checkGlError("return setVertexAttribPointer $attributeLocation")
            return
        }
        floatBuffer.position(0)
        GLES20.glVertexAttribPointer(attributeLocation, 2, GLES20.GL_FLOAT, false, 0, floatBuffer)
        GLES20.glEnableVertexAttribArray(attributeLocation)
        EGLUtil.checkGlError("setVertexAttribPointer $attributeLocation")
    }

```

#### 2，设置坐标数组
```kotlin
  // 顶点坐标，遮罩渲染rect
        val rect = config.rgbPointRect
        vertexArray.setArray(
                VertexUtil.create(
                        config.renderWidth,
                        config.renderHeight,
                        rect,
                        vertexArray.array
                )
        )
        vertexArray.setVertexAttribPointer(shader.aPositionLocation)

```

#### 3，顶点坐标的转换
```kotlin
    fun create(width: Int, height: Int, rect: PointRect, array: FloatArray): FloatArray {
        // x0
        array[0] = switchX(rect.x.toFloat() / width)
        // y0
        array[1] = switchY(rect.y.toFloat() / height)
        // x1
        array[2] = switchX(rect.x.toFloat() / width)
        // y1
        array[3] = switchY((rect.y.toFloat() + rect.height) / height)
        // x2
        array[4] = switchX((rect.x.toFloat() + rect.width) / width)
        // y2
        array[5] = switchY(rect.y.toFloat() / height)
        // x3
        array[6] = switchX((rect.x.toFloat() + rect.width) / width)
        // y3
        array[7] = switchY((rect.y.toFloat() + rect.height) / height)
        return array
    }
    
    private fun switchX(x: Float): Float {
        return x * 2f - 1f
    }
    private fun switchY(y: Float): Float {
        return -y * 2f + 1 //((y * 2f - 2f) * -1f) - 1f
    }
```

#### 4，纹理坐标的转换
```kotlin
    fun create(width: Int, height: Int, rect: PointRect, array: FloatArray): FloatArray {
        // x0
        array[0] = rect.x.toFloat() / width
        // y0
        array[1] = rect.y.toFloat() / height
        // x1
        array[2] = rect.x.toFloat() / width
        // y1
        array[3] = (rect.y.toFloat() + rect.height) / height
        // x2
        array[4] = (rect.x.toFloat() + rect.width) / width
        // y2
        array[5] = rect.y.toFloat() / height
        // x3
        array[6] = (rect.x.toFloat() + rect.width) / width
        // y3
        array[7] = (rect.y.toFloat() + rect.height) / height
        return array
    }
```
### 部分融合的顶点、片元着色器
```kotlin
class FinalMixShader {
    companion object {
        private const val NO_POSITION = -1

        private const val VERTEX = """
                attribute vec4 a_Position;  
                attribute vec2 a_TexCoordinateSrc;
                attribute vec4 a_TexCoordinateRgb;
                attribute vec4 a_TexCoordinateAlpha;
                varying vec2 v_TexCoordinateSrc;
                varying vec2 v_TexCoordinateRgb;
                varying vec2 v_TexCoordinateAlpha;
                void main()
                {
                    v_TexCoordinateAlpha = vec2(a_TexCoordinateAlpha.x, a_TexCoordinateAlpha.y);
                    v_TexCoordinateRgb = vec2(a_TexCoordinateRgb.x, a_TexCoordinateRgb.y);
                    v_TexCoordinateSrc = a_TexCoordinateSrc;
                    gl_Position = a_Position;
                }
        """

        private const val FRAGMENT = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float; 
                uniform sampler2D u_TextureSrcUnit;
                uniform samplerExternalOES u_TextureVideoUnit;
                varying vec2 v_TexCoordinateSrc;
                varying vec2 v_TexCoordinateRgb;
                varying vec2 v_TexCoordinateAlpha;
                void main()
                {
                    vec4 alphaColor = texture2D(u_TextureVideoUnit, v_TexCoordinateAlpha);
                    vec4 rgbColor = texture2D(u_TextureVideoUnit, v_TexCoordinateRgb);
                    vec4 srcRgba = texture2D(u_TextureSrcUnit, v_TexCoordinateSrc);
                    if (srcRgba.rgb == vec3(0.0, 0.0, 0.0) || srcRgba.a == 0.0) {
                        gl_FragColor = vec4(rgbColor.rgb,alphaColor.r);
                    } else {
                         mediump float green = alphaColor.g;
                         mediump float percent = green;
                         gl_FragColor = vec4(mix(rgbColor.rgb, srcRgba.rgb, percent), mix(rgbColor.a, green, percent));
                    }
                }
        """
    }
}
```

### 部分OpenGL渲染
```kotlin
 // 传入遮罩纹理和视频纹理
        if (fboTextureId >= 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
            GLES20.glUniform1i(shader.uTextureSrcUnitLocation, 0)
        }
        if (mVideoTextureId >= 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(VideoRender.GL_TEXTURE_EXTERNAL_OES, mVideoTextureId)
            GLES20.glUniform1i(shader.uTextureMaskUnitLocation, 1)
        }

        EGLUtil.enableBlend()
        // draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGLUtil.checkGlError("final mix render")
```


### 其他说明
本文主要是Android端的实现，是技术预研阶段的设计思想和某些实现，实际应用中的会有更多拓展和优化。仅供学习交流。
早期sdk版本可参看：
[https://github.com/duqian291902259/AlphaPlayerPlus](https://github.com/duqian291902259/AlphaPlayerPlus)