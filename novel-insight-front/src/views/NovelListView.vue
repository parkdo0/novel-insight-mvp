<!--
  ========================================
  NovelListView - 소설 목록 페이지
  ========================================

  [Vue 3 Composition API 핵심 개념]
  - <script setup>: 간결한 setup() 문법 (자동으로 컴포넌트에 노출)
  - ref(): 반응형 상태 관리 (Reactivity System)
  - onMounted(): 라이프사이클 훅 (컴포넌트 마운트 시 실행)

  [무한 스크롤 패턴]
  - 페이지네이션 방식: Offset-based (page, size)
  - loadMore() 함수로 추가 데이터 로드
  - hasMore 플래그로 더 이상 데이터 없음 판단

  [실무 적용]
  - TypeScript 타입 안정성 (Novel 타입 import)
  - 비동기 데이터 로딩 (async/await)
  - 로딩 상태 관리 (loading flag)
-->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNovels, type Novel } from '../api/novelApi'

const router = useRouter()

/**
 * [반응형 상태 (Reactive State)]
 * - ref()로 래핑된 값은 .value로 접근
 * - 템플릿에서는 자동으로 언래핑되어 .value 생략
 * - 값이 변경되면 자동으로 DOM 업데이트 (Virtual DOM Diffing)
 */
const novels = ref<Novel[]>([])  // 소설 목록 (누적)
const loading = ref(false)        // 로딩 상태
const page = ref(0)               // 현재 페이지 (0부터 시작)
const hasMore = ref(true)         // 추가 데이터 존재 여부

/**
 * [무한 스크롤 구현]
 * - 사용자가 "더 보기" 버튼 클릭 시 다음 페이지 로드
 * - 중복 요청 방지: loading 또는 hasMore 체크
 * - finally: 성공/실패 관계없이 loading 해제
 */
const loadMore = async () => {
    if (loading.value || !hasMore.value) return
    loading.value = true
    try {
        const data = await getNovels(page.value, 12)  // Spring Pageable: page=0, size=12
        if (data.content.length === 0) hasMore.value = false
        else {
            novels.value.push(...data.content)  // 기존 배열에 추가 (spread 연산자)
            page.value++
        }
    } finally { loading.value = false }
}

/**
 * [라이프사이클 훅]
 * - onMounted(): 컴포넌트가 DOM에 마운트된 후 실행
 * - 초기 데이터 로딩에 사용
 */
onMounted(() => loadMore())

/**
 * [동적 스타일링]
 * - 소설 ID 기반으로 커버 색상 결정 (5가지 파스텔톤 순환)
 */
const getCoverColor = (id: number) => ['#e3f2fd', '#f3e5f5', '#e8f5e9', '#fff3e0', '#ffebee'][id % 5]
</script>

<template>
    <div class="container">
        <div class="text-center mb-20">
            <h2>실시간 인기 웹소설</h2>
        </div>

        <!--
          [v-for 리스트 렌더링]
          - :key는 필수 (Virtual DOM 효율성)
          - @click: 이벤트 핸들러 (프로그래매틱 네비게이션)
        -->
        <div class="novel-grid">
            <div
                    v-for="novel in novels"
                    :key="novel.id"
                    class="card novel-item"
                    @click="router.push(`/novels/${novel.id}`)"
            >
                <!-- 동적 스타일 바인딩 (:style) -->
                <div class="cover" :style="{ backgroundColor: getCoverColor(novel.id) }">
                    {{ novel.title.substring(0, 2) }}
                </div>

                <div class="info">
                    <h3 class="title">{{ novel.title }}</h3>
                    <p class="author">저자: {{ novel.author }}</p>
                    <div class="meta">
                        <span>조회수: {{ novel.viewCount }}</span>
                        <span>리뷰: {{ novel.reviews?.length || 0 }}</span>
                    </div>
                    <p class="desc">{{ novel.description ? novel.description.substring(0, 40) + '...' : '' }}</p>
                </div>
            </div>
        </div>

        <!--
          [조건부 렌더링 + 동적 텍스트]
          - v-if: hasMore가 true일 때만 렌더링
          - :disabled: loading 상태에 따라 버튼 비활성화
          - 삼항 연산자로 버튼 텍스트 동적 변경
        -->
        <div class="text-center mt-20">
            <button v-if="hasMore" @click="loadMore" class="btn btn-outline" :disabled="loading">
                {{ loading ? '로딩 중...' : '작품 더 보기' }}
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