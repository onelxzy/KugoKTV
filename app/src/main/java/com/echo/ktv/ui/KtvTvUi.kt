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
import kotlinx.coroutines.delay

object KtvTheme {
    val Background = Color(0xFF0D1117)
    val CardBg = Color(0xFF161B22)
    val Accent = Color(0xFF58A6FF)
    val AccentGreen = Color(0xFF3FB950)
    val TextMain = Color(0xFFC9D1D9)
    val TextMuted = Color(0xFF8B949E)
    val Border = Color(0xFF30363D)
}

@Composable
fun TvFocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f)
    Box(
        modifier = modifier
            .scale(scale)
            .border(
                BorderStroke(if (focused) 2.dp else 1.dp, if (focused) KtvTheme.Accent else KtvTheme.Border),
                RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content(focused) }
}

@Composable
fun MainTvScreen() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("home") }
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var searchSongResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var searchMvResults by remember { mutableStateOf<List<MvItem>>(emptyList()) }
    var hotSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var showQr by remember { mutableStateOf(false) }

    val playlist by KtvPlayerManager.playlist.collectAsState()
    val currentPlaying by KtvPlayerManager.currentPlaying.collectAsState()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val isVocalOff by KtvPlayerManager.isVocalEliminated.collectAsState()

    val localIp = remember { IpUtils.getLocalIpAddress() }
    val qrBitmap = remember(localIp) { QrCodeUtils.generateQrCode("http://$localIp:19985/") }

    LaunchedEffect(Unit) {
        KugouApi.getHotSongs { r -> r.onSuccess { hotSongs = it } }
    }

    if (isPlayerFullscreen) BackHandler { isPlayerFullscreen = false }
    else if (currentTab != "home") BackHandler { currentTab = "home" }

    if (isPlayerFullscreen && currentPlaying != null) {
        PlayerFullscreen(currentPlaying!!)
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(KtvTheme.Background)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // === Top bar ===
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("酷唱 KTV", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KtvTheme.Accent)
                Spacer(Modifier.width(20.dp))
                listOf(
                    "🔍 搜歌" to "search_song",
                    "🎬 搜MV" to "search_mv",
                    "📋 已点(${playlist.size})" to "queue",
                    "📱 扫码" to "qr"
                ).forEach { (label, action) ->
                    Spacer(Modifier.width(6.dp))
                    TvFocusBox(onClick = {
                        if (action == "qr") showQr = true else currentTab = action
                    }) {
                        Text(label, fontSize = 13.sp, color = KtvTheme.TextMain,
                            modifier = Modifier.background(KtvTheme.CardBg).padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                TvFocusBox(onClick = { KtvPlayerManager.setVocalElimination(!isVocalOff) }) {
                    Text(if (isVocalOff) "🎙伴奏" else "🎤原唱", fontSize = 13.sp, color = if (isVocalOff) KtvTheme.AccentGreen else KtvTheme.TextMain,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
                Spacer(Modifier.width(6.dp))
                TvFocusBox(onClick = { KtvPlayerManager.skipCurrent() }) {
                    Text("⏭切歌", fontSize = 13.sp, color = KtvTheme.TextMain, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
                Spacer(Modifier.width(6.dp))
                TvFocusBox(onClick = { KtvPlayerManager.togglePlayPause() }) {
                    Text(if (isPlaying) "⏸暂停" else "▶播放", fontSize = 13.sp, color = KtvTheme.TextMain, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // === Content ===
            when (currentTab) {
                "home" -> HomeContent(
                    currentPlaying = currentPlaying,
                    hotSongs = hotSongs,
                    onPlayerClick = { if (currentPlaying != null) isPlayerFullscreen = true },
                    onTab = { currentTab = it },
                    modifier = Modifier.weight(1f)
                )
                "rank" -> SongListContent("👑 排行榜", hotSongs, Modifier.weight(1f)) { song ->
                    KtvPlayerManager.addSongToQueue(song)
                    Toast.makeText(context, "已点: ${song.title}", Toast.LENGTH_SHORT).show()
                }
                "search_song" -> SearchContent(
                    mode = "song", keyword = searchKeyword, onKeywordChange = { searchKeyword = it },
                    songResults = searchSongResults, mvResults = emptyList(),
                    onSearch = { KugouApi.searchSong(searchKeyword) { r -> r.onSuccess { searchSongResults = it } } },
                    onSelectSong = { song ->
                        KtvPlayerManager.addSongToQueue(song)
                        Toast.makeText(context, "已点: ${song.title}", Toast.LENGTH_SHORT).show()
                    },
                    onSelectMv = {},
                    modifier = Modifier.weight(1f)
                )
                "search_mv" -> SearchContent(
                    mode = "mv", keyword = searchKeyword, onKeywordChange = { searchKeyword = it },
                    songResults = emptyList(), mvResults = searchMvResults,
                    onSearch = { KugouApi.searchMV(searchKeyword) { r -> r.onSuccess { searchMvResults = it } } },
                    onSelectSong = {},
                    onSelectMv = { mv ->
                        KtvPlayerManager.addMvToQueue(mv)
                        Toast.makeText(context, "已点MV: ${mv.title}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
                "queue" -> QueueContent(playlist, Modifier.weight(1f))
                "empty_fav" -> PlaceholderContent("❤️ 收藏", "暂无收藏歌曲", Modifier.weight(1f))
                "empty_local" -> PlaceholderContent("📁 本地", "暂无本地歌曲", Modifier.weight(1f))
            }
        }
    }

    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = { Button(onClick = { showQr = false }) { Text("关闭") } },
            title = { Text("📱 扫码点歌", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    qrBitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(180.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text("http://$localIp:19985", fontWeight = FontWeight.Bold, color = KtvTheme.Accent)
                    Text("确保手机与电视在同一网络", fontSize = 12.sp, color = KtvTheme.TextMuted)
                }
            }
        )
    }
}

// ==================== HOME ====================
@Composable
fun HomeContent(
    currentPlaying: PlayableItem?,
    hotSongs: List<SongItem>,
    onPlayerClick: () -> Unit,
    onTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth()) {
        // Left: Player preview (50%)
        Column(Modifier.weight(0.5f).fillMaxHeight()) {
            // Player window
            TvFocusBox(onClick = onPlayerClick, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    val player = KtvPlayerManager.getPlayer()
                    if (currentPlaying != null && player != null) {
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply { useController = false; setPlayer(player) } },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.fillMaxWidth().align(Alignment.BottomStart).background(Color(0xAA000000)).padding(10.dp)) {
                            Column {
                                Text("正在播放", fontSize = 11.sp, color = KtvTheme.AccentGreen)
                                Text(currentPlaying.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                Text(currentPlaying.artist, fontSize = 11.sp, color = Color.LightGray)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A1B3A), Color.Black))), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎤", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("等待点歌...", fontSize = 16.sp, color = KtvTheme.TextMuted)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Bottom row: 3 feature cards
            Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureCard("❤️", "收藏", Color(0xFFDA3633), { onTab("empty_fav") }, Modifier.weight(1f))
                FeatureCard("📁", "本地", Color(0xFF8957E5), { onTab("empty_local") }, Modifier.weight(1f))
                FeatureCard("📱", "扫码", Color(0xFF1F6FEB), { onTab("qr") }, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.width(12.dp))

        // Right: Feature grid + Hot list (50%)
        Column(Modifier.weight(0.5f).fillMaxHeight()) {
            // 2x2 Feature grid
            Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureCard("👑", "排行榜", Color(0xFF1F6FEB), { onTab("rank") }, Modifier.weight(1f))
                FeatureCard("🔍", "搜歌名", Color(0xFF238636), { onTab("search_song") }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureCard("🎬", "搜MV", Color(0xFFBF4B8A), { onTab("search_mv") }, Modifier.weight(1f))
                FeatureCard("🔥", "新歌榜", Color(0xFFDA3633), { onTab("rank") }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            // Hot songs preview list
            Text("🔥 热歌推荐", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(hotSongs.take(10)) { song ->
                    Row(
                        Modifier.fillMaxWidth().background(KtvTheme.CardBg, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontSize = 14.sp, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, fontSize = 11.sp, color = KtvTheme.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureCard(emoji: String, label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusBox(onClick = onClick, modifier = modifier.fillMaxHeight()) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f)))).padding(12.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ==================== SEARCH ====================
@Composable
fun SearchContent(
    mode: String,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    songResults: List<SongItem>,
    mvResults: List<MvItem>,
    onSearch: () -> Unit,
    onSelectSong: (SongItem) -> Unit,
    onSelectMv: (MvItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val pinyinKeys = listOf(
        listOf("A","B","C","D","E","F","G"),
        listOf("H","I","J","K","L","M","N"),
        listOf("O","P","Q","R","S","T","U"),
        listOf("V","W","X","Y","Z","1","2")
    )

    Column(modifier.fillMaxSize()) {
        Text(
            if (mode == "song") "🔍 搜索歌曲" else "🎬 搜索MV",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain
        )
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxSize()) {
            // Left: Input area (35%)
            Column(Modifier.weight(0.35f).fillMaxHeight()) {
                // Current keyword display
                Text(
                    if (keyword.isEmpty()) "按方向键选字母" else "关键词: $keyword",
                    fontSize = 16.sp, color = if (keyword.isEmpty()) KtvTheme.TextMuted else KtvTheme.Accent,
                    modifier = Modifier.fillMaxWidth().background(KtvTheme.CardBg, RoundedCornerShape(6.dp)).padding(10.dp)
                )
                Spacer(Modifier.height(6.dp))

                // Pinyin keyboard grid
                pinyinKeys.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { ch ->
                            TvFocusBox(onClick = { onKeywordChange(keyword + ch) }, modifier = Modifier.weight(1f).height(38.dp)) {
                                Text(ch, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.background(Color(0xFF21262D)).padding(4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }

                // Action buttons
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TvFocusBox(onClick = { if (keyword.isNotEmpty()) onKeywordChange(keyword.dropLast(1)) }, modifier = Modifier.weight(1f).height(38.dp)) {
                        Text("⌫ 删除", fontSize = 13.sp, color = Color.White, modifier = Modifier.background(Color(0xFF8B0000)).padding(4.dp))
                    }
                    TvFocusBox(onClick = { onKeywordChange("") }, modifier = Modifier.weight(1f).height(38.dp)) {
                        Text("✕ 清空", fontSize = 13.sp, color = Color.White, modifier = Modifier.background(Color(0xFF484F58)).padding(4.dp))
                    }
                    TvFocusBox(onClick = { if (keyword.isNotEmpty()) onSearch() }, modifier = Modifier.weight(1f).height(38.dp)) {
                        Text("🔍 搜索", fontSize = 13.sp, color = Color.White, modifier = Modifier.background(Color(0xFF238636)).padding(4.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))
                // TextField for physical keyboard input
                @OptIn(ExperimentalMaterial3Api::class)
                TextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    placeholder = { Text("也可直接打字...", fontSize = 12.sp) },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = KtvTheme.CardBg,
                        focusedTextColor = KtvTheme.TextMain,
                        unfocusedTextColor = KtvTheme.TextMain,
                        cursorColor = KtvTheme.Accent
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                    singleLine = true
                )
                Spacer(Modifier.height(4.dp))
                TvFocusBox(onClick = { if (keyword.isNotEmpty()) onSearch() }, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    Text("搜索: $keyword", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = KtvTheme.Accent,
                        modifier = Modifier.background(KtvTheme.CardBg).padding(10.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            // Right: Results (65%)
            Column(Modifier.weight(0.65f).fillMaxHeight()) {
                if (mode == "song") {
                    if (songResults.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("输入关键词搜索歌曲", color = KtvTheme.TextMuted, fontSize = 16.sp)
                        }
                    } else {
                        Text("共 ${songResults.size} 首", fontSize = 13.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(songResults) { song ->
                                TvFocusBox(onClick = { onSelectSong(song) }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().background(KtvTheme.CardBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, fontSize = 12.sp, color = KtvTheme.TextMuted)
                                        }
                                        Text("点歌", fontSize = 13.sp, color = KtvTheme.AccentGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (mvResults.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("输入关键词搜索MV", color = KtvTheme.TextMuted, fontSize = 16.sp)
                        }
                    } else {
                        Text("共 ${mvResults.size} 个MV", fontSize = 13.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(mvResults) { mv ->
                                TvFocusBox(onClick = { onSelectMv(mv) }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().background(KtvTheme.CardBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(mv.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(mv.artist, fontSize = 12.sp, color = KtvTheme.TextMuted)
                                        }
                                        Text("点MV", fontSize = 13.sp, color = KtvTheme.Accent, fontWeight = FontWeight.Bold)
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

// ==================== SONG LIST ====================
@Composable
fun SongListContent(title: String, songs: List<SongItem>, modifier: Modifier = Modifier, onSelect: (SongItem) -> Unit) {
    Column(modifier.fillMaxSize()) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 10.dp))
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载中...", color = KtvTheme.TextMuted) }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(songs) { song ->
                    TvFocusBox(onClick = { onSelect(song) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().background(KtvTheme.CardBg).padding(12.dp)) {
                            Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, fontSize = 11.sp, color = KtvTheme.TextMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ==================== QUEUE ====================
@Composable
fun QueueContent(list: List<PlayableItem>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Text("📋 已点歌曲 (${list.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain, modifier = Modifier.padding(bottom = 10.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("点歌队列为空", color = KtvTheme.TextMuted) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = KtvTheme.CardBg), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain)
                                Text(item.artist, fontSize = 12.sp, color = KtvTheme.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== PLACEHOLDER ====================
@Composable
fun PlaceholderContent(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KtvTheme.TextMain)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(message, fontSize = 16.sp, color = KtvTheme.TextMuted)
        }
    }
}

// ==================== FULLSCREEN PLAYER ====================
@Composable
fun PlayerFullscreen(item: PlayableItem) {
    val isVocalOff by KtvPlayerManager.isVocalEliminated.collectAsState()
    val isPlaying by KtvPlayerManager.isPlaying.collectAsState()
    val player = KtvPlayerManager.getPlayer()

    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            if (player != null) {
                pos = player.currentPosition
                dur = player.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    val progress = if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
    val timeText = remember(pos, dur) {
        String.format("%02d:%02d / %02d:%02d", (pos/1000)/60, (pos/1000)%60, (dur/1000)/60, (dur/1000)%60)
    }

    Box(Modifier.fillMaxSize()) {
        // Video surface or audio background
        if (player != null && item is PlayableItem.Mv) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; setPlayer(player) } }, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A1B3A), Color.Black))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(100.dp).background(KtvTheme.Accent.copy(0.15f), CircleShape).border(2.dp, KtvTheme.Accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("🎵", fontSize = 48.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(item.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(item.artist, fontSize = 16.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // Bottom control bar
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0xCC0D1117)).padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = progress, color = KtvTheme.Accent, trackColor = KtvTheme.Border,
                    modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(timeText, color = KtvTheme.TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(item.artist, fontSize = 13.sp, color = KtvTheme.TextMuted)
                }
                Row {
                    TvFocusBox(onClick = { KtvPlayerManager.togglePlayPause() }) {
                        Text(if (isPlaying) "⏸" else "▶", fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    TvFocusBox(onClick = { KtvPlayerManager.setVocalElimination(!isVocalOff) }) {
                        Text(if (isVocalOff) "🎙" else "🎤", fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    TvFocusBox(onClick = { KtvPlayerManager.skipCurrent() }) {
                        Text("⏭", fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}
