package com.ss.ugc.android.alpha_player.controller

import android.view.View
import android.view.ViewGroup
import com.ss.ugc.android.alpha_player.IMonitor
import com.ss.ugc.android.alpha_player.IPlayerAction
import com.ss.ugc.android.alpha_player.model.DataSource
import com.ss.ugc.android.alpha_player.vap.inter.IFetchResource

/**
 * created by dengzhuoyao on 2020/07/07
 */
interface IPlayerController {

    fun start(dataSource: DataSource)

    fun pause()

    fun resume()

    fun stop()

    fun reset()

    fun release()

    fun setVisibility(visibility: Int)

    fun setPlayerAction(playerAction: IPlayerAction)

    fun setFetchResource(fetchResource: IFetchResource?)

    fun setMonitor(monitor: IMonitor)

    fun attachAlphaView(parentView: ViewGroup)

    fun detachAlphaView(parentView: ViewGroup)

    fun getView(): View

    fun getPlayerType(): String
}