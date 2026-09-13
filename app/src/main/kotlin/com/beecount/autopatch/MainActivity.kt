package com.beecount.autopatch

import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton

/**
 * 模块界面：维护「蜜蜂记账分类名单」。
 *
 * 名单决定「分类在蜜蜂记账里找不到 → 归为其他」是否生效：
 * 文件写在自动记账的私有目录（hook 侧要读它），所以保存时可能要 root。
 *
 * 日志不再由本 App 读取：模块日志直接进 LSPosed 自带日志页。
 */
class MainActivity : AppCompatActivity() {

    /** 用户正在编辑名单时，刷新不要用文件内容覆盖输入框。 */
    private var categoriesDirty = false

    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 在 Android 15 起强制边到边，这里统一按边到边处理并自行消费系统栏 insets。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        applyInsets()
        findViewById<MaterialButton>(R.id.save_categories).setOnClickListener { saveCategories() }
        findViewById<MaterialButton>(R.id.clear_categories).setOnClickListener { clearCategories() }
        findViewById<EditText>(R.id.categories).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                categoriesDirty = true
            }
        })
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

    /** 读名单可能要起 root 进程，放到后台线程做，读完再回主线程刷新。 */
    private fun refresh() {
        if (loading) return
        loading = true
        findViewById<TextView>(R.id.version).text = getString(R.string.version_format, versionName())
        findViewById<TextView>(R.id.category_status).text = getString(R.string.category_status_loading)

        Thread {
            val categories = CategoryListFile.read()
            runOnUiThread {
                loading = false
                showCategories(categories)
            }
        }.start()
    }

    private fun showCategories(result: CategoryListFile.Result) {
        val count = CategoryStore.parse(result.text).size
        findViewById<TextView>(R.id.category_status).text = if (count > 0) {
            getString(R.string.category_status_ok, count, result.source)
        } else {
            getString(R.string.category_status_none)
        }
        if (!categoriesDirty) {
            findViewById<EditText>(R.id.categories).setText(result.text)
            categoriesDirty = false
        }
    }

    /** 名单要写进自动记账的私有目录，所以和 hook 侧一样可能走 root。 */
    private fun saveCategories() {
        val button = findViewById<MaterialButton>(R.id.save_categories)
        val text = findViewById<EditText>(R.id.categories).text.toString()
        button.isEnabled = false
        Thread {
            val saved = CategoryListFile.save(text)
            runOnUiThread {
                button.isEnabled = true
                if (saved) {
                    categoriesDirty = false
                    toast(if (text.isBlank()) R.string.category_saved_empty else R.string.category_saved)
                    refresh()
                } else {
                    toast(R.string.category_save_failed)
                }
            }
        }.start()
    }

    private fun clearCategories() {
        findViewById<EditText>(R.id.categories).setText("")
        categoriesDirty = true
        saveCategories()
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "?"

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}