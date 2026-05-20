package com.xuyutech.hongbaoshu.data

import android.content.Context
import android.net.Uri
import com.xuyutech.hongbaoshu.core.AppLogger
import java.io.IOException
import kotlinx.serialization.json.Json

private const val TEXT_PATH = "text/text.json"
private const val NARRATION_DIR = "audio/narration"
private const val FLIP_SOUND_PATH = "sound/page_flip.wav.ogg"
private const val COVER_IMAGE_PATH = "images/cover.png"

class ContentLoaderImpl : ContentLoader {

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
        }
    }

    private var cachedResult: BookLoadResult? = null
    private val narrationMap: MutableMap<String, Uri> = mutableMapOf()
    private var flipUri: Uri? = null
    private var coverUri: Uri? = null

    override suspend fun loadBook(context: Context): BookLoadResult {
        cachedResult?.let { return it }
        val book = readBookJson(context)
        buildAudioMaps(context)
        val missing = collectMissingSentenceAudio(book)
        val result = BookLoadResult(book = book, missingSentenceAudioIds = missing)
        cachedResult = result
        return result
    }

    override fun narrationUri(sentenceId: String): Uri? {
        val uri = narrationMap[sentenceId]
        AppLogger.d("ContentLoader", "narrationUri: id=$sentenceId, uri=$uri, mapSize=${narrationMap.size}")
        return uri
    }

    override fun flipSound(): Uri? = flipUri

    override fun coverImage(): Uri? = coverUri

    private fun readBookJson(context: Context): Book {
        val text = context.assets.open(TEXT_PATH).use { it.reader(Charsets.UTF_8).readText() }
        val bookJson = json.decodeFromString(BookJson.serializer(), text)
        val book = Book.fromJson(bookJson)
        validateIds(book)
        return book
    }

    private fun buildAudioMaps(context: Context) {
        narrationMap.clear()
        coverUri = assetUri(COVER_IMAGE_PATH)

        // narration
        val narrationFiles = context.assets.list(NARRATION_DIR).orEmpty().sorted()
        narrationFiles.forEach { filename ->
            val prefix = filename.substringBefore('_').substringBeforeLast('.')
            if (prefix.isNotBlank()) {
                narrationMap[prefix] = assetUri("$NARRATION_DIR/$filename")
            }
        }
        // flip sound
        flipUri = assetUri(FLIP_SOUND_PATH)
    }

    private fun collectMissingSentenceAudio(book: Book): Set<String> {
        val missing = mutableSetOf<String>()
        book.chapters.forEach { chapter ->
            chapter.paragraphs.forEach { paragraph ->
                if (paragraph.type == ParagraphType.text) {
                    paragraph.sentences.forEach { sentence ->
                        if (!narrationMap.containsKey(sentence.id)) {
                            missing.add(sentence.id)
                        }
                    }
                }
            }
        }
        return missing
    }

    private fun assetUri(path: String): Uri =
        Uri.parse("file:///android_asset/$path")

    private fun validateIds(book: Book) {
        val chapters = mutableSetOf<String>()
        val paragraphs = mutableSetOf<String>()
        val sentences = mutableSetOf<String>()
        book.chapters.forEach { ch ->
            if (!chapters.add(ch.id)) error("Duplicate chapter id: ${ch.id}")
            ch.paragraphs.forEach { p ->
                if (!paragraphs.add(p.id)) error("Duplicate paragraph id: ${p.id}")
                p.sentences.forEach { s ->
                    if (!sentences.add(s.id)) error("Duplicate sentence id: ${s.id}")
                }
            }
        }
    }
}
