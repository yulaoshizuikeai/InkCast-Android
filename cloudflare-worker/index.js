export default {
  /**
   * InkCast-Android (v2.0) Cloudflare Worker Stream & Feed Proxy
   * 
   * Features:
   * 1. Streaming audio proxy supporting HTTP Range requests (crucial for ExoPlayer seek / chunk streaming)
   * 2. RSS XML feed proxying
   * 3. Zero-buffer stream pass-through (avoids CF worker memory limit on large MP3/M4A files)
   * 4. Full CORS headers with exposed Range headers
   * 5. User-Agent spoofing to bypass CDN bot protection
   */
  async fetch(request, env, ctx) {
    const reqUrl = new URL(request.url);

    // 1. CORS Preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
          "Access-Control-Allow-Headers": "Range, Content-Type, Authorization, User-Agent, Accept",
          "Access-Control-Expose-Headers": "Content-Length, Content-Range, Accept-Ranges, Content-Type, ETag",
          "Access-Control-Max-Age": "86400",
        },
      });
    }

    // 2. Health check
    if (reqUrl.pathname === "/health") {
      return new Response(JSON.stringify({ status: "ok", timestamp: Date.now() }), {
        status: 200,
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    // 3. Root endpoint info
    if (reqUrl.pathname === "/" && !reqUrl.searchParams.has("url")) {
      return new Response(
        JSON.stringify(
          {
            name: "InkCast Audio & Feed Proxy",
            version: "2.0.0",
            status: "online",
            description: "High-performance streaming proxy for InkCast-Android E-ink podcast player.",
            usage: {
              stream: "/proxy/stream?url=<encoded_audio_or_rss_url>",
              health: "/health"
            }
          },
          null,
          2
        ),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json; charset=utf-8",
            "Access-Control-Allow-Origin": "*",
          },
        }
      );
    }

    // 4. Extract target URL
    const targetUrl = reqUrl.searchParams.get("url");
    if (!targetUrl) {
      return new Response(
        JSON.stringify({
          error: "Missing 'url' query parameter",
          usage: `${reqUrl.origin}/proxy/stream?url=${encodeURIComponent("https://example.com/audio.mp3")}`
        }),
        {
          status: 400,
          headers: {
            "Content-Type": "application/json; charset=utf-8",
            "Access-Control-Allow-Origin": "*",
          },
        }
      );
    }

    try {
      // Validate protocol
      const parsedTarget = new URL(targetUrl);
      if (parsedTarget.protocol !== "http:" && parsedTarget.protocol !== "https:") {
        return new Response(
          JSON.stringify({ error: "Invalid protocol. Only http: and https: are supported." }),
          {
            status: 400,
            headers: {
              "Content-Type": "application/json; charset=utf-8",
              "Access-Control-Allow-Origin": "*",
            },
          }
        );
      }

      // Forward request headers
      const forwardHeaders = new Headers();

      // CRITICAL: Forward byte Range requests (essential for ExoPlayer / Media3 seeking)
      const range = request.headers.get("Range");
      if (range) {
        forwardHeaders.set("Range", range);
      }
      const ifRange = request.headers.get("If-Range");
      if (ifRange) {
        forwardHeaders.set("If-Range", ifRange);
      }

      // Realistic User-Agent to prevent CDN blocking
      const incomingUa = request.headers.get("User-Agent");
      if (incomingUa && !incomingUa.toLowerCase().includes("curl") && !incomingUa.toLowerCase().includes("python")) {
        forwardHeaders.set("User-Agent", incomingUa);
      } else {
        forwardHeaders.set("User-Agent", "InkCast/2.0 (Android; Linux; E-ink Native)");
      }

      const accept = request.headers.get("Accept");
      if (accept) {
        forwardHeaders.set("Accept", accept);
      }

      // Fetch upstream directly streaming body
      const originResponse = await fetch(targetUrl, {
        method: request.method,
        headers: forwardHeaders,
        redirect: "follow",
      });

      // Prepare response headers
      const respHeaders = new Headers();

      const copyHeaders = [
        "content-type",
        "content-length",
        "content-range",
        "accept-ranges",
        "last-modified",
        "etag",
        "cache-control",
        "content-disposition"
      ];

      for (const h of copyHeaders) {
        const val = originResponse.headers.get(h);
        if (val !== null) {
          respHeaders.set(h, val);
        }
      }

      // Ensure Accept-Ranges is advertised for audio seekability
      if (!respHeaders.has("accept-ranges")) {
        respHeaders.set("accept-ranges", "bytes");
      }

      // Set CORS
      respHeaders.set("Access-Control-Allow-Origin", "*");
      respHeaders.set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
      respHeaders.set(
        "Access-Control-Expose-Headers",
        "Content-Length, Content-Range, Accept-Ranges, Content-Type, ETag"
      );

      // Stream the response directly (zero-buffer memory safe)
      return new Response(originResponse.body, {
        status: originResponse.status,
        statusText: originResponse.statusText,
        headers: respHeaders,
      });
    } catch (err) {
      return new Response(
        JSON.stringify({
          error: "Upstream proxy request failed",
          message: err.message,
          targetUrl
        }),
        {
          status: 502,
          headers: {
            "Content-Type": "application/json; charset=utf-8",
            "Access-Control-Allow-Origin": "*",
          },
        }
      );
    }
  },
};
