<template>
  <div class="library-view">
    <MusicList title="音乐库" :show-search="true" :show-favorite="true" />
  </div>
</template>

<script setup>
import MusicList from '../components/MusicList.vue'
</script>

<style scoped>
.library-view {
  height: 100%;
}
</style>