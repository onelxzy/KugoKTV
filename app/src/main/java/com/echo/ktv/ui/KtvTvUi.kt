package com.echo.ktv.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.media3.ui.PlayerView
import com.echo.ktv.api.KugouApi
import com.echo.ktv.api.MvItem
import com.echo.ktv.api.SongItem
import com.echo.ktv.playback.KtvPlayerManager
import com.echo.ktv.playback.PlayableItem
import com.echo.ktv.server.IpUtils
import com.echo.ktv.server.QrCodeUtils
import kotlinx.coroutines.delay

// TV Theme Colors
object KtvTheme {
    val Background = Color(0xFF070A13)
    val CardBg = Color(0xFF131A2E)
    val Accent = Color(0xFF00E5FF)
    val TextMain = Color(0xFFF1F5F9)
    val TextMuted = Color(0xFF64748B)
}

fun PlayableItem.toSongItem(): SongItem {
    return when (this) {
        is PlayableItem.Song -> songItem
        is PlayableItem.Mv -> SongItem(mvItem.title, mvItem.artist, mvItem.mvHash, "mv", mvItem.duration)
    }
}

@Composable
fun TvFocusableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f)
    val borderStroke = if (isFocused) BorderStroke(3.dp, KtvTheme.Accent) else BorderStroke(1.dp, Color.Transparent)

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(500)
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .scale(scale)
            .border(borderStroke, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
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
    var searchMvs by remember { mutableStateOf<List<MvItem>>(emptyList()) }
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

    // Automatic search execution when searchKeyword changes
    LaunchedEffect(searchKeyword) {
        if (searchKeyword.isNotBlank()) {
            KugouApi.searchMV(searchKeyword) { result ->
                result.onSuccess { mvs ->
                    searchMvs = mvs
                    if (mvs.isEmpty()) {
                        Toast.makeText(context, "未找到关联歌曲: $searchKeyword", Toast.LENGTH_SHORT).show()
                    }
                }
                result.onFailure { err ->
                    Toast.makeText(context, "搜索遇到问题: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            searchMvs = emptyList()
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
                        // Left Column (Player & Bottom 3 cards): 45% width
                        Column(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            IntegratedPlayerWindow(
                                currentPlaying = currentPlaying,
                                onClick = {
                                    if (currentPlaying != null) {
                                        isPlayerFullscreen = true
                                    } else {
                                        Toast.makeText(context, "请先点歌开始播放", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Bottom 3 cards Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
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

                        // Center Grid Column (2x2 cards): 35% width
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
                                    subtitle = "首字母快速找歌",
                                    emoji = "📢",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF7F00FF), Color(0xFFE100FF))
                                    ),
                                    onClick = { currentTab = "search" },
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
                                    subtitle = "按拼音搜喜爱的歌星",
                                    emoji = "⭐",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFF8C00), Color(0xFFFF0080))
                                    ),
                                    onClick = { currentTab = "search" },
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
                            "search" -> SearchMvContent(
                                keyword = searchKeyword,
                                onKeywordChange = { searchKeyword = it },
                                mvs = searchMvs,
                                onSearch = {
                                    if (searchKeyword.isNotBlank()) {
                                        Toast.makeText(context, "正在搜索: $searchKeyword", Toast.LENGTH_SHORT).show()
                                        KugouApi.searchMV(searchKeyword) { result ->
                                            result.onSuccess { searchMvs = it }
                                            result.onFailure { err ->
                                                Toast.makeText(context, "搜索失败: ${err.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onSelect = { mv ->
                                    KtvPlayerManager.addMvToQueue(mv)
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
        QrCodeDialog(
            localIp = localIp,
            qrBitmap = qrBitmap,
            onDismiss = { showQrDialog = false }
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
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "酷唱 KTV 🎙",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = KtvTheme.Accent,
            modifier = Modifier.padding(end = 24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        TvFocusableItem(
            onClick = onSearchClick,
            autoFocus = true
        ) { _ ->
            Text("🔍 搜索", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onQueueClick) { _ ->
            Text("📋 已点 ($selectedSize)", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onQrClick) { _ ->
            Text("📱 扫码点歌", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))

        TvFocusableItem(onClick = onVocalClick) { _ ->
            Text(
                text = if (isVocalEliminated) "🎙 伴奏" else "🎤 原唱",
                fontSize = 16.sp,
                color = if (isVocalEliminated) KtvTheme.Accent else Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onSkipClick) { _ ->
            Text("⏭ 切歌", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onPlayPauseClick) { _ ->
            Text(
                text = if (isPlaying) "⏸ 暂停" else "▶ 播放",
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onReplayClick) { _ ->
            Text("🔄 重唱", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun IntegratedPlayerWindow(
    currentPlaying: PlayableItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val player = KtvPlayerManager.getPlayer()
    
    TvFocusableItem(
        onClick = onClick,
        modifier = modifier
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (currentPlaying != null && player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setPlayer(player)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(Color(0x99000000))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = currentPlaying.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentPlaying.artist,
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.1f), CircleShape)
                                .border(2.dp, Color(0xFF00E5FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤", fontSize = 32.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "原唱",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, KtvTheme.Accent, RoundedCornerShape(10.dp))
                )
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
        modifier = modifier.fillMaxHeight()
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = emoji,
                fontSize = 32.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
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
                    )
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
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                songs.take(3).forEachIndexed { index, song ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
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
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = "🔥",
                fontSize = 60.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 12.dp)
                    .scale(if (isFocused) 1.2f else 1.0f)
            )
        }
    }
}

@Composable
fun QrCodeDialog(
    localIp: String,
    qrBitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = {
            Text("手机扫码点歌 📱", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("请确保手机与电视在同一局域网下，扫码或访问地址：", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("http://$localIp:19985", fontWeight = FontWeight.Bold, color = KtvTheme.Accent, fontSize = 18.sp)
            }
        }
    )
}

@Composable
fun SongsListGrid(title: String, songs: List<SongItem>, onSelect: (SongItem) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = KtvTheme.TextMain,
            modifier = Modifier.padding(bottom = 16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无缓存，唱过的歌曲会自动下载到本地！", color = KtvTheme.TextMuted, fontSize = 18.sp)
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
fun HotSongsGrid(songs: List<SongItem>, onSelect: (SongItem) -> Unit) {
    SongsListGrid("🔥 推荐热歌点播", songs, onSelect)
}

@Composable
fun SearchMvContent(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    mvs: List<MvItem>,
    onSearch: () -> Unit,
    onSelect: (MvItem) -> Unit
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

        // Right Column: Search input and results list
        Column(modifier = Modifier.weight(1f)) {
            // Search Input Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                TextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    placeholder = { Text("输入歌手/歌名首字母拼音...") },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = KtvTheme.CardBg,
                        textColor = KtvTheme.TextMain,
                        cursorColor = KtvTheme.Accent
                    ),
                    modifier = Modifier
                        .weight(1.0f)
                        .clip(RoundedCornerShape(8.dp))
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        focusManager.moveFocus(FocusDirection.Down)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        focusManager.moveFocus(FocusDirection.Up)
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                TvFocusableItem(onClick = onSearch) { _ ->
                    Text("搜 索", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }

            // Results List
            if (mvs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KtvTheme.CardBg.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (keyword.isBlank()) "请输入首字母搜索歌曲，如 'ZJL' 搜周杰伦，'QT' 搜晴天"
                        else "搜索中，或未找到关联歌曲...",
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
                    items(mvs) { mv ->
                        TvFocusableItem(onClick = { onSelect(mv) }) { _ ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(KtvTheme.CardBg)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(Color.DarkGray, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎬", fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = mv.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KtvTheme.TextMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = mv.artist,
                                        fontSize = 13.sp,
                                        color = KtvTheme.TextMuted,
                                        modifier = Modifier.padding(top = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("▶ 正在播放", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KtvTheme.Accent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(currentPlaying.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(currentPlaying.artist, fontSize = 14.sp, color = KtvTheme.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (list.isEmpty() && currentPlaying == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("点歌队列空空如也，请从手机扫码或搜索添加歌曲", color = KtvTheme.TextMuted)
            }
        } else if (list.isNotEmpty()) {
            Text("等待播放 (${list.size}):", fontSize = 16.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KtvTheme.CardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(item.artist, fontSize = 14.sp, color = KtvTheme.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val context = LocalContext.current
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()
    val musicVolume by KtvPlayerManager.musicVolume.collectAsState()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val favorites by KtvPlayerManager.favorites.collectAsState()

    val player = KtvPlayerManager.getPlayer()
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            if (player != null) {
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    val timeString = remember(currentPosition, duration) {
        val curSec = (currentPosition / 1000) % 60
        val curMin = (currentPosition / 1000) / 60
        val durSec = (duration / 1000) % 60
        val durMin = (duration / 1000) / 60
        String.format("%02d:%02d / %02d:%02d", curMin, curSec, durMin, durSec)
    }

    val songItemRepresentation = remember(item) { item.toSongItem() }
    val isFav = favorites.contains(songItemRepresentation)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            player?.let { p ->
                                val newPos = (p.currentPosition - 10000L).coerceAtLeast(0L)
                                p.seekTo(newPos)
                                Toast.makeText(context, "<< 快退 10 秒", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            player?.let { p ->
                                val targetDuration = p.duration.coerceAtLeast(0L)
                                val newPos = (p.currentPosition + 10000L).coerceAtMost(targetDuration)
                                p.seekTo(newPos)
                                Toast.makeText(context, ">> 快进 10 秒", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            KtvPlayerManager.togglePlayPause()
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onCloseFullscreen()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
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
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.1f), CircleShape)
                            .border(3.dp, Color(0xFF00E5FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 60.sp)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (isVocalEliminated) "正在伴奏播放..." else "正在原唱播放...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = KtvTheme.Accent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.artist,
                        fontSize = 16.sp,
                        color = KtvTheme.TextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Bottom Overlay Bar (height limited so title overflow never pushes buttons off-screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xCC070A13))
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Progress Bar and Time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = progress,
                        color = KtvTheme.Accent,
                        trackColor = Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = timeString,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(0.4f)) {
                        Text(
                            text = item.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artist,
                            fontSize = 14.sp,
                            color = KtvTheme.TextMuted,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight(0.6f)
                    ) {
                        TvFocusableItem(onClick = { KtvPlayerManager.togglePlayPause() }) { _ ->
                            Text(if (isPlaying) "暂停 ⏸" else "播放 ▶", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TvFocusableItem(onClick = { KtvPlayerManager.toggleFavorite(songItemRepresentation) }) { _ ->
                            Text(
                                text = if (isFav) "已收藏 ❤️" else "收藏 ♡",
                                color = if (isFav) Color.Red else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TvFocusableItem(onClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) }) { _ ->
                            Text(
                                text = if (isVocalEliminated) "🎙 伴奏模式" else "🎤 原唱模式",
                                color = if (isVocalEliminated) KtvTheme.Accent else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TvFocusableItem(onClick = { KtvPlayerManager.skipCurrent() }) { _ ->
                            Text("切歌 ⏭", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "音量: ${(musicVolume * 100).toInt()}%",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
