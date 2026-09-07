package com.justbrowse.core.sync

/**
 * Last-Write-Wins 合并算法。
 *
 * 对每个实体集合（书签/历史/脚本），按 ID 分组：
 * - 只存在于远端 → 新增到本地
 * - 只存在于本地 → 保留
 * - 两边都有 → 比较 updatedAt，取较新者
 *
 * 这样可以保证：
 * - 无冲突：每个实体独立决定
 * - 最终一致：所有设备收敛到相同状态
 * - 无密码传输：快照不含敏感信息
 */
object LwwMerge {

    data class MergeResult(
        val toAddLocally: List<BookmarkSync> = emptyList(),
        val toAddRemotely: List<BookmarkSync> = emptyList(),
        val toUpdateLocally: List<BookmarkSync> = emptyList(),
        val toUpdateRemotely: List<BookmarkSync> = emptyList(),
        val hasConflicts: Boolean = false
    )

    fun mergeBookmarks(local: List<BookmarkSync>, remote: List<BookmarkSync>): MergeResult {
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }

        val toAddLocally = mutableListOf<BookmarkSync>()
        val toAddRemotely = mutableListOf<BookmarkSync>()
        val toUpdateLocally = mutableListOf<BookmarkSync>()
        val toUpdateRemotely = mutableListOf<BookmarkSync>()

        // 处理远端条目
        for ((id, remoteItem) in remoteMap) {
            val localItem = localMap[id]
            if (localItem == null) {
                toAddLocally.add(remoteItem)
            } else if (remoteItem.updatedAt > localItem.updatedAt) {
                toUpdateLocally.add(remoteItem)
            } else if (localItem.updatedAt > remoteItem.updatedAt) {
                toAddRemotely.add(localItem)  // 本地更新，推送到远端
            }
        }

        // 处理本地独有的条目
        for ((id, localItem) in localMap) {
            if (!remoteMap.containsKey(id)) {
                toAddRemotely.add(localItem)
            }
        }

        return MergeResult(
            toAddLocally = toAddLocally,
            toAddRemotely = toAddRemotely,
            toUpdateLocally = toUpdateLocally,
            toUpdateRemotely = toUpdateRemotely,
            hasConflicts = false  // LWW 无冲突
        )
    }

    fun mergeHistory(local: List<HistorySync>, remote: List<HistorySync>): Pair<List<HistorySync>, List<HistorySync>> {
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }

        val toAddLocally = mutableListOf<HistorySync>()
        val toAddRemotely = mutableListOf<HistorySync>()

        for ((id, remoteItem) in remoteMap) {
            val localItem = localMap[id]
            if (localItem == null) {
                toAddLocally.add(remoteItem)
            } else if (remoteItem.visitedAt > localItem.visitedAt) {
                toAddLocally.add(remoteItem.copy(visitCount = maxOf(remoteItem.visitCount, localItem.visitCount)))
            }
        }

        for ((id, localItem) in localMap) {
            if (!remoteMap.containsKey(id)) {
                toAddRemotely.add(localItem)
            }
        }

        return toAddLocally to toAddRemotely
    }

    fun mergeScripts(local: List<ScriptSync>, remote: List<ScriptSync>): Pair<List<ScriptSync>, List<ScriptSync>> {
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }

        val toAddLocally = mutableListOf<ScriptSync>()
        val toAddRemotely = mutableListOf<ScriptSync>()

        for ((id, remoteItem) in remoteMap) {
            val localItem = localMap[id]
            if (localItem == null) {
                toAddLocally.add(remoteItem)
            } else if (remoteItem.updatedAt > localItem.updatedAt) {
                toAddLocally.add(remoteItem)
            }
        }

        for ((id, localItem) in localMap) {
            if (!remoteMap.containsKey(id)) {
                toAddRemotely.add(localItem)
            }
        }

        return toAddLocally to toAddRemotely
    }
}
