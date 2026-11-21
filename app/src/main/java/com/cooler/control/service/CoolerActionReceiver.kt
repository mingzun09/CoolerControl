package com.cooler.control.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cooler.control.CoolerApp
import com.cooler.control.protocol.CoolerMode

/**
 * 通知栏 / 流体云胶囊上的按钮回调：按顺序切换散热模式。
 */
class CoolerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CYCLE_MODE) return
        val client = (context.applicationContext as CoolerApp).client
        val current = client.state.value.mode
        val order = CoolerMode.displayOrder
        val next = when {
            current == null -> order.first()
            else -> {
                val i = order.indexOf(current)
                order[(i + 1) % order.size]
            }
        }
        client.setMode(next)
    }

    companion object {
        const val ACTION_CYCLE_MODE = "com.cooler.control.action.CYCLE_MODE"
    }
}
