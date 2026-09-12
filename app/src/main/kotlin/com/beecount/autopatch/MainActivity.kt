package com.beecount.autopatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton

/** 模块的简单界面：查看/复制/清空调试日志。 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 在 Android 15 起强制边到边，这里统一按边到边处理并自行消费系统栏 insets。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        applyInsets()
        findViewById<MaterialButton>(R.id.refresh).setOnClickListener { refresh() }
        findViewById<MaterialButton>(R.id.copy).setOnClickListener { copyLog() }
        findViewById<MaterialButton>(R.id.clear).setOnClickListener {
            LogStore.clear(this)
            refresh()
            toast(R.string.log_cleared)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** 状态栏高度补到顶栏，导航栏高度补到根布局，让顶栏底色铺满状态栏区域。 */
    private fun applyInsets() {
        val root = findViewById<View>(R.id.root)
        val toolbar = findViewById<View>(R.id.toolbar)
        val rootPadding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        val barPadding = intArrayOf(
            toolbar.paddingLeft,
            toolbar.paddingTop,
            toolbar.paddingRight,
            toolbar.paddingBottom,
        )
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = !dark

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(barPadding[0], barPadding[1] + bars.top, barPadding[2], barPadding[3])
            view.setPadding(
                rootPadding[0] + bars.left,
                rootPadding[1],
                rootPadding[2] + bars.right,
                rootPadding[3] + bars.bottom,
            )
            insets
        }
    }

    @Suppress("DEPRECATION")
    private fun refresh() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        findViewById<TextView>(R.id.version).text = getString(R.string.version_format, version)
        findViewById<TextView>(R.id.status).text = getString(R.string.path_format, LogStore.path(this))
        findViewById<TextView>(R.id.log).text =
            LogStore.read(this).ifEmpty { getString(R.string.log_empty) }
    }

    private fun copyLog() {
        val content = LogStore.read(this)
        if (content.isEmpty()) {
            toast(R.string.log_empty)
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("BeeCountAutoPatch", content))
        toast(R.string.log_copied)
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
