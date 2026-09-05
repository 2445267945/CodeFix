// src/api/task.js

import http from './http'

/**
 * 创建审计任务
 *
 * POST /api/agent/tasks
 *
 * @param {Object} data
 * @param {string} data.code
 * @param {string} [data.fileName]
 * @param {Array} [data.smells]
 * @returns {Promise<Object>}
 */
export function createTask(data) {
    return http.post('/api/agent/tasks', data)
}

/**
 * 查询任务详情
 *
 * GET /api/agent/tasks/{taskId}
 *
 * @param {string} taskId
 * @returns {Promise<Object>}
 */
export function getTask(taskId) {
    return http.get(`/api/agent/tasks/${taskId}`)
}

/**
 * 查询 Task 的 Run 历史
 */
export function getTaskRuns(taskId) {
    return http.get(`/api/agent/tasks/${taskId}/runs`)
}


/**
 * 查询任务事件
 *
 * GET /api/agent/tasks/{taskId}/events
 *
 * @param {string} taskId
 * @param {string} [runId]
 * @returns {Promise<Array>}
 */
export function getTaskEvents(taskId, runId = null) {
    const params = {}

    if (runId) {
        params.runId = runId
    }

    return http.get(`/api/agent/tasks/${taskId}/events`, {
        params
    })
}

/**
 * 查询任务最终结果
 *
 * GET /api/agent/tasks/{taskId}/result
 *
 * @param {string} taskId
 * @param {string} runId
 * @returns {Promise<Object>}
 */
export function getTaskResult(taskId, runId = null) {
    const params = {}

    if (runId) {
        params.runId = runId
    }

    return http.get(`/api/agent/tasks/${taskId}/result`, {
        params
    })
}

/**
 * 从 checkpoint 继续执行当前 Run
 *
 * POST /api/agent/tasks/{taskId}/resume?runId=xxx
 *
 * @param {string} taskId
 * @param {string} runId
 * @returns {Promise<Object>}
 */
export function resumeTask(taskId, runId) {
    return http.post(`/api/agent/tasks/${taskId}/resume`, null, {
        params: {
            runId
        }
    })
}

/**
 * 重新执行任务
 *
 * 注意：
 * Retry 会由后端创建新的 Run，
 * 因此前端只需要提供 taskId。
 *
 * POST /api/agent/tasks/{taskId}/retry
 *
 * @param {string} taskId
 * @returns {Promise<Object>}
 */
export function retryTask(taskId) {
    return http.post(`/api/agent/tasks/${taskId}/retry`)
}

/**
 * 主动取消当前 Run
 *
 * POST /api/agent/tasks/{taskId}/cancel?runId=xxx
 *
 * @param {string} taskId
 * @param {string} runId
 * @returns {Promise<Object>}
 */
export function cancelTask(taskId, runId) {
    return http.post(`/api/agent/tasks/${taskId}/cancel`, null, {
        params: {
            runId
        }
    })
}

export function sendChatMessage(data) {
    return http.post(
        '/api/agent/sessions/messages',
        data
    )
}

/**
 * 查询任务列表
 *
 * GET /api/agent/tasks
 *
 * @returns {Promise<Array>}
 */
export function getTasks() {
    return http.get('/api/agent/tasks')
}

export function commandTask(data) {
    return http.post(
        `/api/agent/tasks/command`,
        data
    )
}

/**
 * 导出 Task API
 */
export default {
    createTask,
    getTask,
    getTasks,
    getTaskRuns,
    getTaskEvents,
    getTaskResult,
    resumeTask,
    sendChatMessage,
    retryTask,
    cancelTask,
    commandTask
}