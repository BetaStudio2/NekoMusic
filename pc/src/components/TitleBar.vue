<template>
  <div class="title-bar">
    <div class="title-bar-left">
      <span class="title">NekoMusic</span>
    </div>
    <div class="title-bar-right">
      <button class="window-button minimize" @click="minimize" title="最小化">
        <svg width="12" height="12" viewBox="0 0 12 12">
          <rect x="0" y="5" width="12" height="2" fill="currentColor"/>
        </svg>
      </button>
      <button class="window-button maximize" @click="maximize" title="最大化">
        <svg width="12" height="12" viewBox="0 0 12 12">
          <rect x="1" y="1" width="10" height="10" fill="none" stroke="currentColor" stroke-width="1.5"/>
        </svg>
      </button>
      <button class="window-button close" @click="close" title="关闭">
        <svg width="12" height="12" viewBox="0 0 12 12">
          <path d="M1 1L11 11M11 1L1 11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
const minimize = () => {
  if (window.electronAPI?.minimize) {
    window.electronAPI.minimize()
  }
}

const maximize = () => {
  if (window.electronAPI?.maximize) {
    window.electronAPI.maximize()
  }
}

const close = () => {
  if (window.electronAPI?.close) {
    window.electronAPI.close()
  }
}
</script>

<style scoped>
.title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 40px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  -webkit-app-region: drag;
  user-select: none;
}

.title-bar-left {
  display: flex;
  align-items: center;
  padding-left: 16px;
}

.title {
  color: white;
  font-size: 14px;
  font-weight: 500;
}

.title-bar-right {
  display: flex;
  height: 100%;
  -webkit-app-region: no-drag;
}

.window-button {
  width: 46px;
  height: 100%;
  border: none;
  background: transparent;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s;
}

.window-button:hover {
  background: rgba(255, 255, 255, 0.15);
}

.window-button.close:hover {
  background: #e81123;
}

.window-button svg {
  opacity: 0.9;
}
</style>