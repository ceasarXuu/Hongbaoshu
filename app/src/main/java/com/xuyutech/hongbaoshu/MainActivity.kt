package com.xuyutech.hongbaoshu

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuyutech.hongbaoshu.bookshelf.BookshelfBook
import com.xuyutech.hongbaoshu.bookshelf.BookshelfScreen
import com.xuyutech.hongbaoshu.bookshelf.BookshelfViewModel
import com.xuyutech.hongbaoshu.bookshelf.ImportProgressDialog
import com.xuyutech.hongbaoshu.bookshelf.ImportState
import com.xuyutech.hongbaoshu.audio.AudioManager
import com.xuyutech.hongbaoshu.core.AppLogger
import com.xuyutech.hongbaoshu.di.ServiceLocator
import com.xuyutech.hongbaoshu.pack.repository.PackRepository
import com.xuyutech.hongbaoshu.reader.ReaderViewModel
import com.xuyutech.hongbaoshu.reader.ReaderViewModelFactory
import com.xuyutech.hongbaoshu.reader.ReaderScreen
import com.xuyutech.hongbaoshu.ui.theme.HongbaoshuTheme
import com.xuyutech.hongbaoshu.storage.ProgressStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingExternalImportUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExternalImportUri = extractImportUri(intent)
        setContent {
            HongbaoshuTheme {
                HongbaoshuApp(
                    externalImportUri = pendingExternalImportUri,
                    onExternalImportConsumed = { pendingExternalImportUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingExternalImportUri = extractImportUri(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = when (newConfig.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            Configuration.ORIENTATION_PORTRAIT -> "portrait"
            else -> "undefined"
        }
        AppLogger.i("MainActivity", "onConfigurationChanged orientation=$orientation")
    }

    private fun extractImportUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            }
            else -> null
        }
    }
}

@Composable
private fun HongbaoshuApp(
    externalImportUri: Uri?,
    onExternalImportConsumed: () -> Unit
) {
    val context = LocalContext.current
    var initError by remember { mutableStateOf<String?>(null) }
    var audioManager: AudioManager? = null
    var progressStore: ProgressStore? = null
    var pageCacheStore: com.xuyutech.hongbaoshu.storage.PageCacheStore? = null
    var packRepository: PackRepository? = null
    var activePackLoader: com.xuyutech.hongbaoshu.data.ActivePackContentLoader? = null

    try {
        audioManager = ServiceLocator.provideAudioManager(context)
    } catch (e: Exception) {
        AppLogger.e("MainActivity", "Failed to create AudioManager", e)
        initError = "音频管理器初始化失败: ${e.message}"
    }

    if (initError == null) {
        try {
            progressStore = ServiceLocator.provideProgressStore(context)
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to create ProgressStore", e)
            initError = "进度存储初始化失败: ${e.message}"
        }
    }

    if (initError == null) {
        try {
            pageCacheStore = ServiceLocator.providePageCacheStore(context)
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to create PageCacheStore", e)
            initError = "页面缓存初始化失败: ${e.message}"
        }
    }

    if (initError == null) {
        try {
            packRepository = ServiceLocator.providePackRepository(context)
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to create PackRepository", e)
            initError = "资源仓储初始化失败: ${e.message}"
        }
    }

    if (initError == null) {
        try {
            activePackLoader = ServiceLocator.provideActivePackContentLoader(context)
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to create ActivePackContentLoader", e)
            initError = "内容加载器初始化失败: ${e.message}"
        }
    }

    if (initError != null || audioManager == null || progressStore == null || 
        pageCacheStore == null || packRepository == null || activePackLoader == null) {
        if (initError == null) {
            initError = "部分组件初始化失败，请重启应用"
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text("初始化失败: $initError", color = Color.Red)
        }
        return
    }

    val am = audioManager!!
    val ps = progressStore!!
    val pcs = pageCacheStore!!
    val repo = packRepository!!
    val apl = activePackLoader!!

    val activePackId = remember { mutableStateOf("builtin") }
    DisposableEffect(Unit) {
        onDispose { am.release() }
    }
    val screen = remember { mutableStateOf(Screen.Bookshelf) }

    val builtinMigrator = remember { ServiceLocator.provideBuiltinMigrator(context) }
    var builtinMigrated by remember { mutableStateOf(false) }
    var bookshelfViewModel by remember { mutableStateOf<BookshelfViewModel?>(null) }

    LaunchedEffect(Unit) {
        try {
            builtinMigrator.invoke(forceMigrate = true)
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "BuiltinMigrator failed", e)
        }
        // 迁移完成后创建 bookshelfViewModel
        bookshelfViewModel = BookshelfViewModel(repo)
        builtinMigrated = true
    }

    if (!builtinMigrated) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text("正在初始化资源...")
        }
        return
    }

    LaunchedEffect(activePackId.value) {
        apl.setActivePackId(activePackId.value)
        am.updateContentLoader(apl)
    }

    val viewModel: ReaderViewModel = viewModel(
        key = "reader_${activePackId.value}_${builtinMigrated}",
        factory = ReaderViewModelFactory(
            context.applicationContext as android.app.Application,
            activePackId.value,
            apl,
            ps,
            am,
            pcs
        )
    )


    val bvm = bookshelfViewModel ?: return
    val packs = bvm.packs.collectAsState()
    val scope = rememberCoroutineScope()
    val bookshelfMessage = remember { mutableStateOf<String?>(null) }
    var importState   by remember { mutableStateOf<ImportState>(ImportState.Idle) }

    val performImport: (android.net.Uri) -> Unit = { targetUri ->
        importState = ImportState.Importing
        scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    targetUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val result = repo.import(targetUri)
            if (result.status == com.xuyutech.hongbaoshu.pack.importer.PackImportResult.Status.SUCCESS || 
                result.status == com.xuyutech.hongbaoshu.pack.importer.PackImportResult.Status.SKIPPED) {
                importState = ImportState.Idle
                bookshelfMessage.value = result.message
            } else {
                importState = ImportState.Error(result.message ?: "导入报错", targetUri)
            }
        }
    }

    LaunchedEffect(externalImportUri) {
        val uri = externalImportUri ?: return@LaunchedEffect
        performImport(uri)
        onExternalImportConsumed()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            performImport(uri)
        }
    }
    
    // 监听加载状态
    val readerState = viewModel.state.observeAsState()
    val isLoading = readerState.value?.isLoading ?: true
    
    val book = readerState.value?.book
    val missingCount = readerState.value?.missingAudio?.size ?: 0
    val activePackIndex = packs.value.firstOrNull { it.packId == activePackId.value }
    // 统一所有包的朗读控制逻辑,基于实际的音频资源可用性
    val narrationControlsEnabled = activePackIndex?.hasNarration == true && activePackIndex.isValid

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        ReaderNavHost(
            screen = screen.value,
            isLoading = isLoading,
            books = packs.value.map { p ->
                BookshelfBook(
                    packId = p.packId,
                    title = p.bookTitle,
                    author = p.bookAuthor,
                    edition = p.bookEdition,
                    coverUri = repo.coverUri(p.packId),
                    statusText = when {
                        !p.isValid -> "不可用"
                        !p.hasNarration -> "仅文本"
                        p.missingNarrationSentenceCount > 0 -> "音频缺失(${p.missingNarrationSentenceCount})"
                        else -> "文本+音频"
                    }
                )
            },
            onOpenBook = { selected ->
                scope.launch { repo.markOpened(selected.packId) }
                activePackId.value = selected.packId
                screen.value = Screen.Reader
            },
            onDeletePack = { target ->
                scope.launch {
                    repo.delete(target.packId)
                }
            },
            onRevalidatePack = { target ->
                scope.launch {
                    repo.revalidate(target.packId)
                }
            },
            onBackToBookshelf = {
                viewModel.pauseNarration()
                screen.value = Screen.Bookshelf
            },
            message = bookshelfMessage.value,
            onMessageShown = { bookshelfMessage.value = null },
            onImport = {
                importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
            viewModel = viewModel,
            narrationControlsEnabled = narrationControlsEnabled,
            audioManager = am
        )

        ImportProgressDialog(
            state = importState,
            onRetry = { performImport(it) },
            onCancel = { importState = ImportState.Idle }
        )
    }
}

private enum class Screen { Bookshelf, Reader }

@Composable
private fun ReaderNavHost(
    screen: Screen,
    isLoading: Boolean,
    books: List<BookshelfBook>,
    onOpenBook: (BookshelfBook) -> Unit,
    onDeletePack: (BookshelfBook) -> Unit,
    onRevalidatePack: (BookshelfBook) -> Unit,
    onBackToBookshelf: () -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
    onImport: () -> Unit,
    viewModel: ReaderViewModel,
    narrationControlsEnabled: Boolean,
    audioManager: AudioManager
) {
    when (screen) {
        Screen.Bookshelf -> BookshelfScreen(
            books = books,
            onOpenBook = onOpenBook,
            onImport = onImport,
            onDeletePack = onDeletePack,
            onRevalidatePack = onRevalidatePack,
            message = message,
            onMessageShown = onMessageShown,
            isLoading = isLoading,
            modifier = Modifier.fillMaxSize()
        )
        Screen.Reader -> ReaderScreen(
            viewModel = viewModel,
            audioManager = audioManager,
            narrationControlsEnabled = narrationControlsEnabled,
            onBack = onBackToBookshelf
        )
    }
}
