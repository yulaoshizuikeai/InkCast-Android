# InkCast-Android 📻

[![Android CI/CD](https://github.com/yulaoshizuikeai/InkCast-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/yulaoshizuikeai/InkCast-Android/actions/workflows/android-ci.yml)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(E--Ink)-black)
![Media3](https://img.shields.io/badge/Audio-AndroidX%20Media3%201.4.1-blue)

**InkCast** 是一款专为**电子墨水屏（E-Ink）设备**深度定制的极简播客客户端。结合高对比度黑白界面与分页式交互，彻底消除 E-Ink 设备上的滚动残影与闪烁痛点，带来纯粹、省电、舒适的听觉阅读体验。

---

## ✨ 核心特性

- 📄 **墨水屏专属 UI**：纯粹黑白高对比度布局（无低对比灰阶与微动效），支持按页翻查列表，告别滑动残影与刷新卡顿。
- ⚡ **智能流媒体加速代理**：针对国内直连海外播客 CDN 慢的问题，深度整合 Cloudflare Worker 流代理（`podcast.yunet.cfd`），原生支持 HTTP Range 局部请求、毫秒级拖拽快进与断点续传。
- 🎵 **现代化音频中枢**：基于 **AndroidX Media3 (ExoPlayer + MediaSession)** 实现，支持后台播放、锁屏线控、倍速调节与定时停止。
- 🔍 **智能订阅解析**：内置轻量级 XML 解析与智能重试机制，支持直接输入播客名称、RSS 链接或音频直链。
- 🖼️ **轻量级封面缓存**：内存 LruCache + 二值化高反差渲染，兼顾美观与极低能耗。

---

## 🚀 云端构建流程 (CI / CD)

本项目配置了完整的 GitHub Actions 自动化持续集成与交付流水线，支持代码质量检测、单元测试、多渠道 APK 打包及版本发布。

### 1. 触发机制
| 触发事件 | 触发条件 | 执行任务 |
| :--- | :--- | :--- |
| **代码推送 (`Push`)** | 推送至 `main` 分支 | 运行单元测试、打包 Debug & Release APK 并上传工件 |
| **拉取请求 (`PR`)** | 目标为 `main` 分支 | 自动化代码校验与测试，保障分支质量 |
| **手动触发 (`workflow_dispatch`)** | GitHub Actions 页面手动点击 | 可自由选择构建目标（`all` / `debug` / `release`） |
| **版本发布 (`Tag`)** | 推送 `v*` 标签（如 `v2.0.0`） | 自动创建 GitHub Release 并附带各构型 APK 安装包 |

### 2. 下载构建产物 (APK)
1. 访问本仓库的 **[Actions](https://github.com/yulaoshizuikeai/InkCast-Android/actions)** 页面。
2. 点击最近一次成功的 Workflow 记录。
3. 在页面底部的 **Artifacts (工件)** 区域，即可直接下载打包好的 `InkCast-APKs.zip`（内含命名规范的 Debug 与 Release APK）。

### 3. 一键发布新版本 (GitHub Release)
本地打标签并推送后，云端流水线会自动完成构建并创建 Release：
```bash
git tag v2.0.0
git push origin v2.0.0
```

---

## 🛠️ 本地开发与构建

### 环境要求
- **JDK**: Java 17 (推荐 Temurin 17)
- **Gradle**: 8.13 (已随仓库自带 Gradle Wrapper)
- **Android SDK**: Compile SDK 35, Min SDK 26

### 常用命令
```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 编译生成 Debug 安装包
./gradlew assembleDebug

# 编译生成 Release 安装包
./gradlew assembleRelease
```
编译产物位于 `app/build/outputs/apk/` 目录下。

---

## 🌐 架构与配套服务

```
InkCast-Android (Compose + Media3)
       │
       ▼ (智能分流)
  ┌───────────────┴────────────────┐
  │ 国内源直连                     │ 海外源加速
  ▼                                ▼
各主流播客平台 CDN          Cloudflare Worker (`podcast.yunet.cfd`)
                                  │
                                  ▼
                        全球 Anycast 边缘流式代理
```

配套 Cloudflare Worker 代理代码位于 [`cloudflare-worker/`](file:///c:/Users/Yu_233/Documents/antigravity/cool-bohr/cloudflare-worker)。

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。
