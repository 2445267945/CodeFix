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
 * Agent Chat Block
 *
 * 对应后端：AgentChatBlockVO
 *
 * 顶层对话由 Cando（Supervisor）产生。
 * 当 Cando 调用 run_explorer / run_fixer 时，
 * 会产生一个 type = "delegate" 的委派节点，
 * 子 Agent 的 narration / action / review 等 Block
 * 都会挂在该节点的 children 中。
 *
 * @typedef {Object} AgentChatBlock
 * @property {string} id
 * @property {string} type narration | action | file_change | review | status | delegate
 * @property {string} [action] READ | SEARCH | WRITE | VERIFY | DELEGATE
 * @property {string} [status] waiting | running | completed | failed
 * @property {string} [title]
 * @property {string} [summary]
 * @property {string} [content]
 * @property {string} [toolName]
 * @property {string} [runId]
 * @property {string} [agentName] 产生该 Block 的 Agent
 * @property {string} [delegateAgent] 被委派的子 Agent（仅 delegate 节点）
 * @property {string} [parentAgent]
 * @property {string} [actionId]
 * @property {boolean} [requiresApproval]
 * @property {AgentChatBlock[]} [children] 子 Agent 活动（仅 delegate 节点）
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