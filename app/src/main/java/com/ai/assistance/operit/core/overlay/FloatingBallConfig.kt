package com.ai.assistance.operit.core.overlay

import android.content.Context
import android.content.SharedPreferences

/** 悬浮球持久化配置 */
object FloatingBallConfig {

    private const val PREF = "floating_ball_pref"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_SIZE = "size_dp"
    private const val KEY_KEEP_ALIVE = "keep_alive"

    const val MIN_SIZE_DP = 32f
    const val MAX_SIZE_DP = 96f
    const val DEFAULT_SIZE_DP = 48f

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun getX(context: Context): Int = prefs(context).getInt(KEY_X, 0)
    fun getY(context: Context): Int = prefs(context).getInt(KEY_Y, 300)
    fun savePosition(context: Context, x: Int, y: Int) =
        prefs(context).edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()

    fun getSizeDp(context: Context): Float = prefs(context).getFloat(KEY_SIZE, DEFAULT_SIZE_DP)
    fun saveSizeDp(context: Context, sizeDp: Float) = prefs(context)
        .edit()
        .putFloat(KEY_SIZE, sizeDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP))
        .apply()

    fun isKeepAlive(context: Context): Boolean = prefs(context).getBoolean(KEY_KEEP_ALIVE, true)
    fun setKeepAlive(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_KEEP_ALIVE, value).apply()
}
