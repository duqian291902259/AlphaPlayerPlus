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
package com.ss.ugc.android.alpha_player.vap.plugin

import android.view.MotionEvent
import com.ss.ugc.android.alpha_player.vap.AnimConfig
import com.ss.ugc.android.alpha_player.vap.Constant
import com.ss.ugc.android.alpha_player.vap.mix.MixAnimPlugin
import com.ss.ugc.android.alpha_player.vap.util.ALog

/**
 * 动画插件管理
 */
class AnimPluginManager {
    companion object {
        private const val TAG = "${Constant.TAG}.AnimPluginManager"
        private const val DIFF_TIMES = 4
    }

    private val mixAnimPlugin = MixAnimPlugin()//player

    private val plugins = listOf<IAnimPlugin>(mixAnimPlugin)

    // 当前渲染的帧
    @Volatile
    private var frameIndex = 0
    private var needAdjust = true //粗暴调整一下帧索引

    // 当前解码的帧
    private var decodeIndex = 1

    // 帧不相同的次数, 连续多次不同则直接使用decodeIndex
    private var frameDiffTimes = 0

    fun getMixAnimPlugin(): MixAnimPlugin {
        return mixAnimPlugin
    }

    fun onConfigCreate(config: AnimConfig): Int {
        ALog.i(TAG, "onConfigCreate")
        plugins.forEach {
            val res = it.onConfigCreate(config)
            if (res != Constant.OK) {
                return res
            }
        }
        return Constant.OK
    }

    fun onRenderCreate(textureID: Int) {
        ALog.i(TAG, "onRenderCreate")
        frameIndex = 0
        plugins.forEach {
            it.onRenderCreate(textureID)
        }
    }

    fun onDecoding(decodeIndex: Int) {
        ALog.d(TAG, "onDecoding decodeIndex=$decodeIndex")
        this.decodeIndex = decodeIndex
        plugins.forEach {
            it.onDecoding(decodeIndex)
        }
    }

    var totalFrame = 0 //总的帧数

    fun onRendering() {
        /*if (decodeIndex > frameIndex + 1 || frameDiffTimes >= DIFF_TIMES) {
            ALog.i(
                TAG,
                "jump frameIndex= $frameIndex,decodeIndex=$decodeIndex,frameDiffTimes=$frameDiffTimes"
            )
            frameIndex = decodeIndex
        }
        if (decodeIndex != frameIndex) {
            frameDiffTimes++
        } else {
            frameDiffTimes = 0
        }*/
        if (frameIndex > totalFrame) {
            return
        }
        //ALog.d(TAG, "onRendering frameIndex=$frameIndex")
        plugins.forEach {
            it.onRendering(frameIndex) // 第一帧 0
        }
        if (frameIndex == 0 && needAdjust) {
            needAdjust = false
            return
        }
        frameIndex++
    }

    fun reset() {
        needAdjust = true
        frameIndex = 0
    }

    fun onRelease() {
        reset()
        ALog.i(TAG, "onRelease")
        plugins.forEach {
            it.onRelease()
        }
    }

    fun onDestroy() {
        reset()
        ALog.i(TAG, "onDestroy")
        plugins.forEach {
            it.onDestroy()
        }
    }

    fun onDispatchTouchEvent(ev: MotionEvent): Boolean {
        plugins.forEach {
            if (it.onDispatchTouchEvent(ev)) return true
        }
        return false
    }
}