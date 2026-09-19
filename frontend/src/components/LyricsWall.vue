<script setup>
/**
 * LyricsWall —— AMLL 歌词墙
 * ------------------------------------------------------------
 * 使用官方 @applemusic-like-lyrics/vue 的 LyricPlayer（Apple Music 风格
 * 逐行滚动 + 弹簧动效 + 逐字高亮）。
 *
 * 数据衔接：
 *   本项目歌词由后端返回 LRC 文本，PlayerView 已解析为
 *   [{ time(秒), text, translation }]，这里转换成 AMLL 的 LyricLine[]
 *   （毫秒、words[]、translatedLyric）。
 *
 * 说明：不依赖 @applemusic-like-lyrics/lyric 的格式解析，避免猜测我们
 * 自定义的双语 `{"翻译"}` 约定；直接手工构造 LyricLine 更可控。
 */
import { computed } from 'vue'
import { LyricPlayer } from '@applemusic-like-lyrics/vue'
import '@applemusic-like-lyrics/core/style.css'
import NIcon from '@/icons/NIcon.vue'

const props = defineProps({
  /** PlayerView 解析结果：{ time(秒), text, translation } */
  lines: { type: Array, default: () => [] },
  /** 当前播放时间（秒，可含小数） */
  currentTime: { type: Number, default: 0 },
  /** 是否正在播放 */
  playing: { type: Boolean, default: false },
  /** 没有最后一行结束时间时的兜底时长（毫秒） */
  tailMs: { type: Number, default: 8000 },
})

/** 秒 → 毫秒（AMLL 要求整数毫秒） */
const toMs = (sec) => Math.max(0, Math.round((Number(sec) || 0) * 1000))

const lyricLines = computed(() =>
  props.lines.map((line, i) => {
    const startTime = toMs(line.time)
    const next = props.lines[i + 1]
    // 下一行起点即本行终点；最后一行给一个兜底时长
    const endTime = next ? Math.max(startTime + 1, toMs(next.time)) : startTime + props.tailMs
    return {
      words: [{ word: line.text || '', startTime, endTime }],
      translatedLyric: line.translation || '',
      romanLyric: '',
      startTime,
      endTime,
      isBG: false,
      isDuet: false,
    }
  })
)

const currentMs = computed(() => toMs(props.currentTime))
</script>

<template>
  <div class="wall">
    <LyricPlayer
      v-if="lyricLines.length"
      class="wall__player"
      :lyric-lines="lyricLines"
      :current-time="currentMs"
      :playing="playing"
    />
    <div v-else class="wall__empty">
      <NIcon name="file-text" :size="24" />
      <p>暂无歌词</p>
    </div>
  </div>
</template>

<style scoped>
.wall {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
  overflow: hidden;
}

.wall__player {
  width: 100%;
  height: 100%;
}

.wall__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--n-space-2);
  height: 100%;
  color: var(--n-text-faint);
}

.wall__empty p {
  margin: 0;
  font-size: var(--n-text-sm);
}
</style>
