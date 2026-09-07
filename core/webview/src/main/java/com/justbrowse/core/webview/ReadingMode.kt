package com.justbrowse.core.webview

import android.util.Log
import com.justbrowse.core.scripts.ScriptInjectTarget

/**
 * 阅读模式：通过 JS 注入提取正文内容并重新排版。
 * 使用 Mozilla Readability 算法的简化版。
 */
object ReadingMode {

    /**
     * 注入阅读模式脚本。先尝试提取正文，成功则替换页面内容。
     */
    fun inject(engine: ScriptInjectTarget, onActivated: () -> Unit) {
        val script = """
            (function() {
                // 简化版 Readability：选择最可能包含正文的容器
                function extractArticle() {
                    var candidates = [];
                    var paragraphs = document.querySelectorAll('article, [role="main"], .post-content, .article-content, .entry-content, main');
                    if (paragraphs.length > 0) {
                        return paragraphs[0].innerHTML;
                    }
                    // 启发式：找包含最多 <p> 的 div
                    var divs = document.querySelectorAll('div');
                    var best = null;
                    var bestScore = 0;
                    for (var i = 0; i < divs.length; i++) {
                        var pCount = divs[i].querySelectorAll('p').length;
                        var textLen = divs[i].innerText.length;
                        var score = pCount * 100 + textLen;
                        if (score > bestScore && textLen > 200) {
                            bestScore = score;
                            best = divs[i];
                        }
                    }
                    return best ? best.innerHTML : null;
                }

                var content = extractArticle();
                if (!content) {
                    GM_Bridge.log('ReadingMode: no article found');
                    return 'NO_CONTENT';
                }

                // 获取页面标题
                var title = document.title || '';
                var originUrl = window.location.href;

                // 替换页面
                document.documentElement.innerHTML = `
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>${'$'}{title}</title>
                    <style>
                        * { box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Georgia', serif;
                            font-size: 18px;
                            line-height: 1.7;
                            max-width: 680px;
                            margin: 0 auto;
                            padding: 20px;
                            background: #fefefe;
                            color: #333;
                        }
                        @media (prefers-color-scheme: dark) {
                            body { background: #1a1a1a; color: #e0e0e0; }
                            a { color: #5fa8ff; }
                            img { opacity: 0.9; }
                        }
                        img { max-width: 100%; height: auto; }
                        h1 { font-size: 1.6em; margin-top: 0; }
                        h2, h3 { font-size: 1.3em; }
                        p { margin: 1em 0; }
                        a { color: #1a73e8; text-decoration: none; }
                        pre, code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; }
                        blockquote { border-left: 3px solid #ccc; margin: 1em 0; padding-left: 1em; color: #666; }
                        .reading-mode-banner {
                            text-align: center;
                            font-size: 0.8em;
                            color: #999;
                            padding: 8px;
                            border-bottom: 1px solid #eee;
                            margin-bottom: 16px;
                        }
                    </style>
                </head>
                <body>
                    <div class="reading-mode-banner">
                        📖 Reading Mode · <a href="${'$'}{originUrl}">Original</a>
                    </div>
                    <article>${'$'}{content}</article>
                </body>
                </html>`;
                document.close();
                return 'OK';
            })();
        """.trimIndent()

        engine.evaluateJavascript(script) { result: String? ->
            Log.d("ReadingMode", "Result: $result")
            if (result?.contains("OK") == true) {
                onActivated()
            }
        }
    }
}
