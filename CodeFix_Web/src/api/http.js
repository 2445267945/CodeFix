// src/api/http.js

import axios from 'axios'
import { API_BASE_URL } from '../config'

/**
 * 全局 HTTP 客户端
 *
 * 统一负责：
 * 1. baseURL
 * 2. 请求超时
 * 3. 请求拦截
 * 4. Result<T> 统一响应处理
 * 5. HTTP / 网络异常处理
 */
const http = axios.create({
    baseURL: API_BASE_URL,
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json'
    }
})

/**
 * 请求拦截器
 */
http.interceptors.request.use(
    (config) => {
        // 以后如果需要登录 Token，可以统一在这里处理
        //
        // const token = localStorage.getItem('token')
        // if (token) {
        //     config.headers.Authorization = `Bearer ${token}`
        // }

        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

/**
 * 响应拦截器
 *
 * 后端统一返回：
 *
 * {
 *     code: 1,
 *     msg: "success",
 *     data: ...
 * }
 *
 * 这里统一判断业务是否成功：
 *
 * code === 1
 *     → 返回 data
 *
 * code !== 1
 *     → 直接抛出业务异常
 */
http.interceptors.response.use(
    (response) => {
        const result = response.data

        // 防止后端返回非 Result 格式
        if (!result || typeof result !== 'object') {
            console.error('接口返回格式异常:', result)

            return Promise.reject(
                new Error('接口返回格式异常')
            )
        }

        const { code, msg, data } = result

        // 业务成功
        if (code === 1) {
            return data
        }

        // 业务失败
        const error = new Error(msg || '请求失败')

        // 保留后端 Result 信息，方便页面或全局处理
        error.code = code
        error.msg = msg
        error.data = data
        error.result = result

        console.error(
            '业务请求失败:',
            code,
            msg
        )

        return Promise.reject(error)
    },
    (error) => {
        /**
         * HTTP / 网络错误
         */
        if (error.response) {
            console.error(
                'HTTP 请求失败:',
                error.response.status,
                error.response.data
            )
        } else if (error.request) {
            console.error(
                'HTTP 请求未收到响应:',
                error.request
            )
        } else {
            console.error(
                'HTTP 请求配置失败:',
                error.message
            )
        }

        return Promise.reject(error)
    }
)

export default http