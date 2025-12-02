<script setup lang="ts">
import { ref, onMounted, watch } from 'vue' // watch 추가
import { useRoute, useRouter } from 'vue-router'
import { getNovelDetail, type Novel } from '../api/novelApi'

const route = useRoute()
const router = useRouter()
const novel = ref<Novel | null>(null)
const loading = ref(true)

const loadData = async () => {
  try {
    const novelId = Number(route.params.id)
    novel.value = await getNovelDetail(novelId)
  } catch (e) {
    alert("소설 정보 로딩 실패")
    router.push('/')
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())

// ★ [추가] 라우트가 변경되어도(예: 뷰어에서 뒤로가기) 데이터 갱신 시도
watch(() => route.params.id, () => {
  loadData()
})
</script>

<template>
  <div v-if="loading" class="loading">로딩 중...</div>
  <div v-else-if="novel" class="detail-wrap">
    <div class="mb-20">
      <button @click="router.push('/')" class="btn btn-outline btn-sm">← 목록으로</button>
    </div>

    <div class="novel-header">
      <h1>{{ novel.title }}</h1>
      <p class="author">작가: {{ novel.author }}</p>
      <div class="meta">👁️ 조회수 {{ novel.viewCount }}</div>
    </div>

    <div class="tabs">
      <router-link :to="{ name: 'novel-info' }" class="tab" active-class="active">작품 정보</router-link>
      <router-link :to="{ name: 'novel-episodes' }" class="tab" active-class="active">회차 목록</router-link>
      <router-link :to="{ name: 'novel-reviews' }" class="tab" active-class="active">리뷰</router-link>
    </div>

    <router-view :novel="novel" />
  </div>
</template>

<style scoped>
.loading { text-align: center; padding: 50px; color: #999; }
.detail-wrap { max-width: 900px; margin: 0 auto; padding: 20px; }
.novel-header { border-bottom: 1px solid #eee; padding-bottom: 20px; margin-bottom: 20px; }
.author { color: #555; }
.meta { color: #888; font-size: 0.9rem; margin-top: 5px; }
.mb-20 { margin-bottom: 20px; }
/* 버튼, 탭 스타일은 style.css 전역 스타일 따름 */
</style>