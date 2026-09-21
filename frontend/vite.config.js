import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

/** 本地后端默认地址（backend/src/main/resources/config.yml 的 port 默认 65535） */
const DEFAULT_DEV_PROXY_TARGET = 'http://localhost:65535'

// https://vite.dev/config/
export default defineConfig(({ command, mode }) => {
  // 开发联调代理：把 /api、/version 转发到本地后端，前端与接口/音频/封面变成同源，
  // 无需后端为浏览器放开 CORS。仅 dev server 生效，生产构建不受影响。
  // 目标可用 VITE_DEV_PROXY_TARGET 覆盖（如指向线上 https://music.cnmsb.xin）。
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_DEV_PROXY_TARGET || DEFAULT_DEV_PROXY_TARGET

  return {
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
      // 构建输出
      outDir: '../backend/src/main/resources/site',
      // 输出目录在项目根之外，需显式开启清理，避免旧哈希产物堆积
      emptyOutDir: true,
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
            // Vue 生态必须最先归类：@vue/*（runtime-core/reactivity 等）如果漏掉，
            // 会被并进 AMLL/Pixi 大块，导致入口静态依赖 477KB 的 amll-vendor。
            if (
              id.includes('vue-router') ||
              id.includes('/@vue/') ||
              /node_modules[/\\]vue[/\\]/.test(id)
            ) {
              return 'vue-vendor'
            }
            if (id.includes('chart.js')) return 'chart-vendor'
            if (id.includes('/axios/')) return 'axios-vendor'
            if (id.includes('qrcode')) return 'qrcode-vendor'
            if (id.includes('vue-toastification')) return 'ui-vendor'
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
      // 仅在 dev server 注册；生产构建 command 为 build，不会带上代理
      proxy:
        command === 'serve'
          ? {
              '/api': { target: proxyTarget, changeOrigin: true, secure: false },
              '/version': { target: proxyTarget, changeOrigin: true, secure: false },
            }
          : undefined,
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
  }
})
