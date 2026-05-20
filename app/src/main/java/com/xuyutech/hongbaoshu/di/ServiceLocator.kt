package com.xuyutech.hongbaoshu.di

import android.content.Context
import com.xuyutech.hongbaoshu.audio.AudioManager
import com.xuyutech.hongbaoshu.audio.AudioManagerImpl
import com.xuyutech.hongbaoshu.data.ActivePackContentLoader
import com.xuyutech.hongbaoshu.data.ContentLoader
import com.xuyutech.hongbaoshu.data.ContentLoaderImpl
import com.xuyutech.hongbaoshu.pack.loader.FilePackContentLoader
import com.xuyutech.hongbaoshu.pack.index.PackIndexStore
import com.xuyutech.hongbaoshu.pack.importer.ZipPackImporter
import com.xuyutech.hongbaoshu.pack.repository.AndroidPackRepository
import com.xuyutech.hongbaoshu.pack.repository.PackRepository
import com.xuyutech.hongbaoshu.pack.storage.PackFileStore
import com.xuyutech.hongbaoshu.storage.PageCacheStore
import com.xuyutech.hongbaoshu.storage.ProgressStore

/**
 * Lightweight service locator to wire core singletons.
 * Replace with DI framework (e.g., Hilt) later if需要。
 */
object ServiceLocator {
    @Volatile
    private var activePackLoader: ActivePackContentLoader? = null

    @Volatile
    private var audio: AudioManager? = null

    @android.annotation.SuppressLint("StaticFieldLeak") // Holds ApplicationContext, safe
    @Volatile
    private var progressStore: ProgressStore? = null
    
    @android.annotation.SuppressLint("StaticFieldLeak") // Holds ApplicationContext, safe
    @Volatile
    private var pageCacheStore: PageCacheStore? = null

    @android.annotation.SuppressLint("StaticFieldLeak") // Holds ApplicationContext, safe
    @Volatile
    private var packIndexStore: PackIndexStore? = null

    @android.annotation.SuppressLint("StaticFieldLeak") // Holds ApplicationContext, safe
    @Volatile
    private var packFileStore: PackFileStore? = null

    @android.annotation.SuppressLint("StaticFieldLeak") // Holds ApplicationContext, safe
    @Volatile
    private var packImporter: ZipPackImporter? = null

    @android.annotation.SuppressLint("StaticFieldLeak") // Holds ApplicationContext through stores, safe
    @Volatile
    private var packRepository: PackRepository? = null

    fun provideActivePackContentLoader(context: Context): ActivePackContentLoader =
        activePackLoader ?: synchronized(this) {
            activePackLoader ?: ActivePackContentLoader(
                builtinLoader = ContentLoaderImpl(),
                packLoaderProvider = { packId ->
                    FilePackContentLoader(providePackFileStore(context).packDir(packId))
                }
            ).also { activePackLoader = it }
        }

    fun provideContentLoader(context: Context): ContentLoader = provideActivePackContentLoader(context)

    fun provideAudioManager(context: Context): AudioManager =
        audio ?: synchronized(this) {
            audio ?: AudioManagerImpl(context.applicationContext, provideContentLoader(context)).also {
                audio = it
            }
        }

    fun provideProgressStore(context: Context): ProgressStore =
        progressStore ?: synchronized(this) {
            progressStore ?: ProgressStore(context.applicationContext).also { progressStore = it }
        }
    
    fun providePageCacheStore(context: Context): PageCacheStore =
        pageCacheStore ?: synchronized(this) {
            pageCacheStore ?: PageCacheStore(context.applicationContext).also { pageCacheStore = it }
        }

    fun providePackIndexStore(context: Context): PackIndexStore =
        packIndexStore ?: synchronized(this) {
            packIndexStore ?: PackIndexStore(context.applicationContext).also { packIndexStore = it }
        }

    fun providePackFileStore(context: Context): PackFileStore =
        packFileStore ?: synchronized(this) {
            packFileStore ?: PackFileStore(context.applicationContext).also { packFileStore = it }
        }

    fun providePackImporter(context: Context): ZipPackImporter =
        packImporter ?: synchronized(this) {
            packImporter ?: ZipPackImporter(
                context = context.applicationContext,
                packIndexStore = providePackIndexStore(context),
                packFileStore = providePackFileStore(context)
            ).also { packImporter = it }
        }

    fun providePackRepository(context: Context): PackRepository =
        packRepository ?: synchronized(this) {
            packRepository ?: AndroidPackRepository(
                packIndexStore = providePackIndexStore(context),
                packFileStore = providePackFileStore(context),
                packImporter = providePackImporter(context)
            ).also { packRepository = it }
        }

    @Volatile
    private var builtinMigrator: com.xuyutech.hongbaoshu.pack.importer.BuiltinMigrator? = null

    fun provideBuiltinMigrator(context: Context): com.xuyutech.hongbaoshu.pack.importer.BuiltinMigrator =
        builtinMigrator ?: synchronized(this) {
            builtinMigrator ?: com.xuyutech.hongbaoshu.pack.importer.BuiltinMigrator(
                context = context.applicationContext,
                packIndexStore = providePackIndexStore(context),
                packFileStore = providePackFileStore(context)
            ).also { builtinMigrator = it }
        }

    fun clear() {
        audio?.release()
        audio = null
        activePackLoader = null
        progressStore = null
        pageCacheStore = null
        packIndexStore = null
        packFileStore = null
        packImporter = null
        packRepository = null
        builtinMigrator = null
    }
}
