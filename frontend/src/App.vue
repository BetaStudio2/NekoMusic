<script setup>
import { RouterView, useRoute } from 'vue-router'
import { computed } from 'vue'
import SearchHeader from './components/SearchHeader.vue'
import Footer from './components/Footer.vue'
import GlobalPlayer from './components/GlobalPlayer.vue'

const route = useRoute()

// 全幅背景壳层：去掉 main 内边距外框（与 app--home 的页面集合对齐）
const isFlushMain = computed(
  () =>
    route.name === 'download' ||
    route.name === 'home' ||
    route.name === 'detail' ||
    route.name === 'search' ||
    route.name === 'search-query' ||
    route.name === 'playlists' ||
    route.name === 'create-playlist' ||
    route.name === 'playlist-detail' ||
    route.name === 'about' ||
    route.name === 'privacy' ||
    route.name === 'login' ||
    route.name === 'register' ||
    route.name === 'forgot-password' ||
    route.name === 'favorites' ||
    route.name === 'ranking' ||
    route.name === 'latest' ||
    route.name === 'upload' ||
    route.name === 'account' ||
    route.name === 'vip' ||
    route.name === 'not-found'
)
// 下载页独立布局：不显示顶栏搜索、底栏与全局播放器
const isDownloadPage = computed(() => route.name === 'download')
// 顶栏 / 播放器 / 底栏：与深色内容区一致的 chrome（管理后台除外）
const isChromeDarkShell = computed(
  () =>
    route.name === 'home' ||
    route.name === 'detail' ||
    route.name === 'search' ||
    route.name === 'search-query' ||
    route.name === 'playlists' ||
    route.name === 'create-playlist' ||
    route.name === 'playlist-detail' ||
    route.name === 'about' ||
    route.name === 'privacy' ||
    route.name === 'login' ||
    route.name === 'register' ||
    route.name === 'forgot-password' ||
    route.name === 'favorites' ||
    route.name === 'ranking' ||
    route.name === 'latest' ||
    route.name === 'upload' ||
    route.name === 'account' ||
    route.name === 'vip' ||
    route.name === 'not-found'
)
</script>

<template>
  <div id="app" :class="{ 'app--home': isChromeDarkShell }">
    <SearchHeader v-if="!isDownloadPage" :chrome-dark="isChromeDarkShell" />
    <main :class="{ 'main--flush': isFlushMain }">
      <RouterView />
    </main>
    <GlobalPlayer v-if="!isDownloadPage" :chrome-dark="isChromeDarkShell" />
    <Footer v-if="!isDownloadPage" :chrome-dark="isChromeDarkShell" />
  </div>
</template>

<style scoped>
#app {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

/* 深色内容页：三层背景（与 glass-page 页共用） */
#app.app--home {
  background:
    radial-gradient(1200px 700px at 12% -10%, rgba(105, 200, 223, 0.18), transparent 55%),
    radial-gradient(900px 600px at 92% 8%, rgba(120, 145, 164, 0.12), transparent 50%),
    linear-gradient(180deg, #070b10, #0b1118 42%, #06090d 100%);
}

main {
  flex: 1;
  padding: 20px;
  position: relative;
}

/* 下载页 / 首页：全幅背景，避免 main 内边距形成一圈「外框」 */
main.main--flush {
  padding: 0;
}

.content {
  max-width: 800px;
  margin: 0 auto;
  text-align: center;
  position: relative;
  padding-top: 40px;
}

.welcome-card {
  background: rgba(15, 23, 33, 0.76);
  border-radius: 24px;
  padding: 40px;
  box-shadow: 0 22px 70px rgba(0, 0, 0, 0.34);
  border: 1px solid rgba(143, 174, 198, 0.16);
  position: relative;
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  margin: 20px auto;
  max-width: 90%;
  overflow: hidden;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.welcome-card::before {
  content: '';
  position: absolute;
  top: -10px;
  left: -10px;
  right: -10px;
  bottom: -10px;
  background: linear-gradient(45deg, rgba(105, 200, 223, 0.22), rgba(111, 137, 156, 0.18), rgba(105, 200, 223, 0.22));
  background-size: 300%;
  border-radius: 30px;
  z-index: -1;
  filter: blur(18px);
  opacity: 0.35;
  animation: gradientShift 10s ease infinite;
}

.welcome-title {
  margin-bottom: 1rem;
  font-size: 2.2rem;
  color: #eff9fc;
  background: linear-gradient(120deg, #eff9fc, #9beaff, #d7edf5);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  position: relative;
  z-index: 1;
}

.welcome-subtitle {
  color: #a6b7c4;
  font-size: 1.2rem;
  margin-bottom: 30px;
  position: relative;
  z-index: 1;
}

.decoration-element {
  position: relative;
  height: 60px;
  margin-top: 20px;
}

.music-note {
  position: absolute;
  font-size: 1.8rem;
  opacity: 0.7;
  animation: float 3s ease-in-out infinite;
  position: relative;
  z-index: 1;
}

.note-1 {
  top: 0;
  left: 30%;
  color: #9beaff;
  animation-delay: 0s;
}

.note-2 {
  top: 10px;
  right: 30%;
  color: #d7edf5;
  animation-delay: 1.5s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-10px) rotate(5deg);
  }
}

@keyframes gradientShift {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.welcome-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 40px rgba(31, 38, 135, 0.5);
}
</style>
