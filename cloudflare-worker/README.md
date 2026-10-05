# InkCast Cloudflare Worker Proxy

专为 **InkCast-Android (v2.0)** 墨水屏播客播放器设计的海外音频与 RSS 智能分流代理。

## 部署信息
- **Worker 名称**: `inkcast-proxy`
- **专属自定义域名**: **`https://podcast.yunet.cfd`**（免翻墙直连体验最佳）
- **备用 Workers.dev 域名**: `https://inkcast-proxy.harlan0804.workers.dev`
- **健康检查**: `https://podcast.yunet.cfd/health`

## 核心特性
1. **HTTP Range 断点续传与拖动支持**：ExoPlayer 在拖动快进、续播时依赖 `206 Partial Content` 与 `Range: bytes=start-end`，本 Worker 原生透传并返回 `Content-Range` 与 `Accept-Ranges: bytes`。
2. **零缓冲流式传输（Zero-buffer Streaming）**：直接返回 `Response(originResponse.body)`，不将数十 MB 的音频存入 Worker 内存，杜绝内存超限与延迟。
3. **CORS 全开**：支持 Android App 及各类 Web 端调试。
4. **防反爬 User-Agent 伪装**：自动注入合规客户端 UA，防止海外 CDN（NPR, Megaphone, Simplecast 等）拦截。

## 接口使用说明
- **音频/RSS 流代理**: 
  ```
  GET https://podcast.yunet.cfd/proxy/stream?url=<URL_ENCODED_TARGET>
  ```
  示例：
  ```bash
  curl -i "https://podcast.yunet.cfd/proxy/stream?url=https%3A%2F%2Ffeeds.npr.org%2F510318%2Fpodcast.xml"
  ```
- **Range 局部请求测试**:
  ```bash
  curl -i -r 0-1023 "https://podcast.yunet.cfd/proxy/stream?url=<MP3_URL>"
  ```
