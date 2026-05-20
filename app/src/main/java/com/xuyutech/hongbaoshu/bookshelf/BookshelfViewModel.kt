package com.xuyutech.hongbaoshu.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuyutech.hongbaoshu.pack.model.PackIndex
import com.xuyutech.hongbaoshu.pack.repository.PackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BookshelfViewModel(
    packRepository: PackRepository
) : ViewModel() {

    val packs: StateFlow<List<PackIndex>> = packRepository.packs
        .map { it.sortedByDescending { p -> p.lastOpenedAt ?: p.importedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

