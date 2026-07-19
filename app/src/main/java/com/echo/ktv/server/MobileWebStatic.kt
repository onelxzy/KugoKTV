package com.echo.ktv.server

object MobileWebStatic {
    val INDEX_HTML = """
<!DOCTYPE html>
<html lang="zh">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>酷唱 KTV - 手机点歌台</title>
    <style>
        :root {
            --bg-color: #0b0f19;
            --card-bg: #1e293b;
            --accent-color: #06b6d4;
            --text-color: #f8fafc;
            --text-muted: #94a3b8;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-color);
            margin: 0;
            padding: 0;
            padding-bottom: 90px;
        }
        header {
            background-color: rgba(30, 41, 59, 0.8);
            backdrop-filter: blur(12px);
            position: sticky;
            top: 0;
            z-index: 100;
            padding: 15px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
        }
        h1 {
            font-size: 1.2rem;
            margin: 0;
            color: var(--accent-color);
        }
        .container {
            padding: 15px;
        }
        .search-box {
            display: flex;
            gap: 10px;
            margin-bottom: 20px;
        }
        input[type="text"] {
            flex: 1;
            background-color: var(--card-bg);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 8px;
            padding: 12px;
            color: var(--text-color);
            font-size: 1rem;
            outline: none;
        }
        input[type="text"]:focus {
            border-color: var(--accent-color);
        }
        button {
            background-color: var(--accent-color);
            color: white;
            border: none;
            border-radius: 8px;
            padding: 12px 20px;
            font-size: 1rem;
            font-weight: bold;
            cursor: pointer;
            transition: all 0.2s;
        }
        button:active {
            transform: scale(0.95);
        }
        .tabs {
            display: flex;
            gap: 10px;
            margin-bottom: 15px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            padding-bottom: 8px;
        }
        .tab {
            padding: 8px 16px;
            cursor: pointer;
            color: var(--text-muted);
            border-radius: 6px;
        }
        .tab.active {
            background-color: rgba(6, 182, 212, 0.15);
            color: var(--accent-color);
            font-weight: bold;
        }
        .song-list {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .song-card {
            background-color: var(--card-bg);
            border-radius: 10px;
            padding: 12px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border: 1px solid rgba(255, 255, 255, 0.02);
        }
        .song-info {
            flex: 1;
            margin-right: 15px;
        }
        .song-title {
            font-weight: bold;
            font-size: 1rem;
            margin-bottom: 4px;
        }
        .song-artist {
            font-size: 0.85rem;
            color: var(--text-muted);
        }
        .btn-add {
            background-color: rgba(6, 182, 212, 0.1);
            color: var(--accent-color);
            border: 1px solid var(--accent-color);
            padding: 6px 14px;
            font-size: 0.85rem;
            border-radius: 6px;
        }
        .btn-add:active {
            background-color: var(--accent-color);
            color: white;
        }
        /* Sticky Footer Controls */
        .controls-bar {
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            background-color: rgba(30, 41, 59, 0.95);
            backdrop-filter: blur(16px);
            border-top: 1px solid rgba(255, 255, 255, 0.08);
            padding: 12px 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            z-index: 1000;
        }
        .now-playing {
            display: flex;
            flex-direction: column;
            max-width: 50%;
        }
        .now-playing-title {
            font-weight: bold;
            font-size: 0.95rem;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .now-playing-artist {
            font-size: 0.8rem;
            color: var(--text-muted);
        }
        .controls-buttons {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .btn-circle {
            width: 44px;
            height: 44px;
            border-radius: 50%;
            padding: 0;
            display: flex;
            justify-content: center;
            align-items: center;
        }
        .btn-circle-outline {
            background-color: transparent;
            border: 2px solid var(--accent-color);
            color: var(--accent-color);
        }
        /* Volume and Vocal Toggle overlay */
        .settings-panel {
            background-color: var(--card-bg);
            border-radius: 12px;
            padding: 15px;
            margin-bottom: 20px;
        }
        .settings-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 12px;
        }
        .settings-item:last-child {
            margin-bottom: 0;
        }
        .slider {
            width: 60%;
            accent-color: var(--accent-color);
        }
    </style>
</head>
<body>
    <header>
        <h1>酷唱 KTV · 点歌台</h1>
        <button id="btn-refresh" style="padding: 6px 12px; font-size: 0.8rem;">刷新状态</button>
    </header>

    <div class="container">
        <!-- Status / Settings Panel -->
        <div class="settings-panel">
            <div class="settings-item">
                <span>原唱 / 伴奏切换</span>
                <button id="btn-vocal" class="btn-add" style="border-radius: 20px;">原唱</button>
            </div>
            <div class="settings-item">
                <span>伴奏音量</span>
                <input type="range" id="vol-slider" class="slider" min="0" max="100" value="100">
            </div>
        </div>

        <!-- Search Box -->
        <div class="search-box">
            <input type="text" id="search-input" placeholder="输入歌名、歌手搜索MV...">
            <button id="btn-search">搜索</button>
        </div>

        <div class="tabs">
            <div class="tab active" id="tab-mv">酷狗 MV 库</div>
            <div class="tab" id="tab-song">纯音频库 (备用)</div>
            <div class="tab" id="tab-queue">已点已播</div>
        </div>

        <div class="song-list" id="list-container">
            <div style="text-align: center; color: var(--text-muted); margin-top: 40px;">请输入关键词搜索歌曲</div>
        </div>
    </div>

    <!-- Bottom Playing bar -->
    <div class="controls-bar">
        <div class="now-playing">
            <span class="now-playing-title" id="play-title">暂无播放</span>
            <span class="now-playing-artist" id="play-artist">-</span>
        </div>
        <div class="controls-buttons">
            <button id="btn-play" class="btn-circle btn-circle-outline">▶</button>
            <button id="btn-skip" class="btn-circle">⏭</button>
        </div>
    </div>

    <script>
        let currentTab = 'mv';
        let statusInterval = null;

        document.getElementById('tab-mv').addEventListener('click', () => switchTab('mv'));
        document.getElementById('tab-song').addEventListener('click', () => switchTab('song'));
        document.getElementById('tab-queue').addEventListener('click', () => switchTab('queue'));
        document.getElementById('btn-search').addEventListener('click', doSearch);
        document.getElementById('btn-refresh').addEventListener('click', updateStatus);

        document.getElementById('btn-play').addEventListener('click', () => sendControl('play'));
        document.getElementById('btn-skip').addEventListener('click', () => sendControl('skip'));
        
        const vocalBtn = document.getElementById('btn-vocal');
        vocalBtn.addEventListener('click', () => {
            const current = vocalBtn.innerText === '伴奏';
            sendControl('vocal', !current);
        });

        const volSlider = document.getElementById('vol-slider');
        volSlider.addEventListener('input', (e) => {
            sendControl('volume', e.target.value / 100);
        });

        function switchTab(tab) {
            currentTab = tab;
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            document.getElementById('tab-' + tab).classList.add('active');
            
            if (tab === 'queue') {
                loadQueue();
            } else {
                document.getElementById('list-container').innerHTML = '<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">请输入关键词搜索歌曲</div>';
            }
        }

        async function doSearch() {
            const input = document.getElementById('search-input').value.trim();
            if (!input) return;
            
            const container = document.getElementById('list-container');
            container.innerHTML = '<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">正在检索中...</div>';

            try {
                const response = await fetch(`/api/search?q=${'$'}{encodeURIComponent(input)}&type=${'$'}{currentTab}`);
                const data = await response.json();
                
                if (data.length === 0) {
                    container.innerHTML = '<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">未找到匹配歌曲</div>';
                    return;
                }

                container.innerHTML = '';
                data.forEach(item => {
                    const card = document.createElement('div');
                    card.className = 'song-card';
                    card.innerHTML = `
                        <div class="song-info">
                            <div class="song-title">${'$'}{item.title}</div>
                            <div class="song-artist">${'$'}{item.artist}</div>
                        </div>
                        <button class="btn-add">点歌</button>
                    `;
                    card.querySelector('.btn-add').addEventListener('click', () => addSong(item));
                    container.appendChild(card);
                });
            } catch (err) {
                container.innerHTML = '<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">检索失败，请重试</div>';
            }
        }

        async function addSong(item) {
            try {
                const response = await fetch('/api/add', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        type: currentTab,
                        title: item.title,
                        artist: item.artist,
                        hash: item.mvHash || item.hash,
                        albumAudioId: item.albumAudioId || '',
                        duration: item.duration || 0,
                        cover: item.cover || ''
                    })
                });
                alert('点歌成功！已加入队列');
            } catch (err) {
                alert('点歌失败，请检查网络');
            }
        }

        async function loadQueue() {
            const container = document.getElementById('list-container');
            container.innerHTML = '<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">加载已点列表中...</div>';
            try {
                const response = await fetch('/api/playlist');
                const list = await response.json();
                
                if (list.length === 0) {
                    container.innerHTML = '<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">已点队列为空，快去点歌吧！</div>';
                    return;
                }

                container.innerHTML = '';
                list.forEach((item, index) => {
                    const card = document.createElement('div');
                    card.className = 'song-card';
                    card.innerHTML = `
                        <div class="song-info">
                            <div class="song-title">${'$'}{index + 1}. ${'$'}{item.title}</div>
                            <div class="song-artist">${'$'}{item.artist}</div>
                        </div>
                    `;
                    container.appendChild(card);
                });
            } catch (err) {
                container.innerHTML = '<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">加载队列失败</div>';
            }
        }

        async function sendControl(action, value = null) {
            try {
                await fetch('/api/control', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action, value })
                });
                setTimeout(updateStatus, 200);
            } catch (err) {
                console.error(err);
            }
        }

        async function updateStatus() {
            try {
                const response = await fetch('/api/status');
                const status = await response.json();
                
                if (status.current) {
                    document.getElementById('play-title').innerText = status.current.title;
                    document.getElementById('play-artist').innerText = status.current.artist;
                } else {
                    document.getElementById('play-title').innerText = '暂无播放';
                    document.getElementById('play-artist').innerText = '-';
                }

                document.getElementById('btn-play').innerText = status.isPlaying ? '⏸' : '▶';
                vocalBtn.innerText = status.isVocalEliminated ? '伴奏' : '原唱';
                vocalBtn.className = status.isVocalEliminated ? 'btn-add' : 'btn-add btn-circle-outline';
            } catch (err) {
                console.error(err);
            }
        }

        // Auto update status
        updateStatus();
        statusInterval = setInterval(updateStatus, 3000);
    </script>
</body>
</html>
    """.trimIndent()
}
