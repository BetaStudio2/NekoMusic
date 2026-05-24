import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  plugins: [
    vue(),
    command === 'serve' && vueDevTools(),
  ].filter(Boolean),
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    // 生产环境构建优化
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true, // 生产环境移除 console
        drop_debugger: true,
        passes: 2,
      },
    },
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('chart.js')) return 'chart-vendor'
          if (id.includes('/axios/')) return 'axios-vendor'
          if (id.includes('qrcode')) return 'qrcode-vendor'
          if (id.includes('vue-toastification')) return 'ui-vendor'
          if (id.includes('vue-router') || /node_modules[/\\]vue[/\\]/.test(id)) {
            return 'vue-vendor'
          }
        },
        // 文件名哈希，利于缓存
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
      },
    },
    // 文件大小警告阈值
    chunkSizeWarningLimit: 500,
  },
  server: {
    host: true,
    port: 5173,
    strictPort: false,
    allowedHosts: ['music.cnmsb.xin', 'localhost'],
    // 开发环境也启用生产级别的优化
    hmr: true,
  },
  // 确保开发和生产环境行为一致
  define: {
    __VUE_OPTIONS_API__: false,
    __VUE_PROD_DEVTOOLS__: false,
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: false,
  },
  // 优化依赖预构建
  optimizeDeps: {
    include: ['vue', 'vue-router', 'vue-toastification'],
  },
}))
