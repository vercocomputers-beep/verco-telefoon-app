package org.verco.telefoon

import android.content.Context

/** Hoe een uitgaand gesprek vanuit de telefoon-contacten wordt afgehandeld. */
enum class CallMode { ASK, ZAAK, PRIVE }

object CallRouting {
    private const val KEY = "callMode"

    fun mode(ctx: Context): CallMode =
        runCatching {
            CallMode.valueOf(
                ctx.getSharedPreferences("verco", Context.MODE_PRIVATE).getString(KEY, CallMode.ASK.name)!!
            )
        }.getOrDefault(CallMode.ASK)

    fun setMode(ctx: Context, m: CallMode) {
        ctx.getSharedPreferences("verco", Context.MODE_PRIVATE).edit().putString(KEY, m.name).apply()
    }
}
