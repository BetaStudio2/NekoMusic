<template>
  <div class="layout">
    <TitleBar />
    <div class="layout-content">
      <Sidebar />
      <div class="main-content">
        <router-view />
      </div>
    </div>
    <PlayerBar />
  </div>
</template>

<script setup>
import TitleBar from './TitleBar.vue'
import Sidebar from './Sidebar.vue'
import PlayerBar from './PlayerBar.vue'
</script>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.layout-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.main-content {
  flex: 1;
  overflow: auto;
  background: rgba(255, 255, 255, 0.95);
}
</style>