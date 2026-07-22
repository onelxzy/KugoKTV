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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
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
import com.echo.ktv.playback.KtvPlayerManager
import com.echo.ktv.playback.PlayableItem
import com.echo.ktv.server.IpUtils
import com.echo.ktv.server.QrCodeUtils
import kotlinx.coroutines.delay

// High-contrast, premium dark mode design tokens
object KtvTheme {
    val Background = Color(0xFF0F172A)
    val CardBg = Color(0xFF1E293B)
    val CardBgHover = Color(0xFF334155)
    val Accent = Color(0xFF00E5FF) // Vibrant Cyan
    val AccentGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFF3B82F6)))
    val TextMain = Color(0xFFF8FAFC)
    val TextMuted = Color(0xFF94A3B8)
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
            .scale(if (isFocused) 1.05f else 1.0f)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) KtvTheme.Accent else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
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
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        content(isFocused)
    }
}

@Composable
fun MainTvScreen() {
    val context = LocalContext.current

    var currentTab by remember { mutableStateOf("home") } // home, search, queue, songs_list, category
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

    // Automatic search execution when searchKeyword or searchMode changes
    LaunchedEffect(searchKeyword, searchMode) {
        if (searchKeyword.isNotBlank()) {
            if (searchMode == "song") {
                KugouApi.searchSong(searchKeyword) { result ->
                    result.onSuccess { searchSongs = it }
                }
            } else {
                KugouApi.searchSinger(searchKeyword) { result ->
                    result.onSuccess { searchSingers = it }
                }
            }
        } else {
            searchSongs = emptyList()
            searchSingers = emptyList()
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
            onCloseFullscreen = { isPlayerFullscreen = false }
        )
    } else {
        // Main Home Layout or Tab Sub-screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF160E36), Color(0xFF070A13))
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                TopBar(
                    selectedSize = totalSelectedCount,
                    onSearchClick = { currentTab = "search" },
                    onQueueClick = { currentTab = "queue" },
                    onVocalClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) },
                    onSkipClick = { KtvPlayerManager.skipCurrent() },
                    onPlayPauseClick = { KtvPlayerManager.togglePlayPause() },
                    onReplayClick = {
                        KtvPlayerManager.getPlayer()?.seekTo(0)
                        Toast.makeText(context, "正在重新播放", Toast.LENGTH_SHORT).show()
                    },
                    onQrClick = { showQrDialog = true },
                    isVocalEliminated = isVocalEliminated,
                    isPlaying = isPlaying
                )

                Spacer(modifier = Modifier.height(10.dp))

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
                                        Toast.makeText(context, "请先点歌后再进入播放页面", Toast.LENGTH_SHORT).show()
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
                                    .height(85.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GridCard(
                                    title = "常唱",
                                    subtitle = "历史点歌记录",
                                    emoji = "🎙",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
                                    ),
                                    onClick = {
                                        displaySongsList = history
                                        listTitle = "🎙 经典常唱歌曲"
                                        currentTab = "songs_list"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "收藏",
                                    subtitle = "我的专属列表",
                                    emoji = "❤️",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFC466B), Color(0xFF3F5EFB))
                                    ),
                                    onClick = {
                                        displaySongsList = favorites
                                        listTitle = "❤️ 我的收藏歌单"
                                        currentTab = "songs_list"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "分类",
                                    subtitle = "在线智能分类",
                                    emoji = "💬",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE))
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
                                    subtitle = "最新金曲大赏",
                                    emoji = "👑",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
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
                                    subtitle = "首字母快速点歌",
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
                                    subtitle = "按拼音搜喜爱的歌手",
                                    emoji = "⭐",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFF8C00), Color(0xFFFF0080))
                                    ),
                                    onClick = {
                                        searchMode = "singer"
                                        currentTab = "search"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "本地",
                                    subtitle = "离线免网缓存库",
                                    emoji = "📁",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2))
                                    ),
                                    onClick = {
                                        displaySongsList = localSongs
                                        listTitle = "📁 本地缓存歌曲"
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
                                listTitle = "🔥 酷唱新歌推荐"
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
                            "songs_list" -> SongsListGrid(listTitle, displaySongsList) { song ->
                                KtvPlayerManager.addSongToQueue(song)
                            }
                            "category" -> {
                                if (currentCategoryName.isNotEmpty()) {
                                    SongsListGrid("💬 分类 - $currentCategoryName", displaySongsList) { song ->
                                        KtvPlayerManager.addSongToQueue(song)
                                    }
                                } else {
                                    CategorySelectionGrid { category ->
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
                                    }
                                }
                            }
                            "search" -> SearchContent(
                                keyword = searchKeyword,
                                onKeywordChange = { searchKeyword = it },
                                searchMode = searchMode,
                                onSearchModeChange = { searchMode = it },
                                songs = searchSongs,
                                singers = searchSingers,
                                onSelectSong = { song -> KtvPlayerManager.addSongToQueue(song) },
                                onSelectSinger = { singer ->
                                    searchMode = "song"
                                    searchKeyword = singer.singerName
                                }
                            )
                            "queue" -> PlaylistQueueContent(playlist, currentPlaying)
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("关闭", color = KtvTheme.Accent)
                }
            },
            title = { Text("📱 手机扫码点歌", color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "扫码点歌二维码",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("请确保手机与电视处于同一局域网", color = KtvTheme.TextMuted, fontSize = 14.sp)
                    Text("扫码或访问: http://$localIp:19985/", color = KtvTheme.Accent, fontSize = 14.sp)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
fun TopBar(
    selectedSize: Int,
    onSearchClick: () -> Unit,
    onQueueClick: () -> Unit,
    onVocalClick: () -> Unit,
    onSkipClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onReplayClick: () -> Unit,
    onQrClick: () -> Unit,
    isVocalEliminated: Boolean,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(KtvTheme.CardBg.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "酷唱 KTV",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = KtvTheme.Accent
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("🎤", fontSize = 20.sp)
        }

        // Action Buttons Row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TopBarButton(label = "搜索", icon = "🔍", onClick = onSearchClick)
            TopBarButton(label = "已点 ($selectedSize)", icon = "📋", onClick = onQueueClick)
            TopBarButton(label = "扫码点歌", icon = "📱", onClick = onQrClick)
            TopBarButton(
                label = if (isVocalEliminated) "伴奏" else "原唱",
                icon = "🎤",
                isHighlighted = isVocalEliminated,
                onClick = onVocalClick
            )
            TopBarButton(label = "重唱", icon = "🔄", onClick = onReplayClick)
            TopBarButton(label = "切歌", icon = "⏭", onClick = onSkipClick)
            TopBarButton(
                label = if (isPlaying) "暂停" else "播放",
                icon = if (isPlaying) "⏸" else "▶",
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
                        else -> Color(0xFF334155).copy(alpha = 0.6f)
                    },
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                fontSize = 14.sp,
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.Medium
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
                .background(KtvTheme.CardBg, RoundedCornerShape(12.dp))
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
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay gradient for text legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )

                // Current song info at bottom left
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
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
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Expand button indicator at top right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(8.dp)
                ) {
                    Text("⛶ 全屏", color = Color.White, fontSize = 12.sp)
                }
            } else {
                // Empty state when no song is playing
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(KtvTheme.Accent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 36.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无播放中的歌曲",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = KtvTheme.TextMain
                    )
                    Text(
                        text = "点击上方按键点歌，即可在此开启卡拉 OK 演唱",
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
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = emoji,
                fontSize = 32.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .scale(if (isFocused) 1.2f else 1.0f)
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "HOT SONGS",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                songs.take(3).forEachIndexed { index, song ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "${index + 1}. ${song.title}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            // Big 🔥 Flame Emoji at Bottom Right Corner
            Text(
                text = "🔥",
                fontSize = 50.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .scale(if (isFocused) 1.2f else 1.0f)
            )
        }
    }
}

@Composable
fun SongsListGrid(title: String, songs: List<SongItem>, onSelect: (SongItem) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 16.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无数据", color = KtvTheme.TextMuted, fontSize = 18.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(songs) { song ->
                    TvFocusableItem(
                        onClick = { onSelect(song) },
                        modifier = Modifier.fillMaxWidth()
                    ) { _ ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(KtvTheme.CardBg)
                                .padding(16.dp)
                        ) {
                            Text(song.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, fontSize = 14.sp, color = KtvTheme.TextMuted, maxLines = 1, modifier = Modifier.padding(top = 4.dp), overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionGrid(onSelect: (String) -> Unit) {
    val categories = listOf("国语流行", "经典粤语", "欧美金曲", "民谣摇滚")
    val gradients = listOf(
        Brush.horizontalGradient(colors = listOf(Color(0xFFFF8C00), Color(0xFFFF0080))),
        Brush.horizontalGradient(colors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF))),
        Brush.horizontalGradient(colors = listOf(Color(0xFF7F00FF), Color(0xFFE100FF))),
        Brush.horizontalGradient(colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)))
    )
    val emojis = listOf("🎵", "🎙", "🌍", "🎸")

    Column(modifier = Modifier.fillMaxSize()) {
        Text("💬 选择歌曲分类", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(categories.size) { index ->
                val name = categories[index]
                TvFocusableItem(
                    onClick = { onSelect(name) },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradients[index])
                            .padding(20.dp)
                    ) {
                        Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.CenterStart))
                        Text(emojis[index], fontSize = 50.sp, modifier = Modifier.align(Alignment.CenterEnd).scale(if (isFocused) 1.2f else 1.0f))
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
    songs: List<SongItem>,
    singers: List<SingerItem>,
    onSelectSong: (SongItem) -> Unit,
    onSelectSinger: (SingerItem) -> Unit
) {
    val letters = ('A'..'Z').map { it.toString() }
    val focusManager = LocalFocusManager.current

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: A-Z Keyboard Panel (width 260dp)
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(KtvTheme.CardBg.copy(alpha = 0.5f))
                .padding(10.dp)
        ) {
            Text(
                text = "拼音首字母遥控键盘",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = KtvTheme.Accent,
                modifier = Modifier.padding(bottom = 6.dp)
            )

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
                        modifier = Modifier.height(38.dp)
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isFocused) KtvTheme.Accent else Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TvFocusableItem(
                    onClick = {
                        if (keyword.isNotEmpty()) {
                            onKeywordChange(keyword.dropLast(1))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("退格 ⌫", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                TvFocusableItem(
                    onClick = {
                        onKeywordChange("")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF64748B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("清空 🗑", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right Column: Search input, mode toggle and results list
        Column(modifier = Modifier.weight(1f)) {
            // Search Input & Mode Toggle Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                TextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    placeholder = { Text(if (searchMode == "song") "输入歌名/拼音首字母..." else "输入歌手/拼音首字母...") },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = KtvTheme.CardBg,
                        textColor = KtvTheme.TextMain,
                        cursorColor = KtvTheme.Accent
                    ),
                    modifier = Modifier
                        .weight(1.0f)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Mode Toggle: 搜歌曲 vs 搜歌手
                Row(
                    modifier = Modifier
                        .background(KtvTheme.CardBg, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TvFocusableItem(onClick = { onSearchModeChange("song") }) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (searchMode == "song") KtvTheme.Accent else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "🎵 搜歌曲",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (searchMode == "song") Color.Black else Color.White
                            )
                        }
                    }

                    TvFocusableItem(onClick = { onSearchModeChange("singer") }) { isFocused ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (searchMode == "singer") KtvTheme.Accent else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "👤 搜歌手",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (searchMode == "singer") Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            // Results List based on searchMode
            if (searchMode == "song") {
                if (songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(KtvTheme.CardBg.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (keyword.isBlank()) "请输入拼音首字母搜索歌曲，如 'ZJL' 搜周杰伦，'QT' 搜晴天"
                            else "未找到与 '$keyword' 相关的歌曲",
                            color = KtvTheme.TextMuted,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(songs) { song ->
                            TvFocusableItem(onClick = { onSelectSong(song) }) { _ ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(KtvTheme.CardBg)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                if (song.mvHash.isNotEmpty()) Color(0xFF0284C7) else Color(0xFF475569),
                                                RoundedCornerShape(6.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (song.mvHash.isNotEmpty()) "🎬" else "🎵",
                                            fontSize = 22.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KtvTheme.TextMain,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = song.artist,
                                                fontSize = 13.sp,
                                                color = KtvTheme.TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (song.mvHash.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "[MV]",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KtvTheme.Accent
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Singer Mode
                if (singers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(KtvTheme.CardBg.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (keyword.isBlank()) "请输入拼音首字母搜索歌手，如 'ZJ' 搜张杰"
                            else "未找到与 '$keyword' 相关的歌手",
                            color = KtvTheme.TextMuted,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(singers) { singer ->
                            TvFocusableItem(onClick = { onSelectSinger(singer) }) { _ ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(KtvTheme.CardBg)
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
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
                                        Text("👤", fontSize = 32.sp)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = singer.singerName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KtvTheme.TextMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "点击查看该歌手金曲",
                                        fontSize = 12.sp,
                                        color = KtvTheme.TextMuted,
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

@Composable
fun PlaylistQueueContent(list: List<PlayableItem>, currentPlaying: PlayableItem?) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("🎶 已点点播队列", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 16.dp))
        
        if (currentPlaying != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("▶ 正在播放:", fontSize = 16.sp, color = KtvTheme.Accent, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("${currentPlaying.title} - ${currentPlaying.artist}", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (list.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(KtvTheme.CardBg.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("点播队列为空，快去点首歌吧！", color = KtvTheme.TextMuted, fontSize = 18.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list.size) { index ->
                    val item = list[index]
                    TvFocusableItem(
                        onClick = { KtvPlayerManager.removeAt(index) },
                        modifier = Modifier.fillMaxWidth()
                    ) { _ ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(KtvTheme.CardBg)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text("${index + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KtvTheme.Accent, modifier = Modifier.width(30.dp))
                                Column {
                                    Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.artist, fontSize = 13.sp, color = KtvTheme.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text("删除 🗑", color = Color(0xFFEF4444), fontSize = 14.sp)
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
    onCloseFullscreen: () -> Unit
) {
    val player = KtvPlayerManager.getPlayer()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()
    val musicVolume by KtvPlayerManager.musicVolume.collectAsState()

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
            .background(Color.Black)
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

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TopBarButton(
                                label = if (isPlaying) "暂停" else "播放",
                                icon = if (isPlaying) "⏸" else "▶",
                                onClick = { KtvPlayerManager.togglePlayPause() }
                            )
                            TopBarButton(
                                label = if (isVocalEliminated) "伴奏" else "原唱",
                                icon = "🎤",
                                isHighlighted = isVocalEliminated,
                                onClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) }
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
