package com.justbrowse.core.adblock

/**
 * 内置默认广告过滤规则（EasyList 子集）。
 * 生产环境应从网络拉取完整 EasyList，这里提供基础兜底规则。
 */
object DefaultRules {

    val RULES = """
        ! JustBrowse Default Ad Block Rules
        ! 兜底规则 — 生产环境应从 easylist.to 拉取完整列表

        ! === 常见广告网络 ===
        ||doubleclick.net^
        ||googlesyndication.com^
        ||googleadservices.com^
        ||google-analytics.com^
        ||adservice.google.com^
        ||adnxs.com^
        ||adsrvr.org^
        ||adsymptotic.com^
        ||advertising.com^
        ||adform.net^
        ||adition.com|
        ||amazon-adsystem.com^
        ||adsystem.amazon.com^
        ||moatads.com^
        ||rubiconproject.com^
        ||openx.net^
        ||pubmatic.com^
        ||criteo.com|
        ||criteo.net^
        ||taboola.com^
        ||outbrain.com|
        ||revcontent.com|
        ||mgid.com|
        ||adroll.com|
        ||bidswitch.net^
        ||casalemedia.com^
        ||contextweb.com^
        ||dotomi.com|
        ||mathtag.com|
        ||media.net^
        ||quantserve.com|
        ||scorecardresearch.com|
        ||serving-sys.com|
        ||sharethrough.com|
        ||stickyadstv.com|
        ||triplelift.com|
        ||yieldmo.com|

        ! === 常见广告路径 ===
        /ads/
        /adsense.
        /adserver/
        /advert.
        /advertising/
        /banners/
        /popunder.
        /popup.
        /sponsor.

        ! === 常见广告文件 || 模式 ||
        ||*/ad.gif
        ||*/ads.js
        ||*/banner.gif
        ||*/banner.jpg
        ||*/pop.js
        ||*/sponsor.
        ||*/tracking.js
        ||*/analytics.js

        ! === 元素隐藏规则 ===
        ##.ad
        ##.ads
        ##.adsbygoogle
        ##.advert
        ##.advertisement
        ##.banner-ad
        ##.sponsored
        ##.sponsor
        ##[id^="ad-"]
        ##[id^="ads-"]
        ##[class^="ad-"]
        ##[class^="ads-"]
        ##[data-ad]
        div[data-ad-container]
        iframe[src*="ads"]
        iframe[src*="doubleclick"]

        ! === 例外规则 ===
        @@||google.com^${'$'}domain=google.com
        @@||youtube.com^${'$'}domain=youtube.com
    """.trimIndent()
}
