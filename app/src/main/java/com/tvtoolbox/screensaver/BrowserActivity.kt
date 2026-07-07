package com.tvtoolbox.screensaver

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

/**
 * 浏览器 Activity。
 *
 * 实现：
 * - 基于 Android 系统 WebView（底层是 Chrome / Chromium 内核）
 * - 起始页：本地 assets/browser_home.html，二次元风格 + ycy 图床背景 + 中央搜索框
 * - 地址栏：URL 直接跳转 / 关键词搜索（Bing）
 * - 后退 / 前进 / 刷新 / 停止 / 主页
 * - 加载进度条
 * - TV D-pad 焦点适配（按钮可被聚焦）
 * - 错误用 AppDialog 应用内置弹窗
 *
 * 设计参考：保持与 App 整体玻璃质感一致。
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnGo: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: MaterialToolbar

    /** 当前是否正在加载。true 时刷新按钮显示为停止。 */
    private var isLoading = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_browser)

        // 绑定 View
        toolbar = findViewById(R.id.toolbar)
        webView = findViewById(R.id.webView)
        etUrl = findViewById(R.id.etUrl)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnHome = findViewById(R.id.btnHome)
        btnGo = findViewById(R.id.btnGo)
        progressBar = findViewById(R.id.progressBar)

        // toolbar 顶部 padding（状态栏 inset）
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }
        // 自定义返回按钮（替代 toolbar 默认 navigationIcon）
        val btnToolbarBack = findViewById<android.widget.ImageButton>(R.id.btnToolbarBack)
        btnToolbarBack.setOnClickListener { finish() }
        FocusHelper.setupFocus(btnToolbarBack)

        // 配置 WebView
        configureWebView()

        // 按钮：TV 焦点导航 + 点击事件
        setupButtons()

        // 地址栏：回车触发跳转
        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                navigateFromAddressBar()
                true
            } else {
                false
            }
        }

        // 首次加载起始页（本地 HTML，二次元风格搜索起始页）
        if (savedInstanceState == null) {
            loadHomePage()
        } else {
            savedInstanceState.getBundle(KEY_STATE)?.let { webView.restoreState(it) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        // 保持 WebView 内 JS 可用，DOM storage 启用（多数网页必需）
        // 注意：这只是 WebView 客户端，不会引入新权限
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            blockNetworkImage = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            // User-Agent：保留默认（系统 WebView = Chrome 内核），不假装桌面浏览器
            // 但加上 mobile 标识让部分网站跳转移动版
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                setLoading(true)
                // 同步地址栏到当前 URL：
                // - 起始页（file:///android_asset/）：清空地址栏，让 hint 显示
                // - 其他：显示当前 URL
                if (url != null && !url.startsWith(HOME_PAGE_URL_PREFIX)) {
                    if (url != etUrl.text.toString()) {
                        etUrl.setText(url)
                    }
                } else {
                    etUrl.setText("")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                setLoading(false)
                if (url != null && !url.startsWith(HOME_PAGE_URL_PREFIX)) {
                    etUrl.setText(url)
                } else {
                    etUrl.setText("")
                }
                updateNavButtons()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // 默认在 WebView 内打开链接，不跳外部浏览器
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // 只关心主框架错误，子资源失败不影响页面
                if (request?.isForMainFrame == true && error != null) {
                    setLoading(false)
                    val msg = error.description?.toString() ?: "未知错误"
                    showAppMessage(
                        title = getString(R.string.browser_error_title),
                        message = getString(R.string.browser_error_message, msg)
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    toolbar.title = title
                }
            }
        }
    }

    private fun setupButtons() {
        // TV 焦点导航 + 手机按压反馈
        FocusHelper.setupFocusAll(btnBack, btnForward, btnRefresh, btnHome, btnGo)
        // 首次聚焦到地址栏，方便立刻输入
        etUrl.postDelayed({ etUrl.requestFocus() }, 200)

        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        btnForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        btnRefresh.setOnClickListener {
            if (isLoading) webView.stopLoading() else webView.reload()
        }
        btnHome.setOnClickListener {
            loadHomePage()
        }
        btnGo.setOnClickListener {
            navigateFromAddressBar()
        }
    }

    /** 切换"加载中"状态，刷新按钮显示为停止。 */
    private fun setLoading(loading: Boolean) {
        isLoading = loading
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnRefresh.contentDescription = getString(
            if (loading) R.string.browser_stop else R.string.browser_refresh
        )
    }

    /** 更新后退 / 前进按钮的可用状态。 */
    private fun updateNavButtons() {
        btnBack.isEnabled = webView.canGoBack()
        btnForward.isEnabled = webView.canGoForward()
        btnBack.alpha = if (btnBack.isEnabled) 1.0f else 0.4f
        btnForward.alpha = if (btnForward.isEnabled) 1.0f else 0.4f
    }

    /** 加载浏览器起始页（本地二次元风格搜索起始页）。 */
    private fun loadHomePage() {
        webView.loadUrl(HOME_PAGE_URL)
        etUrl.setText("")
        toolbar.title = getString(R.string.browser_title)
    }

    /** 解析地址栏输入并加载：URL 直接跳转 / 关键词走搜索引擎。 */
    private fun navigateFromAddressBar() {
        val input = etUrl.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) return
        loadUrl(input)
    }

    /**
     * 加载 URL 或搜索关键词。
     *
     * 判定逻辑：
     * - 包含空格 → 视为搜索关键词
     * - 看起来像 URL（含 . 且无空格）→ 补 https:// 直接加载
     * - 否则视为搜索关键词
     */
    private fun loadUrl(input: String) {
        val target = when {
            // 已是完整 URL
            input.startsWith("http://") || input.startsWith("https://") -> input

            // about: 等内部协议直接传给 WebView
            input.startsWith("about:") -> input

            // 包含空格 → 一定是搜索
            input.contains(" ") -> buildSearchUrl(input)

            // 看起来像 URL：含 . 且不含空格，且不是单个普通词
            looksLikeUrl(input) -> "https://$input"

            // 否则按搜索处理
            else -> buildSearchUrl(input)
        }
        webView.loadUrl(target)
        etUrl.setText(target)
    }

    /** 简单 URL 启发式：包含 . 且 TLD 部分是字母（不包含 / 后缀也允许）。 */
    private fun looksLikeUrl(s: String): Boolean {
        if (s.contains(' ')) return false
        // localhost / IP
        if (s == "localhost" || s.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?"))) return true
        // 域名：xxx.yyy 至少一个点，TLD 字母 ≥2
        val idx = s.indexOf('.')
        if (idx < 0 || idx == s.length - 1) return false
        // 取 host 部分（去掉 path / query）
        val host = s.substringBefore('/').substringBefore('?')
        val dotIdx = host.indexOf('.')
        if (dotIdx < 0) return false
        val tail = host.substring(dotIdx + 1)
        // TLD 必须是字母（避免 192.168.1.1 等已被前一条命中）
        return tail.matches(Regex("[A-Za-z]{2,}(:\\d+)?"))
    }

    /** 构造搜索 URL（默认 Bing：全球可用、国内可达）。 */
    private fun buildSearchUrl(query: String): String {
        // 没有指定搜索引擎时可切换：当前用 Bing
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return "https://www.bing.com/search?q=$encoded"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // TV 遥控器 / 物理键盘：按 Back 时优先让 WebView 回退一页
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保留 WebView 状态，避免旋转 / 切后台后丢页面
        val state = Bundle()
        webView.saveState(state)
        outState.putBundle(KEY_STATE, state)
    }

    override fun onDestroy() {
        // 主动销毁 WebView，避免泄漏
        webView.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    private companion object {
        private const val KEY_STATE = "webview_state"
        /** 起始页 URL：本地 assets 中的 HTML（二次元风格 + ycy 图床背景）。 */
        private const val HOME_PAGE_URL = "file:///android_asset/browser_home.html"
        /** 用于识别"当前是否在起始页"的前缀。 */
        private const val HOME_PAGE_URL_PREFIX = "file:///android_asset/browser_home"
    }
}
