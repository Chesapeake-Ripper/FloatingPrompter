package com.floating.prompter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("floating_prompter_prefs", Context.MODE_PRIVATE)

    var promptText: String
        get() = prefs.getString(KEY_PROMPT_TEXT, "欢迎使用悬浮提词助手！\n\n1. 支持长文本上下滑动翻阅。\n2. 拖拽顶部【⋮⋮】可任意移动位置。\n3. 点击【—】可最小化为悬浮球。\n4. 底层采用 FLAG_NOT_FOCUSABLE 技术，不会被判定为切屏。") ?: ""
        set(value) = prefs.edit().putString(KEY_PROMPT_TEXT, value).apply()

    var alpha: Float
        get() = prefs.getFloat(KEY_ALPHA, 0.90f)
        set(value) = prefs.edit().putFloat(KEY_ALPHA, value).apply()

    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 14f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value).apply()

    var posX: Int
        get() = prefs.getInt(KEY_POS_X, 80)
        set(value) = prefs.edit().putInt(KEY_POS_X, value).apply()

    var posY: Int
        get() = prefs.getInt(KEY_POS_Y, 200)
        set(value) = prefs.edit().putInt(KEY_POS_Y, value).apply()

    var isMinimized: Boolean
        get() = prefs.getBoolean(KEY_IS_MINIMIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_MINIMIZED, value).apply()

    var isLargeSize: Boolean
        get() = prefs.getBoolean(KEY_IS_LARGE_SIZE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LARGE_SIZE, value).apply()

    companion object {
        private const val KEY_PROMPT_TEXT = "key_prompt_text"
        private const val KEY_ALPHA = "key_alpha"
        private const val KEY_FONT_SIZE = "key_font_size"
        private const val KEY_POS_X = "key_pos_x"
        private const val KEY_POS_Y = "key_pos_y"
        private const val KEY_IS_MINIMIZED = "key_is_minimized"
        private const val KEY_IS_LARGE_SIZE = "key_is_large_size"
    }
}
