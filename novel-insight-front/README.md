# Novel Insight - 프론트엔드

웹소설 리뷰 감정 분석 플랫폼의 프론트엔드 애플리케이션입니다.

---

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [프로젝트 구조](#프로젝트-구조)
3. [기술 스택](#기술-스택)
4. [핵심 개념](#핵심-개념)
5. [개발 패턴](#개발-패턴)
6. [환경 설정 및 실행](#환경-설정-및-실행)

---

## 프로젝트 개요

Vue 3 + TypeScript + Vite를 사용한 **모던 SPA (Single Page Application)**입니다.

### 주요 기능

- **회원 인증**: JWT 토큰 기반 로그인/회원가입
- **웹소설 목록**: 무한 스크롤, 검색, 필터링
- **소설 상세**: 에피소드 목록, 리뷰 조회
- **리뷰 작성**: 감정 분석 결과 실시간 표시
- **포인트 결제**: 에피소드 구매 시스템
- **마이페이지**: 구매 이력, 내 리뷰 관리

---

## 프로젝트 구조

```
novel-insight-front/
├── public/                    # 정적 파일 (favicon 등)
├── src/
│   ├── main.ts               # 애플리케이션 진입점
│   ├── App.vue               # 루트 컴포넌트
│   ├── style.css             # 전역 스타일
│   │
│   ├── api/                  # API 통신 모듈
│   │   ├── client.ts         # Axios 인스턴스 설정
│   │   ├── auth.ts           # 인증 API
│   │   ├── novel.ts          # 소설 API
│   │   ├── review.ts         # 리뷰 API
│   │   └── purchase.ts       # 결제 API
│   │
│   ├── components/           # 재사용 가능한 컴포넌트
│   │   ├── auth/             # 인증 관련 컴포넌트
│   │   │   ├── LoginForm.vue           # 로그인 폼
│   │   │   └── SignupForm.vue          # 회원가입 폼
│   │   ├── common/           # 공통 컴포넌트
│   │   │   ├── Header.vue              # 상단 네비게이션
│   │   │   └── Footer.vue              # 하단 푸터
│   │   ├── novel/            # 소설 관련 컴포넌트
│   │   │   ├── NovelCard.vue           # 소설 카드
│   │   │   └── EpisodeList.vue         # 에피소드 목록
│   │   └── review/           # 리뷰 관련 컴포넌트
│   │       ├── ReviewForm.vue          # 리뷰 작성 폼
│   │       └── ReviewItem.vue          # 리뷰 아이템
│   │
│   ├── views/                # 페이지 컴포넌트
│   │   ├── NovelListView.vue          # 소설 목록 페이지
│   │   ├── NovelDetailLayout.vue      # 소설 상세 페이지
│   │   ├── LoginView.vue              # 로그인 페이지
│   │   ├── MyPageView.vue             # 마이페이지
│   │   └── ViewerPage.vue             # 뷰어 페이지
│   │
│   ├── router/               # Vue Router 설정
│   │   └── index.ts          # 라우팅 정의
│   │
│   └── stores/               # Pinia 상태 관리
│       └── auth.ts           # 인증 상태 관리
│
├── index.html                # HTML 엔트리 포인트
├── vite.config.ts            # Vite 설정
├── tsconfig.json             # TypeScript 설정
├── package.json              # 의존성 관리
└── Dockerfile                # Docker 이미지 빌드
```

### 디렉토리 설계 원칙

- **views/**: 라우팅에 매핑되는 페이지 컴포넌트
- **components/**: 재사용 가능한 작은 컴포넌트 (도메인별 분류)
- **api/**: 백엔드 API 호출 로직 분리 (컴포넌트에서 직접 axios 호출 금지)
- **stores/**: 전역 상태 관리 (Pinia)
- **router/**: 라우팅 설정 (경로와 컴포넌트 매핑)

---

## 기술 스택

### 핵심 라이브러리

| 기술 | 버전 | 용도 | 비고 |
|------|------|------|------|
| **Vue 3** | 3.5.24 | UI 프레임워크 | Composition API |
| **TypeScript** | 5.9.3 | 타입 안전성 | 타입 체크 |
| **Vite** | 7.2.4 | 빌드 도구 | 초고속 HMR |
| **Pinia** | 3.0.4 | 상태 관리 | Vuex 후속 |
| **Vue Router** | 4.6.3 | 라우팅 | SPA 네비게이션 |
| **Axios** | 1.13.2 | HTTP 클라이언트 | RESTful API 통신 |

### 개발 도구

| 도구 | 용도 |
|------|------|
| **vue-tsc** | Vue 파일 타입 체크 |
| **@vitejs/plugin-vue** | Vite에서 Vue SFC 지원 |
| **@types/node** | Node.js 타입 정의 |

---

## 핵심 개념

### 1. Vue 3 Composition API

**왜 Options API가 아닌 Composition API인가?**

Options API의 문제점:
- 로직이 여러 옵션(data, methods, computed 등)에 분산됨
- 코드 재사용이 어려움 (Mixins의 한계)
- TypeScript 지원 미흡

**Composition API의 장점**:
- 관련 로직을 함께 그룹화 가능
- 로직 재사용이 쉬움 (Composables)
- TypeScript 완벽 지원

**실무 적용 예시**:

```vue
<script setup lang="ts">
// `<script setup>` 문법: Composition API의 간결한 형태
// - setup() 함수 자동 생성
// - 선언된 변수/함수 자동으로 템플릿에 노출
// - import한 컴포넌트 자동 등록

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

// ref(): 반응형 상태 선언
const novels = ref<Novel[]>([])  // 타입 안전성 보장
const isLoading = ref(false)

// onMounted(): 컴포넌트 마운트 시 실행 (라이프사이클 훅)
onMounted(async () => {
  isLoading.value = true
  novels.value = await fetchNovels()
  isLoading.value = false
})

// 함수 정의 (자동으로 템플릿에서 사용 가능)
const handleClick = (id: number) => {
  router.push(`/novel/${id}`)
}
</script>

<template>
  <!-- novels, isLoading, handleClick 자동으로 사용 가능 -->
  <div v-if="isLoading">로딩 중...</div>
  <div v-else>
    <div v-for="novel in novels" :key="novel.id" @click="handleClick(novel.id)">
      {{ novel.title }}
    </div>
  </div>
</template>
```

**Options API와 비교**:
```vue
<!-- Options API (구식) -->
<script>
export default {
  data() {
    return {
      novels: [],
      isLoading: false
    }
  },
  mounted() {
    this.fetchNovels()
  },
  methods: {
    fetchNovels() {
      // ...
    }
  }
}
</script>

<!-- Composition API (현재 프로젝트) -->
<script setup>
// 훨씬 간결하고 타입 안전
</script>
```

**참고 파일**: `views/NovelListView.vue`

---

### 2. 반응형 상태 (Reactive State)

**ref() vs reactive()**

| 구분 | ref() | reactive() |
|------|-------|------------|
| **용도** | 원시 타입 (string, number, boolean) | 객체 타입 (object, array) |
| **접근 방식** | `.value` 필요 (JS에서) | 직접 접근 |
| **TypeScript** | 타입 추론 우수 | 타입 추론 보통 |
| **권장** | ✅ 대부분의 경우 | 특수한 경우만 |

**실무 패턴**:
```typescript
import { ref, reactive } from 'vue'

// ✅ 권장: ref 사용
const count = ref(0)
const user = ref<User | null>(null)
const novels = ref<Novel[]>([])

// JavaScript에서는 .value로 접근
count.value++
novels.value.push(newNovel)

// ❌ 비권장: reactive 사용 (구조 분해 시 반응성 상실)
const state = reactive({
  count: 0,
  user: null
})
// 구조 분해 시 반응성 잃음
const { count } = state  // count는 더 이상 반응형이 아님!
```

**템플릿에서는 .value 불필요**:
```vue
<template>
  <!-- 자동으로 unwrap됨 -->
  <div>{{ count }}</div>
  <div>{{ user?.name }}</div>
</template>
```

**참고 파일**: `components/auth/LoginForm.vue`

---

### 3. Pinia - 상태 관리

**Vuex vs Pinia**

| 비교 항목 | Vuex 4 | Pinia |
|-----------|--------|-------|
| **TypeScript 지원** | 복잡한 타이핑 필요 | 완벽한 타입 추론 |
| **Mutations** | 필수 (보일러플레이트) | 없음 (직접 수정) |
| **모듈 시스템** | 복잡한 네임스페이스 | 자동 모듈화 |
| **DevTools** | Vue Devtools 지원 | Vue Devtools 지원 |
| **공식 추천** | Vue 2 시대 | Vue 3 공식 |

**실무 적용**:

```typescript
// stores/auth.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// defineStore: Pinia 스토어 정의
export const useAuthStore = defineStore('auth', () => {
  // State: ref()로 선언
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<User | null>(null)

  // Getters: computed()로 선언
  const isLoggedIn = computed(() => !!token.value)
  const userName = computed(() => user.value?.name ?? '게스트')

  // Actions: 일반 함수로 선언
  const login = async (username: string, password: string) => {
    const response = await authApi.login({ username, password })
    token.value = response.token
    user.value = response.user
    localStorage.setItem('token', response.token)
  }

  const logout = () => {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
  }

  return { token, user, isLoggedIn, userName, login, logout }
})
```

**컴포넌트에서 사용**:
```vue
<script setup>
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

// 반응형 상태 직접 접근
console.log(authStore.isLoggedIn)  // computed getter

// 액션 호출
const handleLogin = async () => {
  await authStore.login('user', 'pass')
}
</script>
```

**왜 localStorage를 사용하는가?**
- JWT 토큰 영속성 보장 (브라우저 새로고침 시에도 유지)
- XSS 공격 위험이 있지만, HttpOnly Cookie보다 프론트엔드 개발이 단순
- 실무에서는 HttpOnly Cookie + Refresh Token 패턴 권장

**참고 파일**: `stores/auth.ts`

---

### 4. Vue Router - 클라이언트 사이드 라우팅

**전통적인 MPA vs SPA**

| 비교 항목 | MPA (Multi-Page App) | SPA (Single Page App) |
|-----------|----------------------|------------------------|
| **페이지 전환** | 서버에서 새 HTML 응답 | 클라이언트에서 DOM 교체 |
| **속도** | 느림 (전체 페이지 로드) | 빠름 (부분 업데이트) |
| **SEO** | 유리 | 불리 (SSR 필요) |
| **사용자 경험** | 깜빡임 | 부드러운 전환 |

**라우터 설정**:

```typescript
// router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  // HTML5 History Mode: URL에 '#' 없음
  history: createWebHistory(import.meta.env.BASE_URL),

  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/NovelListView.vue')  // 지연 로딩
    },
    {
      path: '/novel/:id',  // 동적 라우트 파라미터
      name: 'novel-detail',
      component: () => import('@/views/NovelDetailLayout.vue')
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/mypage',
      name: 'mypage',
      component: () => import('@/views/MyPageView.vue'),
      meta: { requiresAuth: true }  // 인증 필요 표시
    }
  ]
})

// 네비게이션 가드: 인증 체크
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  // 인증이 필요한 페이지인데 로그인 안됨
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next('/login')  // 로그인 페이지로 리다이렉트
  } else {
    next()  // 정상 진행
  }
})

export default router
```

**컴포넌트에서 라우터 사용**:
```vue
<script setup>
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()  // 프로그래매틱 네비게이션
const route = useRoute()    // 현재 라우트 정보

// 페이지 이동
const goToDetail = (id: number) => {
  router.push(`/novel/${id}`)  // push: 히스토리에 추가
}

// 현재 라우트 파라미터
const novelId = route.params.id
</script>

<template>
  <!-- 선언적 네비게이션 -->
  <router-link to="/login">로그인</router-link>

  <!-- 현재 라우트에 매칭되는 컴포넌트 렌더링 -->
  <router-view />
</template>
```

**참고 파일**: `router/index.ts`

---

### 5. Axios - HTTP 클라이언트

**인터셉터를 활용한 전역 설정**

```typescript
// api/client.ts
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

// Axios 인스턴스 생성 (기본 설정)
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 요청 인터셉터: 모든 요청에 JWT 토큰 자동 추가
apiClient.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()

    // 토큰이 있으면 Authorization 헤더에 추가
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 응답 인터셉터: 401 에러 시 자동 로그아웃
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 인증 만료 → 로그아웃 처리
      const authStore = useAuthStore()
      authStore.logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default apiClient
```

**API 모듈화**:
```typescript
// api/novel.ts
import apiClient from './client'

export const novelApi = {
  // 소설 목록 조회
  getList: (page: number, size: number) =>
    apiClient.get('/api/novels', { params: { page, size } }),

  // 소설 상세 조회
  getDetail: (id: number) =>
    apiClient.get(`/api/novels/${id}`),

  // 소설 생성
  create: (data: NovelCreateRequest) =>
    apiClient.post('/api/novels', data)
}
```

**컴포넌트에서 사용**:
```vue
<script setup>
import { novelApi } from '@/api/novel'

const fetchNovels = async () => {
  try {
    const response = await novelApi.getList(0, 20)
    novels.value = response.data
  } catch (error) {
    console.error('소설 목록 조회 실패:', error)
  }
}
</script>
```

**왜 인터셉터가 중요한가?**
- **DRY 원칙**: 모든 API 호출마다 토큰 추가 코드 중복 방지
- **일관성**: 인증 로직을 중앙에서 관리
- **유지보수성**: 인증 방식 변경 시 한 곳만 수정

**참고 파일**: `api/client.ts`

---

### 6. TypeScript - 타입 안전성

**왜 JavaScript가 아닌 TypeScript인가?**

JavaScript의 문제점:
```javascript
// 런타임 에러 발생
const user = { name: 'Kim' }
console.log(user.age.toFixed(2))  // Cannot read property 'toFixed' of undefined
```

TypeScript의 장점:
```typescript
interface User {
  name: string
  age?: number  // optional
}

const user: User = { name: 'Kim' }
console.log(user.age?.toFixed(2))  // 컴파일 타임에 optional chaining 강제
```

**실무 적용**:
```typescript
// DTO 타입 정의
export interface NovelResponse {
  id: number
  title: string
  author: string
  genre: string
  createdAt: string
}

export interface NovelCreateRequest {
  title: string
  author: string
  genre: string
}

// API 함수에 타입 지정
export const novelApi = {
  getDetail: (id: number): Promise<AxiosResponse<NovelResponse>> =>
    apiClient.get(`/api/novels/${id}`)
}

// 컴포넌트에서 사용
const novel = ref<NovelResponse | null>(null)

onMounted(async () => {
  const response = await novelApi.getDetail(1)
  novel.value = response.data  // 타입 안전성 보장

  // IDE 자동완성 지원
  console.log(novel.value.title)  // ✅
  console.log(novel.value.unknown)  // ❌ 컴파일 에러
})
```

**타입 가드**:
```typescript
// 타입 좁히기
if (novel.value !== null) {
  // 이 블록 안에서는 novel.value가 null이 아님을 보장
  console.log(novel.value.title)  // ✅ 안전
}
```

---

### 7. Vite - 차세대 빌드 도구

**Webpack vs Vite**

| 비교 항목 | Webpack | Vite |
|-----------|---------|------|
| **Dev Server 시작** | 느림 (전체 번들링) | 빠름 (ESM 기반) |
| **HMR** | 느림 | 초고속 |
| **프로덕션 빌드** | Webpack | Rollup |
| **설정 복잡도** | 복잡 | 간단 |

**Vite의 동작 원리**:

개발 모드:
1. 브라우저의 네이티브 ESM 지원 활용
2. 파일 변경 시 해당 모듈만 다시 로드 (HMR)
3. 번들링 없이 즉시 시작

프로덕션 빌드:
1. Rollup으로 최적화된 번들 생성
2. Code Splitting, Tree Shaking 자동 적용
3. 정적 파일 압축 및 최소화

**환경 변수 사용**:
```typescript
// .env.development
VITE_API_BASE_URL=http://localhost:8080

// .env.production
VITE_API_BASE_URL=https://api.example.com

// 코드에서 사용
const apiUrl = import.meta.env.VITE_API_BASE_URL
```

**참고 파일**: `vite.config.ts`

---

## 개발 패턴

### 1. 컴포넌트 설계 원칙

#### (1) Presentational vs Container 패턴

**Presentational Component (표현 컴포넌트)**:
- UI 렌더링만 담당
- Props로 데이터 받음
- 이벤트를 상위로 emit

```vue
<!-- components/novel/NovelCard.vue -->
<script setup lang="ts">
// Props 정의 (부모로부터 받음)
defineProps<{
  novel: Novel
}>()

// 이벤트 정의 (부모로 전달)
const emit = defineEmits<{
  click: [id: number]
}>()
</script>

<template>
  <div class="card" @click="emit('click', novel.id)">
    <h3>{{ novel.title }}</h3>
    <p>{{ novel.author }}</p>
  </div>
</template>
```

**Container Component (컨테이너 컴포넌트)**:
- 데이터 페칭 담당
- 비즈니스 로직 처리
- 표현 컴포넌트 조합

```vue
<!-- views/NovelListView.vue -->
<script setup>
import NovelCard from '@/components/novel/NovelCard.vue'
import { ref, onMounted } from 'vue'
import { novelApi } from '@/api/novel'

// 데이터 페칭
const novels = ref<Novel[]>([])

onMounted(async () => {
  const response = await novelApi.getList()
  novels.value = response.data
})

// 이벤트 핸들링
const handleCardClick = (id: number) => {
  router.push(`/novel/${id}`)
}
</script>

<template>
  <div class="novel-list">
    <NovelCard
      v-for="novel in novels"
      :key="novel.id"
      :novel="novel"
      @click="handleCardClick"
    />
  </div>
</template>
```

#### (2) Props Down, Events Up

Vue의 데이터 흐름 원칙:
- **Props Down**: 부모 → 자식으로 데이터 전달
- **Events Up**: 자식 → 부모로 이벤트 전달

```vue
<!-- 부모 컴포넌트 -->
<template>
  <ChildComponent
    :message="parentMessage"    <!-- Props Down -->
    @custom-event="handleEvent"  <!-- Events Up -->
  />
</template>
```

**왜 이렇게 설계하는가?**
- 단방향 데이터 흐름 유지 (디버깅 용이)
- 컴포넌트 간 결합도 낮춤
- 재사용성 향상

---

### 2. 컴포저블 (Composables)

**개념**: 재사용 가능한 로직을 함수로 추출

**실무 예시**:
```typescript
// composables/useAuth.ts
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

export function useAuth() {
  const authStore = useAuthStore()
  const router = useRouter()

  const isLoggedIn = computed(() => authStore.isLoggedIn)
  const userName = computed(() => authStore.user?.name ?? '게스트')

  const requireAuth = () => {
    if (!isLoggedIn.value) {
      alert('로그인이 필요합니다.')
      router.push('/login')
      return false
    }
    return true
  }

  return {
    isLoggedIn,
    userName,
    requireAuth
  }
}
```

**컴포넌트에서 사용**:
```vue
<script setup>
import { useAuth } from '@/composables/useAuth'

const { isLoggedIn, userName, requireAuth } = useAuth()

const handleReviewSubmit = () => {
  if (!requireAuth()) return  // 로그인 체크 재사용

  // 리뷰 제출 로직
}
</script>
```

**컴포저블의 장점**:
- 로직 재사용 (여러 컴포넌트에서 동일한 로직 공유)
- 테스트 용이성
- 코드 정리 (컴포넌트가 간결해짐)

---

### 3. v-if vs v-show

**실행 방식 차이**:

| 디렉티브 | 렌더링 방식 | 토글 비용 | 초기 렌더링 비용 |
|----------|-------------|-----------|------------------|
| **v-if** | DOM 추가/제거 | 높음 | 낮음 |
| **v-show** | CSS display 토글 | 낮음 | 높음 |

**선택 기준**:
- **v-if**: 조건이 자주 변하지 않을 때 (예: 로그인 상태)
- **v-show**: 조건이 자주 변할 때 (예: 탭 전환)

```vue
<template>
  <!-- v-if: 로그인 상태는 자주 안 바뀜 -->
  <div v-if="isLoggedIn">
    환영합니다, {{ userName }}님!
  </div>
  <div v-else>
    <router-link to="/login">로그인</router-link>
  </div>

  <!-- v-show: 탭은 자주 바뀜 -->
  <div v-show="activeTab === 'info'" class="tab-content">정보</div>
  <div v-show="activeTab === 'reviews'" class="tab-content">리뷰</div>
</template>
```

---

### 4. v-for에서 :key의 중요성

**왜 :key가 필요한가?**

Vue는 Virtual DOM을 사용하여 효율적으로 DOM을 업데이트함. :key가 없으면 Vue는 리스트의 순서만으로 요소를 식별하여 **잘못된 요소를 재사용**할 수 있음.

**❌ 잘못된 예시**:
```vue
<template>
  <!-- :key 없음 → 버그 발생 가능 -->
  <div v-for="novel in novels">
    {{ novel.title }}
  </div>
</template>
```

**✅ 올바른 예시**:
```vue
<template>
  <!-- 고유한 :key 제공 → 정확한 업데이트 -->
  <div v-for="novel in novels" :key="novel.id">
    {{ novel.title }}
  </div>
</template>
```

**:key 선택 기준**:
- **고유해야 함**: id, uuid 등
- **안정적이어야 함**: 배열 인덱스는 비권장 (순서 변경 시 문제)
- **원시 타입**: string, number (객체는 비권장)

---

## 환경 설정 및 실행

### 1. 사전 요구사항

- Node.js 18+ (LTS 버전 권장)
- npm 또는 yarn

### 2. 의존성 설치

```bash
# 프로젝트 디렉토리로 이동
cd novel-insight-front

# 의존성 설치
npm install
```

### 3. 환경 변수 설정

`.env.development` 파일 생성:
```env
VITE_API_BASE_URL=http://localhost:8080
```

`.env.production` 파일 생성:
```env
VITE_API_BASE_URL=https://api.example.com
```

### 4. 개발 서버 실행

```bash
# 개발 서버 시작 (http://localhost:5173)
npm run dev

# 또는 포트 지정
npm run dev -- --port 3000
```

### 5. 프로덕션 빌드

```bash
# TypeScript 타입 체크 + 프로덕션 빌드
npm run build

# 빌드 결과 미리보기
npm run preview
```

빌드 결과는 `dist/` 디렉토리에 생성됩니다.

### 6. Docker로 실행

```bash
# Docker 이미지 빌드
docker build -t novel-insight-front .

# 컨테이너 실행
docker run -p 80:80 novel-insight-front
```

Nginx로 정적 파일 서빙 (설정: `nginx.conf`)

---

## 주요 페이지 설명

### 1. 소설 목록 페이지 (`NovelListView.vue`)

**기능**:
- 무한 스크롤로 소설 목록 로딩
- 검색/필터링 기능
- 카드 형식으로 소설 표시

**핵심 로직**:
```typescript
const novels = ref<Novel[]>([])
let page = 0

const loadMore = async () => {
  const response = await novelApi.getList(page++, 20)
  novels.value.push(...response.data)  // 기존 목록에 추가
}

// Intersection Observer로 무한 스크롤 구현
onMounted(() => {
  const observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting) {
      loadMore()
    }
  })
  observer.observe(document.querySelector('.load-trigger'))
})
```

### 2. 소설 상세 페이지 (`NovelDetailLayout.vue`)

**기능**:
- 소설 정보 표시
- 에피소드 목록
- 리뷰 목록 및 작성

**핵심 로직**:
```typescript
const route = useRoute()
const novelId = Number(route.params.id)  // URL에서 ID 추출

const novel = ref<NovelResponse | null>(null)

onMounted(async () => {
  const response = await novelApi.getDetail(novelId)
  novel.value = response.data
})
```

### 3. 리뷰 작성 폼 (`ReviewForm.vue`)

**기능**:
- 별점 선택
- 리뷰 내용 작성
- 제출 시 감정 분석 결과 표시

**핵심 로직**:
```typescript
const reviewText = ref('')
const rating = ref(5)

const submitReview = async () => {
  if (!requireAuth()) return  // 로그인 체크

  const response = await reviewApi.create({
    novelId: props.novelId,
    content: reviewText.value,
    rating: rating.value
  })

  alert(`리뷰가 등록되었습니다! 감정: ${response.data.sentiment}`)
  reviewText.value = ''  // 폼 초기화
  emit('review-created')  // 부모에게 알림
}
```

---

## 성능 최적화

### 1. 코드 스플리팅 (Code Splitting)

**자동 적용**:
```typescript
// 동적 import → 별도 chunk 생성
const routes = [
  {
    path: '/',
    component: () => import('@/views/NovelListView.vue')  // chunk 분리
  }
]
```

### 2. 이미지 최적화

- WebP 포맷 사용
- Lazy Loading: `<img loading="lazy">`
- CDN 활용

### 3. API 요청 최적화

- Debounce/Throttle 적용 (검색 입력)
- 캐싱 활용 (Pinia에 캐시 저장)
- Pagination/Infinite Scroll

---

## 트러블슈팅

### 1. CORS 에러

```
Access to XMLHttpRequest at 'http://localhost:8080' from origin 'http://localhost:5173'
has been blocked by CORS policy
```

**해결**: 백엔드에서 CORS 허용 설정 필요
```kotlin
// Spring Boot
@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("*")
    }
}
```

### 2. Vite 빌드 오류

```
[vite]: Rollup failed to resolve import
```

**해결**: TypeScript 경로 alias 설정
```typescript
// vite.config.ts
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
```

---

## 참고 자료

- [Vue 3 공식 문서](https://vuejs.org/)
- [Pinia 공식 문서](https://pinia.vuejs.org/)
- [Vue Router 공식 문서](https://router.vuejs.org/)
- [Vite 공식 문서](https://vitejs.dev/)
- [TypeScript 핸드북](https://www.typescriptlang.org/docs/)

---

## 라이선스

이 프로젝트는 학습 목적으로 작성되었습니다.
