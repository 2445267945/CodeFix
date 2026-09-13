// src/stores/task.js

import { defineStore } from 'pinia'

import {
    createTask,
    getTasks,
    getTask,
    getTaskRuns,
    getTaskEvents,
    getTaskResult,
    sendChatMessage,
    resumeTask,
    retryTask,
    cancelTask,
    commandTask,
} from '../api/task'

export const useTaskStore = defineStore('task', {
    state: () => ({
        // Session 当前任务列表
        tasks: [],

        // 当前 Task
        task: null,

        // 当前 Task 的所有 Run
        runs: [],

        // 当前正在查看的 Run
        viewedRunId: null,

        // 当前查看 Run 的原始 Events
        events: [],

        // 当前 Task / Session 的完整 Result
        result: null,

        // 当前 Task 的最新 Run
        currentRunId: null,

        // SSE
        sse: null,
        sseConnected: false,

        loading: false,
        runsLoading: false,
        eventsLoading: false,
        resultLoading: false,
        operating: false,
        sending: false,

        error: null
    }),

    getters: {
        taskId: (state) =>
            state.task?.taskId || '',

        sessionId: (state) =>
            state.task?.sessionId ||
            state.result?.chat?.sessionId ||
            '',

        runId: (state) =>
            state.task?.runId ||
            state.currentRunId ||
            '',

        viewedRun: (state) => {
            if (!state.viewedRunId) {
                return null
            }

            return (
                state.runs.find(
                    run =>
                        run.runId ===
                        state.viewedRunId
                ) || null
            )
        },

        status: (state) =>
            state.task?.status ?? null,

        statusValue: (state) =>
            state.task?.statusValue || '',

        isViewingCurrentRun: (state) => {
            return (
                !!state.viewedRunId &&
                state.viewedRunId ===
                (
                    state.task?.runId ||
                    state.currentRunId
                )
            )
        },

        isRunning: (state) => {
            const status =
                state.task?.statusValue

            return [
                'QUEUED',
                'THINKING',
                'EXECUTING',
                'BLOCKED',
                'WAITING_HUMAN'
            ].includes(status)
        },

        isCompleted: (state) =>
            state.task?.statusValue ===
            'FINISHED',

        canResume: (state) =>
            state.task?.statusValue ===
            'CANCELLED',

        canRetry: (state) => {
            const status =
                state.task?.statusValue

            return [
                'CANCELLED',
                'ERROR'
            ].includes(status)
        },

        canCancel: (state) => {
            const status =
                state.task?.statusValue

            return [
                'QUEUED',
                'THINKING',
                'EXECUTING',
                'BLOCKED',
                'WAITING_HUMAN'
            ].includes(status)
        },

        workspacePath: (state) => state.result?.rootPath || '',

        /**
         * 当前完整 Chat
         */
        chat: (state) =>
            state.result?.chat || null,

        /**
         * 当前 Session 的全部 Turns
         */
        turns: (state) =>
            state.result?.chat?.turns || []
    },

    actions: {
        reset() {
            this.disconnectSse()

            this.tasks = []
            this.task = null
            this.runs = []
            this.viewedRunId = null
            this.events = []
            this.result = null
            this.currentRunId = null

            this.loading = false
            this.runsLoading = false
            this.eventsLoading = false
            this.resultLoading = false
            this.operating = false
            this.sending = false

            this.error = null
        },

        async fetchTasks() {
            this.loading = true
            this.error = null

            try {
                const response = await getTasks()

                this.tasks = Array.isArray(response)
                    ? response
                    : []

                return this.tasks
            } catch (error) {
                this.error = this.getErrorMessage(
                    error,
                    '获取任务列表失败'
                )
                throw error
            } finally {
                this.loading = false
            }
        },

        async createTask(data) {
            this.loading = true
            this.error = null

            try {
                const response =
                    await createTask(data)

                this.task = response

                this.currentRunId =
                    response?.runId || null

                this.viewedRunId =
                    response?.runId || null

                return response
            } catch (error) {
                this.error = this.getErrorMessage(
                    error,
                    '创建任务失败'
                )
                throw error
            } finally {
                this.loading = false
            }
        },

        async fetchTask(taskId) {
            this.loading = true
            this.error = null

            try {
                const response =
                    await getTask(taskId)

                this.task = response

                if (response?.runId) {
                    this.currentRunId =
                        response.runId

                    if (!this.viewedRunId) {
                        this.viewedRunId =
                            response.runId
                    }
                }

                return response
            } catch (error) {
                this.error = this.getErrorMessage(
                    error,
                    '获取任务失败'
                )
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * 查询 Run 历史
         */
        async fetchRuns(taskId) {
            this.runsLoading = true
            this.error = null

            try {
                const response =
                    await getTaskRuns(taskId)

                this.runs =
                    Array.isArray(response)
                        ? response
                        : []

                if (
                    !this.viewedRunId &&
                    this.task?.runId
                ) {
                    this.viewedRunId =
                        this.task.runId
                }

                return this.runs
            } catch (error) {
                this.error = this.getErrorMessage(
                    error,
                    '获取 Run 历史失败'
                )
                throw error
            } finally {
                this.runsLoading = false
            }
        },

        /**
         * 切换查看的 Run
         */
        async switchRun(runId) {
            if (!runId) {
                return
            }

            this.viewedRunId = runId

            this.events = []
            this.result = null

            await Promise.all([
                this.fetchEvents(
                    this.taskId,
                    runId
                ),
                this.fetchResult(
                    this.taskId,
                    runId
                )
            ])
        },

        async fetchEvents(
            taskId,
            runId = null
        ) {
            this.eventsLoading = true
            this.error = null

            try {
                const targetRunId =
                    runId ||
                    this.viewedRunId ||
                    this.task?.runId ||
                    this.currentRunId

                const response =
                    await getTaskEvents(
                        taskId,
                        targetRunId
                    )

                this.events =
                    Array.isArray(response)
                        ? response
                        : []

                return this.events
            } catch (error) {
                this.error = this.getErrorMessage(
                    error,
                    '获取任务事件失败'
                )
                throw error
            } finally {
                this.eventsLoading = false
            }
        },

        /**
         * 查询完整 Result
         *
         * 当前返回的 chat 已经是：
         *
         * Session
         *   └── turns[]
         */
        async fetchResult(
            taskId,
            runId = null
        ) {
            this.resultLoading = true
            this.error = null

            try {
                const targetRunId =
                    runId ||
                    this.viewedRunId ||
                    this.task?.runId ||
                    this.currentRunId

                const response =
                    await getTaskResult(
                        taskId,
                        targetRunId
                    )

                this.result = response

                return response
            } catch (error) {
                this.error = this.getErrorMessage(
                    error,
                    '获取任务结果失败'
                )
                throw error
            } finally {
                this.resultLoading = false
            }
        },

        /**
         * =====================================================
         * Chat State
         * =====================================================
         */

        /**
         * 确保 result.chat.turns 存在。
         */
        ensureChat() {
            if (!this.result) {
                this.result = {
                    taskId:
                        this.task?.taskId || '',
                    runId:
                        this.viewedRunId ||
                        this.currentRunId ||
                        '',
                    status:
                        this.task?.statusValue || '',
                    chat: null
                }
            }

            if (!this.result.chat) {
                this.result.chat = {
                    sessionId:
                        this.task?.sessionId || '',
                    taskId:
                        this.task?.taskId || '',
                    runId:
                        this.viewedRunId ||
                        this.currentRunId ||
                        '',
                    turns: []
                }
            }

            if (
                !Array.isArray(
                    this.result.chat.turns
                )
            ) {
                this.result.chat.turns = []
            }
        },

        /**
         * 更新当前 Turn 的 Phase。
         *
         * Java 每次实时事件都会返回当前 Run 的完整 Phase 快照，
         * 前端直接替换当前 Turn.phases。
         *
         * 不在前端重新聚合 Block。
         */
        updateChatPhases(
            phases,
            taskId,
            runId
        ) {
            if (!taskId || !runId) {
                return
            }

            if (!Array.isArray(phases)) {
                return
            }

            this.ensureChat()

            let turn =
                this.findChatTurnByTaskId(
                    taskId
                )

            if (!turn) {
                turn =
                    this.createChatTurn({
                        taskId,
                        runId
                    })
            }

            if (!turn) {
                console.warn(
                    '[SSE] 无法创建对应 Turn，无法更新 Phase:',
                    taskId,
                    runId
                )
                return
            }

            /*
             * Retry：
             * 同一个 Task 的新 Run
             * 继续使用当前 Turn。
             */
            turn.runId = runId

            turn.phases = phases
        },

        /**
         * 找到某个 Run 对应的 Turn。
         *
         * 一个 Task = 一个 Turn，
         * 所以当前通过 runId 定位。
         */
        findChatTurnByRunId(runId) {
            if (!runId) {
                return null
            }

            const turns =
                this.result?.chat?.turns

            if (!Array.isArray(turns)) {
                return null
            }

            return (
                turns.find(
                    turn =>
                        turn?.runId === runId
                ) || null
            )
        },

        /**
         * 找到某个 Task 对应的 Turn。
         */
        findChatTurnByTaskId(taskId) {
            if (!taskId) {
                return null
            }

            const turns =
                this.result?.chat?.turns

            if (!Array.isArray(turns)) {
                return null
            }

            return (
                turns.find(
                    turn =>
                        turn?.taskId === taskId
                ) || null
            )
        },

        /**
         * 创建一个临时 Turn。
         *
         * 主要用于：
         * 新消息创建了新的 Task/Run，
         * 但完整 Result 尚未重新拉取时。
         */
        createChatTurn({
            taskId,
            runId,
            user = null
        }) {
            if (!taskId || !runId) {
                return null
            }

            this.ensureChat()

            let turn =
                this.findChatTurnByTaskId(
                    taskId
                )

            if (turn) {
                return turn
            }

            turn = {
                taskId,
                runId,
                user,
                agent: {
                    agentName: '',
                    blocks: [],
                    finalAnswer: null
                },
                phases: []
            }

            this.result.chat.turns.push(
                turn
            )

            return turn
        },

        /**
         * 发送后新增一轮用户消息。
         *
         * 要求后端返回 taskId / runId。
         */
        appendUserTurn(
            messageVO
        ) {
            if (!messageVO) {
                return null
            }

            const taskId =
                messageVO.taskId

            const runId =
                messageVO.runId

            if (!taskId || !runId) {
                console.warn(
                    '[Chat] 消息响应缺少 taskId/runId:',
                    messageVO
                )
                return null
            }

            const user = {
                messageId:
                    messageVO.messageId || '',
                content:
                    messageVO.content || '',
                timestamp:
                    messageVO.createdAt || Date.now()
            }

            return this.createChatTurn({
                taskId,
                runId,
                user
            })
        },

        /**
         * 新增 Block
         */
        appendChatBlock(
            block,
            taskId,
            runId
        ) {
            if (!block?.id || !taskId || !runId) {
                return
            }

            this.ensureChat()

            let turn =
                this.findChatTurnByTaskId(
                    taskId
                )

            if (!turn) {
                turn =
                    this.createChatTurn({
                        taskId,
                        runId
                    })
            }

            if (!turn) {
                console.warn(
                    '[SSE] 无法创建对应 Turn:',
                    taskId,
                    runId
                )
                return
            }

            /*
             * Retry：
             * 同一个 Task，
             * 新 Run 会覆盖当前 Turn 的 runId。
             */
            turn.runId = runId

            if (!turn.agent) {
                turn.agent = {
                    agentName:
                        block.agent || '',
                    blocks: [],
                    finalAnswer: null
                }
            }

            if (
                !Array.isArray(
                    turn.agent.blocks
                )
            ) {
                turn.agent.blocks = []
            }

            const exists =
                turn.agent.blocks.some(
                    item =>
                        item?.id === block.id
                )

            if (!exists) {
                turn.agent.blocks.push(block)
            }
        },

        /**
         * 更新 Block
         */
        updateChatBlock(
            block,
            taskId,
            runId
        ) {
            if (!block?.id || !taskId || !runId) {
                return
            }

            this.ensureChat()

            let turn =
                this.findChatTurnByTaskId(
                    taskId
                )

            if (!turn) {
                turn =
                    this.createChatTurn({
                        taskId,
                        runId
                    })
            }

            if (!turn) {
                console.warn(
                    '[SSE] 无法创建对应 Turn:',
                    taskId,
                    runId
                )
                return
            }

            turn.runId = runId

            if (!turn.agent) {
                turn.agent = {
                    agentName:
                        block.agent || '',
                    blocks: [],
                    finalAnswer: null
                }
            }

            if (
                !Array.isArray(
                    turn.agent.blocks
                )
            ) {
                turn.agent.blocks = []
            }

            const blocks =
                turn.agent.blocks

            const index =
                blocks.findIndex(
                    item =>
                        item?.id === block.id
                )

            if (index === -1) {
                blocks.push(block)
                return
            }

            const mergedBlock = {
                ...blocks[index]
            }

            Object.entries(block).forEach(
                ([key, value]) => {
                    if (
                        value !== null &&
                        value !== undefined
                    ) {
                        mergedBlock[key] = value
                    }
                }
            )

            blocks[index] = mergedBlock
        },

        /**
         * =====================================================
         * 发送新一轮对话
         * =====================================================
         */
        async sendMessage(content, workspacePath = "", permissionProfile) {
            const text = content?.trim();
            if (!text) {
                return null;
            }

            /*
             * 新建对话时 sessionId 可以为空。
             *
             * workspaceName：
             * - 有值：本次真正发送消息时，后端创建 Workspace 并绑定 Session
             * - 无值：不创建 Workspace，正常创建/继续 Session
             */
            const sessionId =
                this.sessionId || "";

            const normalizedWorkspacePath = workspacePath?.trim() || this.result?.rootPath || "";

            if (!normalizedWorkspacePath) {
                throw new Error(
                    "请先选择一个本地工作目录"
                );
            }

            this.sending = true;
            this.error = null;

            try {
                const response =
                    await sendChatMessage({
                        content: text,
                        sessionId,
                        workspacePath: normalizedWorkspacePath,
                        permissionProfile,
                    });

                /*
                 * 后端必须返回：
                 *
                 * messageId
                 * sessionId
                 * taskId
                 * runId
                 * role
                 * content
                 * createdAt
                 */
                if (
                    !response?.sessionId ||
                    !response?.taskId ||
                    !response?.runId
                ) {
                    throw new Error(
                        "发送消息成功，但后端未返回完整的 sessionId/taskId/runId"
                    );
                }

                /*
                 * 当前 Session
                 */
                const newSessionId =
                    response.sessionId;

                /*
                 * 新消息一定创建一个新的 Task
                 */
                const newTaskId =
                    response.taskId;

                /*
                 * 新 Task 当前最新 Run
                 */
                const newRunId =
                    response.runId;

                /*
                 * 当前 Task
                 */
                this.task = {
                    ...(this.task || {}),
                    taskId: newTaskId,
                    sessionId: newSessionId,
                    runId: newRunId,
                };

                this.currentRunId =
                    newRunId;

                this.viewedRunId =
                    newRunId;

                /*
                 * 创建/追加当前新的 Turn。
                 *
                 * User Message 立即显示。
                 */
                this.appendUserTurn(response);

                /*
                 * 切换 SSE
                 */
                this.disconnectSse();

                this.connectSse(
                    newTaskId
                );

                return response;

            } catch (error) {
                this.error =
                    this.getErrorMessage(
                        error,
                        "发送消息失败"
                    );

                throw error;

            } finally {
                this.sending = false;
            }
        },

        /**
         * =====================================================
         * Task 操作
         * =====================================================
         */
        async approveAction(runId) {
            this.operating = true
            this.error = null

            try {
                return await commandTask({
                    runId: runId,
                    command: 'APPROVE'
                })
            } catch (error) {
                this.error = this.getErrorMessage(
                    error,
                    '批准操作失败'
                )
                throw error
            } finally {
                this.operating = false
            }
        },

        async rejectAction(runId) {
            this.operating = true
            this.error = null

            try {
                return await commandTask({
                    runId: runId,
                    command: 'REJECT'
                })
            } catch (error) {
                this.error = this.getErrorMessage(
                    error,
                    '拒绝操作失败'
                )
                throw error
            } finally {
                this.operating = false
            }
        },

        async resume(
            taskId,
            runId = null
        ) {
            this.operating = true
            this.error = null

            try {
                const targetRunId =
                    runId ||
                    this.task?.runId ||
                    this.currentRunId

                if (!targetRunId) {
                    throw new Error(
                        '缺少 runId，无法继续执行'
                    )
                }

                const response =
                    await resumeTask(
                        taskId,
                        targetRunId
                    )

                if (response) {
                    this.task = {
                        ...this.task,
                        ...response
                    }

                    if (response.runId) {
                        this.currentRunId =
                            response.runId

                        this.viewedRunId =
                            response.runId
                    }
                }

                return response
            } catch (error) {
                this.error =
                    this.getErrorMessage(
                        error,
                        '继续执行失败'
                    )
                throw error
            } finally {
                this.operating = false
            }
        },

        async retry(taskId) {
            this.operating = true
            this.error = null

            try {
                const response =
                    await retryTask(taskId)

                if (response) {
                    this.task = {
                        ...this.task,
                        ...response
                    }

                    if (response.runId) {
                        this.currentRunId =
                            response.runId

                        this.viewedRunId =
                            response.runId
                    }
                }

                this.events = []
                this.result = null

                await this.fetchRuns(
                    taskId
                )

                return response
            } catch (error) {
                this.error =
                    this.getErrorMessage(
                        error,
                        '重新执行失败'
                    )
                throw error
            } finally {
                this.operating = false
            }
        },

        async cancel(
            taskId,
            runId = null
        ) {
            this.operating = true
            this.error = null

            try {
                const targetRunId =
                    runId ||
                    this.task?.runId ||
                    this.currentRunId

                if (!targetRunId) {
                    throw new Error(
                        '缺少 runId，无法取消任务'
                    )
                }

                const response =
                    await cancelTask(
                        taskId,
                        targetRunId
                    )

                if (response) {
                    this.task = {
                        ...this.task,
                        ...response
                    }

                    if (response.runId) {
                        this.currentRunId =
                            response.runId

                        this.viewedRunId =
                            response.runId
                    }
                }

                return response
            } catch (error) {
                this.error =
                    this.getErrorMessage(
                        error,
                        '取消任务失败'
                    )
                throw error
            } finally {
                this.operating = false
            }
        },

        /**
         * =====================================================
         * SSE
         * =====================================================
         */
        connectSse(taskId) {
            if (!taskId) {
                return
            }

            this.disconnectSse()

            const baseUrl =
                import.meta.env
                    .VITE_SSE_BASE_URL ||
                'http://localhost:8080'

            const url =
                `${baseUrl}/api/agent/tasks/${taskId}/stream`

            const source =
                new EventSource(url)

            this.sse = source

            source.onopen = () => {
                this.sseConnected = true
            }

            source.addEventListener(
                'CONNECTED',
                event => {
                }
            )

            const eventNames = [
                'BLOCK_APPEND',
                'BLOCK_UPDATE',
                'RESULT_REFRESH'
            ]

            eventNames.forEach(
                eventName => {
                    source.addEventListener(
                        eventName,
                        event => {
                            this.handleSseEvent(
                                event
                            )
                        }
                    )
                }
            )

            source.onerror = error => {
                this.sseConnected = false

                console.error(
                    '[SSE] 连接异常:',
                    error
                )
            }
        },

        handleSseEvent(event) {
            if (!event?.data) {
                return
            }

            try {
                const message =
                    JSON.parse(
                        event.data
                    )
                if (!message.messageId) {
                    return
                }

                /*
                 * 当前 SSE 连接属于当前 Task。
                 */
                if (
                    this.task &&
                    message.taskId &&
                    message.taskId !==
                    this.task.taskId
                ) {
                    return
                }

                /*
                 * 非当前 Run 的事件不更新
                 * 当前正在查看的 Run。
                 */
                if (
                    message.runId &&
                    this.viewedRunId &&
                    message.runId !==
                    this.viewedRunId
                ) {
                    return
                }

                switch (message.type) {

                    case 'BLOCK_APPEND':
                        this.appendChatBlock(
                            message.block,
                            message.taskId,
                            message.runId
                        )

                        this.updateChatPhases(
                            message.phases,
                            message.taskId,
                            message.runId
                        )
                        break

                        if (message.refreshResult) {
                            Promise.all([
                                this.fetchTask(message.taskId),
                                this.fetchResult(
                                    message.taskId,
                                    message.runId
                                )
                            ]).catch(error => {
                                console.error(
                                    '[SSE] 刷新取消后的 Task / Result 失败:',
                                    error
                                )
                            })
                        }

                    case 'BLOCK_UPDATE':
                        this.updateChatBlock(
                            message.block,
                            message.taskId,
                            message.runId
                        )

                        this.updateChatPhases(
                            message.phases,
                            message.taskId,
                            message.runId
                        )
                        break

                    case 'RESULT_REFRESH':
                        /*
                         * Java 已经完成：
                         *
                         * Event
                         * Task
                         * Run
                         * Assistant ChatMessage
                         *
                         * 重新获取完整 Session Chat。
                         */
                        Promise.all([
                            this.fetchResult(
                                message.taskId,
                                message.runId
                            ),
                            this.fetchTask(
                                message.taskId
                            )
                        ]).catch(
                            error => {
                                console.error(
                                    '[SSE] 刷新 Task / Result 失败:',
                                    error
                                )
                            }
                        )

                        break

                    default:
                        console.warn(
                            '[SSE] 未知 Stream 类型:',
                            message.type
                        )
                }
            } catch (error) {
                console.error(
                    '[SSE] AgentChatStream JSON解析失败:',
                    event.data,
                    error
                )
            }
        },

        disconnectSse() {
            if (this.sse) {
                this.sse.close()
                this.sse = null
            }

            this.sseConnected = false
        },

        getErrorMessage(
            error,
            defaultMessage
        ) {
            return (
                error?.response?.data
                    ?.message ||
                error?.response?.data
                    ?.error ||
                error?.message ||
                defaultMessage
            )
        }
    }
})