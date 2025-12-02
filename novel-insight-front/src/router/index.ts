import { createRouter, createWebHistory } from 'vue-router'
import NovelListView from '../views/NovelListView.vue'
import LoginView from '../views/LoginView.vue'
import MyPageView from '../views/MyPageView.vue' // ★ Import 확인

import NovelDetailLayout from '../views/NovelDetailLayout.vue'
import NovelInfo from '../components/novel/NovelInfo.vue'
import EpisodeList from '../components/novel/EpisodeList.vue'
import ReviewList from '../components/novel/ReviewList.vue'
import ViewerPage from '../views/ViewerPage.vue'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        { path: '/', component: NovelListView },
        { path: '/login', component: LoginView },
        { path: '/mypage', component: MyPageView }, // ★ 등록 확인

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
        {
            path: '/viewer/:episodeId',
            name: 'viewer',
            component: ViewerPage
        }
    ]
})

export default router