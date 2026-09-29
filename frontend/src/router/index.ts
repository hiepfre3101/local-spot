import { createRouter, createWebHistory } from 'vue-router'

// Danh sách route đầy đủ: docs/design/sitemap.md (dựng ở checklist F)
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/pages/HomePage.vue'),
    },
  ],
})

export default router
