package com.xuyutech.hongbaoshu.pack.importer

import android.content.Context
import android.net.Uri
import com.xuyutech.hongbaoshu.core.SafeFileOps
import com.xuyutech.hongbaoshu.data.BookJson
import com.xuyutech.hongbaoshu.pack.index.PackIndexStore
import com.xuyutech.hongbaoshu.pack.model.PackIndex
import com.xuyutech.hongbaoshu.pack.model.PackManifest
import com.xuyutech.hongbaoshu.pack.storage.PackFileStore
import com.xuyutech.hongbaoshu.pack.storage.PackInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipPackImporter(
    private val context: Context,
    private val packIndexStore: PackIndexStore,
    private val packFileStore: PackFileStore
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val safeFileOps = SafeFileOps(File(context.cacheDir, "backups/pack_import"))

    suspend fun import(uri: Uri): PackImportResult = withContext(Dispatchers.IO) {
        val tmpRoot = File(context.cacheDir, "pack_import").apply { mkdirs() }
        val tmpDir = File(tmpRoot, "tmp_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            unzipTo(uri, tmpDir)

            val manifestFile = File(tmpDir, "manifest.json")
            if (!manifestFile.exists()) {
                return@withContext PackImportResult(
                    status = PackImportResult.Status.FAILED,
                    message = "资源包缺少 manifest.json",
                    errorCode = PackImportResult.ErrorCode.MANIFEST_INVALID
                )
            }

            val manifest = runCatching {
                json.decodeFromString(PackManifest.serializer(), manifestFile.readText(Charsets.UTF_8))
            }.getOrElse {
                return@withContext PackImportResult(
                    status = PackImportResult.Status.FAILED,
                    message = "manifest.json 解析失败",
                    errorCode = PackImportResult.ErrorCode.MANIFEST_INVALID
                )
            }

            if (manifest.formatVersion != 1) {
                return@withContext PackImportResult(
                    status = PackImportResult.Status.FAILED,
                    packId = manifest.packId,
                    message = "资源包格式不支持: ${manifest.formatVersion}",
                    errorCode = PackImportResult.ErrorCode.FORMAT_UNSUPPORTED
                )
            }
            if (!isValidPackId(manifest.packId)) {
                return@withContext PackImportResult(
                    status = PackImportResult.Status.FAILED,
                    packId = manifest.packId,
                    message = "packId 不合法",
                    errorCode = PackImportResult.ErrorCode.MANIFEST_INVALID
                )
            }

            val bookPath = manifest.resources.text.path
            val bookFile = File(tmpDir, bookPath)
            if (!bookFile.exists()) {
                return@withContext PackImportResult(
                    status = PackImportResult.Status.FAILED,
                    packId = manifest.packId,
                    message = "资源包缺少 ${bookPath}",
                    errorCode = PackImportResult.ErrorCode.FILE_MISSING
                )
            }

            val bookJson = runCatching {
                json.decodeFromString(BookJson.serializer(), bookFile.readText(Charsets.UTF_8))
            }.getOrElse {
                return@withContext PackImportResult(
                    status = PackImportResult.Status.FAILED,
                    packId = manifest.packId,
                    message = "book.json 解析失败",
                    errorCode = PackImportResult.ErrorCode.BOOK_INVALID
                )
            }

            // Validate ID uniqueness (minimal: ensure no duplicates at chapter/paragraph/sentence level).
            runCatching { validateIds(bookJson) }.getOrElse {
                return@withContext PackImportResult(
                    status = PackImportResult.Status.FAILED,
                    packId = manifest.packId,
                    message = "book.json ID 校验失败",
                    errorCode = PackImportResult.ErrorCode.BOOK_INVALID
                )
            }

            val existing = packIndexStore.find(manifest.packId)
            if (existing != null) {
                when {
                    manifest.packVersion > existing.packVersion -> {
                        // upgrade allowed
                    }
                    manifest.packVersion == existing.packVersion -> {
                        return@withContext PackImportResult(
                            status = PackImportResult.Status.SKIPPED,
                            packId = manifest.packId,
                            message = "资源包已存在（版本相同）"
                        )
                    }
                    else -> {
                        return@withContext PackImportResult(
                            status = PackImportResult.Status.FAILED,
                            packId = manifest.packId,
                            message = "资源包版本较低，拒绝覆盖",
                            errorCode = PackImportResult.ErrorCode.VERSION_CONFLICT
                        )
                    }
                }
            }

            val targetDir = packFileStore.packDir(manifest.packId)
            val stagingDir = File(targetDir.parentFile, ".staging_${manifest.packId}_${System.currentTimeMillis()}")
            safeFileOps.moveToBackup(stagingDir, "stale_staging_${manifest.packId}")

            // Move validated content into files/packs/<packId> via staging dir.
            moveDir(tmpDir, stagingDir)
            packFileStore.safeFileOps().atomicReplace(stagingDir, targetDir, "replace_pack_${manifest.packId}")

            val coverExists = manifest.resources.cover?.path?.let { File(targetDir, it).exists() } ?: File(targetDir, "images/cover.png").exists()
            val flipExists = manifest.resources.flipSound?.path?.let { File(targetDir, it).exists() } ?: File(targetDir, "sound/page_flip.wav.ogg").exists()
            val narrationDir = manifest.resources.narration?.dir?.let { File(targetDir, it) } ?: File(targetDir, "audio/narration")
            val narrationExists = narrationDir.exists() && narrationDir.isDirectory && (narrationDir.listFiles()?.any { it.isFile } == true)
            val missingNarrationSentenceCount = PackInspector.inspectMissingNarrationSentenceCount(targetDir) ?: 0

            packIndexStore.upsert(
                PackIndex(
                    packId = manifest.packId,
                    packVersion = manifest.packVersion,
                    formatVersion = manifest.formatVersion,
                    bookTitle = manifest.book.title,
                    bookAuthor = manifest.book.author,
                    bookEdition = manifest.book.edition,
                    importedAt = System.currentTimeMillis(),
                    hasCover = coverExists,
                    hasFlipSound = flipExists,
                    hasNarration = narrationExists,
                    missingNarrationSentenceCount = missingNarrationSentenceCount,
                    isValid = true
                )
            )

            PackImportResult(
                status = PackImportResult.Status.SUCCESS,
                packId = manifest.packId,
                message = "导入成功"
            )
        } catch (e: Exception) {
            PackImportResult(
                status = PackImportResult.Status.FAILED,
                message = "导入失败：${e.message ?: "IO 错误"}",
                errorCode = PackImportResult.ErrorCode.IO_ERROR
            )
        } finally {
            safeFileOps.moveToBackup(tmpDir, "import_tmp")
        }
    }

    private fun unzipTo(uri: Uri, destDir: File) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "openInputStream returned null" }
            ZipInputStream(input.buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("/").replace("\\", "/")
                    if (name.isNotBlank()) {
                        val outFile = safeFileOps.ensureChildPath(destDir, name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun validateIds(bookJson: BookJson) {
        val chapters = HashSet<String>()
        val paragraphs = HashSet<String>()
        val sentences = HashSet<String>()
        bookJson.chapters.forEach { ch ->
            require(chapters.add(ch.id)) { "Duplicate chapter id: ${ch.id}" }
            ch.paragraphs.forEach { p ->
                require(paragraphs.add(p.id)) { "Duplicate paragraph id: ${p.id}" }
                p.sentences.forEach { s ->
                    require(sentences.add(s.id)) { "Duplicate sentence id: ${s.id}" }
                }
            }
        }
    }

    private fun moveDir(src: File, dest: File) {
        if (!src.renameTo(dest)) {
            copyDir(src, dest)
            safeFileOps.moveToBackup(src, "move_dir_source")
        }
    }

    private fun copyDir(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyDir(child, File(dest, child.name))
            }
        } else {
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
        }
    }
}

