import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from '@/App.vue'
import { createAppRouter } from '@/app/router'
import { bindUnauthorizedNavigation } from '@/app/router/unauthorizedNavigation'
import { useAuthStore } from '@/app/stores/auth'
import { ensureCurrentInterviewSessionBinding } from '@/features/interview/stores/currentInterview'

import '@/app/styles/tokens.css'
import '@/app/styles/reset.css'
import '@/app/styles/base.css'
import '@/app/styles/utilities.css'
import '@/app/styles/shells.css'

const app = createApp(App)
const pinia = createPinia()
const router = createAppRouter()

app.use(pinia)
app.use(router)
bindUnauthorizedNavigation(router)
ensureCurrentInterviewSessionBinding()

const auth = useAuthStore()
await auth.initialize()
await router.isReady()
app.mount('#app')
