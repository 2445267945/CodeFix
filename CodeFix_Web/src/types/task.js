// src/types/task.js

/**
 * Task 创建请求
 *
 * 对应后端：AgentTaskCreateDTO
 *
 * @typedef {Object} AgentTaskCreateRequest
 * @property {string} code
 * @property {string} [fileName]
 * @property {Array<Object>} [smells]
 */

/**
 * Task 创建响应
 *
 * 对应后端：TaskCreateVO
 *
 * @typedef {Object} TaskCreateResponse
 * @property {string} taskId
 * @property {string} runId
 * @property {string} sessionId
 * @property {number} status
 * @property {string} [statusValue]
 */

/**
 * Task 详情
 *
 * 对应后端：TaskDetailVO
 *
 * @typedef {Object} TaskDetail
 * @property {string} taskId
 * @property {string} runId
 * @property {string} sessionId
 * @property {number} status
 * @property {string} statusValue
 * @property {string} question
 * @property {number} createdAt
 * @property {number} updatedAt
 */

/**
 * Task 操作响应
 *
 * 对应后端：TaskOperateVO
 *
 * @typedef {Object} TaskOperateResponse
 * @property {string} taskId
 * @property {string} [runId]
 * @property {number} [status]
 * @property {string} [statusValue]
 * @property {string} [message]
 */

/**
 * Task 最终结果
 *
 * 对应后端：TaskResultVO
 *
 * @typedef {Object} TaskResult
 * @property {string} taskId
 * @property {string} runId
 * @property {string} status
 * @property {string|null} code
 * @property {string|null} changes
 */

/**
 * 创建一个空的 Task 对象
 *
 * @returns {TaskDetail|null}
 */
export function createEmptyTask() {
    return null
}

/**
 * 创建一个空的 TaskResult
 *
 * @returns {TaskResult}
 */
export function createEmptyTaskResult() {
    return {
        taskId: '',
        runId: '',
        status: '',
        code: null,
        changes: null
    }
}