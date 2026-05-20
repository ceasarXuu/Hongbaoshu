package com.xuyutech.hongbaoshu.pack.importer

import android.content.Context
import com.xuyutech.hongbaoshu.core.AppLogger
import com.xuyutech.hongbaoshu.core.SafeFileOps
import com.xuyutech.hongbaoshu.data.ContentLoaderImpl
import com.xuyutech.hongbaoshu.pack.index.PackIndexStore
import com.xuyutech.hongbaoshu.pack.model.BookMetadata
import com.xuyutech.hongbaoshu.pack.model.NarrationResource
import com.xuyutech.hongbaoshu.pack.model.PackIndex
import com.xuyutech.hongbaoshu.pack.model.PackManifest
import com.xuyutech.hongbaoshu.pack.model.PackResources
import com.xuyutech.hongbaoshu.pack.model.ResourceItem
import com.xuyutech.hongbaoshu.pack.storage.PackFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

class BuiltinMigrator(
    private val context: Context,
    private val packIndexStore: PackIndexStore,
    private val packFileStore: PackFileStore
) {
    private val safeFileOps = SafeFileOps(File(context.filesDir, "backups/builtin_migration"))

    suspend fun invoke(forceMigrate: Boolean = false) = withContext(Dispatchers.IO) {
        val packId = "builtin"
        val existing = packIndexStore.find(packId)
        val targetDir = packFileStore.packDir(packId)
        val manifestFile = File(targetDir, "manifest.json")

        // 若已迁移且有效，或者目录存在且清单存在，忽略（除非强制迁移）
        if (!forceMigrate && existing != null && targetDir.exists() && manifestFile.exists()) {
            AppLogger.d("BuiltinMigrator", "Migration skipped, already exists")
            return@withContext
        }

        AppLogger.d("BuiltinMigrator", "Starting migration, force=$forceMigrate, targetDir=$targetDir")
        
        // 清理旧数据（如果存在）
        if (targetDir.exists()) {
            safeFileOps.moveToBackup(targetDir, "builtin_remigrate")
        }
        
        targetDir.mkdirs()

        // 复制 assets 到 pack 目录（可选资源不存在时降级，不中断迁移）
        copyAssetDirIfExists("text", File(targetDir, "text"), required = true)
        copyAssetDirIfExists("audio", File(targetDir, "audio"), required = false)
        copyAssetDirIfExists("images", File(targetDir, "images"), required = false)
        copyAssetDirIfExists("sound", File(targetDir, "sound"), required = false)

        // 使用 ContentLoader 读取信息
        val bookResult = ContentLoaderImpl().loadBook(context)
        val book = bookResult.book
        val missingIds = bookResult.missingSentenceAudioIds.toList().sorted()
        val missingCount = missingIds.size
        if (missingIds.isNotEmpty()) {
            AppLogger.w(
                "BuiltinMigrator",
                "Builtin narration missing ids(count=$missingCount): ${missingIds.joinToString(limit = 10)}"
            )
        }

        val hasCover = File(targetDir, "images/cover.png").exists()
        val hasFlipSound = File(targetDir, "sound/page_flip.wav.ogg").exists()
        val hasNarration = File(targetDir, "audio/narration")
            .let { it.exists() && it.isDirectory && (it.listFiles()?.any { f -> f.isFile } == true) }

        val manifest = PackManifest(
            formatVersion = 1,
            packId = packId,
            packVersion = 1,
            book = BookMetadata(
                title = book.title,
                author = book.author,
                edition = book.edition
            ),
            resources = PackResources(
                text = ResourceItem(path = "text/text.json"),
                cover = if (hasCover) ResourceItem(path = "images/cover.png") else null,
                flipSound = if (hasFlipSound) ResourceItem(path = "sound/page_flip.wav.ogg") else null,
                narration = if (hasNarration) NarrationResource(dir = "audio/narration", codec = "mp3") else null
            )
        )

        // Generate manifest.json
        val json = Json { prettyPrint = true }
        File(targetDir, "manifest.json").writeText(
            json.encodeToString(PackManifest.serializer(), manifest)
        )

        val index = PackIndex(
            packId = packId,
            packVersion = 1,
            formatVersion = 1,
            bookTitle = book.title,
            bookAuthor = book.author,
            bookEdition = book.edition,
            importedAt = System.currentTimeMillis(),
            hasCover = hasCover,
            hasFlipSound = hasFlipSound,
            hasNarration = hasNarration,
            missingNarrationSentenceCount = missingCount,
            isValid = true
        )
        packIndexStore.upsert(index)
    }

    private fun copyAssetDirIfExists(srcPath: String, destDir: File, required: Boolean) {
        val am = context.assets
        val list = runCatching { am.list(srcPath) }.getOrNull()

        if (list == null) {
            if (required) error("Required asset path missing: $srcPath")
            AppLogger.w("BuiltinMigrator", "Optional asset path missing: $srcPath")
            return
        }

        if (list.isEmpty()) {
            val copied = runCatching {
                destDir.parentFile?.mkdirs()
                am.open(srcPath).use { input ->
                    FileOutputStream(destDir).use { output ->
                        input.copyTo(output)
                    }
                }
            }.isSuccess
            if (!copied) {
                if (required) error("Required asset file missing: $srcPath")
                AppLogger.w("BuiltinMigrator", "Optional asset file missing: $srcPath")
            }
        } else {
            destDir.mkdirs()
            for (child in list) {
                copyAssetDirIfExists("$srcPath/$child", File(destDir, child), required = required)
            }
        }
    }
}
