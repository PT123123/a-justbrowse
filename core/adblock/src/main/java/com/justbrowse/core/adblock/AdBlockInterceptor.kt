package com.justbrowse.core.adblock

/**
 * 广告拦截器接口。
 * M3: 真实实现（EasyList 规则匹配 + CSS 注入）。
 */
interface AdBlockInterceptor {
    fun shouldBlock(url: String): Boolean
    fun shouldBlock(url: String, pageDomain: String, requestType: String): Boolean
    fun getElementHidingCss(pageDomain: String): String
    val ruleCount: Int
    val isEnabled: Boolean

    /** 运行时开关（设置页实时切换；默认空实现，供不可变实现复用） */
    fun setEnabled(enabled: Boolean) {}
}

/**
 * 真实广告拦截器实现。
 */
class EasyListAdBlockInterceptor(
    private val engine: AdBlockEngine,
    enabled: Boolean = true
) : AdBlockInterceptor {

    var enabled: Boolean = enabled
        private set

    override val ruleCount: Int get() = engine.ruleCount
    override val isEnabled: Boolean get() = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun shouldBlock(url: String): Boolean {
        if (!enabled) return false
        return engine.shouldBlock(url)
    }

    override fun shouldBlock(url: String, pageDomain: String, requestType: String): Boolean {
        if (!enabled) return false
        return engine.shouldBlock(url, pageDomain)
    }

    override fun getElementHidingCss(pageDomain: String): String {
        if (!enabled) return ""
        return engine.getElementHidingCss(pageDomain)
    }
}

/** M0 默认实现：不拦截任何请求。 */
class NoopAdBlockInterceptor : AdBlockInterceptor {
    override val ruleCount: Int = 0
    override val isEnabled: Boolean = false
    override fun shouldBlock(url: String): Boolean = false
    override fun shouldBlock(url: String, pageDomain: String, requestType: String): Boolean = false
    override fun getElementHidingCss(pageDomain: String): String = ""
}
