# Ke-TV · TV 工具箱

安卓 TV 自定义图床屏保 APP。装上电视后可以替换系统默认屏保，从你自己的图床接口拉取图片轮播显示。

## 功能

- 自定义图床 URL（返回 JSON 图片列表的接口）
- 图片轮播屏保，切换间隔可调（5 秒 ~ 5 分钟）
- Ken Burns 动态平移缩放效果
- 随机 / 顺序播放
- 测试图床连通性
- 一键跳转系统屏保设置
- 单图加载失败自动跳过

## 下载 APK

到 [Releases](../../releases) 页下载最新 `TVToolbox-v1.0.apk`，或直接下载仓库根目录下的 [`TVToolbox-v1.0.apk`](./TVToolbox-v1.0.apk)。

## 安装

1. 把 APK 拷到 U 盘，插到电视 USB 口，用电视自带的「文件管理」打开安装
   - 或用 `adb install TVToolbox-v1.0.apk`
2. 小米电视装完会提示「未知来源」，去 **设置 → 账号与安全 → 允许安装未知应用** 给文件管理器授权后重试

## 配置屏保

1. 在电视应用列表找到「**TV 工具箱**」打开
2. 进入「图床设置」→「图床 URL」，填入返回 JSON 图片列表的接口地址
3. 选「切换间隔」「随机顺序」「动态平移缩放」
4. 点「**测试图床连通性**」，会拉一次 JSON 并提示「成功：拉取到 N 张图片」
5. 点「**打开系统屏保设置**」→ 把屏保选成「**TV 工具箱屏保**」

## 图床 JSON 格式（4 种都自动识别）

**格式 1 — 字符串数组（最简单）：**
```json
["https://img.example.com/1.jpg", "https://img.example.com/2.jpg"]
```

**格式 2 — 对象数组带 url 字段：**
```json
[{"url": "https://..."}, {"url": "https://..."}]
```

**格式 3 — 包在 images 字段里：**
```json
{ "images": [{"url": "..."}, {"url": "..."}] }
```

**格式 4 — 兼容常见图床 API（data 字段，url/link/src 等字段名都行）：**
```json
{ "data": [{"link": "..."}, {"src": "..."}] }
```

字段名支持 `url` / `link` / `src` / `file` / `path` / `image_url` / `img` / `download_url` 等。

## 示例图床 JSON

参考 [`example-images.json`](./example-images.json)（用 Unsplash 风景图演示）。

## 技术栈

- Kotlin + Android Gradle Plugin 8.13.2
- minSdk 21 (Android 5.0)，targetSdk 34 (Android 14)
- DreamService（Android 标准屏保机制）
- Coil（图片加载）+ OkHttp（网络）+ Gson（JSON 解析）

## 从源码编译

```bash
# 需要 JDK 21、Android SDK 34、Gradle 8.14
cd TVToolbox
./gradlew assembleDebug
# 产物在 app/build/outputs/apk/debug/app-debug.apk
```

## 已知限制

- Debug 签名，每次更新若签名变了需先卸载旧版
- 小米电视的 MIUI 屏保是系统级的，本 APP 通过 Android 标准 DreamService 机制覆盖
- 如果某些 MIUI 版本上系统屏保设置被隐藏，可用「当贝市场」之类的工具强制调用屏保选择器

## License

MIT
