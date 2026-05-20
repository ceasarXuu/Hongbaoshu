package com.xuyutech.hongbaoshu.reader

import androidx.compose.ui.text.TextMeasurer
import com.xuyutech.hongbaoshu.data.Book
import com.xuyutech.hongbaoshu.data.Chapter
import com.xuyutech.hongbaoshu.storage.PageCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ReaderPaginationController(
    private val packId: String,
    private val pageCacheStore: PageCacheStore,
    private val pageEngine: PageEngine,
    private val scope: CoroutineScope,
    private val bookProvider: () -> Book?
) {
    private val pageCache: MutableMap<String, List<Page>> = mutableMapOf()
    private var currentWidthPx: Int = 0
    private var currentHeightPx: Int = 0
    private var precomputeJob: Job? = null

    var isPrecomputing = false
        private set

    fun loadCachedPagesFromDisk(chapter: Chapter, fontSizeLevel: Int): List<Page>? {
        val key = cacheKey(chapter.id, fontSizeLevel, currentWidthPx, currentHeightPx)
        val cached = pageCache[key]
        if (cached != null) return cached

        val diskKey = diskCacheKey(fontSizeLevel, currentWidthPx, currentHeightPx)
        val diskCache = pageCacheStore.load(diskKey) ?: return null
        val pages = diskCache[chapter.id] ?: return null
        pageCache[key] = pages
        pageEngine.buildSentenceRanges(chapter)
        return pages
    }

    fun getCachedPages(chapterId: String, fontSizeLevel: Int): List<Page>? {
        val key = cacheKey(chapterId, fontSizeLevel, currentWidthPx, currentHeightPx)
        return pageCache[key]
    }

    fun cachePages(chapterId: String, fontSizeLevel: Int, pages: List<Page>) {
        val key = cacheKey(chapterId, fontSizeLevel, currentWidthPx, currentHeightPx)
        pageCache[key] = pages
    }

    fun updateScreenSize(widthPx: Int, heightPx: Int) {
        if (currentWidthPx != widthPx || currentHeightPx != heightPx) {
            currentWidthPx = widthPx
            currentHeightPx = heightPx
            pageCache.clear()
        }
    }

    fun computeCurrentChapter(
        currentChapterIndex: Int,
        textMeasurer: TextMeasurer,
        buildConfig: (Int) -> PageConfig,
        fontSizeLevel: Int
    ) {
        val book = bookProvider() ?: return
        val chapter = book.chapters.getOrNull(currentChapterIndex) ?: return
        val key = cacheKey(chapter.id, fontSizeLevel, currentWidthPx, currentHeightPx)
        if (pageCache[key] != null) return

        val diskKey = diskCacheKey(fontSizeLevel, currentWidthPx, currentHeightPx)
        val diskCache = pageCacheStore.load(diskKey)
        if (diskCache != null) {
            diskCache[chapter.id]?.let { pages ->
                pageCache[key] = pages
                pageEngine.buildSentenceRanges(chapter)
                return
            }
        }

        val config = buildConfig(fontSizeLevel)
        val pages = pageEngine.paginate(chapter, config, textMeasurer)
        pageCache[key] = pages
        pageEngine.buildSentenceRanges(chapter)
    }

    fun computeRemainingChapters(
        textMeasurer: TextMeasurer,
        buildConfig: (Int) -> PageConfig,
        fontSizeLevel: Int
    ) {
        val book = bookProvider() ?: return
        val chapterIds = book.chapters.map { it.id }
        if (areAllChaptersCachedForFontSize(
                chapterIds = chapterIds,
                fontSizeLevel = fontSizeLevel,
                widthPx = currentWidthPx,
                heightPx = currentHeightPx,
                pageCacheKeys = pageCache.keys
            )
        ) return

        val diskKey = diskCacheKey(fontSizeLevel, currentWidthPx, currentHeightPx)
        val diskCache = pageCacheStore.load(diskKey)
        hydrateFromDisk(book, fontSizeLevel, diskCache)
        if (areAllChaptersCachedForFontSize(
                chapterIds = chapterIds,
                fontSizeLevel = fontSizeLevel,
                widthPx = currentWidthPx,
                heightPx = currentHeightPx,
                pageCacheKeys = pageCache.keys
            )
        ) return

        val config = buildConfig(fontSizeLevel)
        val diskData = (diskCache ?: emptyMap()).toMutableMap()
        var hasNewData = false

        book.chapters.forEach { chapter ->
            val key = cacheKey(chapter.id, fontSizeLevel, currentWidthPx, currentHeightPx)
            if (pageCache[key] == null) {
                val pages = pageEngine.paginate(chapter, config, textMeasurer)
                pageCache[key] = pages
                diskData[chapter.id] = pages
                pageEngine.buildSentenceRanges(chapter)
                hasNewData = true
            }
        }

        if (hasNewData) {
            pageCacheStore.save(diskKey, diskData)
        }
    }

    fun computeAllChapters(
        currentChapterIndex: Int,
        textMeasurer: TextMeasurer,
        buildConfig: (Int) -> PageConfig,
        fontSizeLevel: Int
    ) {
        computeCurrentChapter(currentChapterIndex, textMeasurer, buildConfig, fontSizeLevel)
        computeRemainingChapters(textMeasurer, buildConfig, fontSizeLevel)
    }

    fun startPrecompute(
        currentChapterIndex: Int,
        currentFontSize: Int,
        textMeasurer: TextMeasurer,
        buildConfig: (Int) -> PageConfig
    ) {
        if (bookProvider() == null) return
        precomputeJob?.cancel()
        precomputeJob = scope.launch(Dispatchers.Default) {
            isPrecomputing = true
            try {
                computeAllChapters(currentChapterIndex, textMeasurer, buildConfig, currentFontSize)
            } finally {
                isPrecomputing = false
            }
        }
    }

    private fun hydrateFromDisk(
        book: Book,
        fontSizeLevel: Int,
        diskCache: Map<String, List<Page>>?
    ) {
        if (diskCache == null) return
        book.chapters.forEach { chapter ->
            val key = cacheKey(chapter.id, fontSizeLevel, currentWidthPx, currentHeightPx)
            if (pageCache[key] == null) {
                diskCache[chapter.id]?.let { pages ->
                    pageCache[key] = pages
                    pageEngine.buildSentenceRanges(chapter)
                }
            }
        }
    }

    private fun cacheKey(
        chapterId: String,
        fontSizeLevel: Int,
        widthPx: Int,
        heightPx: Int
    ): String = "${chapterId}_${fontSizeLevel}_${widthPx}_${heightPx}"

    private fun diskCacheKey(fontSizeLevel: Int, widthPx: Int, heightPx: Int): String {
        return "${packId}_${fontSizeLevel}_${widthPx}_${heightPx}"
    }
}
