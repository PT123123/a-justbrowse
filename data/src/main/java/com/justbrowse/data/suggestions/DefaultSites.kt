package com.justbrowse.data.suggestions

data class SuggestedSite(
    val title: String,
    val url: String,
    val icon: String = ""
)

object DefaultSites {
    val sites = listOf(
        SuggestedSite("百度", "https://www.baidu.com", ""),
        SuggestedSite("哔哩哔哩", "https://www.bilibili.com", ""),
        SuggestedSite("GitHub", "https://github.com", ""),
        SuggestedSite("知乎", "https://www.zhihu.com", ""),
        SuggestedSite("微博", "https://weibo.com", ""),
        SuggestedSite("淘宝", "https://www.taobao.com", ""),
        SuggestedSite("京东", "https://www.jd.com", ""),
        SuggestedSite("抖音", "https://www.douyin.com", ""),
        SuggestedSite("微信", "https://weixin.qq.com", ""),
        SuggestedSite("QQ", "https://www.qq.com", ""),
        SuggestedSite("网易", "https://www.163.com", ""),
        SuggestedSite("搜狐", "https://www.sohu.com", ""),
        SuggestedSite("新浪", "https://www.sina.com.cn", ""),
        SuggestedSite("豆瓣", "https://www.douban.com", ""),
        SuggestedSite("小红书", "https://www.xiaohongshu.com", ""),
        SuggestedSite("美团", "https://www.meituan.com", ""),
        SuggestedSite("滴滴", "https://www.didiglobal.com", ""),
        SuggestedSite("高德地图", "https://www.amap.com", ""),
        SuggestedSite("携程", "https://www.ctrip.com", ""),
        SuggestedSite("飞猪", "https://www.fliggy.com", ""),
        SuggestedSite("CSDN", "https://www.csdn.net", ""),
        SuggestedSite("掘金", "https://juejin.cn", ""),
        SuggestedSite("V2EX", "https://www.v2ex.com", ""),
        SuggestedSite("Stack Overflow", "https://stackoverflow.com", ""),
        SuggestedSite("YouTube", "https://www.youtube.com", ""),
        SuggestedSite("Twitter", "https://twitter.com", ""),
        SuggestedSite("Reddit", "https://www.reddit.com", ""),
        SuggestedSite("Wikipedia", "https://zh.wikipedia.org", ""),
        SuggestedSite("Google", "https://www.google.com", ""),
        SuggestedSite("Bing", "https://www.bing.com", "")
    )

    fun search(query: String, limit: Int = 5): List<SuggestedSite> {
        val q = query.lowercase()
        return sites.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }.take(limit)
    }
}
