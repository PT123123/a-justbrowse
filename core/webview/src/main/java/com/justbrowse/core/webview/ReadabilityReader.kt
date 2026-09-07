package com.justbrowse.core.webview

object ReadabilityReader {
    fun inject(target: com.justbrowse.core.scripts.ScriptInjectTarget) {
        val js = """
        (function(){
            if(document.getElementById('jb-reader'))return;
            var article=new Readability(document).parse();
            if(!article)return;
            var s=document.createElement('style');
            s.id='jb-reader-style';
            s.textContent='body{margin:0;padding:0;background:#f5f5f5!important;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}.jb-reader-container{max-width:680px;margin:0 auto;padding:24px;background:#fff;min-height:100vh;box-shadow:0 0 20px rgba(0,0,0,0.05)}.jb-reader-title{font-size:28px;font-weight:700;line-height:1.4;margin:0 0 16px;color:#1a1a1a}.jb-reader-meta{font-size:14px;color:#666;margin-bottom:24px;padding-bottom:16px;border-bottom:1px solid #eee}.jb-reader-content{font-size:18px;line-height:1.8;color:#333}.jb-reader-content p{margin:0 0 1.5em}.jb-reader-content img{max-width:100%;height:auto;border-radius:8px;margin:1em 0}.jb-reader-content a{color:#0066cc;text-decoration:none}.jb-reader-content blockquote{border-left:4px solid #ddd;margin:1em 0;padding-left:1em;color:#555}';
            document.head.appendChild(s);
            var d=document.createElement('div');
            d.id='jb-reader';
            d.className='jb-reader-container';
            d.innerHTML='<h1 class="jb-reader-title">'+article.title+'</h1><div class="jb-reader-meta">'+article.byline+' &middot; '+article.siteName+'</div><div class="jb-reader-content">'+article.content+'</div>';
            document.body.innerHTML='';
            document.body.appendChild(d);
        })();
        """;
        target.evaluateJavascript(js, null);
    }
}
