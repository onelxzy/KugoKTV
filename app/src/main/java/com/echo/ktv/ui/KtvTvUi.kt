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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.echo.ktv.api.KugouApi
import com.echo.ktv.api.MvItem
import com.echo.ktv.api.SongItem
import com.echo.ktv.playback.KtvPlayerManager
import com.echo.ktv.playback.PlayableItem
import com.echo.ktv.server.IpUtils
import com.echo.ktv.server.QrCodeUtils

// TV Theme Colors
object KtvTheme {
    val Background = Color(0xFF070A13)
    val CardBg = Color(0xFF131A2E)
    val Accent = Color(0xFF00E5FF)
    val TextMain = Color(0xFFF1F5F9)
    val TextMuted = Color(0xFF64748B)
}

@Composable
fun TvFocusableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f)
    val borderStroke = if (isFocused) BorderStroke(3.dp, KtvTheme.Accent) else BorderStroke(1.dp, Color.Transparent)

    Box(
        modifier = modifier
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
    var currentTab by remember { mutableStateOf("home") } // home, search, queue, hot_songs
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var searchMvs by remember { mutableStateOf<List<MvItem>>(emptyList()) }
    var hotSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var showQrDialog by remember { mutableStateOf(false) }

    val playlist by KtvPlayerManager.playlist.collectAsState()
    val currentPlaying by KtvPlayerManager.currentPlaying.collectAsState()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()

    val localIp = remember { IpUtils.getLocalIpAddress() }
    val qrBitmap = remember(localIp) {
        QrCodeUtils.generateQrCode("http://$localIp:19985/")
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
    } else if (currentTab != "home") {
        BackHandler {
            currentTab = "home"
        }
    }

    if (isPlayerFullscreen && currentPlaying != null) {
        // Fullscreen Player
        VideoPlayerOverlay(currentPlaying!!)
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
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                TopBar(
                    playlistSize = playlist.size,
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
                    // Home Dashboard layout (matches screenshot!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Left Column (Player & Bottom 3 cards)
                        Column(
                            modifier = Modifier
                                .width(480.dp)
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
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Bottom 3 cards Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                GridCard(
                                    title = "常唱",
                                    subtitle = "经典老歌/必点",
                                    emoji = "🎙",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
                                    ),
                                    onClick = { currentTab = "hot_songs" },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "收藏",
                                    subtitle = "我的专属歌单",
                                    emoji = "❤️",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFC466B), Color(0xFF3F5EFB))
                                    ),
                                    onClick = { currentTab = "hot_songs" },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "分类",
                                    subtitle = "拼音/语种/曲风",
                                    emoji = "💬",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE))
                                    ),
                                    onClick = { currentTab = "hot_songs" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        // Center Grid Column (2x2 cards)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                GridCard(
                                    title = "排行榜",
                                    subtitle = "最新金曲大赏",
                                    emoji = "👑",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
                                    ),
                                    onClick = { currentTab = "hot_songs" },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "歌名",
                                    subtitle = "按歌名首字母拼音",
                                    emoji = "📢",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF7F00FF), Color(0xFFE100FF))
                                    ),
                                    onClick = { currentTab = "search" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                GridCard(
                                    title = "歌星",
                                    subtitle = "男歌手/女歌手/乐队",
                                    emoji = "⭐",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFF8C00), Color(0xFFFF0080))
                                    ),
                                    onClick = { currentTab = "search" },
                                    modifier = Modifier.weight(1f)
                                )
                                GridCard(
                                    title = "本地",
                                    subtitle = "本地歌曲库列表",
                                    emoji = "🪐",
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2))
                                    ),
                                    onClick = { currentTab = "hot_songs" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        // Right Area (Tall "新歌榜" card)
                        TallFeatureCard(
                            title = "新歌榜",
                            songs = hotSongs,
                            onClick = { currentTab = "hot_songs" }
                        )
                    }
                } else {
                    // Sub-screen (Search list, Queue list, or Hot songs list)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (currentTab) {
                            "hot_songs" -> HotSongsGrid(hotSongs) { song ->
                                KtvPlayerManager.addSongToQueue(song)
                                Toast.makeText(context, "已点: ${song.title}", Toast.LENGTH_SHORT).show()
                            }
                            "search" -> SearchMvContent(
                                keyword = searchKeyword,
                                onKeywordChange = { searchKeyword = it },
                                mvs = searchMvs,
                                onSearch = {
                                    KugouApi.searchMV(searchKeyword) { result ->
                                        result.onSuccess { searchMvs = it }
                                    }
                                },
                                onSelect = { mv ->
                                    KtvPlayerManager.addMvToQueue(mv)
                                    Toast.makeText(context, "已点: ${mv.title}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            "queue" -> PlaylistQueueContent(playlist)
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
    playlistSize: Int,
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
            text = "金调 KTV 🎙",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = KtvTheme.Accent,
            modifier = Modifier.padding(end = 24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        TvFocusableItem(onClick = onSearchClick) {
            Text("🔍 搜索", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onQueueClick) {
            Text("📋 已点 ($playlistSize)", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onQrClick) {
            Text("📱 扫码点歌", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))

        TvFocusableItem(onClick = onVocalClick) {
            Text(
                text = if (isVocalEliminated) "🎙 伴奏" else "🎤 原唱",
                fontSize = 16.sp,
                color = if (isVocalEliminated) KtvTheme.Accent else Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onSkipClick) {
            Text("⏭ 切歌", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onPlayPauseClick) {
            Text(
                text = if (isPlaying) "⏸ 暂停" else "▶ 播放",
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvFocusableItem(onClick = onReplayClick) {
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
            .width(480.dp)
            .height(270.dp)
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
                        .padding(10.dp)
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
                                colors = listOf(Color(0xFF3F2B96), Color(0xFF0F0B26))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .background(Color(0xFFE100FF).copy(alpha = 0.2f), CircleShape)
                                .border(2.dp, Color(0xFFE100FF), CircleShape),
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
        modifier = modifier.height(110.dp)
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Text(
                text = emoji,
                fontSize = 40.sp,
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
            .width(220.dp)
            .fillMaxHeight()
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
                    color = Color.White
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
fun HotSongsGrid(songs: List<SongItem>, onSelect: (SongItem) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("🔥 推荐热歌点播", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(songs) { song ->
                TvFocusableItem(
                    onClick = { onSelect(song) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KtvTheme.CardBg)
                            .padding(16.dp)
                    ) {
                        Text(song.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, fontSize = 14.sp, color = KtvTheme.TextMuted, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SearchMvContent(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    mvs: List<MvItem>,
    onSearch: () -> Unit,
    onSelect: (MvItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            TextField(
                value = keyword,
                onValueChange = onKeywordChange,
                placeholder = { Text("输入歌手/歌名拼音搜索...") },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = KtvTheme.CardBg,
                    textColor = KtvTheme.TextMain,
                    cursorColor = KtvTheme.Accent
                ),
                modifier = Modifier
                    .weight(1.0f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            TvFocusableItem(onClick = onSearch) {
                Text("搜 索", fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(mvs) { mv ->
                TvFocusableItem(onClick = { onSelect(mv) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KtvTheme.CardBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color.DarkGray)
                        )
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(mv.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(mv.artist, fontSize = 13.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistQueueContent(list: List<PlayableItem>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("🎶 已点点播队列", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 16.dp))
        if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("点歌队列空空如也，请从手机扫码或搜索添加歌曲", color = KtvTheme.TextMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KtvTheme.CardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(item.artist, fontSize = 14.sp, color = KtvTheme.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerOverlay(item: PlayableItem) {
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()
    val musicVolume by KtvPlayerManager.musicVolume.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        val player = KtvPlayerManager.getPlayer()
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
                    .background(Color(0xFF05050A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "正在伴奏播放...",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = KtvTheme.Accent
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = item.title,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = item.artist,
                        fontSize = 20.sp,
                        color = KtvTheme.TextMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xCC070A13))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(item.artist, fontSize = 16.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvFocusableItem(onClick = { KtvPlayerManager.togglePlayPause() }) {
                        Text("播放/暂停", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    TvFocusableItem(onClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) }) {
                        Text(
                            text = if (isVocalEliminated) "🎙 伴奏模式" else "🎤 原唱模式",
                            color = if (isVocalEliminated) KtvTheme.Accent else Color.White,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    TvFocusableItem(onClick = { KtvPlayerManager.skipCurrent() }) {
                        Text("切歌 ⏭", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "伴奏音量: ${(musicVolume * 100).toInt()}%",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
