package com.echo.ktv.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import coil.compose.AsyncImage
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.echo.ktv.api.KugouApi
import com.echo.ktv.api.MvItem
import com.echo.ktv.api.SingerItem
import com.echo.ktv.api.SongItem
import com.echo.ktv.auth.UserManager
import com.echo.ktv.auth.UserProfile
import com.echo.ktv.playback.KtvPlayerManager
import com.echo.ktv.playback.AccompanimentSource
import com.echo.ktv.playback.PlayableItem
import com.echo.ktv.server.IpUtils
import com.echo.ktv.server.QrCodeUtils
import kotlinx.coroutines.delay

// Premium Commercial KTV Design System & Tokens
object KtvTheme {
    val Background = Color(0xFF0B0F19)
    val SurfaceDark = Color(0xFF131B2E)
    val CardBg = Color(0xFF1E293B).copy(alpha = 0.85f)
    val CardBgHover = Color(0xFF334155)
    val CardBorder = Color(0xFF334155).copy(alpha = 0.6f)
    val Accent = Color(0xFF00E5FF) // Vibrant Cyan
    val AccentPink = Color(0xFFFF2D55)
    val AccentPurple = Color(0xFF8B5CF6)
    val AccentGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFF3B82F6)))
    val GoldGradient = Brush.horizontalGradient(colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500)))
    val TextMain = Color(0xFFF8FAFC)
    val TextMuted = Color(0xFF94A3B8)
}

@Composable
fun FavoriteHeartIcon(
    isFav: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 15.dp
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.85f)
            cubicTo(w * 0.12f, h * 0.58f, 0f, h * 0.32f, w * 0.22f, h * 0.12f)
            cubicTo(w * 0.38f, -h * 0.02f, w * 0.48f, h * 0.14f, w * 0.5f, h * 0.22f)
            cubicTo(w * 0.52f, h * 0.14f, w * 0.62f, -h * 0.02f, w * 0.78f, h * 0.12f)
            cubicTo(w * 1.0f, h * 0.32f, w * 0.88f, h * 0.58f, w * 0.5f, h * 0.85f)
            close()
        }

        if (isFav) {
            drawPath(
                path = path,
                color = Color(0xFFFF2D55)
            )
        } else {
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.85f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.7.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
fun TvFocusableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .scale(if (isFocused) 1.04f else 1.0f)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) KtvTheme.Accent else KtvTheme.CardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        content(isFocused)
    }
}

@Composable
fun MainTvScreen() {
    val context = LocalContext.current

    var currentTab by remember { mutableStateOf("home") } // home, search, queue, songs_list, category, favorites, history
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf("song") } // song or singer
    var searchSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var searchSingers by remember { mutableStateOf<List<SingerItem>>(emptyList()) }
    var hotSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var displaySongsList by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var listTitle by remember { mutableStateOf("") }
    var currentCategoryName by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var showDspDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    val userProfile by UserManager.userProfile.collectAsState()

    var currentSingerId by remember { mutableStateOf(0) }
    var currentSingerName by remember { mutableStateOf("") }
    var currentSingerPage by remember { mutableStateOf(1) }
    var totalSongsCount by remember { mutableStateOf(0) }
    var hasMoreSongs by remember { mutableStateOf(false) }
    var isLoadingMoreSongs by remember { mutableStateOf(false) }

    val playlist by KtvPlayerManager.playlist.collectAsState()
    val currentPlaying by KtvPlayerManager.currentPlaying.collectAsState()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()
    val history by KtvPlayerManager.history.collectAsState()
    val favorites by KtvPlayerManager.favorites.collectAsState()
    val localSongs by KtvPlayerManager.localSongs.collectAsState()

    val localIp = remember { IpUtils.getLocalIpAddress() }
    val qrBitmap = remember(localIp) {
        QrCodeUtils.generateQrCode("http://$localIp:19985/")
    }

    // Total ordered count = queued + currently playing
    val totalSelectedCount = playlist.size + (if (currentPlaying != null) 1 else 0)

    // Explicit search execution function called on Search button click
    val performSearch: () -> Unit = {
        if (searchKeyword.isNotBlank()) {
            Toast.makeText(context, "🔍 正在检索: $searchKeyword", Toast.LENGTH_SHORT).show()
            if (searchMode == "song") {
                KugouApi.searchSong(searchKeyword) { result ->
                    result.onSuccess { searchSongs = it }
                }
            } else {
                KugouApi.searchSinger(searchKeyword) { result ->
                    result.onSuccess { searchSingers = it }
                }
            }
        }
    }

    // Load initial Hot songs
    LaunchedEffect(Unit) {
        KugouApi.getHotSongs { result ->
            result.onSuccess { hotSongs = it }
        }
    }

    // Handle Back Press for TV navigation
    if (isPlayerFullscreen) {
        BackHandler {
            isPlayerFullscreen = false
        }
    } else if (currentTab == "category" && currentCategoryName.isNotEmpty()) {
        BackHandler {
            currentCategoryName = ""
        }
    } else if (currentTab != "home") {
        BackHandler {
            currentTab = "home"
        }
    }

    if (isPlayerFullscreen && currentPlaying != null) {
        // Fullscreen Player
        VideoPlayerOverlay(
            item = currentPlaying!!,
            onDspClick = { showDspDialog = true },
            onCloseFullscreen = { isPlayerFullscreen = false }
        )
    } else {
        // Main Home Layout or Tab Sub-screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF111726), Color(0xFF070A13))
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar (Clean 6 Essential Buttons)
                TopBar(
                    selectedSize = totalSelectedCount,
                    userProfile = userProfile,
                    onLoginClick = { showLoginDialog = true },
                    onUserProfileClick = { showUserProfileDialog = true },
                    onSearchClick = { currentTab = "search" },
                    onQueueClick = { currentTab = "queue" },
                    onQrClick = { showQrDialog = true },
                    onVocalClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) },
                    onSkipClick = { KtvPlayerManager.skipCurrent() },
                    onPlayPauseClick = { KtvPlayerManager.togglePlayPause() },
                    isVocalEliminated = isVocalEliminated,
                    isPlaying = isPlaying
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (currentTab == "home") {
                    // Home Dashboard layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Left Area (Playback Preview Card & 3 Quick Cards): 45% width
                        Column(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight()
                        ) {
                            PlaybackPreviewCard(
                                currentPlaying = currentPlaying,
                                isPlaying = isPlaying,
                                playlist = playlist,
                                onExpandClick = {
                                    if (currentPlaying != null) {
                                        isPlayerFullscreen = true
                                    } else {
                                        Toast.makeText(context, "请先点歌后再进入全屏播放", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Bottom 3 cards Row: 常唱, 收藏, 分类
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(88.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GridCard(
                                    title = "常唱",
                                    subtitle = "历史点播",
                                    emoji = "🎙",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF0BA360), Color(0xFF3CBA92))
                                    ),
                                    onClick = {
                                        displaySongsList = history
                                        listTitle = "🎙 经典常唱歌曲 (${history.size} 首)"
                                        currentTab = "history"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "收藏",
                                    subtitle = "我的红心",
                                    emoji = "❤️",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFF0844), Color(0xFFFFB199))
                                    ),
                                    onClick = {
                                        displaySongsList = favorites
                                        listTitle = "❤️ 我的收藏歌单 (${favorites.size} 首)"
                                        currentTab = "favorites"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "分类",
                                    subtitle = "智能曲库",
                                    emoji = "💬",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00B4DB), Color(0xFF0083B0))
                                    ),
                                    onClick = {
                                        currentTab = "category"
                                        currentCategoryName = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Middle Area (2x2 Feature Grid): 35% width
                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GridCard(
                                    title = "排行榜",
                                    subtitle = "热歌金曲",
                                    emoji = "👑",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF0052D4), Color(0xFF4364F7), Color(0xFF6FB1FC))
                                    ),
                                    onClick = {
                                        displaySongsList = hotSongs
                                        listTitle = "👑 排行榜热门推荐"
                                        currentTab = "songs_list"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "歌名",
                                    subtitle = "拼音点歌",
                                    emoji = "📢",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF7F00FF), Color(0xFFE100FF))
                                    ),
                                    onClick = {
                                        searchMode = "song"
                                        currentTab = "search"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GridCard(
                                    title = "歌星",
                                    subtitle = "热门歌手",
                                    emoji = "⭐",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFF8008), Color(0xFFFFC837))
                                    ),
                                    onClick = {
                                        searchMode = "singer"
                                        currentTab = "search"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "本地",
                                    subtitle = "离线歌库",
                                    emoji = "📁",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B))
                                    ),
                                    onClick = {
                                        displaySongsList = localSongs
                                        listTitle = "📁 本地离线缓存歌曲 (${localSongs.size} 首)"
                                        currentTab = "songs_list"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Right Area (Tall "新歌榜" card): 20% width
                        TallFeatureCard(
                            title = "新歌榜",
                            songs = hotSongs,
                            onClick = {
                                displaySongsList = hotSongs
                                listTitle = "🔥 酷唱新歌推荐榜"
                                currentTab = "songs_list"
                            },
                            modifier = Modifier
                                .weight(0.2f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    // Sub-screens
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (currentTab) {
                            "songs_list" -> SongsListGrid(
                                title = listTitle,
                                songs = displaySongsList,
                                totalCount = if (totalSongsCount > 0) totalSongsCount else displaySongsList.size,
                                hasMore = hasMoreSongs,
                                isLoadingMore = isLoadingMoreSongs,
                                onLoadMore = {
                                    if (!isLoadingMoreSongs && hasMoreSongs) {
                                        isLoadingMoreSongs = true
                                        val nextPage = currentSingerPage + 1
                                        KugouApi.getSingerSongs(currentSingerId, currentSingerName, page = nextPage, pageSize = 30) { result ->
                                            isLoadingMoreSongs = false
                                            result.onSuccess { (list, total) ->
                                                if (list.isNotEmpty()) {
                                                    currentSingerPage = nextPage
                                                    displaySongsList = displaySongsList + list
                                                    totalSongsCount = total
                                                    hasMoreSongs = displaySongsList.size < total
                                                } else {
                                                    hasMoreSongs = false
                                                }
                                            }
                                            result.onFailure {
                                                isLoadingMoreSongs = false
                                                Toast.makeText(context, "加载更多失败，请重试", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onSelect = { song -> KtvPlayerManager.addSongToQueue(song) },
                                onBack = { currentTab = "home" }
                            )
                            "favorites" -> SongsListGrid(
                                title = "❤️ 我的收藏歌单 (${favorites.size} 首)",
                                songs = favorites,
                                onSelect = { song -> KtvPlayerManager.addSongToQueue(song) },
                                onBack = { currentTab = "home" }
                            )
                            "history" -> SongsListGrid(
                                title = "🎙 经典常唱记录 (${history.size} 首)",
                                songs = history,
                                onSelect = { song -> KtvPlayerManager.addSongToQueue(song) },
                                onBack = { currentTab = "home" }
                            )
                            "category" -> {
                                if (currentCategoryName.isNotEmpty()) {
                                    SongsListGrid(
                                        title = "💬 分类 - $currentCategoryName",
                                        songs = displaySongsList,
                                        onSelect = { song -> KtvPlayerManager.addSongToQueue(song) },
                                        onBack = { currentCategoryName = "" }
                                    )
                                } else {
                                    CategorySelectionGrid(
                                        onSelect = { category ->
                                            currentCategoryName = category
                                            Toast.makeText(context, "正在查询分类: $category", Toast.LENGTH_SHORT).show()
                                            KugouApi.searchSong(category) { result ->
                                                result.onSuccess {
                                                    displaySongsList = it
                                                    if (it.isEmpty()) {
                                                        Toast.makeText(context, "未找到该分类的歌曲", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                result.onFailure { err ->
                                                    Toast.makeText(context, "获取分类失败: ${err.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onBack = { currentTab = "home" }
                                    )
                                }
                            }
                            "search" -> SearchContent(
                                keyword = searchKeyword,
                                onKeywordChange = { searchKeyword = it },
                                searchMode = searchMode,
                                onSearchModeChange = { searchMode = it },
                                onExecuteSearch = performSearch,
                                songs = searchSongs,
                                singers = searchSingers,
                                onSelectSong = { song -> KtvPlayerManager.addSongToQueue(song) },
                                onSelectSinger = { singer ->
                                    currentSingerId = singer.singerId
                                    currentSingerName = singer.singerName
                                    currentSingerPage = 1
                                    listTitle = "👤 ${singer.singerName} - 全部歌曲"
                                    currentTab = "songs_list"
                                    Toast.makeText(context, "正在加载 ${singer.singerName} 的全部曲目...", Toast.LENGTH_SHORT).show()
                                    KugouApi.getSingerSongs(singer.singerId, singer.singerName, page = 1, pageSize = 30) { result ->
                                        result.onSuccess { (list, total) ->
                                            displaySongsList = list
                                            totalSongsCount = total
                                            hasMoreSongs = list.size < total
                                        }
                                        result.onFailure {
                                            Toast.makeText(context, "获取曲库失败: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onBack = { currentTab = "home" }
                            )
                            "queue" -> PlaylistQueueContent(
                                list = playlist,
                                currentPlaying = currentPlaying,
                                onBack = { currentTab = "home" },
                                onGoSearch = {
                                    searchMode = "song"
                                    currentTab = "search"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showQrDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .width(540.dp)
                    .background(Color(0xFF131C2E), RoundedCornerShape(16.dp))
                    .border(1.5.dp, KtvTheme.Accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Large QR Code with clean white padding
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "点歌二维码",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Right Column: Instructions, IP URL, and Close Button
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "📱 手机扫码点歌",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "手机连接同一 WiFi 即可扫码无线点歌",
                            fontSize = 13.sp,
                            color = KtvTheme.TextMuted,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // URL IP Badge
                        Box(
                            modifier = Modifier
                                .background(KtvTheme.Accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, KtvTheme.Accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "http://$localIp:19985",
                                fontSize = 13.sp,
                                color = KtvTheme.Accent,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        TvFocusableItem(
                            onClick = { showQrDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .background(if (isFocused) KtvTheme.Accent else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 22.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "关闭",
                                    color = if (isFocused) Color.Black else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDspDialog) {
        KtvDspTuningDialog(onDismiss = { showDspDialog = false })
    }
    if (showLoginDialog) {
        ConceptQrLoginDialog(onDismiss = { showLoginDialog = false })
    }
    if (showUserProfileDialog && userProfile != null) {
        UserProfileDialog(
            userProfile = userProfile!!,
            onDismiss = { showUserProfileDialog = false },
            onLogout = {
                UserManager.logout()
                showUserProfileDialog = false
                Toast.makeText(context, "?? ?????", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun KtvDspTuningDialog(
    onDismiss: () -> Unit
) {
    val dspSettings by KtvPlayerManager.dspSettings.collectAsState()
    var cutDepth by remember(dspSettings) { mutableStateOf(dspSettings.vocalCutDepth) }
    var bassBoost by remember(dspSettings) { mutableStateOf(dspSettings.bassBoost) }
    var gainBoost by remember(dspSettings) { mutableStateOf(dspSettings.gainBoost) }
    var channelMode by remember(dspSettings) { mutableStateOf(dspSettings.channelMode) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(580.dp)
                .background(Color(0xFF131C2E), RoundedCornerShape(16.dp))
                .border(1.5.dp, KtvTheme.Accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎛", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "消音伴奏调音台",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // One-Click Reset Button
                    TvFocusableItem(
                        onClick = {
                            KtvPlayerManager.resetDspSettingsToDefault()
                        }
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isFocused) Color(0xFFEF4444) else Color(0xFF1E293B),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("🔄 恢复最佳默认", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Channel Mode Row
                Text("声道伴奏模式", fontSize = 13.sp, color = KtvTheme.TextMuted, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val modes = listOf("智能立体声消音", "左声道伴奏", "右声道伴奏")
                    modes.forEachIndexed { index, modeName ->
                        TvFocusableItem(
                            onClick = {
                                channelMode = index
                                KtvPlayerManager.updateDspSettings(
                                    dspSettings.copy(channelMode = index)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (channelMode == index) KtvTheme.Accent else Color(0xFF1E293B),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (channelMode == index) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Slider 1: 消音深度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎤 人声消减深度", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${(cutDepth * 100).toInt()}%", fontSize = 14.sp, color = KtvTheme.Accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = cutDepth,
                    onValueChange = {
                        cutDepth = it
                    },
                    onValueChangeFinished = {
                        KtvPlayerManager.updateDspSettings(
                            dspSettings.copy(vocalCutDepth = cutDepth)
                        )
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = KtvTheme.Accent,
                        activeTrackColor = KtvTheme.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                // Slider 2: 低音丰满度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🥁 伴奏低音丰满度", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${(bassBoost * 100).toInt()}%", fontSize = 14.sp, color = KtvTheme.Accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = bassBoost,
                    onValueChange = {
                        bassBoost = it
                    },
                    onValueChangeFinished = {
                        KtvPlayerManager.updateDspSettings(
                            dspSettings.copy(bassBoost = bassBoost)
                        )
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = KtvTheme.Accent,
                        activeTrackColor = KtvTheme.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                // Slider 3: 伴奏音量补偿
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔊 伴奏音量增益补偿", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${(gainBoost * 100).toInt()}%", fontSize = 14.sp, color = KtvTheme.Accent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = gainBoost,
                    onValueChange = {
                        gainBoost = it
                    },
                    onValueChangeFinished = {
                        KtvPlayerManager.updateDspSettings(
                            dspSettings.copy(gainBoost = gainBoost)
                        )
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = KtvTheme.Accent,
                        activeTrackColor = KtvTheme.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Close Button
                TvFocusableItem(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .background(if (isFocused) KtvTheme.Accent else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("完成", color = if (isFocused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TopBar(
    selectedSize: Int,
    userProfile: UserProfile?,
    onLoginClick: () -> Unit,
    onUserProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onQueueClick: () -> Unit,
    onQrClick: () -> Unit,
    onVocalClick: () -> Unit,
    onSkipClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    isVocalEliminated: Boolean,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF131C2E).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.dp, KtvTheme.CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Title, Branding & User Status Chip
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(KtvTheme.AccentGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("??", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "?? KTV",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PRO",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = KtvTheme.Accent,
                modifier = Modifier
                    .background(KtvTheme.Accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // User Status Capsule (Compact, Anti-Wrap)
            if (userProfile == null) {
                TvFocusableItem(onClick = onLoginClick) { isFocused ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (isFocused) KtvTheme.Accent else Color(0xFF1E293B),
                                RoundedCornerShape(20.dp)
                            )
                            .border(1.dp, if (isFocused) KtvTheme.Accent else Color(0xFF334155), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("??", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "?????",
                            fontSize = 12.sp,
                            color = if (isFocused) Color.Black else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            } else {
                TvFocusableItem(onClick = onUserProfileClick) { isFocused ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (isFocused) KtvTheme.Accent else Color(0xFF1E293B),
                                RoundedCornerShape(20.dp)
                            )
                            .border(1.dp, if (userProfile.isVip) Color(0xFFF59E0B) else Color(0xFF334155), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (userProfile.isVip) "??" else "??", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = userProfile.nickname.take(4),
                            fontSize = 12.sp,
                            color = if (isFocused) Color.Black else if (userProfile.isVip) Color(0xFFFDE047) else Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (userProfile.isVip) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VIP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isFocused) Color.Black else Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons Row (6 Essential Buttons with compact horizontal spacing)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TopBarButton(label = "??", icon = "??", onClick = onSearchClick)
            TopBarButton(
                label = "?? ($selectedSize)",
                icon = "??",
                isHighlighted = selectedSize > 0,
                onClick = onQueueClick
            )
            TopBarButton(label = "????", icon = "??", onClick = onQrClick)
            TopBarButton(
                label = if (isVocalEliminated) "??" else "??",
                icon = "??",
                isHighlighted = isVocalEliminated,
                onClick = onVocalClick
            )
            TopBarButton(label = "??", icon = "?", onClick = onSkipClick)
            TopBarButton(
                label = if (isPlaying) "??" else "??",
                icon = if (isPlaying) "?" else "?",
                onClick = onPlayPauseClick
            )
        }
    }
}

@Composable
fun TopBarButton(
    label: String,
    icon: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    TvFocusableItem(onClick = onClick) { isFocused ->
        Row(
            modifier = Modifier
                .background(
                    when {
                        isFocused -> KtvTheme.Accent
                        isHighlighted -> Color(0xFFE11D48)
                        else -> Color(0xFF1E293B)
                    },
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun PlaybackPreviewCard(
    currentPlaying: PlayableItem?,
    isPlaying: Boolean,
    playlist: List<PlayableItem>,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val player = KtvPlayerManager.getPlayer()

    TvFocusableItem(
        onClick = onExpandClick,
        modifier = modifier
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
        ) {
            if (currentPlaying != null && player != null) {
                // Live ExoPlayer Surface View
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setPlayer(player)
                        }
                    },
                    update = { view ->
                        if (view.player != player) {
                            view.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay gradient for text legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                // Current song info at bottom left
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (currentPlaying is PlayableItem.Mv) "🎬 MV 播放中" else "🎵 原唱音频",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KtvTheme.Accent,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentPlaying.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentPlaying.artist,
                        fontSize = 14.sp,
                        color = KtvTheme.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Expand button indicator at top right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("⛶ 全屏", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Empty state when no song is playing
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                            .border(2.dp, Color(0xFF334155), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 34.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "当前暂无歌曲正在播放",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "点击右侧板块或顶部【搜索】快速点歌",
                        fontSize = 13.sp,
                        color = KtvTheme.TextMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GridCard(
    title: String,
    subtitle: String,
    emoji: String,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusableItem(
        onClick = onClick,
        modifier = modifier
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = emoji,
                fontSize = 34.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .scale(if (isFocused) 1.15f else 1.0f)
            )
        }
    }
}

@Composable
fun TallFeatureCard(
    title: String,
    songs: List<SongItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusableItem(
        onClick = onClick,
        modifier = modifier
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                    ),
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "HOT SONGS",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(14.dp))

                songs.take(3).forEachIndexed { index, song ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "${index + 1}. ${song.title}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    }
                }
            }

            // Big 🔥 Flame Emoji at Bottom Right Corner
            Text(
                text = "🔥",
                fontSize = 48.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .scale(if (isFocused) 1.2f else 1.0f)
            )
        }
    }
}

@Composable
fun SongsListGrid(
    title: String,
    songs: List<SongItem>,
    totalCount: Int = songs.size,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    onSelect: (SongItem) -> Unit,
    onBack: () -> Unit
) {
    val favorites by KtvPlayerManager.favorites.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Row with Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvFocusableItem(onClick = onBack) { _ ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("← 返回", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = KtvTheme.TextMain
                )
            }

            Text(
                text = if (totalCount > songs.size) "已加载 ${songs.size} / 共 $totalCount 首" else "共 ${songs.size} 首歌曲",
                fontSize = 13.sp,
                color = KtvTheme.TextMuted
            )
        }

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF131C2E).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("暂无歌曲数据", color = KtvTheme.TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("去搜索或分类中发现更多精彩歌曲吧", color = KtvTheme.TextMuted.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(songs) { index, song ->
                    val isFav = favorites.any { 
                        (it.hash.isNotEmpty() && it.hash == song.hash) || 
                        (it.title == song.title && it.artist == song.artist) 
                    }

                    TvFocusableItem(
                        onClick = { onSelect(song) },
                        modifier = Modifier.fillMaxWidth()
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isFocused) Color(0xFF1E293B) else Color(0xFF131C2E))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Rank / Index Badge
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        when (index) {
                                            0 -> Color(0xFFFFD700)
                                            1 -> Color(0xFFC0C0C0)
                                            2 -> Color(0xFFCD7F32)
                                            else -> Color(0xFF1E293B)
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (index < 3) Color.Black else Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Song Title & Artist
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 13.sp,
                                    color = KtvTheme.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Action Buttons: Favorite Heart + 点歌 (Strictly uniform 28dp height)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Favorite Heart Button (34x28dp)
                                TvFocusableItem(
                                    onClick = { KtvPlayerManager.toggleFavorite(song) },
                                    modifier = Modifier.size(width = 34.dp, height = 28.dp)
                                ) { _ ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isFav) Color(0xFFFF2D55).copy(alpha = 0.18f) else Color(0xFF1E293B),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isFav) Color(0xFFFF2D55).copy(alpha = 0.6f) else KtvTheme.CardBorder,
                                                RoundedCornerShape(6.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FavoriteHeartIcon(isFav = isFav, size = 15.dp)
                                    }
                                }

                                // Add to Queue Badge
                                Box(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .background(KtvTheme.Accent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .border(1.dp, KtvTheme.Accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+ 点歌", color = KtvTheme.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Load More Button spanning across 2 columns
                if (hasMore && onLoadMore != null) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        TvFocusableItem(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .background(
                                        if (isFocused) KtvTheme.Accent else Color(0xFF1E293B),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(1.dp, KtvTheme.Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⏳", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("正在加载更多歌曲...", color = if (isFocused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⬇️", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("点击加载更多歌曲 (已加载 ${songs.size} / 共 $totalCount 首)", color = if (isFocused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionGrid(
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf("国语流行", "经典粤语", "欧美金曲", "民谣摇滚", "抖音热歌", "影视原声")
    val gradients = listOf(
        Brush.horizontalGradient(colors = listOf(Color(0xFFFF8C00), Color(0xFFFF0080))),
        Brush.horizontalGradient(colors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF))),
        Brush.horizontalGradient(colors = listOf(Color(0xFF7F00FF), Color(0xFFE100FF))),
        Brush.horizontalGradient(colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
        Brush.horizontalGradient(colors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))),
        Brush.horizontalGradient(colors = listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2)))
    )
    val emojis = listOf("🎵", "🎙", "🌍", "🎸", "🔥", "🎬")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvFocusableItem(onClick = onBack) { _ ->
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("← 返回首页", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text("💬 在线智能歌曲分类", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(categories.size) { index ->
                val name = categories[index]
                TvFocusableItem(
                    onClick = { onSelect(name) },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradients[index], RoundedCornerShape(12.dp))
                            .padding(18.dp)
                    ) {
                        Text(name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.align(Alignment.CenterStart))
                        Text(emojis[index], fontSize = 42.sp, modifier = Modifier.align(Alignment.CenterEnd).scale(if (isFocused) 1.2f else 1.0f))
                    }
                }
            }
        }
    }
}

@Composable
fun SearchContent(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    searchMode: String, // "song" or "singer"
    onSearchModeChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    songs: List<SongItem>,
    singers: List<SingerItem>,
    onSelectSong: (SongItem) -> Unit,
    onSelectSinger: (SingerItem) -> Unit,
    onBack: () -> Unit
) {
    val letters = ('A'..'Z').map { it.toString() }
    val favorites by KtvPlayerManager.favorites.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: A-Z Keyboard Panel (width 260dp)
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(Color(0xFF131C2E), RoundedCornerShape(12.dp))
                .border(1.dp, KtvTheme.CardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "拼音首字母键盘",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = KtvTheme.Accent
                )
                TvFocusableItem(onClick = onBack) { _ ->
                    Text("← 首页", color = KtvTheme.TextMuted, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Letter Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(letters) { letter ->
                    TvFocusableItem(
                        onClick = {
                            onKeywordChange(keyword + letter)
                        },
                        modifier = Modifier.height(36.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isFocused) KtvTheme.Accent else Color(0xFF1E293B), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons: 退格, 清空, 搜索
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TvFocusableItem(
                    onClick = {
                        if (keyword.isNotEmpty()) {
                            onKeywordChange(keyword.dropLast(1))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEF4444), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⌫ 退格", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                TvFocusableItem(
                    onClick = {
                        onKeywordChange("")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF475569), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🗑 清空", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                TvFocusableItem(
                    onClick = onExecuteSearch,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(38.dp)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isFocused) KtvTheme.Accent else Color(0xFF2563EB), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔍 搜索", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right Column: Search Results
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Search Input Header & Mode Toggle with Interactive BasicTextField
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131C2E), RoundedCornerShape(12.dp))
                    .border(1.dp, KtvTheme.CardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    BasicTextField(
                        value = keyword,
                        onValueChange = onKeywordChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(KtvTheme.Accent),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { onExecuteSearch() }
                        ),
                        decorationBox = { innerTextField ->
                            if (keyword.isEmpty()) {
                                Text(
                                    text = "搜索歌曲 / 歌手...",
                                    fontSize = 15.sp,
                                    color = KtvTheme.TextMuted
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mode Toggle: 搜歌曲 vs 搜歌手
                    TvFocusableItem(
                        onClick = { onSearchModeChange("song") }
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (searchMode == "song") KtvTheme.Accent else Color(0xFF1E293B),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🎵 搜歌曲",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (searchMode == "song") Color.Black else Color.White
                            )
                        }
                    }

                    TvFocusableItem(
                        onClick = { onSearchModeChange("singer") }
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (searchMode == "singer") KtvTheme.Accent else Color(0xFF1E293B),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "👤 搜歌手",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (searchMode == "singer") Color.Black else Color.White
                            )
                        }
                    }

                    TvFocusableItem(
                        onClick = onExecuteSearch
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2563EB), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("🔍 开始搜索", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results List
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (searchMode == "song") {
                    if (songs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (keyword.isEmpty()) "输入歌名或拼音缩写开始点歌" else "未搜索到相关歌曲",
                                color = KtvTheme.TextMuted,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(songs) { song ->
                                val isFav = favorites.any { 
                                    (it.hash.isNotEmpty() && it.hash == song.hash) || 
                                    (it.title == song.title && it.artist == song.artist) 
                                }

                                TvFocusableItem(
                                    onClick = { onSelectSong(song) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { isFocused ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isFocused) Color(0xFF1E293B) else Color(0xFF131C2E))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.artist,
                                                fontSize = 12.sp,
                                                color = KtvTheme.TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TvFocusableItem(
                                                onClick = { KtvPlayerManager.toggleFavorite(song) },
                                                modifier = Modifier.size(width = 34.dp, height = 28.dp)
                                            ) { _ ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            if (isFav) Color(0xFFFF2D55).copy(alpha = 0.18f) else Color(0xFF1E293B),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (isFav) Color(0xFFFF2D55).copy(alpha = 0.6f) else KtvTheme.CardBorder,
                                                            RoundedCornerShape(6.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    FavoriteHeartIcon(isFav = isFav, size = 15.dp)
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .background(KtvTheme.Accent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                    .border(1.dp, KtvTheme.Accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("+ 点歌", color = KtvTheme.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Singer Search Results
                    if (singers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (keyword.isEmpty()) "输入歌手姓名或拼音缩写开始点歌" else "未搜索到相关歌手",
                                color = KtvTheme.TextMuted,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(singers) { singer ->
                                TvFocusableItem(
                                    onClick = { onSelectSinger(singer) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { _ ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF131C2E), RoundedCornerShape(12.dp))
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (singer.imgUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = singer.imgUrl,
                                                contentDescription = singer.singerName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, KtvTheme.Accent.copy(alpha = 0.6f), CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                                        ),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("👤", fontSize = 28.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = singer.singerName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KtvTheme.TextMain,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (singer.songCount > 0) "共 ${singer.songCount} 首曲目" else "点击查看曲库",
                                            fontSize = 12.sp,
                                            color = if (singer.songCount > 0) KtvTheme.Accent else KtvTheme.TextMuted,
                                            fontWeight = if (singer.songCount > 0) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistQueueContent(
    list: List<PlayableItem>,
    currentPlaying: PlayableItem?,
    onBack: () -> Unit,
    onGoSearch: () -> Unit
) {
    val favorites by KtvPlayerManager.favorites.collectAsState()
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Row with Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvFocusableItem(onClick = onBack) { _ ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("← 返回首页", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "🎶 已点点播队列 (${list.size} 首待播)",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = KtvTheme.TextMain
                )
            }

            TvFocusableItem(onClick = onGoSearch) { _ ->
                Box(
                    modifier = Modifier
                        .background(KtvTheme.AccentGradient, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("+ 继续点歌", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        // Currently Playing Hero Card
        if (currentPlaying != null) {
            val isCurrentFav = favorites.any { it.title == currentPlaying.title }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.5.dp, KtvTheme.Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(KtvTheme.Accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("正在播放", fontSize = 12.sp, color = KtvTheme.Accent, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isVocalEliminated) "【伴奏模式】" else "【原唱模式】",
                                    fontSize = 11.sp,
                                    color = if (isVocalEliminated) Color(0xFFE11D48) else Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${currentPlaying.title} - ${currentPlaying.artist}",
                                fontSize = 17.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Actions for currently playing song
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TopBarButton(
                            label = "收藏",
                            icon = if (isCurrentFav) "❤️" else "♡",
                            isHighlighted = isCurrentFav,
                            onClick = { KtvPlayerManager.toggleCurrentFavorite() }
                        )
                        TopBarButton(
                            label = if (isVocalEliminated) "切回原唱" else "切至伴奏",
                            icon = "🎤",
                            isHighlighted = isVocalEliminated,
                            onClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) }
                        )
                        TopBarButton(
                            label = "切歌",
                            icon = "⏭",
                            onClick = { KtvPlayerManager.skipCurrent() }
                        )
                    }
                }
            }
        }

        // Upcoming Queue List
        if (list.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF131C2E).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("待播队列为空，快去点首歌吧！", color = KtvTheme.TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(list) { index, item ->
                    val songItem = when (item) {
                        is PlayableItem.Song -> item.songItem
                        is PlayableItem.Mv -> SongItem(item.mvItem.title, item.mvItem.artist, item.mvItem.mvHash, "mv", item.mvItem.duration, item.mvItem.mvHash)
                    }
                    val isFav = favorites.any { 
                        (it.hash.isNotEmpty() && it.hash == songItem.hash) || 
                        (it.title == songItem.title && it.artist == songItem.artist) 
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF131C2E), RoundedCornerShape(10.dp))
                            .border(1.dp, KtvTheme.CardBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Order index badge
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFF1E293B), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KtvTheme.Accent)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = item.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artist,
                                        fontSize = 13.sp,
                                        color = KtvTheme.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // 3 Powerful Actions: 置顶, 立即播, 删除
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 收藏 toggle (34x28dp)
                                TvFocusableItem(
                                    onClick = { KtvPlayerManager.toggleFavorite(songItem) },
                                    modifier = Modifier.size(width = 34.dp, height = 28.dp)
                                ) { _ ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isFav) Color(0xFFFF2D55).copy(alpha = 0.18f) else Color(0xFF1E293B),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isFav) Color(0xFFFF2D55).copy(alpha = 0.6f) else KtvTheme.CardBorder,
                                                RoundedCornerShape(6.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FavoriteHeartIcon(isFav = isFav, size = 15.dp)
                                    }
                                }

                                if (index > 0) {
                                    TvFocusableItem(onClick = { KtvPlayerManager.moveToTop(index) }) { isFocused ->
                                        Box(
                                            modifier = Modifier
                                                .background(if (isFocused) KtvTheme.Accent else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("🔝 置顶", color = if (isFocused) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                TvFocusableItem(onClick = { KtvPlayerManager.playNow(index) }) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .background(if (isFocused) KtvTheme.Accent else Color(0xFF2563EB), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                        Text("▶ 立即播", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TvFocusableItem(onClick = { KtvPlayerManager.removeAt(index) }) { isFocused ->
                                    Box(
                                        modifier = Modifier
                                            .background(if (isFocused) Color(0xFFDC2626) else Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("🗑 删除", color = if (isFocused) Color.White else Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerOverlay(
    item: PlayableItem,
    onDspClick: () -> Unit = {},
    onCloseFullscreen: () -> Unit
) {
    val player = KtvPlayerManager.getPlayer()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()
    val musicVolume by KtvPlayerManager.musicVolume.collectAsState()
    val favorites by KtvPlayerManager.favorites.collectAsState()

    val isCurrentFav = favorites.any { it.title == item.title }

    var showControls by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var totalDurationMs by remember { mutableStateOf(0L) }

    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    LaunchedEffect(isPlaying) {
        while (true) {
            player?.let { p ->
                currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                totalDurationMs = p.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    showControls = true
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            player?.let { p ->
                                val newPos = (p.currentPosition - 10000).coerceAtLeast(0L)
                                p.seekTo(newPos)
                                currentPositionMs = newPos
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            player?.let { p ->
                                val newPos = (p.currentPosition + 10000).coerceAtMost(p.duration.coerceAtLeast(0L))
                                p.seekTo(newPos)
                                currentPositionMs = newPos
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Spacebar -> {
                            KtvPlayerManager.togglePlayPause()
                            true
                        }
                        Key.Escape, Key.Back -> {
                            onCloseFullscreen()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Player Surface View
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setPlayer(player)
                    }
                },
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Vinyl Disc animation for Audio songs
        if (item is PlayableItem.Song) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2C1B4D), Color(0xFF070A13))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 40.dp)
                ) {
                    // Rotating Vinyl Disc
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .rotate(if (isPlaying) angle else 0f)
                            .background(Color(0xFF1E1E1E), CircleShape)
                            .border(6.dp, Color(0xFF333333), CircleShape)
                            .border(2.dp, KtvTheme.Accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(KtvTheme.AccentGradient, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎵", fontSize = 28.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (isVocalEliminated) "正在伴奏模式..." else "正在原唱播放...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = KtvTheme.Accent
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.artist,
                        fontSize = 18.sp,
                        color = KtvTheme.TextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvFocusableItem(onClick = onCloseFullscreen) { _ ->
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("← 退出全屏", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (isVocalEliminated) "正在伴奏模式..." else "正在原唱播放...",
                            color = KtvTheme.Accent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("💡 方向键左右可快进快退", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    }
                }

                // Bottom Progress Bar and Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    // Seek Progress Slider Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentSec = currentPositionMs / 1000
                        val totalSec = totalDurationMs / 1000
                        val currentStr = String.format("%02d:%02d", currentSec / 60, currentSec % 60)
                        val totalStr = String.format("%02d:%02d", totalSec / 60, totalSec % 60)

                        Slider(
                            value = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs.toFloat() else 0f,
                            onValueChange = { fraction ->
                                player?.let { p ->
                                    val target = (fraction * p.duration).toLong()
                                    p.seekTo(target)
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = KtvTheme.Accent,
                                activeTrackColor = KtvTheme.Accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$currentStr / $totalStr",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = item.artist,
                                fontSize = 14.sp,
                                color = KtvTheme.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Bottom Control Buttons (Play/Pause, Favorite Heart, Vocal switch, Dsp Tuning, Skip, Volume)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TopBarButton(
                                label = if (isPlaying) "暂停" else "播放",
                                icon = if (isPlaying) "⏸" else "▶",
                                onClick = { KtvPlayerManager.togglePlayPause() }
                            )
                            TopBarButton(
                                label = "收藏",
                                icon = if (isCurrentFav) "❤️" else "♡",
                                isHighlighted = isCurrentFav,
                                onClick = { KtvPlayerManager.toggleCurrentFavorite() }
                            )
                            TopBarButton(
                                label = if (isVocalEliminated) "伴奏" else "原唱",
                                icon = "🎤",
                                isHighlighted = isVocalEliminated,
                                onClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) }
                            )
                            TopBarButton(
                                label = "调音",
                                icon = "🎛",
                                onClick = onDspClick
                            )
                            TopBarButton(
                                label = "切歌",
                                icon = "⏭",
                                onClick = { KtvPlayerManager.skipCurrent() }
                            )
                            TopBarButton(
                                label = "音量: ${(musicVolume * 100).toInt()}%",
                                icon = "🔊",
                                onClick = {
                                    val nextVol = if (musicVolume >= 1.0f) 0.2f else musicVolume + 0.2f
                                    KtvPlayerManager.setVolume(nextVol)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ConceptQrLoginDialog(
    onDismiss: () -> Unit
) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrKey by remember { mutableStateOf("") }
    var qrStatusText by remember { mutableStateOf("? ???????...") }
    var isExpired by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun loadQr() {
        qrStatusText = "? ???????..."
        isExpired = false
        qrBitmap = null
        KugouApi.fetchConceptQrCode { result ->
            result.onSuccess { (key, url) ->
                qrKey = key
                qrBitmap = QrCodeUtils.generateQrCode(url, 320, 320)
                qrStatusText = "? ????????????App ??"
            }
            result.onFailure {
                qrStatusText = "? ?????????????"
                isExpired = true
            }
        }
    }

    LaunchedEffect(Unit) {
        loadQr()
    }

    // Polling login status every 2.5s (Anti-risk safe polling interval)
    LaunchedEffect(qrKey) {
        if (qrKey.isEmpty()) return@LaunchedEffect
        while (!isExpired) {
            delay(2500)
            KugouApi.checkConceptQrCodeStatus(qrKey) { res ->
                res.onSuccess { checkResult ->
                    when (checkResult.status) {
                        0 -> {
                            isExpired = true
                            qrStatusText = "?? ??????????"
                        }
                        1 -> {
                            qrStatusText = "? ?????????????..."
                        }
                        2 -> {
                            qrStatusText = "?? ??????????????????"
                        }
                        4 -> {
                            // Login successful!
                            UserManager.saveLogin(
                                userId = checkResult.userId,
                                token = checkResult.token,
                                nickname = checkResult.nickname,
                                avatarUrl = checkResult.avatarUrl,
                                vipType = checkResult.vipType,
                                vipToken = checkResult.vipToken,
                                isVip = checkResult.vipType > 0 || checkResult.vipToken.isNotEmpty()
                            )
                            Toast.makeText(context, "?? ??????? ${checkResult.nickname}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .background(Color(0xFF131C2E), RoundedCornerShape(16.dp))
                .border(1.5.dp, KtvTheme.Accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("??", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "????? ????",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Prominent Concept Edition Notice Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF854D0E).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFEAB308).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "?? ????????????App ????",
                            color = Color(0xFFFDE047),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "?? ?????? VIP ??????????????????? App",
                            color = Color(0xFFFDE047).copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // QR Code Image Box
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Concept Login QR",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = KtvTheme.Accent, modifier = Modifier.size(36.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = qrStatusText,
                    color = if (isExpired) Color(0xFFEF4444) else KtvTheme.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvFocusableItem(onClick = { loadQr() }) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isFocused) KtvTheme.Accent else Color(0xFF1E293B),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "?? ?????",
                                color = if (isFocused) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TvFocusableItem(onClick = onDismiss) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isFocused) Color(0xFFE11D48) else Color(0xFF334155),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "??",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .background(Color(0xFF131C2E), RoundedCornerShape(16.dp))
                .border(1.5.dp, KtvTheme.Accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text("?? ??????", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

                Spacer(modifier = Modifier.height(18.dp))

                // Avatar / Nickname / VIP Status
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(KtvTheme.AccentGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (userProfile.isVip) "??" else "??", fontSize = 32.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = userProfile.nickname,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (userProfile.isVip) "?? ????? VIP ???? (???????/??)" else "???? (????/????)",
                    fontSize = 12.sp,
                    color = if (userProfile.isVip) Color(0xFFFDE047) else KtvTheme.TextMuted,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvFocusableItem(onClick = onLogout) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isFocused) Color(0xFFE11D48) else Color(0xFF1E293B),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text("????", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    TvFocusableItem(onClick = onDismiss) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isFocused) KtvTheme.Accent else Color(0xFF334155),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 22.dp, vertical = 8.dp)
                        ) {
                            Text("??", color = if (isFocused) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
