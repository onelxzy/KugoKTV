# 酷唱 KTV (KugoKTV) - Android TV 电视端家庭点唱软件

酷唱 KTV 是一款专为智能电视和 Android 电视盒子（TV Box）打造的家庭 K 歌点唱客户端。项目基于 Kotlin + Jetpack Compose TV 开发，整合在线高清 MV 流媒体播放、局域网手机扫码无线点歌以及免登录本地实时 DSP 伴奏消音引擎。

---

## 📸 功能特性

* **🎬 超清 MV 检索与在线点唱**
  * 支持搜索歌曲名、歌手名或拼音缩写，支持热门推荐榜与歌手曲库浏览。
  * 自动解析并优先加载 1080P/720P 高清官方 MV 视频流，无 MV 时平滑切换至高保真音频模式。

* **🎛️ 本地实时 DSP 消音引擎与调音台**
  * **声学多频段分离**：采用二阶 IIR Butterworth 低通（<160Hz）与高通（>4200Hz）滤波，完整保留伴奏底鼓、贝斯与高频环境声场。
  * **人声共振峰陷波（Formant Notch Filter）**：针对 1500Hz 喉部发声频段进行衰减，抑制中心人声穿透力。
  * **声道模式兼容**：支持「智能立体声消音」、「左声道伴奏」以及「右声道伴奏」三种处理模式。
  * **图形化调音台**：提供消音深度、低音丰满度与伴奏音量补偿滑块微调，支持一键恢复最佳预设参数。

* **📱 局域网手机扫码无线点歌**
  * 电视端内置轻量级 Ktor Web 服务（默认监听 `19985` 端口）。
  * 手机与电视连接同一 Wi-Fi，扫描屏幕二维码即可打开手机点歌台 H5 页面，实现无线搜歌、点播排队、切歌、暂停与音量控制。

* **📋 已点队列与收藏夹管理**
  * 已点歌曲列表支持实时查看、一键置顶、立即播放以及移除操作。
  * 支持收藏常用歌曲，便于快速调取常唱曲目。

* **📺 电视大屏与遥控器交互适配**
  * 针对电视遥控器十字方向键（D-Pad）优化焦点流动与视觉反馈。
  * 搜索框同时支持屏幕虚拟全键盘、实体键盘输入与鼠标点击操作。

---

## 🏗️ 技术栈与依赖

| 模块 | 使用技术 |
| :--- | :--- |
| **语言与运行时** | Kotlin 1.9+ / Android SDK (minSdk 24, targetSdk 34) |
| **界面与交互** | Jetpack Compose for TV, Material 3 |
| **媒体播放引擎** | AndroidX Media3 (ExoPlayer), MergingMediaSource |
| **音频信号处理 (DSP)** | Android MediaCodec, Biquad IIR Filter, Soft-Saturation Limiter |
| **局域网点歌服务** | Ktor Server (CIO Engine), Web ContentNegotiation |
| **网络与数据处理** | OkHttp 4, Gson |
| **二维码生成** | ZXing Core |

---

## 🛠️ 编译与打包

### 本地编译
1. 克隆本项目：
   ```bash
   git clone https://github.com/onelxzy/KugoKTV.git
   ```
2. 使用最新版 **Android Studio** 打开 `KugoKTV` 根目录。
3. 等待 Gradle 同步完成后，连接 Android TV / 模拟器运行或执行打包：
   ```bash
   ./gradlew assembleRelease
   ```

### CI / CD 云端自动打包
项目已配置 GitHub Actions 工作流（`.github/workflows/android-build.yml`），每次向 `main` 分支提交代码均会自动触发打包，并在 Releases / Actions Artifacts 页面生成签名的 APK 安装包。

---

## 🤝 鸣谢

本项目在开发与协议分析过程中，参考并借鉴了以下优秀的开源项目与框架，特此致谢：

* **[EchoMusic](https://github.com/onelxzy/EchoMusic)**：提供了详尽的酷狗音乐与 MV 接口分析、加密签名机制与服务模型参考。
* **[AndroidX Media3 (ExoPlayer)](https://github.com/androidx/media)**：强大的 Android 现代流媒体与多音轨合并播放框架。
* **[Ktor](https://github.com/ktorio/ktor)**：轻量高效的 Kotlin 异步 Web 服务器引擎。
* **[ZXing](https://github.com/zxing/zxing)**：便捷的多平台二维码生成与编解码库。

---

## 📄 免责声明

1. 本项目仅供 Android TV 开发、音视频处理（DSP）以及 Jetpack Compose 架构的技术学习与交流使用。
2. 项目中调用的多媒体数据及音频/视频流均来自于网络第三方公开接口，相关音视频版权归原版权方所有。
3. 请勿将本项目用于任何商业营利用途。

---

## 📜 许可证 (License)

本项目基于 [MIT License](LICENSE) 开源。
