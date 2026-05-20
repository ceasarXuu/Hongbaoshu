package com.xuyutech.hongbaoshu.pack.repository

import android.net.Uri
import com.xuyutech.hongbaoshu.pack.importer.PackImportResult
import com.xuyutech.hongbaoshu.pack.importer.ZipPackImporter
import com.xuyutech.hongbaoshu.pack.index.PackIndexStore
import com.xuyutech.hongbaoshu.pack.model.PackIndex
import com.xuyutech.hongbaoshu.pack.storage.PackFileStore
import com.xuyutech.hongbaoshu.pack.storage.PackInspector
import kotlinx.coroutines.flow.Flow

class AndroidPackRepository(
    private val packIndexStore: PackIndexStore,
    private val packFileStore: PackFileStore,
    private val packImporter: ZipPackImporter
) : PackRepository {
    override val packs: Flow<List<PackIndex>> = packIndexStore.packs

    override suspend fun find(packId: String): PackIndex? = packIndexStore.find(packId)

    override suspend fun markOpened(packId: String) {
        packIndexStore.markOpened(packId)
    }

    override suspend fun import(uri: Uri): PackImportResult = packImporter.import(uri)

    override suspend fun delete(packId: String) {
        packIndexStore.delete(packId)
        packFileStore.deletePack(packId)
    }

    override suspend fun revalidate(packId: String) {
        val existing = packIndexStore.find(packId) ?: return
        val inspection = packFileStore.inspect(packId)
        val missingNarrationSentenceCount =
            PackInspector.inspectMissingNarrationSentenceCount(packFileStore.packDir(packId))
                ?: existing.missingNarrationSentenceCount

        packIndexStore.upsert(
            existing.copy(
                hasCover = inspection.hasCover,
                hasFlipSound = inspection.hasFlipSound,
                hasNarration = inspection.hasNarration,
                missingNarrationSentenceCount = missingNarrationSentenceCount,
                isValid = inspection.isValid
            )
        )
    }

    override fun coverUri(packId: String): String? {
        return PackInspector.resolveCoverPath(packFileStore.packDir(packId))
            ?.toURI()
            ?.toString()
    }
}
