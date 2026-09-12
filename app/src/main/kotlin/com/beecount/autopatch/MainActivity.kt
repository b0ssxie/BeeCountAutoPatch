package com.beecount.autopatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** 模块的简单界面：查看/复制/清空调试日志。 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.refresh).setOnClickListener { refresh() }
        findViewById<Button>(R.id.copy).setOnClickListener { copyLog() }
        findViewById<Button>(R.id.clear).setOnClickListener {
            LogStore.clear(this)
            refresh()
            Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Suppress("DEPRECATION")
    private fun refresh() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        findViewById<TextView>(R.id.status).text =
            getString(R.string.status_format, version, LogStore.path(this))
        findViewById<TextView>(R.id.log).text =
            LogStore.read(this).ifEmpty { getString(R.string.log_empty) }
    }

    private fun copyLog() {
        val content = LogStore.read(this)
        if (content.isEmpty()) {
            Toast.makeText(this, R.string.log_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("BeeCountAutoPatch", content))
        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
    }
}
