<template>
  <div class="home-view">
    <MusicList title="推荐音乐" :show-search="false" :show-favorite="true" />
  </div>
</template>

<script setup>
import MusicList from '../components/MusicList.vue'
</script>

<style scoped>
.home-view {
  height: 100%;
}
</style>