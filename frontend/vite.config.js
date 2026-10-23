import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 开发期统一走 /api 前缀代理到网关 9010，
 * 好处：浏览器同源无跨域，且切换后端地址只改一处
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:9010',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
