<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { readEpisode, addComment, getComments, type Episode, type Comment } from '../api/novelApi'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const episode = ref<Episode | null>(null)
const comments = ref<Comment[]>([])
const newComment = ref('')

const loadViewer = async () => {
  try {
    const epId = Number(route.params.episodeId)
    // ★ 여기서 조회수가 올라감
    episode.value = await readEpisode(epId)
    comments.value = await getComments(epId)
  } catch (e) {
    alert("볼 수 없는 회차입니다.")
    router.back()
  }
}

onMounted(() => loadViewer())

const handleAddComment = async () => {
  if (!authStore.isAuthenticated) return alert("로그인 필요")
  if (!newComment.value || !episode.value) return

  try {
    await addComment(episode.value.id, newComment.value)
    newComment.value = ''
    comments.value = await getComments(episode.value.id)
  } catch (e) {
    alert("댓글 등록 실패")
  }
}

// 뒤로 가기 (소설 상세로 이동)
const goBack = () => {
  const novelId = route.query.novelId
  if (novelId) {
    router.push(`/novels/${novelId}/episodes`)
  } else {
    router.back()
  }
}
</script>

<template>
  <div v-if="episode" class="viewer-container">
    <div class="viewer-header">
      <button @click="goBack" class="btn btn-outline">🔙 나가기</button>
      <h3>{{ episode.title }}</h3>
      <div style="width: 60px;"></div> </div>

    <div class="viewer-body">
      <p v-for="(line, idx) in episode.content.split('\n')" :key="idx">{{ line }}</p>
    </div>

    <div class="comments-section container">
      <h4>댓글 ({{ comments.length }})</h4>
      <div class="input-group mb-20">
        <input v-model="newComment" placeholder="댓글을 남겨주세요" />
        <button @click="handleAddComment" class="btn btn-primary">등록</button>
      </div>

      <div v-for="c in comments" :key="c.id" class="cmt-item">
        <span class="writer">{{ c.writerName }}:</span>
        <span class="content">{{ c.content }}</span>
      </div>
    </div>
  </div>
  <div v-else class="loading">로딩 중...</div>
</template>

<style scoped>
.viewer-container { background: white; min-height: 100vh; }
.viewer-header {
  padding: 15px 20px; border-bottom: 1px solid #eee;
  display: flex; justify-content: space-between; align-items: center;
  position: sticky; top: 0; background: white; z-index: 10;
}
.viewer-body {
  max-width: 700px; margin: 0 auto; padding: 40px 20px;
  font-family: 'KoPub Batang', serif; font-size: 1.3rem; line-height: 1.8; color: #222;
}
.comments-section { background: #f9f9f9; padding-top: 20px; padding-bottom: 50px; border-top: 1px solid #eee; }
.input-group { display: flex; gap: 10px; }
.cmt-item { padding: 10px 0; border-bottom: 1px solid #ddd; }
.writer { font-weight: bold; margin-right: 10px; }
.loading { text-align: center; padding: 50px; }
</style>