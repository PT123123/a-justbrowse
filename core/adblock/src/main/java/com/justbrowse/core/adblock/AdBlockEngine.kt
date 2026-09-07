package com.justbrowse.core.adblock

import android.util.Log

/**
 * 广告拦截引擎：持有解析后的规则，提供 URL 拦截查询。
 *
 * 内部维护两个索引：
 * 1. 网络请求拦截规则（URL 匹配）
 * 2. 元素隐藏规则（CSS 注入）
 */
class AdBlockEngine(rules: List<FilterRule>) {

    private val blockRules: List<FilterRule.BlockRule> = rules
        .filterIsInstance<FilterRule.BlockRule>()

    private val exceptionRules: List<FilterRule.BlockRule> = rules
        .filterIsInstance<FilterRule.Exception>()
        .map { it.rule }

    private val elementHidingRules: List<FilterRule.ElementHidingRule> = rules
        .filterIsInstance<FilterRule.ElementHidingRule>()

    // 快速域名索引：domain -> 规则列表
    private val domainIndex: Map<String, List<FilterRule.BlockRule>> = buildMap {
        for (rule in blockRules) {
            extractDomains(rule.pattern).forEach { domain ->
                put(domain, getOrDefault(domain, emptyList()) + rule)
            }
        }
    }

    fun shouldBlock(url: String, pageDomain: String = ""): Boolean {
        val urlDomain = extractDomain(url)

        // 先检查例外规则
        for (rule in exceptionRules) {
            if (rule.matches(url, pageDomain)) return false
        }

        // 检查拦截规则
        for (rule in blockRules) {
            if (rule.matches(url, pageDomain)) {
                return true
            }
        }
        return false
    }

    /**
     * 获取用于当前域名的元素隐藏 CSS 规则。
     */
    fun getElementHidingCss(pageDomain: String): String {
        val sb = StringBuilder()
        for (rule in elementHidingRules) {
            val matches = rule.domains.isEmpty() || rule.domains.any { pageDomain.endsWith(it) }
            if (matches) {
                sb.append(rule.selector).append("{display:none!important;}\n")
            }
        }
        return sb.toString()
    }

    val ruleCount: Int get() = blockRules.size + exceptionRules.size + elementHidingRules.size

    private fun extractDomains(pattern: String): List<String> {
        // 从 ||example.com/... 或 $domain=example.com 提取域名
        val result = mutableListOf<String>()
        if (pattern.startsWith("||")) {
            val domain = pattern.removePrefix("||").substringBefore("/").substringBefore("^")
            if (domain.isNotEmpty() && !domain.contains("*")) {
                result.add(domain)
            }
        }
        return result
    }

    private fun extractDomain(url: String): String {
        return url.removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }

    companion object {
        private const val TAG = "AdBlockEngine"

        /** 从资源/文件加载规则并构建引擎 */
        fun fromRulesText(text: String): AdBlockEngine {
            val rules = EasyListParser.parse(text)
            val engine = AdBlockEngine(rules)
            Log.d(TAG, "Loaded ${engine.ruleCount} rules (${rules.count { it is FilterRule.BlockRule }} block, " +
                "${rules.count { it is FilterRule.Exception }} exception, " +
                "${rules.count { it is FilterRule.ElementHidingRule }} elemhide)")
            return engine
        }
    }
}
