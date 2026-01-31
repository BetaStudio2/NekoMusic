<template>
  <div class="favorites-view">
    <MusicList title="我的收藏" :show-search="false" :show-favorite="true" />
  </div>
</template>

<script setup>
import MusicList from '../components/MusicList.vue'
</script>

<style scoped>
.favorites-view {
  height: 100%;
}
</style>