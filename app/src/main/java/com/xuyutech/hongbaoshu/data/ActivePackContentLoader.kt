package com.xuyutech.hongbaoshu.data

import android.content.Context
import android.net.Uri
import com.xuyutech.hongbaoshu.core.AppLogger

class ActivePackContentLoader(
    private val builtinLoader: ContentLoader,
    private val packLoaderProvider: (String) -> ContentLoader
) : ContentLoader {
    private var activePackId: String = "builtin"
    private val packLoaders: MutableMap<String, ContentLoader> = mutableMapOf()

    fun setActivePackId(packId: String) {
        activePackId = packId
        AppLogger.d("ActivePackContentLoader", "activePackId=$packId")
    }

    fun currentPackId(): String = activePackId

    private fun loaderFor(packId: String): ContentLoader {
        return if (packId == "builtin") {
            builtinLoader
        } else {
            synchronized(packLoaders) {
                packLoaders.getOrPut(packId) { packLoaderProvider(packId) }
            }
        }
    }

    private fun currentLoader(): ContentLoader {
        val loader = loaderFor(activePackId)
        AppLogger.d("ActivePackContentLoader", "currentLoader packId=$activePackId loader=${loader.javaClass.simpleName}")
        return loader
    }

    override suspend fun loadBook(context: Context): BookLoadResult = currentLoader().loadBook(context)

    override fun narrationUri(sentenceId: String): Uri? = currentLoader().narrationUri(sentenceId)

    override fun flipSound(): Uri? = currentLoader().flipSound()

    override fun coverImage(): Uri? = currentLoader().coverImage()
}
