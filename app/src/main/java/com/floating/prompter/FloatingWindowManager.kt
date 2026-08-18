package com.floating.prompter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.*
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

class FloatingWindowManager(
    private val context: Context,
    private val onCloseRequested: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = PreferencesManager(context)

    private var windowView: View? = null
    private var bubbleView: View? = null

    private var windowParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var currentFontSize = prefs.fontSize
    private var currentAlpha = prefs.alpha
    private var isLargeSize = prefs.isLargeSize
    private var isShowingBubble = false

    fun show() {
        if (prefs.isMinimized) {
            showBubble()
        } else {
            showWindow()
        }
    }

    fun updateText(text: String) {
        prefs.promptText = text
        windowView?.findViewById<TextView>(R.id.tv_floating_content)?.text = text
    }

    fun updateAlpha(alpha: Float) {
        currentAlpha = alpha
        prefs.alpha = alpha
        windowParams?.let {
            it.alpha = alpha
            if (windowView != null && !isShowingBubble) {
                windowManager.updateViewLayout(windowView, it)
            }
        }
    }

    fun updateFontSize(sizeSp: Float) {
        currentFontSize = sizeSp
        prefs.fontSize = sizeSp
        windowView?.findViewById<TextView>(R.id.tv_floating_content)?.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            currentFontSize
        )
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showWindow() {
        if (bubbleView != null) {
            windowManager.removeView(bubbleView)
            bubbleView = null
        }
        if (windowView != null) return

        isShowingBubble = false
        prefs.isMinimized = false

        val inflater = LayoutInflater.from(context)
        windowView = inflater.inflate(R.layout.layout_floating_window, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val widthPx = dpToPx(if (isLargeSize) 340 else 280)

        windowParams = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            // 核心参数：FLAG_NOT_FOCUSABLE 保证底层学习/考试软件不失去焦点，不触发切屏检测
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.posX
            y = prefs.posY
            alpha = currentAlpha
        }

        bindWindowViews(windowView!!)
        windowManager.addView(windowView, windowParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindWindowViews(view: View) {
        val tvContent = view.findViewById<TextView>(R.id.tv_floating_content)
        val scrollView = view.findViewById<ScrollView>(R.id.scroll_view_content)
        val dragHeader = view.findViewById<View>(R.id.layout_drag_header)
        val btnMinimize = view.findViewById<TextView>(R.id.btn_minimize)
        val btnClose = view.findViewById<TextView>(R.id.btn_close)
        val btnFontPlus = view.findViewById<TextView>(R.id.btn_font_plus)
        val btnFontMinus = view.findViewById<TextView>(R.id.btn_font_minus)
        val btnToggleAlpha = view.findViewById<TextView>(R.id.btn_toggle_alpha)
        val btnToggleSize = view.findViewById<TextView>(R.id.btn_toggle_size)
        val btnScrollTop = view.findViewById<TextView>(R.id.btn_scroll_top)
        val btnScrollBottom = view.findViewById<TextView>(R.id.btn_scroll_bottom)

        tvContent.text = prefs.promptText
        tvContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize)

        // 调整 ScrollView 高度
        val svParams = scrollView.layoutParams
        svParams.height = dpToPx(if (isLargeSize) 320 else 210)
        scrollView.layoutParams = svParams

        // 拖拽窗口
        setupDraggable(dragHeader, windowParams) { x, y ->
            prefs.posX = x
            prefs.posY = y
            windowView?.let { windowManager.updateViewLayout(it, windowParams) }
        }

        // 最小化为悬浮球
        btnMinimize.setOnClickListener {
            showBubble()
        }

        // 关闭悬浮窗
        btnClose.setOnClickListener {
            onCloseRequested()
        }

        // 字号调节
        btnFontPlus.setOnClickListener {
            if (currentFontSize < 26f) {
                currentFontSize += 1.5f
                updateFontSize(currentFontSize)
            }
        }
        btnFontMinus.setOnClickListener {
            if (currentFontSize > 10f) {
                currentFontSize -= 1.5f
                updateFontSize(currentFontSize)
            }
        }

        // 快速切换透明度 (高透 / 浅透 / 不透)
        btnToggleAlpha.setOnClickListener {
            val nextAlpha = when {
                currentAlpha >= 0.85f -> 0.45f
                currentAlpha >= 0.40f -> 0.70f
                else -> 0.95f
            }
            updateAlpha(nextAlpha)
        }

        // 切换窗口大小
        btnToggleSize.setOnClickListener {
            isLargeSize = !isLargeSize
            prefs.isLargeSize = isLargeSize
            val newWidth = dpToPx(if (isLargeSize) 340 else 280)
            val newHeight = dpToPx(if (isLargeSize) 320 else 210)

            windowParams?.width = newWidth
            svParams.height = newHeight
            scrollView.layoutParams = svParams

            btnToggleSize.text = if (isLargeSize) "标准窗" else "放大窗"
            windowView?.let { windowManager.updateViewLayout(it, windowParams) }
        }
        btnToggleSize.text = if (isLargeSize) "标准窗" else "放大窗"

        // 滚动到顶部与底部
        btnScrollTop.setOnClickListener {
            scrollView.fullScroll(ScrollView.FOCUS_UP)
        }
        btnScrollBottom.setOnClickListener {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showBubble() {
        if (windowView != null) {
            windowManager.removeView(windowView)
            windowView = null
        }
        if (bubbleView != null) return

        isShowingBubble = true
        prefs.isMinimized = true

        val inflater = LayoutInflater.from(context)
        bubbleView = inflater.inflate(R.layout.layout_floating_bubble, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            dpToPx(52),
            dpToPx(52),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.posX
            y = prefs.posY
        }

        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = bubbleParams!!.x
                    startY = bubbleParams!!.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        bubbleParams!!.x = startX + dx
                        bubbleParams!!.y = startY + dy
                        bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 点击悬浮球，展开为窗口
                        prefs.posX = bubbleParams!!.x
                        prefs.posY = bubbleParams!!.y
                        showWindow()
                    } else {
                        prefs.posX = bubbleParams!!.x
                        prefs.posY = bubbleParams!!.y
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggable(
        targetView: View,
        layoutParams: WindowManager.LayoutParams?,
        onPositionChanged: (Int, Int) -> Unit
    ) {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        targetView.setOnTouchListener { _, event ->
            if (layoutParams == null) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentX = startX + (event.rawX - touchStartX).toInt()
                    val currentY = startY + (event.rawY - touchStartY).toInt()
                    layoutParams.x = currentX
                    layoutParams.y = currentY
                    onPositionChanged(currentX, currentY)
                    true
                }
                else -> false
            }
        }
    }

    fun destroy() {
        if (windowView != null) {
            windowManager.removeView(windowView)
            windowView = null
        }
        if (bubbleView != null) {
            windowManager.removeView(bubbleView)
            bubbleView = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
