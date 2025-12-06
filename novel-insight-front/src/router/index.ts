import { createRouter, createWebHistory } from 'vue-router'
import NovelListView from '../views/NovelListView.vue'
import LoginView from '../views/LoginView.vue'
import MyPageView from '../views/MyPageView.vue'
import AuthorDashboardView from '../views/AuthorDashboardView.vue'  // 추가

import NovelDetailLayout from '../views/NovelDetailLayout.vue'
import NovelInfo from '../components/novel/NovelInfo.vue'
import EpisodeList from '../components/novel/EpisodeList.vue'
import ReviewList from '../components/novel/ReviewList.vue'
import ViewerPage from '../views/ViewerPage.vue'

//
// # Vue Router 설정
//
// ## 라우트 구조
//
// - `/` - 소설 목록
// - `/login` - 로그인
// - `/mypage` - 마이 페이지
// - `/dashboard` - 작가 대시보드 (새로 추가)
// - `/novels/:id/*` - 소설 상세 (중첩 라우트)
// - `/viewer/:episodeId` - 에피소드 뷰어
//

const router = createRouter({
    history: createWebHistory(),
    routes: [
        { path: '/', component: NovelListView },
        { path: '/login', component: LoginView },
        { path: '/mypage', component: MyPageView },

        // 작가 대시보드 (인증 필요)
        {
            path: '/dashboard',
            name: 'dashboard',
            component: AuthorDashboardView,
            meta: { requiresAuth: true }  // 인증 가드 (향후 추가 가능)
        },

        // 소설 상세 (중첩 라우트)
        {
            path: '/novels/:id',
            component: NovelDetailLayout,
            props: true,
            children: [
                { path: '', redirect: { name: 'novel-info' } },
                { path: 'info', name: 'novel-info', component: NovelInfo },
                { path: 'episodes', name: 'novel-episodes', component: EpisodeList },
                { path: 'reviews', name: 'novel-reviews', component: ReviewList }
            ]
        },

        // 에피소드 뷰어
        {
            path: '/viewer/:episodeId',
            name: 'viewer',
            component: ViewerPage
        }
    ]
})

//
// ## Navigation Guard (향후 추가 가능)
//
// router.beforeEach((to, from, next) => {
//   const authStore = useAuthStore()
//   if (to.meta.requiresAuth && !authStore.isAuthenticated) {
//     next('/login')
//   } else {
//     next()
//   }
// })
//

export default router
