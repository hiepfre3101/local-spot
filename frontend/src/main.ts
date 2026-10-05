import './assets/main.css'

import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { useToast } from 'vue-toastification'

import App from './App.vue'
import { connectAuth } from './api/http'
import { initTheme } from './composables/useTheme'
import { createQueryClient } from './plugins/query'
import { Toast, toastOptions } from './plugins/toast'
import router from './router'
import { useAuthStore } from './stores/auth'

initTheme()

const app = createApp(App)
const pinia = createPinia()
const queryClient = createQueryClient()

app.use(pinia)
app.use(router)
app.use(VueQueryPlugin, { queryClient })
app.use(Toast, toastOptions)

// Cầu nối axios ↔ phiên đăng nhập: gắn token, làm mới khi 401, xử lý hết phiên ở một chỗ
const auth = useAuthStore(pinia)
connectAuth({
  getAccessToken: () => auth.accessToken,
  refresh: () => auth.refresh(),
  onSessionExpired: (reason) => {
    auth.clearSession()
    queryClient.clear() // dữ liệu của phiên cũ (danh sách quản trị...) không được hiện cho người sau
    const toast = useToast()
    toast.info(
      reason === 'locked'
        ? '● Tài khoản của bạn đã bị khoá.'
        : '● Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.',
    )
    const current = router.currentRoute.value
    if (current.matched.some((r) => r.meta.requiresAuth || r.meta.anyPermission)) {
      void router.push({ name: 'login', query: { redirect: current.fullPath } })
    }
  },
})

app.mount('#app')
