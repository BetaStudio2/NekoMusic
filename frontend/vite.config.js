import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  // 开发联调代理：设置 VITE_DEV_PROXY_TARGET 后，把 /api 与 /version 代理到该后端。
  // 好处：前端与接口/音频/封面变成【同源】，彻底绕开跨域，
  // 同时让 Web Audio 的 AnalyserNode 读取到的是同源媒体（不会被判为「污染」而静音）。
  // 未设置该变量时不注册代理，生产构建不受影响（proxy 仅作用于 dev server）。
  const proxyTarget = env.VITE_DEV_PROXY_TARGET

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
            if (id.includes('chart.js')) return 'chart-vendor'
            if (id.includes('/axios/')) return 'axios-vendor'
            if (id.includes('qrcode')) return 'qrcode-vendor'
            if (id.includes('vue-toastification')) return 'ui-vendor'
            // AMLL 歌词墙 + PixiJS：仅在播放页用到，单独成块便于缓存
            if (/node_modules[/\\](@applemusic-like-lyrics|@pixi|gl-matrix|bezier-easing|deep-freeze|@ungap)[/\\]/.test(id)) {
              return 'amll-vendor'
            }
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
      proxy: proxyTarget
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
