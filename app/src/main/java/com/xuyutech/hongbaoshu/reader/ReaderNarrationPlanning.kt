package com.xuyutech.hongbaoshu.reader

internal data class NarrationPlayRequest(
    val sentenceIds: List<String>,
    val startIndex: Int
)

internal fun resolveNarrationPlayRequest(
    requestedSentenceId: String,
    currentPageSentenceIds: List<String>,
    overrideSentenceIds: List<String>? = null
): NarrationPlayRequest {
    val base = when {
        !overrideSentenceIds.isNullOrEmpty() -> overrideSentenceIds
        currentPageSentenceIds.isNotEmpty() -> currentPageSentenceIds
        else -> listOf(requestedSentenceId)
    }
    val sentenceIds = if (requestedSentenceId in base) base else listOf(requestedSentenceId)
    val startIndex = sentenceIds.indexOf(requestedSentenceId).coerceAtLeast(0)
    return NarrationPlayRequest(sentenceIds = sentenceIds, startIndex = startIndex)
}

internal fun pickAutoTurnSentenceToPlay(
    sentenceIds: List<String>,
    lastCompletedSentenceId: String?
): String? {
    val first = sentenceIds.firstOrNull() ?: return null
    if (first == lastCompletedSentenceId && sentenceIds.size > 1) return sentenceIds[1]
    return first
}

internal data class SentenceIdsUpdatePlan(
    val newState: ReaderState,
    val consumePendingRestart: Boolean,
    val clearManualPageTurn: Boolean
)

internal fun planSentenceIdsUpdate(
    current: ReaderState,
    sentenceIds: List<String>,
    pendingNarrationRestart: Boolean
): SentenceIdsUpdatePlan {
    if (current.currentPageSentenceIds == sentenceIds) {
        return SentenceIdsUpdatePlan(
            newState = current,
            consumePendingRestart = false,
            clearManualPageTurn = false
        )
    }

    var newState = current.copy(currentPageSentenceIds = sentenceIds)
    var consumePendingRestart = false
    var clearManualPageTurn = false

    if (pendingNarrationRestart) {
        consumePendingRestart = true
        clearManualPageTurn = true
        if (sentenceIds.isNotEmpty()) {
            newState = newState.copy(needPlayFirstSentence = true)
        }
    }

    return SentenceIdsUpdatePlan(
        newState = newState,
        consumePendingRestart = consumePendingRestart,
        clearManualPageTurn = clearManualPageTurn
    )
}

internal fun areAllChaptersCachedForFontSize(
    chapterIds: List<String>,
    fontSizeLevel: Int,
    widthPx: Int,
    heightPx: Int,
    pageCacheKeys: Set<String>
): Boolean {
    if (chapterIds.isEmpty()) return true
    return chapterIds.all { chapterId ->
        "${chapterId}_${fontSizeLevel}_${widthPx}_${heightPx}" in pageCacheKeys
    }
}
