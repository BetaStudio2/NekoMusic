<template>
  <!-- 列表行里的「正在播放」标记：与底部播放条共用播放状态桥 -->
  <span class="now-playing" role="img" aria-label="正在播放" title="正在播放">
    <NIcon name="audio-lines" :size="14" />
  </span>
</template>

<script setup>
import NIcon from '@/icons/NIcon.vue'
</script>

<style scoped>
.now-playing {
  display: inline-flex;
  align-items: center;
  flex: none;
  color: var(--n-accent);
}
</style>
