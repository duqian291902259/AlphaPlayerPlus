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

object ALog {

    var isDebug = true

    var log: IALog? = null

    fun i(tag: String, msg: String) {
        log?.i(tag, msg)
    }

    fun d(tag: String, msg: String) {
        if (isDebug) {
            log?.d(tag, msg)
        }
    }

    fun e(tag: String, msg: String) {
        log?.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        log?.e(tag, msg, tr)
    }
}

interface IALog {
    fun i(tag: String, msg: String) {}

    fun d(tag: String, msg: String) {}

    fun e(tag: String, msg: String) {}

    fun e(tag: String, msg: String, tr: Throwable) {}
}


