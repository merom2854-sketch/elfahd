package com.alfahdtv.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ResumeInfo(val title: String, val url: String, val image: String, val position: Long, val duration: Long)

class NativeHomeActivity : ComponentActivity() {
    private var pendingDownload: ContentDetail? = null
    private var resumeVersion by mutableIntStateOf(0)
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val detail = pendingDownload
        pendingDownload = null
        if (granted && detail != null) startDownload(detail) else toast("يلزم السماح بالوصول لبدء التحميل")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        NotificationScheduler.schedule(this)
        NotificationScheduler.checkNow(this)
        requestNotifications()
        Handler(Looper.getMainLooper()).postDelayed({ UpdateChecker.check(this, false) }, 1800)
        setContent {
            FahdTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FahdApp(
                        resumeVersion = resumeVersion,
                        onPlay = ::play,
                        onDownload = ::download,
                        onOpenDownloads = { startActivity(Intent(this, DownloadsActivity::class.java)) },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                        onOpenExternal = ::openExternal,
                    )
                }
            }
        }
    }

    override fun onResume() { super.onResume(); resumeVersion++ }

    private fun play(url: String, title: String, image: String? = null) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val trustedHttp = uri?.scheme.equals("http", true) && uri?.host?.lowercase()?.endsWith(".downet.net") == true
        if (!url.startsWith("https://") && !trustedHttp) { toast("مصدر المشاهدة غير متاح الآن"); return }
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("media_url", url).putExtra("media_title", title).putExtra("media_image", image ?: ""))
    }

    private fun download(detail: ContentDetail) {
        if (detail.mediaUrl.isBlank()) { toast("رابط التحميل غير متاح الآن"); return }
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = detail
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else startDownload(detail)
    }

    private fun startDownload(detail: ContentDetail) {
        try {
            VideoDownloads.enqueue(this, detail.mediaUrl, detail.title, "AlFahdTV/3.0 Android", "", "video/mp4")
            toast("بدأ تحميل ${detail.title}")
        } catch (_: Exception) { toast("تعذر بدء التحميل") }
    }

    private fun openExternal(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast("تعذر فتح الرابط") }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 88)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private enum class FahdDestination(val label: String) { HOME("الرئيسية"), MOVIES("الأفلام"), SERIES("المسلسلات"), CHANNELS("القنوات"), LIBRARY("مكتبتي") }

@Composable
private fun FahdApp(
    resumeVersion: Int,
    onPlay: (String, String, String?) -> Unit,
    onDownload: (ContentDetail) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val context = LocalContext.current
    val library = remember { context.getSharedPreferences("data", 0) }
    val storedResume = remember(resumeVersion) {
        context.getSharedPreferences("player_resume", 0).let { prefs ->
            val url = prefs.getString("url", "").orEmpty()
            val title = prefs.getString("title", "").orEmpty()
            val image = prefs.getString("image", "").orEmpty()
            val position = prefs.getLong("position", 0)
            val duration = prefs.getLong("duration", 0)
            if (url.isBlank() || title.isBlank() || position <= 0 || duration <= 0 || position >= duration - 15_000L) null else ResumeInfo(title, url, image, position, duration)
        }
    }
    var favoriteEntries by remember { mutableStateOf(library.getStringSet("favorites", emptySet()).orEmpty().toSet()) }
    var historyEntries by remember { mutableStateOf(library.getStringSet("history", emptySet()).orEmpty().toSet()) }
    val repository = remember { NativeCatalogRepository() }
    var movies by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var series by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var anime by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var filteredMovies by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var filteredSeries by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var movieCategories by remember { mutableStateOf<List<SourceCategory>>(emptyList()) }
    var seriesCategories by remember { mutableStateOf<List<SourceCategory>>(emptyList()) }
    var selectedMovieCategory by remember { mutableStateOf<SourceCategory?>(null) }
    var selectedSeriesCategory by remember { mutableStateOf<SourceCategory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf(FahdDestination.HOME) }
    var selected by remember { mutableStateOf<CatalogItem?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var allKind by remember { mutableStateOf<CatalogKind?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val resume = remember(storedResume, movies, series, anime) {
        storedResume?.let { saved ->
            if (saved.image.isNotBlank()) saved else {
                val baseTitle = saved.title.substringBefore(" •").trim()
                val match = (movies + series + anime).firstOrNull { item ->
                    baseTitle.startsWith(item.title, ignoreCase = true) || item.title.startsWith(baseTitle, ignoreCase = true)
                }
                saved.copy(image = match?.image.orEmpty())
            }
        }
    }

    LaunchedEffect(reloadKey) {
        loading = true; error = false
        try {
            coroutineScope {
                val movieTask = async { repository.catalog(NativeCatalogRepository.MOVIES, CatalogKind.MOVIE) }
                val seriesTask = async { repository.catalog(NativeCatalogRepository.SERIES, CatalogKind.SERIES) }
                val animeTask = async { repository.anime() }
                val manualTask = async { repository.manualContent() }
                val movieCategoriesTask = async { repository.categories(NativeCatalogRepository.MOVIES) }
                val seriesCategoriesTask = async { repository.categories(NativeCatalogRepository.SERIES) }
                val manual = manualTask.await()
                movies = (manual.filter { it.kind == CatalogKind.MOVIE } + movieTask.await()).distinctBy { it.href }
                series = (manual.filter { it.kind == CatalogKind.SERIES } + seriesTask.await()).distinctBy { it.href }
                anime = (manual.filter { it.kind == CatalogKind.ANIME } + animeTask.await()).distinctBy { it.href }
                filteredMovies = movies; filteredSeries = series
                movieCategories = movieCategoriesTask.await(); seriesCategories = seriesCategoriesTask.await()
            }
            error = movies.isEmpty() && series.isEmpty()
        } catch (_: Exception) { error = true }
        loading = false
    }

    fun chooseCategory(kind: CatalogKind, category: SourceCategory?) {
        scope.launch {
            loading = true
            if (kind == CatalogKind.MOVIE) {
                selectedMovieCategory = category
                filteredMovies = if (category == null) movies else repository.catalog(category.url, CatalogKind.MOVIE)
            } else if (kind == CatalogKind.SERIES) {
                selectedSeriesCategory = category
                filteredSeries = if (category == null) series else repository.catalog(category.url, CatalogKind.SERIES)
            }
            loading = false
        }
    }

    BackHandler(enabled = selected != null || searchOpen || allKind != null || destination != FahdDestination.HOME) {
        when { selected != null -> selected = null; searchOpen -> searchOpen = false; allKind != null -> allKind = null; else -> destination = FahdDestination.HOME }
    }

    Scaffold(
        containerColor = FahdColors.Background,
        bottomBar = {
            if (selected == null && !searchOpen) FahdBottomBar(destination) { next -> allKind = null; destination = next }
        },
    ) { padding ->
        val screenKey = "${selected?.href.orEmpty()}|$searchOpen|${allKind?.name.orEmpty()}|${destination.name}"
        AnimatedContent(targetState = screenKey, label = "screen") {
            val item = selected
            when {
                item != null -> DetailScreen(
                    item = item,
                    repository = repository,
                    related = (movies + series + anime).filterNot { it.href == item.href },
                    isFavorite = favoriteEntries.any { it.substringAfterLast('|') == item.href },
                    onToggleFavorite = {
                        val updated = favoriteEntries.filterNot { it.substringAfterLast('|') == item.href }.toMutableSet()
                        if (updated.size == favoriteEntries.size) updated += "${item.title}|${item.href}"
                        favoriteEntries = updated
                        library.edit().putStringSet("favorites", HashSet(updated)).apply()
                    },
                    onRecordHistory = {
                        val updated = historyEntries.filterNot { it.substringAfterLast('|') == item.href }.toMutableSet()
                        updated += "${item.title}|${item.href}"
                        while (updated.size > 30) updated.remove(updated.first())
                        historyEntries = updated
                        library.edit().putStringSet("history", HashSet(updated)).apply()
                    },
                    onRelated = { selected = it },
                    onBack = { selected = null },
                    onPlay = onPlay,
                    onDownload = onDownload,
                )
                searchOpen -> SearchScreen(repository, onBack = { searchOpen = false }, onSelect = { selected = it; searchOpen = false })
                allKind != null -> {
                    val content = when (allKind) { CatalogKind.MOVIE -> movies; CatalogKind.SERIES -> series; CatalogKind.ANIME -> anime; null -> emptyList() }
                    val title = when (allKind) { CatalogKind.MOVIE -> "وصل حديثًا"; CatalogKind.SERIES -> "مسلسلات مختارة"; CatalogKind.ANIME -> "الأنمي والكرتون"; null -> "عرض الكل" }
                    CatalogGrid(title, content, loading, onSearch = { searchOpen = true }, onDownloads = onOpenDownloads, onSettings = onOpenSettings, onSelect = { selected = it }, padding.calculateBottomPadding())
                }
                destination == FahdDestination.HOME -> HomeScreen(movies, series, anime, resume, loading, error, onRetry = { reloadKey++ }, onSelect = { selected = it }, onResume = { resume?.let { onPlay(it.url, it.title, it.image) } }, onViewAll = { allKind = it }, onSearch = { searchOpen = true }, onDownloads = onOpenDownloads, onSettings = onOpenSettings, onTelegram = { onOpenExternal("https://t.me/elfahd_tv") }, contentBottomPadding = padding.calculateBottomPadding())
                destination == FahdDestination.MOVIES -> CatalogGrid("الأفلام", filteredMovies, loading, onSearch = { searchOpen = true }, onDownloads = onOpenDownloads, onSettings = onOpenSettings, onSelect = { selected = it }, padding.calculateBottomPadding(), movieCategories, selectedMovieCategory) { chooseCategory(CatalogKind.MOVIE, it) }
                destination == FahdDestination.SERIES -> CatalogGrid("المسلسلات", filteredSeries, loading, onSearch = { searchOpen = true }, onDownloads = onOpenDownloads, onSettings = onOpenSettings, onSelect = { selected = it }, padding.calculateBottomPadding(), seriesCategories, selectedSeriesCategory) { chooseCategory(CatalogKind.SERIES, it) }
                destination == FahdDestination.CHANNELS -> ChannelsScreen(onBack = { destination = FahdDestination.HOME }, onOpenExternal = onOpenExternal, bottomPadding = padding.calculateBottomPadding())
                else -> LibraryScreen((movies + series + anime).distinctBy { it.href }, favoriteEntries, historyEntries, onSelect = { selected = it }, onDownloads = onOpenDownloads, onSettings = onOpenSettings, bottomPadding = padding.calculateBottomPadding())
            }
        }
    }
}

@Composable
private fun HomeScreen(
    movies: List<CatalogItem>,
    series: List<CatalogItem>,
    anime: List<CatalogItem>,
    resume: ResumeInfo?,
    loading: Boolean,
    error: Boolean,
    onRetry: () -> Unit,
    onSelect: (CatalogItem) -> Unit,
    onResume: () -> Unit,
    onViewAll: (CatalogKind) -> Unit,
    onSearch: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onTelegram: () -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
) {
    var featuredIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(movies.size) {
        featuredIndex = 0
        while (movies.isNotEmpty()) { delay(5_000); featuredIndex = (featuredIndex + 1) % movies.size.coerceAtMost(8) }
    }
    val featured = movies.getOrNull(featuredIndex)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentBottomPadding + 18.dp)) {
        item {
            Hero(featured, featuredIndex, movies.size.coerceAtMost(8), onSelect, onSearch, onDownloads, onSettings)
        }
        if (error) item { ErrorCard(onRetry) }
        if (loading) item { LoadingBlock() }
        if (!loading) {
            if (resume != null) item { ResumeSection(resume, onResume) }
            item { TelegramBanner(onTelegram) }
            item { ContentRail("وصل حديثًا", movies.take(12), onSelect, onViewAll = { onViewAll(CatalogKind.MOVIE) }) }
            item { ContentRail("مسلسلات مختارة", series.take(12), onSelect, onViewAll = { onViewAll(CatalogKind.SERIES) }) }
            item { ContentRail("الأنمي والكرتون", anime.take(12), onSelect, onViewAll = { onViewAll(CatalogKind.ANIME) }) }
        }
    }
}

@Composable
private fun Hero(
    item: CatalogItem?,
    index: Int,
    count: Int,
    onSelect: (CatalogItem) -> Unit,
    onSearch: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(510.dp).background(FahdColors.Surface)) {
        if (item != null) AsyncImage(model = item.image, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .18f), Color.Transparent, FahdColors.Background.copy(alpha = .35f), FahdColors.Background), startY = 0f)))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(painterResource(R.drawable.fahd_logo), "شعار الفهد TV", Modifier.size(43.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.weight(1f))
            GlassIcon(Icons.Rounded.Search, "بحث", onSearch)
            GlassIcon(Icons.Rounded.Download, "التنزيلات", onDownloads)
            GlassIcon(Icons.Rounded.Settings, "الإعدادات", onSettings)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Surface(color = Color.Black.copy(alpha = .62f), shape = RoundedCornerShape(8.dp)) { Text("مختارات الفهد", color = FahdColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(item?.title ?: "الفهد TV", style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            Text(if (item == null) "مكتبتك السينمائية الجديدة" else "${kindLabel(item.kind)}  •  جودة عالية  •  متاح الآن", color = FahdColors.Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { if (item != null) onSelect(item) }, enabled = item != null, colors = ButtonDefaults.buttonColors(containerColor = FahdColors.Red), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp)) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("شاهد الآن", fontWeight = FontWeight.Black)
            }
            if (count > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 17.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(count) { dot -> Box(Modifier.padding(horizontal = 3.dp).size(if (dot == index) 17.dp else 6.dp, 6.dp).clip(CircleShape).background(if (dot == index) FahdColors.Red else Color.White.copy(alpha = .35f))) }
                }
            }
        }
    }
}

@Composable
private fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) {
    IconButton(onClick = click, modifier = Modifier.padding(start = 5.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = .46f))) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(21.dp)) }
}

@Composable
private fun ResumeSection(resume: ResumeInfo, onResume: () -> Unit) {
    val progress = (resume.position.toFloat() / resume.duration.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text("كمل فرجة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), textAlign = TextAlign.End)
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(Modifier.width(310.dp).clickable(onClick = onResume)) {
                    Box(Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(17.dp)).background(FahdColors.SurfaceHigh)) {
                        if (resume.image.isNotBlank()) AsyncImage(resume.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .72f)))))
                        Text(formatResumeTime(resume.position), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 22.dp))
                        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 10.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .4f))) { Box(Modifier.fillMaxWidth(progress).height(5.dp).background(Color(0xFF20D29A))) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(resume.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.MoreVert, "خيارات", tint = FahdColors.Muted, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

private fun formatResumeTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds) else String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
private fun TelegramBanner(onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp).fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color(0xFF0B2A3C)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color(0xFF2BA4DC), modifier = Modifier.size(43.dp)) { Box(contentAlignment = Alignment.Center) { Text("➤", color = Color.White, fontSize = 20.sp) } }
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) { Text("قناة الفهد الجديدة", fontWeight = FontWeight.Bold); Text("تابع أحدث الإضافات والإعلانات", color = FahdColors.Muted, fontSize = 12.sp) }
        Text("اشترك", color = Color(0xFF50C8FF), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContentRail(title: String, content: List<CatalogItem>, onSelect: (CatalogItem) -> Unit, onViewAll: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onViewAll != null) Text("عرض الكل", color = FahdColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onViewAll).padding(horizontal = 10.dp, vertical = 8.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) { items(content, key = { it.href }) { PosterCard(it, onSelect) } }
    }
}

@Composable
private fun PosterCard(item: CatalogItem, onSelect: (CatalogItem) -> Unit) {
    Column(Modifier.width(128.dp).clickable { onSelect(item) }) {
        Box(Modifier.fillMaxWidth().height(192.dp).clip(RoundedCornerShape(15.dp)).background(FahdColors.SurfaceHigh)) {
            AsyncImage(model = item.image, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
            Surface(Modifier.align(Alignment.TopStart).padding(7.dp), color = Color.Black.copy(alpha = .7f), shape = RoundedCornerShape(7.dp)) { Text(kindLabel(item.kind), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) }
        }
        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text("متاح الآن", color = FahdColors.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun NativeTopBar(title: String, onSearch: (() -> Unit)?, onDownloads: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(FahdColors.Background.copy(alpha = .97f)).statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(painterResource(R.drawable.fahd_logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
            if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "بحث") }
        IconButton(onClick = onDownloads) { Icon(Icons.Rounded.Download, "التنزيلات") }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "الإعدادات") }
    }
}

@Composable
private fun CatalogGrid(title: String, content: List<CatalogItem>, loading: Boolean, onSearch: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit, onSelect: (CatalogItem) -> Unit, bottomPadding: androidx.compose.ui.unit.Dp, categories: List<SourceCategory> = emptyList(), selectedCategory: SourceCategory? = null, onCategory: (SourceCategory?) -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        NativeTopBar(title, onSearch, onDownloads, onSettings)
        if (categories.isNotEmpty()) CategoryRow(categories, selectedCategory, onCategory)
        if (loading) LoadingBlock() else LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = bottomPadding + 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
            items(content, key = { it.href }) { PosterCard(it, onSelect) }
        }
    }
}

@Composable
private fun CategoryRow(categories: List<SourceCategory>, selected: SourceCategory?, onSelect: (SourceCategory?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Surface(color = if (selected == null) FahdColors.Red else FahdColors.SurfaceHigh, shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable { onSelect(null) }) { Text("الكل", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)) } }
        items(categories, key = { it.id }) { category -> Surface(color = if (selected?.id == category.id) FahdColors.Red else FahdColors.SurfaceHigh, shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable { onSelect(category) }) { Text(category.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SearchScreen(repository: NativeCatalogRepository, onBack: () -> Unit, onSelect: (CatalogItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.length < 2) { results = emptyList(); loading = false; failed = false; return@LaunchedEffect }
        delay(350)
        loading = true; failed = false
        try { results = repository.search(clean) } catch (_: Exception) { results = emptyList(); failed = true }
        loading = false
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "رجوع") }
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f).focusRequester(focusRequester), singleLine = true, placeholder = { Text("ابحث في كل الأفلام والمسلسلات") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FahdColors.Red, unfocusedBorderColor = FahdColors.Divider), shape = RoundedCornerShape(15.dp))
        }
        Spacer(Modifier.height(18.dp))
        when {
            query.trim().length < 2 -> EmptyMessage("اكتب حرفين على الأقل للبحث")
            loading -> LoadingBlock()
            failed -> EmptyMessage("تعذر تنفيذ البحث، تأكد من الإنترنت وحاول مرة أخرى")
            results.isEmpty() -> EmptyMessage("لا توجد نتائج مطابقة")
            else -> LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) { items(results, key = { it.href }) { PosterCard(it, onSelect) } }
        }
    }
}

@Composable
private fun DetailScreen(
    item: CatalogItem,
    repository: NativeCatalogRepository,
    related: List<CatalogItem>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRecordHistory: () -> Unit,
    onRelated: (CatalogItem) -> Unit,
    onBack: () -> Unit,
    onPlay: (String, String, String?) -> Unit,
    onDownload: (ContentDetail) -> Unit,
) {
    var detail by remember(item.href) { mutableStateOf<ContentDetail?>(null) }
    var loading by remember(item.href) { mutableStateOf(true) }
    var failed by remember(item.href) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(item.href) { try { detail = repository.detail(item); failed = false } catch (_: Exception) { failed = true }; loading = false }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(430.dp).background(FahdColors.Surface)) {
                AsyncImage(item.image, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .16f), FahdColors.Background.copy(alpha = .2f), FahdColors.Background), startY = 0f)))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(Icons.AutoMirrored.Rounded.ArrowForward, "رجوع", onBack); Spacer(Modifier.weight(1f)); GlassIcon(if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (isFavorite) "إزالة من المفضلة" else "حفظ", onToggleFavorite)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(detail?.title ?: item.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp)); Text("${kindLabel(item.kind)}  •  جودة عالية  •  ${if (detail?.episodes?.isNotEmpty() == true) "حلقات متاحة" else "متاح الآن"}", color = FahdColors.Muted)
                }
            }
        }
        if (loading) item { LoadingBlock() }
        if (failed) item { ErrorCard { loading = true; failed = false; scope.launch { try { detail = repository.detail(item) } catch (_: Exception) { failed = true }; loading = false } } }
        detail?.let { loaded ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onRecordHistory(); onPlay(loaded.mediaUrl, loaded.title, item.image) }, enabled = loaded.mediaUrl.isNotBlank(), modifier = Modifier.weight(1.25f).height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = FahdColors.Red)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("شاهد الآن", fontWeight = FontWeight.Black) }
                    OutlinedButton(onClick = { onDownload(loaded) }, enabled = loaded.mediaUrl.isNotBlank(), modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text("تحميل") }
                }
            }
            if (loaded.actors.isNotEmpty()) item { CastRow(loaded.actors) }
            if (loaded.description.isNotBlank()) item { Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) { Text("القصة", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Text(loaded.description, color = FahdColors.Muted, style = MaterialTheme.typography.bodyLarge) } }
            if (loaded.episodes.isNotEmpty()) item {
                Column(Modifier.padding(top = 14.dp)) {
                    Text("الحلقات", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(loaded.episodes) { episode -> OutlinedButton(onClick = { scope.launch { try { val episodeDetail = repository.episode(episode.link, "الحلقة ${episode.number}"); if (episodeDetail.mediaUrl.isNotBlank()) { onRecordHistory(); onPlay(episodeDetail.mediaUrl, "${loaded.title} • الحلقة ${episode.number}", item.image) } else Toast.makeText(context, "مصدر الحلقة غير متاح الآن", Toast.LENGTH_SHORT).show() } catch (_: Exception) { Toast.makeText(context, "تعذر فتح الحلقة، حاول مرة أخرى", Toast.LENGTH_SHORT).show() } } }, shape = RoundedCornerShape(12.dp)) { Text("الحلقة ${episode.number}") } }
                    }
                }
            }
            item { ContentRail("قد يعجبك أيضًا", related.take(12), onSelect = onRelated) }
        }
    }
}

@Composable
private fun CastRow(actors: List<Actor>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text("أبطال العمل", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(actors, key = { it.name }) { actor ->
                Column(Modifier.width(116.dp).clip(RoundedCornerShape(15.dp)).background(FahdColors.SurfaceHigh).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (actor.image.isNotBlank()) AsyncImage(actor.image, actor.name, Modifier.size(72.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(72.dp).clip(CircleShape).background(FahdColors.Divider), contentAlignment = Alignment.Center) { Text(actor.name.take(1), color = FahdColors.Muted, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(7.dp))
                    Text(actor.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    content: List<CatalogItem>,
    favoriteEntries: Set<String>,
    historyEntries: Set<String>,
    onSelect: (CatalogItem) -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    var showHistory by remember { mutableStateOf(false) }
    val entries = if (showHistory) historyEntries else favoriteEntries
    val byHref = remember(content) { content.associateBy { it.href } }
    val visible = remember(entries, byHref) { entries.mapNotNull { byHref[it.substringAfterLast('|')] } }
    Column(Modifier.fillMaxSize()) {
        NativeTopBar("مكتبتي", onSearch = null, onDownloads = onDownloads, onSettings = onSettings)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                onClick = { showHistory = false },
                modifier = Modifier.weight(1f).height(47.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (!showHistory) FahdColors.Red else FahdColors.SurfaceHigh),
            ) { Icon(Icons.Rounded.Bookmark, null); Spacer(Modifier.width(6.dp)); Text("المفضلة") }
            Button(
                onClick = { showHistory = true },
                modifier = Modifier.weight(1f).height(47.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (showHistory) FahdColors.Red else FahdColors.SurfaceHigh),
            ) { Icon(Icons.Rounded.History, null); Spacer(Modifier.width(6.dp)); Text("شوهد مؤخرًا") }
        }
        if (visible.isEmpty()) {
            EmptyMessage(if (showHistory) "ابدأ مشاهدة فيلم أو مسلسل وسيظهر هنا" else "احفظ أعمالك المفضلة لتجدها هنا")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = bottomPadding + 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) { items(visible, key = { it.href }) { PosterCard(it, onSelect) } }
        }
    }
}

@Composable
private fun ChannelsScreen(onBack: () -> Unit, onOpenExternal: (String) -> Unit, bottomPadding: androidx.compose.ui.unit.Dp) {
    val channels = listOf(
        Triple("الجزيرة الإخبارية", "أخبار وبث مباشر", "https://www.aljazeera.net/live"),
        Triple("سكاي نيوز عربية", "أخبار عربية وعالمية", "https://www.skynewsarabia.com/livestream-%D8%A7%D9%84%D8%A8%D8%AB-%D8%A7%D9%84%D9%85%D8%A8%D8%A7%D8%B4%D8%B1"),
        Triple("العربية", "البث المباشر الرسمي", "https://www.alarabiya.net/live-stream"),
        Triple("فرانس 24 عربي", "أخبار دولية بالعربية", "https://www.france24.com/ar/live"),
    )
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "رجوع") }; Text("القنوات المباشرة", style = MaterialTheme.typography.titleLarge) }
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = bottomPadding + 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            items(channels) { channel -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(FahdColors.Surface).clickable { onOpenExternal(channel.third) }.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(13.dp), color = FahdColors.Red.copy(alpha = .15f), modifier = Modifier.size(49.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LiveTv, null, tint = FahdColors.Red) } }; Column(Modifier.padding(horizontal = 13.dp).weight(1f)) { Text(channel.first, fontWeight = FontWeight.Bold); Text(channel.second, color = FahdColors.Muted, fontSize = 12.sp) }; Text("فتح البث", color = FahdColors.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }
        }
    }
}

@Composable
private fun FahdBottomBar(selected: FahdDestination, onSelect: (FahdDestination) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0C0D12), tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding().drawBehind { drawLine(FahdColors.Divider, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1f) }) {
        FahdDestination.entries.forEach { item ->
            val icon = when (item) { FahdDestination.HOME -> Icons.Rounded.Home; FahdDestination.MOVIES -> Icons.Rounded.Movie; FahdDestination.SERIES -> Icons.Rounded.Tv; FahdDestination.CHANNELS -> Icons.Rounded.LiveTv; FahdDestination.LIBRARY -> Icons.Rounded.PersonOutline }
            NavigationBarItem(selected = item == selected, onClick = { onSelect(item) }, icon = { Icon(icon, item.label) }, label = { Text(item.label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = FahdColors.Red, selectedTextColor = FahdColors.Red, indicatorColor = FahdColors.Red.copy(alpha = .13f), unselectedIconColor = FahdColors.Muted, unselectedTextColor = FahdColors.Muted))
        }
    }
}

@Composable
private fun LoadingBlock() { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FahdColors.Red, strokeWidth = 3.dp) } }

@Composable
private fun ErrorCard(onRetry: () -> Unit) { Column(Modifier.padding(18.dp).fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(FahdColors.Surface).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("تعذر تحميل المحتوى", fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text("تأكد من الإنترنت وحاول مرة أخرى", color = FahdColors.Muted); Spacer(Modifier.height(15.dp)); Button(onClick = onRetry) { Text("إعادة المحاولة") } } }

@Composable
private fun EmptyMessage(message: String) { Box(Modifier.fillMaxWidth().fillMaxHeight(.65f), contentAlignment = Alignment.Center) { Text(message, color = FahdColors.Muted, textAlign = TextAlign.Center) } }

private fun kindLabel(kind: CatalogKind) = when (kind) { CatalogKind.MOVIE -> "فيلم"; CatalogKind.SERIES -> "مسلسل"; CatalogKind.ANIME -> "أنمي" }
