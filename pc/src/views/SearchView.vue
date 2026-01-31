<template>
  <div class="search-view">
    <div v-if="searchQuery" class="search-header">
      <h2 class="search-title">搜索: {{ searchQuery }}</h2>
    </div>
    <MusicList 
      v-if="searchQuery"
      title="" 
      :show-search="false" 
      :show-favorite="true" 
      :fetch-function="fetchSearchResults" 
    />
    <div v-else class="empty-state">
      <svg class="empty-icon" viewBox="0 0 24 24" width="80" height="80">
        <path fill="currentColor" d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
      </svg>
      <p class="empty-text">输入关键词开始搜索音乐</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import MusicList from '../components/MusicList.vue'

const route = useRoute()
const searchQuery = ref('')

const fetchSearchResults = async () => {
  try {
    const response = await fetch('/api/music/search', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: searchQuery.value
      })
    })
    
    const data = await response.json()
    if (data.success && data.results) {
      return data.results
    }
    return []
  } catch (error) {
    console.error('搜索音乐失败:', error)
    return []
  }
}

onMounted(() => {
  const query = route.query.q
  if (query) {
    searchQuery.value = decodeURIComponent(query)
  }
})
</script>

<style scoped>
.search-view {
  height: 100%;
  padding: 24px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.search-header {
  margin-bottom: 24px;
}

.search-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 60px 20px;
  text-align: center;
}

.empty-icon {
  width: 80px;
  height: 80px;
  color: var(--text-muted);
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  color: var(--text-muted);
  margin: 0;
}
</style>