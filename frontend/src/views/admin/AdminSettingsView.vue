<template>
  <div class="subpage">
    <header class="subpage__head">
      <div>
        <h1 class="subpage__title">系统设置</h1>
        <p class="subpage__desc">
          运行时配置统一在这里维护，保存后立即生效；标注「需重启」的项等后端重启后生效。
        </p>
      </div>
      <div class="subpage__actions">
        <NButton variant="secondary" icon="rotate-ccw" :disabled="loading || saving" @click="loadSettings">
          重新加载
        </NButton>
        <NButton
          variant="primary"
          icon="check"
          :disabled="loading || saving || !hasChanges"
          @click="saveChanges"
        >
          保存修改<span v-if="changedCount">（{{ changedCount }}）</span>
        </NButton>
      </div>
    </header>

    <p v-if="loadError" class="err">
      <NIcon name="triangle-alert" :size="16" />
      {{ loadError }}
    </p>

    <p v-if="restartHint" class="notice">
      <NIcon name="triangle-alert" :size="16" />
      以下设置需重启后端才会生效：{{ restartHint }}
    </p>

    <div v-if="loading" class="placeholder">
      <NSpinner />
      <span>正在加载设置…</span>
    </div>

    <template v-else>
      <div class="tabs" role="tablist" aria-label="设置分组">
        <button
          v-for="group in groups"
          :key="group.key"
          type="button"
          role="tab"
          :aria-selected="group.key === activeGroup"
          class="tab"
          :class="{ 'tab--active': group.key === activeGroup }"
          @click="activeGroup = group.key"
        >
          {{ group.title }}
        </button>
      </div>

      <section v-if="currentGroup" class="panel" role="tabpanel">
        <div v-for="item in currentGroup.settings" :key="item.key" class="field">
          <div class="field__main">
            <label class="field__label" :for="`set-${item.key}`">
              {{ item.label }}
              <NTag v-if="item.restartRequired" variant="warning" size="sm">需重启</NTag>
            </label>
            <p class="field__desc">{{ item.description }}</p>
          </div>

          <div class="field__control">
            <template v-if="item.key === 'netease_search_fill.cookie'">
              <NeteaseLoginCard />
              <details class="fallback">
                <summary class="fallback__summary">手动填写 Cookie（扫码不可用时）</summary>
                <div class="secret">
                  <NInput
                    :id="`set-${item.key}`"
                    type="password"
                    :model-value="form[item.key]"
                    :placeholder="item.configured ? '已配置（不填则保持不变）' : '未配置'"
                    :disabled="saving"
                    @update:model-value="onSecretInput(item.key, $event)"
                  />
                  <NButton
                    v-if="item.configured"
                    size="sm"
                    variant="ghost"
                    icon="trash-2"
                    title="清除该项"
                    :disabled="saving"
                    @click="clearSecret(item.key)"
                  />
                </div>
              </details>
            </template>

            <label
              v-else-if="item.type === 'bool'"
              class="switch"
              :class="{ 'switch--on': form[item.key] === 'true' }"
            >
              <input
                :id="`set-${item.key}`"
                type="checkbox"
                :checked="form[item.key] === 'true'"
                :disabled="saving"
                @change="onBoolChange(item.key, $event.target.checked)"
              />
              <span class="switch__track"><span class="switch__thumb" /></span>
              <span class="switch__label">{{ form[item.key] === 'true' ? '开启' : '关闭' }}</span>
            </label>

            <div v-else-if="item.secret" class="secret">
              <NInput
                :id="`set-${item.key}`"
                type="password"
                :model-value="form[item.key]"
                :placeholder="item.configured ? '已配置（不填则保持不变）' : '未配置'"
                :disabled="saving"
                @update:model-value="onSecretInput(item.key, $event)"
              />
              <NButton
                v-if="item.configured"
                size="sm"
                variant="ghost"
                icon="trash-2"
                title="清除该项"
                :disabled="saving"
                @click="clearSecret(item.key)"
              />
            </div>

            <NInput
              v-else-if="item.type === 'list'"
              :id="`set-${item.key}`"
              v-model="form[item.key]"
              textarea
              :rows="5"
              :disabled="saving"
              placeholder="每行一条，留空表示不限制"
            />

            <NInput
              v-else-if="item.type === 'int' || item.type === 'long' || item.type === 'double'"
              :id="`set-${item.key}`"
              v-model="form[item.key]"
              type="number"
              :min="item.min ?? undefined"
              :max="item.max ?? undefined"
              :step="item.type === 'double' ? '0.01' : '1'"
              :disabled="saving"
              class="control--num"
            />

            <NInput
              v-else
              :id="`set-${item.key}`"
              v-model="form[item.key]"
              :disabled="saving"
            />
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NInput, NSpinner, NTag } from '@/ui'
import { fetchAdminSettings, saveAdminSettings } from '@/api/adminSettings.js'
import NeteaseLoginCard from './components/NeteaseLoginCard.vue'

const router = useRouter()
const toast = useToast()

const groups = ref([])
const activeGroup = ref('')
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const restartHint = ref('')

/** 草稿值：key -> 字符串；数值/布尔/列表统一按字符串编辑，提交前由后端按类型校验 */
const form = reactive({})
/** 加载时的原始值，用于只提交改动过的键 */
const original = reactive({})
/** 敏感项：未编辑过就不提交，避免「看了一眼就清空」 */
const secretTouched = reactive({})

const currentGroup = computed(() => groups.value.find((g) => g.key === activeGroup.value) || null)

const allSettings = computed(() => groups.value.flatMap((g) => g.settings || []))

const changedKeys = computed(() =>
  allSettings.value
    .filter((item) => {
      if (item.secret) return secretTouched[item.key] === true
      return (form[item.key] ?? '') !== (original[item.key] ?? '')
    })
    .map((item) => item.key)
)

const changedCount = computed(() => changedKeys.value.length)
const hasChanges = computed(() => changedCount.value > 0)

/** 敏感项：只有真正编辑过的键才会提交，避免「打开页面就被清空」 */
const onSecretInput = (key, value) => {
  form[key] = value ?? ''
  secretTouched[key] = true
}

const clearSecret = (key) => {
  form[key] = ''
  secretTouched[key] = true
}

const onBoolChange = (key, checked) => {
  form[key] = checked ? 'true' : 'false'
}

const loadSettings = async () => {
  loadError.value = ''
  loading.value = true
  try {
    const list = await fetchAdminSettings()
    groups.value = list
    Object.keys(form).forEach((k) => delete form[k])
    Object.keys(original).forEach((k) => delete original[k])
    Object.keys(secretTouched).forEach((k) => delete secretTouched[k])
    for (const group of list) {
      for (const item of group.settings || []) {
        if (item.secret) {
          form[item.key] = ''
          original[item.key] = ''
        } else {
          form[item.key] = item.value ?? ''
          original[item.key] = item.value ?? ''
        }
      }
    }
    if (!list.some((g) => g.key === activeGroup.value)) {
      activeGroup.value = list.length ? list[0].key : ''
    }
  } catch (e) {
    loadError.value = e.message || '加载失败'
    toast.error(loadError.value)
    if (!localStorage.getItem('adminToken')) router.push('/admin/login')
  } finally {
    loading.value = false
  }
}

const saveChanges = async () => {
  if (!hasChanges.value) return
  const values = {}
  for (const key of changedKeys.value) {
    values[key] = form[key] ?? ''
  }
  saving.value = true
  try {
    const result = await saveAdminSettings(values)
    toast.success(`已保存 ${result.updated} 项设置`)
    restartHint.value = result.restartRequired.length
      ? result.restartRequired
          .map((key) => allSettings.value.find((s) => s.key === key)?.label || key)
          .join('、')
      : ''
    await loadSettings()
  } catch (e) {
    toast.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const storedToken = localStorage.getItem('adminToken')
  const storedAdminInfo = localStorage.getItem('adminInfo')
  if (!storedToken || !storedAdminInfo) {
    router.push('/admin/login')
    return
  }
  let adminInfo
  try {
    adminInfo = JSON.parse(storedAdminInfo)
  } catch {
    router.push('/admin/login')
    return
  }
  const role = adminInfo.role || 'admin'
  if (role === 'auditor') {
    toast.info('无权限访问系统设置')
    router.replace('/admin')
    return
  }
  await loadSettings()
})
</script>

<style scoped>
.subpage {
  width: 100%;
}

.subpage__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-6);
}

.subpage__title {
  margin: 0 0 var(--n-space-1);
  font-size: clamp(1.25rem, 2.6vw, 1.6rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.02em;
  color: var(--n-text);
}

.subpage__desc {
  margin: 0;
  max-width: 68ch;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.subpage__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
}

.err,
.notice {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: 0 0 var(--n-space-5);
  padding: var(--n-space-3) var(--n-space-4);
  border-radius: var(--n-radius-control);
  font-size: var(--n-text-sm);
}

.err {
  border: 1px solid rgba(255, 107, 107, 0.28);
  background: var(--n-danger-soft);
  color: #ffb3b3;
}

.notice {
  border: 1px solid var(--n-accent-line);
  background: var(--n-accent-soft);
  color: var(--n-text);
}

.placeholder {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  padding: var(--n-space-6);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ==================== 分组标签 ==================== */
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-2);
  margin-bottom: var(--n-space-4);
}

.tab {
  padding: 6px var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-pill);
  background: var(--n-surface-soft);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  transition:
    background var(--n-duration-fast) var(--n-ease),
    color var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .tab:not(.tab--active):hover {
    background: var(--n-surface-hover);
    color: var(--n-text);
  }
}

.tab--active {
  border-color: var(--n-accent-line);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
  font-weight: var(--n-weight-semibold);
}

/* ==================== 设置项 ==================== */
.panel {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
}

.field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-4) var(--n-space-5);
  border-bottom: 1px solid var(--n-line-subtle);
}

.field:last-child {
  border-bottom: none;
}

.field__main {
  flex: 1 1 260px;
  min-width: 0;
}

.field__label {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  color: var(--n-text);
  font-size: var(--n-text-base);
  font-weight: var(--n-weight-medium);
}

.field__desc {
  margin: var(--n-space-1) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  line-height: var(--n-leading-normal);
}

.field__control {
  flex: 0 1 360px;
  min-width: 0;
}

.control--num {
  max-width: 200px;
}

.secret {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
}

.secret :deep(.n-input) {
  flex: 1;
}

/* 网易云登录：扫码为主，手动 Cookie 作为兜底 */
.fallback {
  margin-top: var(--n-space-3);
  border-top: 1px solid var(--n-line-subtle);
  padding-top: var(--n-space-2);
}

.fallback__summary {
  cursor: pointer;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  user-select: none;
}

.fallback[open] .fallback__summary {
  margin-bottom: var(--n-space-2);
}

/* ==================== 开关 ==================== */
.switch {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-3);
  cursor: pointer;
  user-select: none;
}

.switch input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.switch__track {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  border: 1px solid var(--n-line);
  border-radius: 999px;
  background: var(--n-surface-soft);
  transition: background var(--n-duration-fast) var(--n-ease), border-color var(--n-duration-fast) var(--n-ease);
}

.switch__thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--n-text-faint);
  transition: transform var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease);
}

.switch--on .switch__track {
  border-color: var(--n-accent-line);
  background: var(--n-accent-soft);
}

.switch--on .switch__thumb {
  transform: translateX(20px);
  background: var(--n-accent);
}

.switch__label {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

@media (max-width: 900px) {
  .field {
    padding: var(--n-space-4);
  }

  .field__control {
    flex-basis: 100%;
  }
}
</style>
