// src/config/index.js

/**
 * API 基础地址
 *
 * 开发环境：
 * .env.development
 * VITE_API_BASE_URL=http://localhost:8080
 *
 * 生产环境：
 * .env.production
 * VITE_API_BASE_URL=http://your-server:8080
 */
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

/**
 * SSE 基础地址
 *
 * 暂时先保留配置项。
 * 后端 SSE 接口确定之后再正式使用。
 */
export const SSE_BASE_URL =
  import.meta.env.VITE_SSE_BASE_URL || API_BASE_URL