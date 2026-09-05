/**
 * Agent Chat UI View Model
 *
 * 注意：
 * 这不是后端数据库模型。
 *
 * 后端：
 *   agent_chat_message
 *   agent_event
 *   task_result
 *
 * Java 将这些数据聚合/转换后，
 * 前端得到 AgentChatViewModel。
 */

/**
 * Agent Action 状态
 */
export const AGENT_BLOCK_STATUS = {
  RUNNING: "running",
  COMPLETED: "completed",
  FAILED: "failed",
};

/**
 * Agent Action 类型
 *
 * UI 不直接使用：
 * THINKING / EXECUTING
 * TOOL_CALL / TOOL_RESULT
 *
 * 而是转换成用户更容易理解的 Action。
 */
export const AGENT_ACTION_TYPE = {
  THINK: "THINK",
  READ: "READ",
  SEARCH: "SEARCH",
  WRITE: "WRITE",
  EXECUTE: "EXECUTE",
  VERIFY: "VERIFY",
  DELEGATE: "DELEGATE",
  FINISH: "FINISH",
  ERROR: "ERROR",
};

/**
 * File Change 操作类型
 */
export const FILE_OPERATION = {
  CREATED: "created",
  MODIFIED: "modified",
  DELETED: "deleted",
  RENAMED: "renamed",
};

/**
 * Review 等级
 */
export const REVIEW_LEVEL = {
  INFO: "info",
  SUCCESS: "success",
  WARNING: "warning",
  ERROR: "error",
};

/**
 * ============================================================
 * 1. ChatTurn
 * ============================================================
 *
 * 一次：
 *
 * 用户请求
 *     ↓
 * Agent 执行
 *     ↓
 * Agent 最终回答
 *
 * 对应 UI 中的一轮对话。
 *
 * @typedef {Object} AgentChatTurn
 * @property {string} turnId
 * @property {Object} user
 * @property {Object} agent
 */

/**
 * ============================================================
 * 2. AgentActionBlock
 * ============================================================
 *
 * Agent 正在做什么。
 *
 * 例如：
 *
 * 🧠 正在分析排序类的结构
 * ✓ 已找到 SortUtils.java
 * ✎ 正在修改 SortUtils.java
 * ✓ 验证完成
 *
 * @typedef {Object} AgentActionBlock
 * @property {"action"} type
 * @property {string} id
 * @property {string} agent
 * @property {string} action
 * @property {"running"|"completed"|"failed"} status
 * @property {string} summary
 * @property {string|null} detail
 * @property {string[]} sourceEventIds
 * @property {number} timestamp
 */

/**
 * ============================================================
 * 3. FileChangeBlock
 * ============================================================
 *
 * Agent 实际修改了什么。
 *
 * @typedef {Object} FileChangeBlock
 * @property {"file_change"} type
 * @property {string} id
 * @property {string} filePath
 * @property {"created"|"modified"|"deleted"|"renamed"} operation
 * @property {number} addedLines
 * @property {number} removedLines
 * @property {string|null} summary
 * @property {string|null} diffId
 * @property {string[]} sourceEventIds
 * @property {number} timestamp
 */

/**
 * ============================================================
 * 4. ReviewBlock
 * ============================================================
 *
 * Agent 对修改结果的总结/验证。
 *
 * @typedef {Object} ReviewBlock
 * @property {"review"} type
 * @property {string} id
 * @property {"info"|"success"|"warning"|"error"} level
 * @property {string} title
 * @property {string} content
 * @property {string[]} relatedFiles
 * @property {string[]} sourceEventIds
 * @property {number} timestamp
 */

/**
 * ============================================================
 * 5. FinalAnswerBlock
 * ============================================================
 *
 * Agent 最终给用户的回答。
 *
 * 它对应后端最终 Chat Message 的 AI Answer。
 *
 * @typedef {Object} FinalAnswerBlock
 * @property {"final_answer"} type
 * @property {string} id
 * @property {string} content
 * @property {number} timestamp
 */

/**
 * ============================================================
 * AgentChatBlock
 * ============================================================
 *
 * Agent 工作过程中的任意 UI Block。
 *
 * @typedef {AgentActionBlock|FileChangeBlock|ReviewBlock} AgentChatBlock
 */

/**
 * ============================================================
 * AgentChatViewModel
 * ============================================================
 *
 * 前端真正消费的数据结构。
 *
 * @typedef {Object} AgentChatViewModel
 * @property {string} turnId
 * @property {Object} user
 * @property {string} user.messageId
 * @property {string} user.content
 * @property {number} user.timestamp
 * @property {Object} agent
 * @property {string} agent.agentName
 * @property {AgentChatBlock[]} agent.blocks
 * @property {FinalAnswerBlock|null} agent.finalAnswer
 */

/**
 * 创建空 Chat Turn
 *
 * @returns {AgentChatViewModel}
 */
export function createEmptyAgentChatTurn() {
  return {
    turnId: "",
    user: {
      messageId: "",
      content: "",
      timestamp: 0,
    },

    agent: {
      agentName: "",
      blocks: [],
      finalAnswer: null,
    },
  };
}