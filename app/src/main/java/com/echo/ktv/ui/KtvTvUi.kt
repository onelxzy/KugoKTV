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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.delay

object KtvTheme {
    val Background = Color(0xFF070A13)
    val CardBg = Color(0xFF131A2E)
    val Accent = Color(0xFF00E5FF)
    val TextMain = Color(0xFFF1F5F9)
    val TextMuted = Color(0xFF64748B)
}

// Root-level D-pad interceptor modifier
fun Modifier.tvDpadHandler(): Modifier = this.onPreviewKeyEvent { keyEvent ->
    false // Let Compose's built-in focus system handle D-pad by default
}

@Composable
fun TvFocusableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f)
    val borderColor = if (isFocused) KtvTheme.Accent else Color.Transparent

    Box(
        modifier = modifier
            .scale(scale)
            .border(BorderStroke(if (isFocused) 3.dp else 1.dp, borderColor), RoundedCornerShape(10.dp))
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
    val focusManager = LocalFocusManager.current

    // Tab state: home, search_song, search_mv, queue, rank, empty_fav, empty_local
    var currentTab by remember { mutableStateOf("home") }
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var searchSongResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var searchMvResults by remember { mutableStateOf<List<MvItem>>(emptyList()) }
    var hotSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var showQrDialog by remember { mutableStateOf(false) }

    val playlist by KtvPlayerManager.playlist.collectAsState()
    val currentPlaying by KtvPlayerManager.currentPlaying.collectAsState()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()

    val localIp = remember { IpUtils.getLocalIpAddress() }
    val qrBitmap = remember(localIp) { QrCodeUtils.generateQrCode("http://$localIp:19985/") }

    LaunchedEffect(Unit) {
        KugouApi.getHotSongs { result -> result.onSuccess { hotSongs = it } }
    }

    // Back navigation
    if (isPlayerFullscreen) {
        BackHandler { isPlayerFullscreen = false }
    } else if (currentTab != "home") {
        BackHandler { currentTab = "home" }
    }

    if (isPlayerFullscreen && currentPlaying != null) {
        VideoPlayerOverlay(currentPlaying!!)
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF160E36), Color(0xFF070A13))))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    playlistSize = playlist.size,
                    onSearchSongClick = { currentTab = "search_song" },
                    onSearchMvClick = { currentTab = "search_mv" },
                    onQueueClick = { currentTab = "queue" },
                    onVocalClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) },
                    onSkipClick = { KtvPlayerManager.skipCurrent() },
                    onPlayPauseClick = { KtvPlayerManager.togglePlayPause() },
                    onQrClick = { showQrDialog = true },
                    isVocalEliminated = isVocalEliminated,
                    isPlaying = isPlaying
                )

                Spacer(modifier = Modifier.height(10.dp))

                when (currentTab) {
                    "home" -> HomeDashboard(
                        currentPlaying = currentPlaying,
                        hotSongs = hotSongs,
                        onPlayerClick = {
                            if (currentPlaying != null) isPlayerFullscreen = true
                            else Toast.makeText(context, "请先点歌", Toast.LENGTH_SHORT).show()
                        },
                        onRankClick = { currentTab = "rank" },
                        onSearchSongClick = { currentTab = "search_song" },
                        onSearchMvClick = { currentTab = "search_mv" },
                        onFavClick = { currentTab = "empty_fav" },
                        onLocalClick = { currentTab = "empty_local" },
                        modifier = Modifier.weight(1f)
                    )
                    "rank" -> SongsListScreen("👑 排行榜热歌", hotSongs, Modifier.weight(1f)) { song ->
                        KtvPlayerManager.addSongToQueue(song)
                        Toast.makeText(context, "已点: ${song.title}", Toast.LENGTH_SHORT).show()
                    }
                    "search_song" -> PinyinSearchScreen(
                        mode = "song",
                        keyword = searchKeyword,
                        onKeywordChange = { searchKeyword = it },
                        songResults = searchSongResults,
                        mvResults = emptyList(),
                        onSearch = {
                            KugouApi.searchSong(searchKeyword) { r -> r.onSuccess { searchSongResults = it } }
                        },
                        onSelectSong = { song ->
                            KtvPlayerManager.addSongToQueue(song)
                            Toast.makeText(context, "已点: ${song.title}", Toast.LENGTH_SHORT).show()
                        },
                        onSelectMv = {},
                        modifier = Modifier.weight(1f)
                    )
                    "search_mv" -> PinyinSearchScreen(
                        mode = "mv",
                        keyword = searchKeyword,
                        onKeywordChange = { searchKeyword = it },
                        songResults = emptyList(),
                        mvResults = searchMvResults,
                        onSearch = {
                            KugouApi.searchMV(searchKeyword) { r -> r.onSuccess { searchMvResults = it } }
                        },
                        onSelectSong = {},
                        onSelectMv = { mv ->
                            KtvPlayerManager.addMvToQueue(mv)
                            Toast.makeText(context, "已点MV: ${mv.title}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    "queue" -> PlaylistQueueContent(playlist, Modifier.weight(1f))
                    "empty_fav" -> EmptyScreen("❤️ 收藏歌单", "暂无收藏歌曲，点歌后可在此添加收藏", Modifier.weight(1f))
                    "empty_local" -> EmptyScreen("🪐 本地歌曲", "暂无已下载的本地歌曲文件", Modifier.weight(1f))
                }
            }
        }
    }

    if (showQrDialog) {
        QrCodeDialog(localIp = localIp, qrBitmap = qrBitmap, onDismiss = { showQrDialog = false })
    }
}

@Composable
fun TopBar(
    playlistSize: Int,
    onSearchSongClick: () -> Unit,
    onSearchMvClick: () -> Unit,
    onQueueClick: () -> Unit,
    onVocalClick: () -> Unit,
    onSkipClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onQrClick: () -> Unit,
    isVocalEliminated: Boolean,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("酷唱 KTV 🎙", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KtvTheme.Accent)
        Spacer(Modifier.width(20.dp))

        TopBarButton("🔍 歌名搜索", onSearchSongClick)
        Spacer(Modifier.width(6.dp))
        TopBarButton("🎬 MV搜索", onSearchMvClick)
        Spacer(Modifier.width(6.dp))
        TopBarButton("📋 已点($playlistSize)", onQueueClick)
        Spacer(Modifier.width(6.dp))
        TopBarButton("📱 扫码", onQrClick)

        Spacer(Modifier.weight(1f))

        TopBarButton(if (isVocalEliminated) "🎙 伴奏" else "🎤 原唱", onVocalClick)
        Spacer(Modifier.width(6.dp))
        TopBarButton("⏭ 切歌", onSkipClick)
        Spacer(Modifier.width(6.dp))
        TopBarButton(if (isPlaying) "⏸ 暂停" else "▶ 播放", onPlayPauseClick)
    }
}

@Composable
fun TopBarButton(text: String, onClick: () -> Unit) {
    TvFocusableItem(onClick = onClick) {
        Text(text, fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
fun HomeDashboard(
    currentPlaying: PlayableItem?,
    hotSongs: List<SongItem>,
    onPlayerClick: () -> Unit,
    onRankClick: () -> Unit,
    onSearchSongClick: () -> Unit,
    onSearchMvClick: () -> Unit,
    onFavClick: () -> Unit,
    onLocalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        // Left: Player + bottom 3 cards (45%)
        Column(Modifier.weight(0.45f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            IntegratedPlayerWindow(currentPlaying, onPlayerClick, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GridCard("常唱", "热门必点", "🎙", Brush.horizontalGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))), onRankClick, Modifier.weight(1f))
                GridCard("收藏", "我的歌单", "❤️", Brush.horizontalGradient(listOf(Color(0xFFFC466B), Color(0xFF3F5EFB))), onFavClick, Modifier.weight(1f))
                GridCard("分类", "MV搜索", "💬", Brush.horizontalGradient(listOf(Color(0xFF00F2FE), Color(0xFF4FACFE))), onSearchMvClick, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.width(14.dp))
        // Center: 2x2 cards (35%)
        Column(Modifier.weight(0.35f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GridCard("排行榜", "最新金曲", "👑", Brush.horizontalGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF))), onRankClick, Modifier.weight(1f))
                GridCard("歌名", "拼音搜歌", "📢", Brush.horizontalGradient(listOf(Color(0xFF7F00FF), Color(0xFFE100FF))), onSearchSongClick, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GridCard("歌星", "MV搜索", "⭐", Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFFF0080))), onSearchMvClick, Modifier.weight(1f))
                GridCard("本地", "已下载歌曲", "🪐", Brush.horizontalGradient(listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2))), onLocalClick, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.width(14.dp))
        // Right: Tall new songs card (20%)
        TallFeatureCard("新歌榜", hotSongs, onRankClick, Modifier.weight(0.2f).fillMaxHeight())
    }
}

@Composable
fun IntegratedPlayerWindow(currentPlaying: PlayableItem?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val player = KtvPlayerManager.getPlayer()
    TvFocusableItem(onClick = onClick, modifier = modifier) { isFocused ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (currentPlaying != null && player != null) {
                AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; setPlayer(player) } }, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().align(Alignment.BottomStart).background(Color(0x99000000)).padding(10.dp)) {
                    Column {
                        Text(currentPlaying.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(currentPlaying.artist, fontSize = 11.sp, color = Color.LightGray, maxLines = 1)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2C1B4D), Color(0xFF070A13)))), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(70.dp).background(KtvTheme.Accent.copy(alpha = 0.1f), CircleShape).border(2.dp, KtvTheme.Accent, CircleShape), contentAlignment = Alignment.Center) {
                            Text("👤", fontSize = 32.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("原唱", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            if (isFocused) Box(Modifier.fillMaxSize().border(3.dp, KtvTheme.Accent, RoundedCornerShape(10.dp)))
        }
    }
}

@Composable
fun GridCard(title: String, subtitle: String, emoji: String, gradient: Brush, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusableItem(onClick = onClick, modifier = modifier.fillMaxHeight()) { isFocused ->
        Box(Modifier.fillMaxSize().background(gradient).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
            Text(emoji, fontSize = 28.sp, modifier = Modifier.align(Alignment.CenterEnd).scale(if (isFocused) 1.2f else 1.0f))
        }
    }
}

@Composable
fun TallFeatureCard(title: String, songs: List<SongItem>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusableItem(onClick = onClick, modifier = modifier) { isFocused ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))).padding(14.dp)) {
            Column(Modifier.fillMaxSize()) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("HOT SONGS", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))
                songs.take(3).forEachIndexed { i, song ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}. ${song.title}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                    }
                }
            }
            Text("🔥", fontSize = 50.sp, modifier = Modifier.align(Alignment.BottomEnd).scale(if (isFocused) 1.2f else 1.0f))
        }
    }
}

// ===== PINYIN VIRTUAL KEYBOARD SEARCH =====
@Composable
fun PinyinSearchScreen(
    mode: String, // "song" or "mv"
    keyword: String,
    onKeywordChange: (String) -> Unit,
    songResults: List<SongItem>,
    mvResults: List<MvItem>,
    onSearch: () -> Unit,
    onSelectSong: (SongItem) -> Unit,
    onSelectMv: (MvItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("A","B","C","D","E","F","G","H","I"),
        listOf("J","K","L","M","N","O","P","Q","R"),
        listOf("S","T","U","V","W","X","Y","Z","0-9")
    )

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = if (mode == "song") "🔍 歌名拼音搜索" else "🎬 MV拼音搜索",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // Left: Virtual keyboard (40%)
            Column(Modifier.weight(0.4f).fillMaxHeight()) {
                // Current input display
                Row(
                    Modifier.fillMaxWidth().background(KtvTheme.CardBg, RoundedCornerShape(8.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (keyword.isEmpty()) "请用方向键选字母..." else keyword,
                        fontSize = 18.sp,
                        color = if (keyword.isEmpty()) KtvTheme.TextMuted else KtvTheme.TextMain,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))

                // Keyboard rows
                keys.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { letter ->
                            TvFocusableItem(
                                onClick = {
                                    if (letter == "0-9") onKeywordChange(keyword + "1")
                                    else onKeywordChange(keyword + letter)
                                },
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Text(letter, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(6.dp)).padding(4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Action buttons row
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvFocusableItem(onClick = { if (keyword.isNotEmpty()) onKeywordChange(keyword.dropLast(1)) }, modifier = Modifier.weight(1f).height(42.dp)) {
                        Text("⌫ 删除", fontSize = 14.sp, color = Color.White, modifier = Modifier.background(Color(0xFF991B1B), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                    TvFocusableItem(onClick = { onKeywordChange("") }, modifier = Modifier.weight(1f).height(42.dp)) {
                        Text("✕ 清空", fontSize = 14.sp, color = Color.White, modifier = Modifier.background(Color(0xFF6B7280), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                    TvFocusableItem(onClick = { if (keyword.isNotEmpty()) onSearch() }, modifier = Modifier.weight(1f).height(42.dp)) {
                        Text("🔍 搜索", fontSize = 14.sp, color = Color.White, modifier = Modifier.background(Color(0xFF059669), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }

                // Also allow typing directly via TextField for emulators with keyboard
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                TextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    placeholder = { Text("或直接打字输入...", fontSize = 12.sp) },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = KtvTheme.CardBg,
                        textColor = KtvTheme.TextMain,
                        cursorColor = KtvTheme.Accent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(6.dp))
                TvFocusableItem(onClick = { if (keyword.isNotEmpty()) onSearch() }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Text("搜索: $keyword", fontSize = 16.sp, color = Color.White,
                        modifier = Modifier.background(KtvTheme.Accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(12.dp))
                }
            }

            Spacer(Modifier.width(16.dp))

            // Right: Search results (60%)
            Column(Modifier.weight(0.6f).fillMaxHeight()) {
                if (mode == "song") {
                    if (songResults.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("输入关键词搜索歌曲", color = KtvTheme.TextMuted)
                        }
                    } else {
                        Text("找到 ${songResults.size} 首歌曲", fontSize = 14.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(songResults) { song ->
                                TvFocusableItem(onClick = { onSelectSong(song) }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().background(KtvTheme.CardBg).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(song.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, fontSize = 12.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Text("点歌", fontSize = 14.sp, color = KtvTheme.Accent)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (mvResults.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("输入关键词搜索MV", color = KtvTheme.TextMuted)
                        }
                    } else {
                        Text("找到 ${mvResults.size} 个MV", fontSize = 14.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mvResults) { mv ->
                                TvFocusableItem(onClick = { onSelectMv(mv) }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().background(KtvTheme.CardBg).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(mv.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(mv.artist, fontSize = 12.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Text("点MV", fontSize = 14.sp, color = KtvTheme.Accent)
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
fun SongsListScreen(title: String, songs: List<SongItem>, modifier: Modifier = Modifier, onSelect: (SongItem) -> Unit) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 12.dp))
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无数据", color = KtvTheme.TextMuted) }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(songs) { song ->
                    TvFocusableItem(onClick = { onSelect(song) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().background(KtvTheme.CardBg).padding(14.dp)) {
                            Text(song.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, fontSize = 12.sp, color = KtvTheme.TextMuted, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyScreen(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(message, fontSize = 16.sp, color = KtvTheme.TextMuted)
        }
    }
}

@Composable
fun PlaylistQueueContent(list: List<PlayableItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text("🎶 已点播放队列", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 12.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("点歌队列空空如也，请搜索或扫码添加歌曲", color = KtvTheme.TextMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = KtvTheme.CardBg), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain)
                            Spacer(Modifier.width(12.dp))
                            Text(item.artist, fontSize = 13.sp, color = KtvTheme.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeDialog(localIp: String, qrBitmap: Bitmap?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } },
        title = { Text("手机扫码点歌 📱", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (qrBitmap != null) Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(200.dp))
                Spacer(Modifier.height(12.dp))
                Text("确保手机与电视在同一局域网", fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("http://$localIp:19985", fontWeight = FontWeight.Bold, color = KtvTheme.Accent, fontSize = 18.sp)
            }
        }
    )
}

@Composable
fun VideoPlayerOverlay(item: PlayableItem) {
    val isVocalEliminated by KtvPlayerManager.isVocalEliminated.collectAsState()
    val musicVolume by KtvPlayerManager.musicVolume.collectAsState()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val player = KtvPlayerManager.getPlayer()

    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

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
    val timeStr = remember(currentPosition, duration) {
        val cs = (currentPosition / 1000) % 60; val cm = (currentPosition / 1000) / 60
        val ds = (duration / 1000) % 60; val dm = (duration / 1000) / 60
        String.format("%02d:%02d / %02d:%02d", cm, cs, dm, ds)
    }

    Box(Modifier.fillMaxSize()) {
        if (player != null) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; setPlayer(player) } }, modifier = Modifier.fillMaxSize())
        }

        if (item is PlayableItem.Song) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2C1B4D), Color(0xFF070A13)))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(120.dp).background(KtvTheme.Accent.copy(alpha = 0.1f), CircleShape).border(3.dp, KtvTheme.Accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("🎵", fontSize = 56.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("正在播放...", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = KtvTheme.Accent)
                    Spacer(Modifier.height(6.dp))
                    Text(item.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(item.artist, fontSize = 16.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0xCC070A13)).padding(16.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = progress, color = KtvTheme.Accent, trackColor = Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(timeStr, color = Color.White, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(item.artist, fontSize = 14.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(top = 2.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvFocusableItem(onClick = { KtvPlayerManager.togglePlayPause() }) {
                            Text(if (isPlaying) "暂停 ⏸" else "播放 ▶", color = Color.White, modifier = Modifier.padding(10.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        TvFocusableItem(onClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) }) {
                            Text(if (isVocalEliminated) "🎙 伴奏" else "🎤 原唱", color = if (isVocalEliminated) KtvTheme.Accent else Color.White, modifier = Modifier.padding(10.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        TvFocusableItem(onClick = { KtvPlayerManager.skipCurrent() }) {
                            Text("切歌 ⏭", color = Color.White, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
    }
}
