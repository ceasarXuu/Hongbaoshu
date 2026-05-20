package com.xuyutech.hongbaoshu.pack.storage

import android.content.Context
import com.xuyutech.hongbaoshu.core.SafeFileOps
import java.io.File

data class PackInspection(
    val packExists: Boolean,
    val hasText: Boolean,
    val hasCover: Boolean,
    val hasFlipSound: Boolean,
    val hasNarration: Boolean
) {
    val isValid: Boolean get() = packExists && hasText
}

class PackFileStore(
    private val context: Context
) {
    private val safeFileOps = SafeFileOps(File(context.filesDir, "backups/pack_files"))

    fun packDir(packId: String): File {
        val root = File(context.filesDir, "packs").also { it.mkdirs() }
        return File(root, packId)
    }

    fun pageCacheDir(packId: String): File {
        val root = File(context.cacheDir, "page_cache").also { it.mkdirs() }
        return File(root, packId)
    }

    fun inspect(packId: String): PackInspection {
        return PackInspector.inspectPackRoot(packDir(packId))
    }

    fun deletePack(packId: String) {
        safeFileOps.moveToBackup(packDir(packId), "delete_pack_$packId")
        safeFileOps.moveToBackup(pageCacheDir(packId), "delete_page_cache_$packId")
    }

    fun safeFileOps(): SafeFileOps = safeFileOps
}
