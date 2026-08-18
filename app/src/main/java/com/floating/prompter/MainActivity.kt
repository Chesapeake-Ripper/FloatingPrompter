package com.floating.prompter

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var etContent: EditText
    private lateinit var tvStatus: TextView
    private lateinit var sbAlpha: SeekBar
    private lateinit var tvAlphaLabel: TextView
    private lateinit var sbFontSize: SeekBar
    private lateinit var tvFontLabel: TextView

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            Toast.makeText(this, "悬浮窗权限已授予！", Toast.LENGTH_SHORT).show()
            startFloatingService()
        } else {
            Toast.makeText(this, "需要悬浮窗权限才能在顶层显示", Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "建议开启通知权限，以便保持后台常驻", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)

        initViews()
        initListeners()
        checkNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun initViews() {
        etContent = findViewById(R.id.et_prompt_content)
        tvStatus = findViewById(R.id.tv_service_status)
        sbAlpha = findViewById(R.id.sb_alpha)
        tvAlphaLabel = findViewById(R.id.tv_alpha_label)
        sbFontSize = findViewById(R.id.sb_font_size)
        tvFontLabel = findViewById(R.id.tv_font_label)

        // 载入保存的设置与文本
        etContent.setText(prefs.promptText)

        val initialAlphaProgress = (prefs.alpha * 100).toInt().coerceIn(20, 100)
        sbAlpha.progress = initialAlphaProgress
        tvAlphaLabel.text = "透明度: $initialAlphaProgress%"

        val initialFontProgress = (prefs.fontSize - 10).toInt().coerceIn(0, 18)
        sbFontSize.progress = initialFontProgress
        tvFontLabel.text = "字号: ${prefs.fontSize.toInt()}sp"
    }

    private fun initListeners() {
        val btnPaste = findViewById<Button>(R.id.btn_paste_clip)
        val btnClear = findViewById<Button>(R.id.btn_clear_text)
        val btnStart = findViewById<Button>(R.id.btn_start_service)
        val btnStop = findViewById<Button>(R.id.btn_stop_service)
        val btnBattery = findViewById<Button>(R.id.btn_battery_optimization)

        // 文本输入实时保存 & 实时同步到运行中的悬浮窗
        etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = s?.toString() ?: ""
                prefs.promptText = newText
                if (FloatingService.isRunning) {
                    val intent = Intent(this@MainActivity, FloatingService::class.java).apply {
                        action = FloatingService.ACTION_UPDATE_TEXT
                        putExtra(FloatingService.EXTRA_TEXT, newText)
                    }
                    startService(intent)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 粘贴剪贴板
        btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val textToPaste = clipData.getItemAt(0).text
                if (!textToPaste.isNullOrEmpty()) {
                    etContent.setText(textToPaste)
                    Toast.makeText(this, "已粘贴剪贴板内容", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        }

        // 清空内容
        btnClear.setOnClickListener {
            etContent.setText("")
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
        }

        // 透明度滑块
        sbAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(20)
                tvAlphaLabel.text = "透明度: $clamped%"
                val alphaFloat = clamped / 100f
                prefs.alpha = alphaFloat
                if (FloatingService.isRunning) {
                    val intent = Intent(this@MainActivity, FloatingService::class.java).apply {
                        action = FloatingService.ACTION_UPDATE_ALPHA
                        putExtra(FloatingService.EXTRA_ALPHA, alphaFloat)
                    }
                    startService(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 字号滑块
        sbFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fontSizeSp = (10 + progress).toFloat()
                tvFontLabel.text = "字号: ${fontSizeSp.toInt()}sp"
                prefs.fontSize = fontSizeSp
                if (FloatingService.isRunning) {
                    val intent = Intent(this@MainActivity, FloatingService::class.java).apply {
                        action = FloatingService.ACTION_UPDATE_FONT_SIZE
                        putExtra(FloatingService.EXTRA_FONT_SIZE, fontSizeSp)
                    }
                    startService(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 启动悬浮窗
        btnStart.setOnClickListener {
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
        }

        // 停止悬浮窗
        btnStop.setOnClickListener {
            val intent = Intent(this, FloatingService::class.java)
            stopService(intent)
            updateServiceStatus()
            Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
        }

        // 忽略电池优化请求
        btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, "请在设置中开启“允许显示在其他应用上层”", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "已处于无限制后台运行模式", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFloatingService() {
        val serviceIntent = Intent(this, FloatingService::class.java).apply {
            action = FloatingService.ACTION_START
            putExtra(FloatingService.EXTRA_TEXT, etContent.text.toString())
            putExtra(FloatingService.EXTRA_ALPHA, prefs.alpha)
            putExtra(FloatingService.EXTRA_FONT_SIZE, prefs.fontSize)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updateServiceStatus()
        Toast.makeText(this, "悬浮窗已开启！可以切换到学院软件", Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceStatus() {
        if (FloatingService.isRunning) {
            tvStatus.text = "状态: 运行中 (常驻)"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.secondary))
        } else {
            tvStatus.text = "状态: 未运行"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }
}
