# Neko UI 框架

UI 重构的基础依赖层。所有新页面必须基于本框架构建，禁止绕过令牌与原子组件直接硬编码样式。

> 归档基线：标签 `legacy-ui-v1` / 分支 `legacy-ui` 保存了重构前的原始界面，随时可对比。

## 技术栈

- Vue 3.5（Composition API + `<script setup>`，**不使用 TypeScript**）
- Vite 8
- `morphicons` 1.7（图标变形动画）
- `lucide` 1.47（图标**数据**源）
- `vue-toastification`（消息提示，经 `useToast` 包装）

## 目录结构

```
src/
├── design/            设计令牌与全局重置
│   ├── tokens.css     全站唯一的取值范围来源
│   └── reset.css      元素重置与全局环境
├── icons/             图标层
│   ├── registry.js    图标名 → lucide 数据 的统一注册表
│   └── NIcon.vue      统一图标组件
├── ui/                基础 UI 原子组件
│   ├── NButton.vue / NCard.vue / NInput.vue
│   ├── NTag.vue / NSpinner.vue / NModal.vue
│   └── index.js       统一出口
├── composables/       全局状态与组合式函数
│   ├── useTheme.js    主题（预留浅色）
│   └── useToast.js    消息提示
├── layouts/           布局壳
│   ├── AppShell.vue / PageShell.vue / AmbientBackdrop.vue
│   └── index.js
└── utils/format.js    展示格式化纯函数
```

`design/tokens.css` 与 `design/reset.css` 已在 `main.js` 中全局引入。

## 设计令牌

组件**只允许**引用 CSS 变量，不得硬编码颜色 / 间距 / 圆角 / 时长。命名约定 `--n-<类别>-<语义>`。

常用：

| 类别 | 示例 |
|---|---|
| 背景 | `--n-bg` `--n-bg-soft` `--n-surface` `--n-surface-strong` |
| 文本 | `--n-text` `--n-text-muted` `--n-text-faint` |
| 强调 | `--n-accent` `--n-accent-strong` `--n-accent-soft` |
| 状态 | `--n-danger` `--n-warning` `--n-success` |
| 间距 | `--n-space-1`…`--n-space-16`（4px 基准） |
| 圆角 | `--n-radius-sm` `--n-radius` `--n-radius-lg` `--n-radius-pill` |
| 字号 | `--n-text-xs`…`--n-text-3xl` |
| 阴影 | `--n-shadow-sm` `--n-shadow` `--n-shadow-lg` `--n-shadow-glow` |
| 动效 | `--n-ease` `--n-duration-fast` `--n-duration` |
| 层级 | `--n-z-header` `--n-z-player` `--n-modal` |

## 图标层

图标数据统一在 `src/icons/registry.js` 登记（只在此处 `import` lucide，保证 tree-shaking）。页面通过 `name` 使用：

```vue
<NIcon name="play" />
<NIcon :name="isPlaying ? 'pause' : 'play'" :size="28" />   <!-- 自动弹簧变形 -->
<NIcon from="menu" to="close" :progress="p" />               <!-- 受控变形 -->
```

新增图标：在 `registry.js` 里从 `lucide` 导入并登记一个 kebab-case 名称。未登记的名称在开发环境会告警。

`NIcon` 支持 `size / color / strokeWidth / absoluteStrokeWidth / label / spring`。
`label` 存在时生成 `role="img"`，否则 `aria-hidden`。

## UI 原子组件

| 组件 | 关键 props |
|---|---|
| `NButton` | `variant`(primary/secondary/ghost/danger/outline) `size` `icon` `iconAfter` `loading` `round` `block` |
| `NCard` | `variant`(glass/solid/plain) `pad` `hoverable` + `#header` `#actions` |
| `NInput` | `v-model` `type` `icon` `size` `clearable` `invalid` `textarea` |
| `NTag` | `variant` `size` `icon` `closable` |
| `NSpinner` | `size` `center` |
| `NModal` | `v-model` `title` `size` `maskClosable` `escClosable` + `#footer` |

```js
import { NButton, NCard } from '@/ui'
```

## 组合式函数

```js
import { useTheme } from '@/composables/useTheme'
import { useToast } from '@/composables/useToast'
import { formatDuration, formatCount, formatBytes, formatDate } from '@/utils/format'
```

## 布局

```vue
<AppShell :has-player="true">
  <template #header>…顶栏…</template>
  <PageShell title="标题" subtitle="副标题">
    <template #actions>…</template>
    正文
  </PageShell>
  <template #player>…播放器…</template>
  <template #footer>…页脚…</template>
</AppShell>
```

- `AppShell`：外层骨架，编排环境光 / 顶栏 / 内容 / 播放器 / 底栏；`flush` 用于全幅页面。
- `PageShell`：内容宽度与标题区；`width`(narrow/default/wide/full)、`centered`、`flushTop`。
- `AmbientBackdrop`：装饰背景，已内置于 `AppShell`。

## 自检页

开发环境访问 `/__kit` 可查看全部基础层组件的实际效果。

## 迁移约定

1. 新页面一律使用 `AppShell` + `PageShell`，不重复实现背景与容器。
2. 颜色 / 间距 / 圆角 / 动效一律引用令牌变量。
3. 图标一律走 `NIcon` + `registry`，不在页面直接 import lucide。
4. 提示消息走 `useToast`，不直接 import `vue-toastification`。
5. 迁移完成后，逐页删除旧 `components/` 中被替代的实现，并同步清理 `main.css` / `glassShell.css` 里对应的旧样式。
