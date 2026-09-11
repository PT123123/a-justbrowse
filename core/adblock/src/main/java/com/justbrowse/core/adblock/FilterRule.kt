package com.justbrowse.core.adblock

/**
 * 单条 ABP/EasyList 过滤规则。
 * 支持语法子集：
 * - 基本过滤: ||example.com/banner.gif
 * - 通配符路径: /ads/
 * - 域名锚定: ||example.com^
 * - 分隔符锚定: |http://
 * - 例外规则: @@||example.com^$elemhide
 * - 选项: $script, $image, $third-party, $domain=example.com
 * - 注释: ! comment
 * - 元素隐藏: example.com##.ad-banner
 */
sealed class FilterRule {
    data object Noop : FilterRule()  // 注释或空行
    data class Exception(val rule: BlockRule) : FilterRule()  // @@ 例外
    data class BlockRule(
        val pattern: String,
        val isRegex: Boolean = false,
        val isDomainAnchor: Boolean = false,   // ||
        val isStartAnchor: Boolean = false,     // |
        val isEndAnchor: Boolean = false,       // |
        val options: RuleOptions = RuleOptions(),
        val isException: Boolean = false
    ) : FilterRule() {

        /** 预编译正则（仅 isRegex 时非空），避免每次匹配重新编译 */
        private val compiledRegex: Regex? =
            if (isRegex) Regex(pattern, RegexOption.IGNORE_CASE) else null

        /** 判断此规则是否应该拦截给定的 URL */
        fun matches(url: String, pageDomain: String = "", requestType: String = "") : Boolean {
            // 类型检查
            if (options.requestTypes.isNotEmpty() && requestType.isNotEmpty()) {
                if (requestType !in options.requestTypes) return false
            }
            // third-party 检查
            if (options.thirdParty && pageDomain.isNotEmpty()) {
                if (url.contains(pageDomain)) return false
            }
            // 域名限制检查
            if (options.domainRestrictions.isNotEmpty()) {
                if (pageDomain.isNotEmpty() && pageDomain !in options.domainRestrictions) {
                    return false
                }
            }

            return when {
                isRegex -> compiledRegex?.containsMatchIn(url) == true
                isDomainAnchor -> {
                    // ||example.com/path 匹配任何协议+example.com+path
                    val cleanPattern = pattern.removePrefix("||")
                    val domainPart = cleanPattern.substringBefore('/')
                    val pathPart = cleanPattern.substringAfter('/', "")
                    val urlDomain = url.removePrefix("http://").removePrefix("https://").substringBefore("/")
                    val urlPath = url.removePrefix("http://").removePrefix("https://").substringAfter("/", "")
                    urlDomain == domainPart || urlDomain.endsWith(".$domainPart") &&
                        (pathPart.isEmpty() || urlPath.startsWith(pathPart))
                }
                isStartAnchor -> url.startsWith(pattern)
                isEndAnchor -> url.endsWith(pattern.removeSuffix("|"))
                else -> url.contains(pattern)
            }
        }
    }

    data class ElementHidingRule(
        val domains: List<String>,  // 空 = 全局
        val selector: String        // CSS 选择器
    ) : FilterRule()
}

data class RuleOptions(
    val requestTypes: Set<String> = emptySet(),
    val thirdParty: Boolean = false,
    val domainRestrictions: List<String> = emptyList(),
    val elemhide: Boolean = false
)
