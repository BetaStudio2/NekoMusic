<template>
  <div class="app">
    <TitleBar />
    <div class="content">
      <h1>Hello World</h1>
    </div>
  </div>
</template>

<script setup>
import TitleBar from './components/TitleBar.vue'
</script>

<style scoped>
.app {
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.content {
  display: flex;
  justify-content: center;
  align-items: center;
  height: calc(100vh - 40px);
}

h1 {
  font-size: 4rem;
  color: white;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
}
</style>
