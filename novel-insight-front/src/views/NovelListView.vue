<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNovels, type Novel } from '../api/novelApi'

const router = useRouter()
const novels = ref<Novel[]>([])
const loading = ref(false)
const page = ref(0)
const hasMore = ref(true)

const loadMore = async () => {
    if (loading.value || !hasMore.value) return
    loading.value = true
    try {
        const data = await getNovels(page.value, 12)
        if (data.content.length === 0) hasMore.value = false
        else {
            novels.value.push(...data.content)
            page.value++
        }
    } finally { loading.value = false }
}

onMounted(() => loadMore())
const getCoverColor = (id: number) => ['#e3f2fd', '#f3e5f5', '#e8f5e9', '#fff3e0', '#ffebee'][id % 5]
</script>

<template>
    <div class="container">
        <div class="text-center mb-20">
            <h2>🔥 실시간 인기 웹소설</h2>
        </div>

        <div class="novel-grid">
            <div
                    v-for="novel in novels"
                    :key="novel.id"
                    class="card novel-item"
                    @click="router.push(`/novels/${novel.id}`)"
            >
                <div class="cover" :style="{ backgroundColor: getCoverColor(novel.id) }">
                    {{ novel.title.substring(0, 2) }}
                </div>

                <div class="info">
                    <h3 class="title">{{ novel.title }}</h3>
                    <p class="author">🖊️ {{ novel.author }}</p>
                    <div class="meta">
                        <span>👁️ {{ novel.viewCount }}</span>
                        <span>💬 {{ novel.reviews?.length || 0 }}</span>
                    </div>
                    <p class="desc">{{ novel.description ? novel.description.substring(0, 40) + '...' : '' }}</p>
                </div>
            </div>
        </div>

        <div class="text-center mt-20">
            <button v-if="hasMore" @click="loadMore" class="btn btn-outline" :disabled="loading">
                {{ loading ? '로딩 중...' : '작품 더 보기 👇' }}
            </button>
        </div>
    </div>
</template>

<style scoped>
.novel-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }
.novel-item { display: flex; gap: 15px; cursor: pointer; transition: transform 0.2s; padding: 15px; }
.novel-item:hover { transform: translateY(-3px); border-color: var(--primary-color); }
.cover { width: 80px; height: 110px; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 1.5rem; color: rgba(0,0,0,0.2); flex-shrink: 0; }
.info { flex: 1; overflow: hidden; }
.title { margin: 0 0 5px; font-size: 1.1rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.author { color: #888; font-size: 0.9rem; margin-bottom: 5px; }
.meta { font-size: 0.85rem; color: #999; margin-bottom: 8px; display: flex; gap: 8px; }
.desc { font-size: 0.9rem; color: #666; margin: 0; }
</style>