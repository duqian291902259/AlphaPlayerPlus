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
package com.ss.ugc.android.alpha_player.vap.util

import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import com.ss.ugc.android.alpha_player.vap.Constant
import com.ss.ugc.android.alpha_player.vap.FileContainer


object MediaUtil {

    private const val TAG = "${Constant.TAG}.MediaUtil"

    private var isTypeMapInit = false
    private val supportTypeMap = HashMap<String, Boolean>()

    val isDeviceSupportHevc by lazy {
        checkSupportCodec("video/hevc")
    }


    fun getExtractor(file: FileContainer): MediaExtractor {
        val extractor = MediaExtractor()
        file.setDataSource(extractor)
        return extractor
    }

    /**
     * 是否为h265的视频
     */
    fun checkIsHevc(videoFormat: MediaFormat):Boolean {
        val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: ""
        return mime.contains("hevc")
    }

    fun selectVideoTrack(extractor: MediaExtractor): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                ALog.i(TAG, "Extractor selected track $i ($mime): $format")
                return i
            }
        }
        return -1
    }

    fun selectAudioTrack(extractor: MediaExtractor): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                ALog.i(TAG, "Extractor selected track $i ($mime): $format")
                return i
            }
        }
        return -1
    }

    /**
     * 检查设备解码支持类型
     */
    fun checkSupportCodec(mimeType: String): Boolean {
        if (!isTypeMapInit) {
            isTypeMapInit = true
            getSupportType()
        }
        return supportTypeMap.containsKey(mimeType.toLowerCase())
    }


    private fun getSupportType() {
        try {
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) {
                    continue
                }
                val types = codecInfo.supportedTypes
                for (j in types.indices) {
                    supportTypeMap[types[j].toLowerCase()] = true
                }
            }
            ALog.i(TAG, "supportType=${supportTypeMap.keys}")
        } catch (t: Throwable) {
            ALog.e(TAG, "getSupportType $t")
        }
    }

}