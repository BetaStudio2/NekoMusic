<script setup>
/**
 * CreatePlaylistView —— 创建歌单
 * ------------------------------------------------------------
 * 契约：POST /api/user/playlist/create，Authorization 传裸 userToken。
 */
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { NButton, NCard, NInput } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'
import { t } from '@/i18n'

const toast = useToast()
const router = useRouter()

const playlistName = ref('')
const playlistDescription = ref('')
const submitting = ref(false)

async function handleCreatePlaylist() {
  if (submitting.value) return
  submitting.value = true

  try {
    const token = localStorage.getItem('userToken')
    const requestData = { name: playlistName.value.trim() }
    if (playlistDescription.value.trim()) {
      requestData.description = playlistDescription.value.trim()
    }

    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/create`, {
      method: 'POST',
      headers: {
        Authorization: token,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(requestData),
    })
    const data = await response.json()

    if (data.success) {
      toast.success(t('createPlaylist.created'))
      router.push('/playlists')
    } else {
      toast.error(data.message || t('createPlaylist.createFailed'))
    }
  } catch (error) {
    console.error('歌单创建失败:', error)
    toast.error(t('createPlaylist.createFailed'))
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.back()
}
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="narrow" flush-top>
    <header class="head">
      <NButton
        class="head__back"
        variant="ghost"
        size="sm"
        icon="arrow-left"
        @click="goBack"
      >
        {{ $t('createPlaylist.back') }}
      </NButton>
      <h1 class="head__title">{{ $t('playlists.create') }}</h1>
      <p class="head__lede">{{ $t('createPlaylist.lede') }}</p>
    </header>

    <NCard pad="lg">
      <form class="form" @submit.prevent="handleCreatePlaylist">
        <div class="field">
          <label class="field__label" for="pl-name">
            {{ $t('playlists.nameLabel') }} <span class="field__required">*</span>
          </label>
          <NInput
            id="pl-name"
            v-model="playlistName"
            maxlength="255"
            :placeholder="$t('createPlaylist.namePlaceholder')"
            autocomplete="off"
            required
          />
          <span class="field__count">{{ playlistName.length }}/255</span>
        </div>

        <div class="field">
          <label class="field__label" for="pl-desc">{{ $t('playlists.descLabel') }}</label>
          <NInput
            id="pl-desc"
            v-model="playlistDescription"
            textarea
            :rows="4"
            maxlength="500"
            :placeholder="$t('createPlaylist.descPlaceholder')"
          />
          <span class="field__count">{{ playlistDescription.length }}/500</span>
        </div>

        <div class="form__actions">
          <NButton variant="ghost" @click="goBack">{{ $t('common.cancel') }}</NButton>
          <NButton type="submit" variant="primary" :loading="submitting">{{ $t('playlists.create') }}</NButton>
        </div>
      </form>
    </NCard>
  </PageShell>
</template>

<style scoped>
.head {
  margin-bottom: var(--n-space-6);
}

.head__back {
  margin-bottom: var(--n-space-3);
}

.head__title {
  margin: 0 0 var(--n-space-2);
  font-size: clamp(1.45rem, 3vw, 1.85rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.03em;
}

.head__lede {
  margin: 0;
  color: var(--n-text-muted);
  line-height: var(--n-leading-normal);
}

.form {
  display: flex;
  flex-direction: column;
}

.field {
  margin-bottom: var(--n-space-6);
}

.field__label {
  display: block;
  margin-bottom: var(--n-space-2);
  color: var(--n-text);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
}

.field__required {
  color: var(--n-danger);
}

.field__count {
  display: block;
  margin-top: var(--n-space-1);
  text-align: right;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  font-variant-numeric: tabular-nums;
}

.form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  justify-content: flex-end;
}

@media (max-width: 560px) {
  .form__actions {
    flex-direction: column-reverse;
  }

  .form__actions :deep(.n-btn) {
    width: 100%;
    justify-content: center;
  }
}
</style>
