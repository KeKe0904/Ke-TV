package com.tvtoolbox.screensaver

/**
 * GitHub 代理封装。
 *
 * 设计目的：
 * 即使是正常网络，也可能无法访问 GitHub（DNS 污染、IP 封锁、CDN 故障等）。
 * 默认所有 GitHub 域名的请求都走代理（[PROXY_PREFIX]），让用户在任何网络环境下
 * 都能完成更新检查 + APK 下载。
 *
 * 代理规则：把 GitHub URL 原样拼到代理前缀后面。
 * - `https://github.com/owner/repo/...` → `https://github.tbedu.top/https://github.com/owner/repo/...`
 * - `https://api.github.com/repos/...`   → `https://github.tbedu.top/https://api.github.com/repos/...`
 *
 * 这是常见的 GitHub 文件代理服务的 URL 规则（如 ghproxy、gh.api 等）。
 *
 * 非 GitHub URL 不做处理（避免误代理其他 CDN）。
 *
 * 注意：用户在设置中默认只有"代理下载"这一种方式，没有直连 GitHub 的开关。
 * 如果将来需要支持多代理或直连，可以扩展为 Prefs 配置。
 */
object GithubProxy {

    /**
     * GitHub 文件代理服务前缀。
     * 把原 GitHub URL 拼到这个前缀后面即得到代理 URL。
     */
    private const val PROXY_PREFIX = "https://github.tbedu.top/"

    /**
     * 把 GitHub URL 包装为代理 URL。
     * - 仅代理 `github.com` / `api.github.com` 的 https / http URL
     * - 已经走过代理的 URL 不会被重复包装
     * - 其他 URL 原样返回，避免误伤
     */
    fun wrap(url: String): String {
        if (url.isEmpty()) return url
        // 已经是代理 URL，不重复包装
        if (url.startsWith(PROXY_PREFIX)) return url
        // 仅代理 GitHub 域名（避免误代理其他 CDN）
        val isGithub = url.startsWith("https://github.com/") ||
            url.startsWith("http://github.com/") ||
            url.startsWith("https://api.github.com/") ||
            url.startsWith("http://api.github.com/") ||
            url.startsWith("https://raw.githubusercontent.com/") ||
            url.startsWith("http://raw.githubusercontent.com/") ||
            url.startsWith("https://objects.githubusercontent.com/") ||
            url.startsWith("http://objects.githubusercontent.com/")
        if (!isGithub) return url
        return PROXY_PREFIX + url
    }
}
