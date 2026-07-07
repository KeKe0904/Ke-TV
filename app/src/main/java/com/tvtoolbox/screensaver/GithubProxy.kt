package com.tvtoolbox.screensaver

/**
 * GitHub 代理封装。
 *
 * 设计目的：
 * 即使是正常网络，也可能无法访问 GitHub（DNS 污染、IP 封锁、CDN 故障等）。
 * 默认所有 GitHub 域名的请求都走代理，让用户在任何网络环境下
 * 都能完成更新检查 + APK 下载。
 *
 * 代理选择（已实测 2026-07-07）：
 * - 主代理 [PROXY_PRIMARY] = https://gh.llkk.cc/
 *   - 下载速度 ~5.5 MB/s（最快）
 *   - 支持 github.com / api.github.com / raw.githubusercontent.com
 *   - 返回的 APK 文件完整有效
 * - 备代理 [PROXY_FALLBACK] = https://ghfast.top/
 *   - 下载速度 ~1.1 MB/s（可用）
 *   - 支持 github.com / raw.githubusercontent.com（不支持 api.github.com）
 *
 * URL 规则：把 GitHub URL 原样拼到代理前缀后面。
 * - `https://github.com/owner/repo/...` → `https://gh.llkk.cc/https://github.com/owner/repo/...`
 * - `https://api.github.com/repos/...`   → `https://gh.llkk.cc/https://api.github.com/repos/...`
 *
 * 非 GitHub URL 不做处理（避免误代理其他 CDN）。
 *
 * 失败重试：调用方可调用 [wrapAll] 拿到主备两个 URL 依次尝试，
 * 一个失败再试下一个。直连作为最后兜底。
 */
object GithubProxy {

    /**
     * 主代理前缀（下载最快，API 也支持）。
     */
    private const val PROXY_PRIMARY = "https://gh.llkk.cc/"

    /**
     * 备用代理前缀（主代理失败时使用）。
     * 注意：此代理不支持 api.github.com，仅支持 github.com / raw。
     */
    private const val PROXY_FALLBACK = "https://ghfast.top/"

    /**
     * 把 GitHub URL 包装为代理 URL（用主代理）。
     * - 仅代理 `github.com` / `api.github.com` / `raw.githubusercontent.com` /
     *   `objects.githubusercontent.com` 的 https / http URL
     * - 已经走过代理的 URL 不会被重复包装
     * - 其他 URL 原样返回，避免误伤
     */
    fun wrap(url: String): String = wrapAll(url).first()

    /**
     * 返回该 URL 的所有候选代理 URL（主 + 备），用于失败重试。
     *
     * 顺序：
     * 1. 主代理（gh.llkk.cc）
     * 2. 备代理（ghfast.top）—— 注意备代理不支持 api.github.com
     * 3. 直连（最后兜底，万一用户网络能直连 GitHub）
     *
     * 调用方应依次尝试，直到成功或全部失败。
     */
    fun wrapAll(url: String): List<String> {
        if (url.isEmpty()) return listOf(url)
        // 已经是代理 URL，不重复包装
        if (url.startsWith(PROXY_PRIMARY) || url.startsWith(PROXY_FALLBACK)) return listOf(url)
        // 仅代理 GitHub 域名（避免误代理其他 CDN）
        val isGithub = url.startsWith("https://github.com/") ||
            url.startsWith("http://github.com/") ||
            url.startsWith("https://api.github.com/") ||
            url.startsWith("http://api.github.com/") ||
            url.startsWith("https://raw.githubusercontent.com/") ||
            url.startsWith("http://raw.githubusercontent.com/") ||
            url.startsWith("https://objects.githubusercontent.com/") ||
            url.startsWith("http://objects.githubusercontent.com/")
        if (!isGithub) return listOf(url)

        val result = mutableListOf<String>()
        result.add(PROXY_PRIMARY + url)
        // 备代理不支持 api.github.com，跳过避免误导
        if (!url.contains("api.github.com")) {
            result.add(PROXY_FALLBACK + url)
        }
        // 最后兜底：直连（万一用户网络能直连）
        result.add(url)
        return result
    }
}
