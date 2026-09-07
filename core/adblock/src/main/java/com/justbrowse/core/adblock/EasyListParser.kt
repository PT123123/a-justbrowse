package com.justbrowse.core.adblock

/**
 * EasyList / ABP 规则解析器。
 * 将文本规则行解析为 FilterRule 对象。
 */
object EasyListParser {

    fun parse(lines: String): List<FilterRule> {
        return lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseLine(it) }
            .toList()
    }

    fun parseLine(line: String): FilterRule? {
        // 注释
        if (line.startsWith("!") || line.startsWith("[")) return FilterRule.Noop

        // 元素隐藏规则: domain1,domain2##selector 或 domain1,domain2#?#selector
        val elemHideIdx = line.indexOf("##")
        if (elemHideIdx >= 0) {
            val domains = line.substring(0, elemHideIdx).split(",").filter { it.isNotEmpty() }
            val selector = line.substring(elemHideIdx + 2)
            if (selector.isNotEmpty()) {
                return FilterRule.ElementHidingRule(domains = domains, selector = selector)
            }
            return FilterRule.Noop
        }

        // 网络请求过滤规则
        val isException = line.startsWith("@@")
        val raw = if (isException) line.removePrefix("@@") else line

        // 分离选项部分 $...
        val optionIdx = raw.indexOf('$')
        val patternPart = if (optionIdx >= 0) raw.substring(0, optionIdx) else raw
        val optionPart = if (optionIdx >= 0) raw.substring(optionIdx + 1) else ""

        if (patternPart.isEmpty()) return FilterRule.Noop

        val options = parseOptions(optionPart)

        val rule = FilterRule.BlockRule(
            pattern = patternPart,
            isRegex = patternPart.startsWith("/") && patternPart.endsWith("/"),
            isDomainAnchor = patternPart.startsWith("||"),
            isStartAnchor = patternPart.startsWith("|") && !patternPart.startsWith("||"),
            isEndAnchor = patternPart.endsWith("|") && !patternPart.endsWith("||"),
            options = options,
            isException = isException
        )

        return if (isException) FilterRule.Exception(rule) else rule
    }

    private fun parseOptions(optionStr: String): RuleOptions {
        if (optionStr.isEmpty()) return RuleOptions()

        val parts = optionStr.split(",")
        val requestTypes = mutableSetOf<String>()
        var thirdParty = false
        val domains = mutableListOf<String>()
        var elemhide = false

        for (part in parts) {
            when {
                part == "third-party" -> thirdParty = true
                part == "elemhide" -> elemhide = true
                part.startsWith("domain=") -> {
                    // domain=example.com|~example.org
                    part.removePrefix("domain=").split("|").forEach { d ->
                        if (d.startsWith("~")) {
                            // 排除域名 — 简化处理：暂不支持
                        } else if (d.isNotEmpty()) {
                            domains.add(d)
                        }
                    }
                }
                part == "script" -> requestTypes.add("script")
                part == "image" -> requestTypes.add("image")
                part == "stylesheet" -> requestTypes.add("stylesheet")
                part == "subdocument" -> requestTypes.add("subdocument")
                part == "xmlhttprequest" -> requestTypes.add("xmlhttprequest")
                part == "media" -> requestTypes.add("media")
                part == "font" -> requestTypes.add("font")
                part == "websocket" -> requestTypes.add("websocket")
                part == "other" -> requestTypes.add("other")
                part.startsWith("~") -> {
                    // 排除类型 — 简化处理
                }
            }
        }

        return RuleOptions(
            requestTypes = requestTypes,
            thirdParty = thirdParty,
            domainRestrictions = domains,
            elemhide = elemhide
        )
    }
}
