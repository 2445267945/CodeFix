// src/types/event.js

/**
 * Agent Event
 *
 * 对应后端：AgentEventVO
 *
 * @typedef {Object} AgentEvent
 * @property {string} messageId
 * @property {string} runId
 * @property {string} taskId
 * @property {string} sessionId
 * @property {string} agentName
 * @property {string|null} parentAgent
 * @property {string} event
 * @property {string|number} step
 * @property {string} status
 * @property {string} [agentStatus]
 * @property {Object} [output]
 * @property {number} timestamp
 */

/**
 * 创建一个空事件列表
 *
 * @returns {AgentEvent[]}
 */
export function createEmptyEvents() {
    return []
}

/**
 * 判断事件是否属于某个 Run
 *
 * @param {AgentEvent} event
 * @param {string} runId
 * @returns {boolean}
 */
export function isEventOfRun(event, runId) {
    return Boolean(
        event &&
        runId &&
        event.runId === runId
    )
}