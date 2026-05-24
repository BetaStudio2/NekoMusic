import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import './styles/glassShell.css'
import VueToastification from 'vue-toastification'
import 'vue-toastification/dist/index.css'

const app = createApp(App)

app.use(router)
app.use(VueToastification)

app.mount('#app')
