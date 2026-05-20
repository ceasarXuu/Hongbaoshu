package com.xuyutech.hongbaoshu.pack.repository

import android.net.Uri
import com.xuyutech.hongbaoshu.pack.importer.PackImportResult
import com.xuyutech.hongbaoshu.pack.model.PackIndex
import kotlinx.coroutines.flow.Flow

interface PackRepository {
    val packs: Flow<List<PackIndex>>

    suspend fun find(packId: String): PackIndex?

    suspend fun markOpened(packId: String)

    suspend fun import(uri: Uri): PackImportResult

    suspend fun delete(packId: String)

    suspend fun revalidate(packId: String)

    fun coverUri(packId: String): String?
}
