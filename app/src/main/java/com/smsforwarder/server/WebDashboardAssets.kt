package com.smsforwarder.server

import android.content.Context
import android.util.Log

object WebDashboardAssets {
    private const val TAG = "WebDashboardAssets"

    @Volatile
    private var cachedHtml: String? = null

    fun getDashboardHtml(context: Context?): String {
        cachedHtml?.let { return it }

        if (context != null) {
            try {
                val html = context.assets.open("web/index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (html.isNotBlank()) {
                    cachedHtml = html
                    return html
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load web/index.html from assets, using fallback HTML", e)
            }
        }
        return FALLBACK_HTML
    }

    const val FALLBACK_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>SMS Forwarder PC Connect</title>
  <style>
    body { font-family: sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; text-align: center; }
    .card { background: #1e293b; padding: 24px; border-radius: 12px; max-width: 500px; margin: 40px auto; border: 1px solid #334155; }
    h1 { font-size: 1.4rem; margin-bottom: 12px; }
    p { color: #94a3b8; font-size: 0.9rem; margin-bottom: 20px; }
  </style>
</head>
<body>
  <div class="card">
    <h1>📱 Android SMS Forwarder - PC Connect</h1>
    <p>Connected to device. Open API endpoints at <code>/api/status</code> and <code>/api/messages</code>.</p>
  </div>
</body>
</html>"""
}
