<script setup>
/**
 * MiniLyric —— 播放条迷你滚动歌词
 * ------------------------------------------------------------
 * 对齐 ArchoeraMusic 的 BarLyricText / _Marquee：
 *  - 文本不超宽：右对齐单行显示（超出省略）
 *  - 文本超宽：两段相同文本、段间距 50px，以 30px/s 匀速循环滚动，
 *    启动延迟 2s（对齐原版 _gap / _speed / 起步延迟）
 *  - 暂停时冻结位移（animation-play-state: paused），不空转出帧；
 *    恢复播放接着滚，不回到开头
 *  - 换行：淡入 + 自下方 40% 上移，250ms 进 / 150ms 出
 *
 * 只负责「一条文本怎么滚动」，不关心歌词来源；字号与颜色一律继承外部，
 * 便于在播放条 / 其它紧凑位置复用。
 */
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'

const props = defineProps({
  /** 要显示的文本 */
  text: { type: String, default: '' },
  /** 是否播放中；false 时冻结滚动（原版行为） */
  playing: { type: Boolean, default: false },
  /** 滚动速度（px/s） */
  speed: { type: Number, default: 30 },
  /** 循环时两段文本之间的间距（px） */
  gap: { type: Number, default: 50 },
  /** 起步延迟（ms） */
  delay: { type: Number, default: 2000 },
})

const boxRef = ref(null)
const measureRef = ref(null)

/** 文本实际宽度 */
const textWidth = ref(0)
/** 容器可用宽度 */
const boxWidth = ref(0)

/** 是否需要滚动（留 0.5px 余量，避免亚像素误判） */
const overflowing = computed(() => textWidth.value > boxWidth.value + 0.5)

function measure() {
  const box = boxRef.value
  const ruler = measureRef.value
  if (!box || !ruler) return
  boxWidth.value = box.clientWidth
  textWidth.value = ruler.offsetWidth
}

/** 一个完整循环的位移 = 文本宽 + 段间距 */
const scrollDistance = computed(() => textWidth.value + props.gap)

const rowStyle = computed(() => {
  if (!overflowing.value) return {}
  const distance = scrollDistance.value
  if (distance <= 0) return {}
  return {
    '--ml-distance': `${distance}px`,
    animationDuration: `${Math.round((distance / props.speed) * 1000)}ms`,
    animationDelay: `${props.delay}ms`,
    animationPlayState: props.playing ? 'running' : 'paused',
  }
})

let ro = null

// 换行后重新测宽（DOM 要等 nextTick 才更新）
watch(() => props.text, () => nextTick(measure))

onMounted(() => {
  nextTick(measure)
  if (typeof ResizeObserver !== 'undefined' && boxRef.value) {
    ro = new ResizeObserver(measure)
    ro.observe(boxRef.value)
  }
})

onUnmounted(() => {
  ro?.disconnect()
  ro = null
})
</script>

<template>
  <div ref="boxRef" class="mini-lyric" :style="{ '--ml-gap': gap + 'px' }">
    <!-- 隐藏量尺：与可见文本同字体、始终不换行，用来判断是否需要滚动 -->
    <span ref="measureRef" class="mini-lyric__ruler" aria-hidden="true">{{ text }}</span>

    <Transition name="ml">
      <div v-if="text" :key="text" class="mini-lyric__line">
        <div
          class="mini-lyric__row"
          :class="{ 'is-scrolling': overflowing }"
          :style="rowStyle"
        >
          <template v-if="overflowing">
            <span class="mini-lyric__seg">{{ text }}</span>
            <span class="mini-lyric__seg" aria-hidden="true">{{ text }}</span>
          </template>
          <span v-else class="mini-lyric__static">{{ text }}</span>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.mini-lyric {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

/* 量尺：脱离可视区，但仍是正常布局，offsetWidth 可测 */
.mini-lyric__ruler {
  position: absolute;
  top: -9999px;
  left: 0;
  white-space: nowrap;
  visibility: hidden;
  pointer-events: none;
}

/* 换行进场动画的载体（与滚动动画分开，避免 transform 相互覆盖） */
.mini-lyric__line {
  position: absolute;
  inset: 0;
}

.mini-lyric__row {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 100%;
  display: flex;
  align-items: center;
}

.mini-lyric__row.is-scrolling {
  width: max-content;
  gap: var(--ml-gap, 50px);
  animation-name: mini-lyric-scroll;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

@keyframes mini-lyric-scroll {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(calc(-1 * var(--ml-distance, 0px)));
  }
}

.mini-lyric__seg {
  flex: none;
  white-space: nowrap;
}

/* 不超宽：右对齐单行，超出省略 */
.mini-lyric__static {
  width: 100%;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 换行：淡入 + 自下方 40% 上移（对齐原版 AnimatedSwitcher 的时长/曲线） */
.ml-enter-active {
  transition: opacity 250ms var(--n-ease-out), transform 250ms var(--n-ease-out);
}

.ml-leave-active {
  transition: opacity 150ms var(--n-ease), transform 150ms var(--n-ease);
}

.ml-enter-from,
.ml-leave-to {
  opacity: 0;
  transform: translateY(40%);
}

@media (prefers-reduced-motion: reduce) {
  .mini-lyric__row.is-scrolling {
    animation: none;
  }

  .ml-enter-active,
  .ml-leave-active {
    transition: none;
  }
}
</style>
