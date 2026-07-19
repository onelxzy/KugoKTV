package com.echo.ktv.ui

import android.graphics.Bitmap
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    var currentMenu by remember { mutableStateOf("hot") } // hot, search, queue
    var searchKeyword by remember { mutableStateOf("") }
    var searchMvs by remember { mutableStateOf<List<MvItem>>(emptyList()) }
    var hotSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }

    val playlist by KtvPlayerManager.playlist.collectAsState()
    val currentPlaying by KtvPlayerManager.currentPlaying.collectAsState()

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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(KtvTheme.Background)
    ) {
        // Left Side Panel: Menu & Scan QR Code
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(Color(0xFF0A0F1D))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "酷唱 KTV",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = KtvTheme.Accent,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Menu Items
            val menus = listOf(
                "hot" to "🔥 推荐热歌",
                "search" to "🔍 检索点歌",
                "queue" to "🎶 已点歌单 (${playlist.size})"
            )

            menus.forEach { (key, label) ->
                TvFocusableItem(
                    onClick = { currentMenu = key },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) { isFocused ->
                    val bg = if (currentMenu == key) Color(0xFF1E293B) else Color.Transparent
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFocused || currentMenu == key) KtvTheme.Accent else KtvTheme.TextMain
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1.0f))

            // Phone Ordering QR Code Area
            qrBitmap?.let {
                Text(
                    text = "手机扫码点歌",
                    fontSize = 14.sp,
                    color = KtvTheme.TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "扫码点歌",
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Text(
                    text = "IP: $localIp:19985",
                    fontSize = 12.sp,
                    color = KtvTheme.TextMuted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Right Side Content Area
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .padding(24.dp)
        ) {
            if (currentPlaying != null) {
                // If a song/MV is active, show the Video Player Overlay fullscreen
                VideoPlayerOverlay(currentPlaying!!)
            } else {
                // Otherwise show the list selector
                when (currentMenu) {
                    "hot" -> HotSongsGrid(hotSongs) { KtvPlayerManager.addSongToQueue(it) }
                    "search" -> SearchMvContent(
                        keyword = searchKeyword,
                        onKeywordChange = { searchKeyword = it },
                        mvs = searchMvs,
                        onSearch = {
                            KugouApi.searchMV(searchKeyword) { result ->
                                result.onSuccess { searchMvs = it }
                            }
                        },
                        onSelect = { KtvPlayerManager.addMvToQueue(it) }
                    )
                    "queue" -> PlaylistQueueContent(playlist)
                }
            }
        }
    }
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
            TextField(
                value = keyword,
                onValueChange = onKeywordChange,
                placeholder = { Text("输入歌手/歌名拼音搜索...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = KtvTheme.CardBg,
                    unfocusedContainerColor = KtvTheme.CardBg,
                    focusedTextColor = KtvTheme.TextMain,
                    unfocusedTextColor = KtvTheme.TextMain
                ),
                modifier = Modifier
                    .weight(1.0f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            TvFocusableItem(onClick = onSearch) {
                Text("搜 索", fontSize = 18.sp, color = KtvTheme.TextMain, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
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
                        // Background Cover if available, else standard color
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
            // Video renderer component
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false // Hide default media controls
                        setPlayer(player)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Falling back placeholder: If it is a Song instead of MV, show a dynamic dark background visualizer effect
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

        // Overlay Controllers HUD (Lower banner)
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
                // Song Metadata
                Column {
                    Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(item.artist, fontSize = 16.sp, color = KtvTheme.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }

                // Control panel info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvFocusableItem(onClick = { KtvPlayerManager.togglePlayPause() }) {
                        Text("播放/暂停", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    TvFocusableItem(onClick = { KtvPlayerManager.setVocalElimination(!isVocalEliminated) }) {
                        Text(
                            text = if (isVocalEliminated) "🎙 伴奏模式 (已消音)" else "🎤 原唱模式",
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
