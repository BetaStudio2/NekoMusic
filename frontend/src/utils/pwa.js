import { ref } from 'vue'

const deferredInstallPrompt = ref(null)
const canInstall = ref(false)

export function usePwaInstall() {
  const install = async () => {
    const prompt = deferredInstallPrompt.value
    if (!prompt) return false
    prompt.prompt()
    const result = await prompt.userChoice
    deferredInstallPrompt.value = null
    canInstall.value = false
    return result.outcome === 'accepted'
  }

  return { canInstall, install }
}

export function registerPwa() {
  if (!('serviceWorker' in navigator)) return

  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault()
    deferredInstallPrompt.value = event
    canInstall.value = true
  })

  window.addEventListener('appinstalled', () => {
    deferredInstallPrompt.value = null
    canInstall.value = false
  })

  navigator.serviceWorker.register('/sw.js').catch(() => {})
}
