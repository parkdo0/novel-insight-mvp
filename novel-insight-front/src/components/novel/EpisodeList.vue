<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router' // useRoute 삭제
import { useAuthStore } from '../../stores/auth'
import {
  getEpisodes, purchaseEpisode, createEpisode,
  type Novel, type Episode
} from '../../api/novelApi'

const props = defineProps<{ novel: Novel | null }>()

const router = useRouter()
// const route = useRoute() // ★ [삭제] 빌드 에러 원인 제거
const authStore = useAuthStore()

const episodes = ref<Episode[]>([])
const showWriteForm = ref(false)
const newEpTitle = ref('')
const newEpContent = ref('')
const newEpIsFree = ref(true)

const isAuthor = computed(() => {
  return props.novel && authStore.isAuthenticated && authStore.userId === props.novel.authorId
})

const fetchEpisodes = async () => {
  if (!props.novel) return
  try {
    episodes.value = await getEpisodes(props.novel.id)
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  if (props.novel) fetchEpisodes()
})

const goViewer = async (episode: Episode) => {
  if (!props.novel) return

  // novelId를 쿼리 파라미터로 전달 (이전화/다음화 네비게이션용)
  const viewerUrl = `/viewer/${episode.id}?novelId=${props.novel.id}`

  if (episode.isFree || episode.price === 0 || episode.isPurchased || isAuthor.value) {
    router.push(viewerUrl)
    return
  }

  if (!authStore.isAuthenticated) {
    if (confirm("로그인이 필요합니다.")) {
      router.push({
        path: '/login',
        query: { redirect: `/novels/${props.novel.id}/episodes` }
      })
    }
    return
  }

  if (confirm(`${episode.price}P로 구매하시겠습니까?`)) {
    try {
      await purchaseEpisode(episode.id)
      alert("구매 완료!")
      await authStore.fetchUserInfo()
      episode.isPurchased = true
      router.push(viewerUrl)
    } catch (e: any) {
      const msg = e.response?.data || ""
      if (e.response?.status === 500 || (typeof msg === 'string' && msg.includes("이미"))) {
        episode.isPurchased = true
        router.push(viewerUrl)
      } else {
        alert("구매 실패: " + msg)
      }
    }
  }
}

const handleCreateEpisode = async () => {
  if (!props.novel) return
  if (!newEpTitle.value || !newEpContent.value) return alert("입력해주세요")

  try {
    await createEpisode(props.novel.id, newEpTitle.value, newEpContent.value, newEpIsFree.value)
    alert("등록 완료")
    showWriteForm.value = false
    newEpTitle.value = ''
    newEpContent.value = ''
    fetchEpisodes()
  } catch (e: any) {
    alert("등록 실패: " + (e.response?.data || e.message))
  }
}
</script>

<template>
  <div class="episode-list">
    <div v-if="isAuthor" class="author-tools">
      <button @click="showWriteForm = !showWriteForm">➕ 회차 추가</button>
    </div>

    <div v-if="showWriteForm" class="write-form">
      <input v-model="newEpTitle" placeholder="제목" />
      <textarea v-model="newEpContent" placeholder="내용"></textarea>
      <label><input type="checkbox" v-model="newEpIsFree" /> 무료</label>
      <button @click="handleCreateEpisode">등록</button>
    </div>

    <div v-if="episodes.length === 0" class="empty">등록된 회차가 없습니다.</div>

    <div v-for="ep in episodes" :key="ep.id" class="episode-item" @click="goViewer(ep)">
      <div class="left">
        <span class="title">{{ ep.title }}</span>
        <div class="stats">
          <span>👁️ {{ ep.viewCount }}</span>
          <span>💰 {{ ep.purchaseCount }}</span>
        </div>
      </div>
      <div class="right">
        <span v-if="ep.isFree" class="badge free">무료</span>
        <span v-else-if="ep.isPurchased || isAuthor" class="badge purchased">구매완료</span>
        <span v-else class="badge pay">{{ ep.price }}P</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.episode-item { display: flex; justify-content: space-between; padding: 15px; border-bottom: 1px solid #eee; cursor: pointer; transition: background 0.2s; }
.episode-item:hover { background: #f9f9f9; }
.left { display: flex; flex-direction: column; gap: 5px; }
.title { font-weight: bold; }
.stats { font-size: 0.8rem; color: #888; display: flex; gap: 10px; }
.badge { padding: 4px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: bold; }
.free { background: #e8f5e9; color: #2e7d32; }
.pay { background: #ffebee; color: #c62828; }
.purchased { background: #e3f2fd; color: #1976d2; }
.author-tools { text-align: right; margin-bottom: 10px; }
.author-tools button { background: #42b883; color: white; padding: 8px 16px; border: none; border-radius: 4px; font-weight: bold; cursor: pointer; }
.write-form { padding: 10px; background: #f5f5f5; border-radius: 8px; display: flex; flex-direction: column; gap: 10px; margin-bottom: 10px; }
input, textarea { padding: 8px; border: 1px solid #ddd; border-radius: 4px; width: 100%; box-sizing: border-box; }
.empty { padding: 20px; text-align: center; color: #999; }
</style>