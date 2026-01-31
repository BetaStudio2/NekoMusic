<template>
  <div class="search-view">
    <MusicList title="搜索结果" :show-search="true" :show-favorite="true" />
  </div>
</template>

<script setup>
import MusicList from '../components/MusicList.vue'
</script>

<style scoped>
.search-view {
  height: 100%;
}
</style>