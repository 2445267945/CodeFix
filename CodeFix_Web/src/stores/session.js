import { defineStore } from 'pinia'

import {
    getSessions,
    getSessionChat
} from '../api/session'

import { getWorkspacesWithSessions } from '../api/workspace'

export const useSessionStore = defineStore(
    'session',
    {
        state: () => ({
            // 兼容旧逻辑的扁平会话列表
            sessions: [],

            // 按项目（Workspace）聚合的会话分组
            // [{ workspaceId, workspaceName, sessions: [...] }]
            workspaceGroups: [],

            currentSessionId: '',

            chat: null,

            loading: false,
            chatLoading: false,

            error: null
        }),

        getters: {
            currentSession: (state) =>
                state.sessions.find(
                    session =>
                        session.sessionId ===
                        state.currentSessionId
                ) || null
        },

        actions: {

            /**
             * 按项目（Workspace）聚合加载会话。
             *
             * 调用后端聚合接口：
             * GET /api/agent/workspace/sessions
             */
            async fetchWorkspaceSessions() {
                this.loading = true
                this.error = null

                try {
                    const response =
                        await getWorkspacesWithSessions()

                    const groups = Array.isArray(response)
                        ? response
                        : []

                    this.workspaceGroups = groups.map(group => ({
                        workspaceId: group?.workspaceId || '',
                        workspaceName:
                            group?.workspaceName || '未命名项目',
                        sessions: Array.isArray(group?.sessions)
                            ? group.sessions
                            : []
                    }))

                    // 同步维护扁平列表，兼容旧逻辑
                    this.sessions = this.workspaceGroups.flatMap(
                        group =>
                            group.sessions.map(session => ({
                                ...session,
                                workspaceId:
                                    session.workspaceId ||
                                    group.workspaceId,
                                workspaceName: group.workspaceName
                            }))
                    )

                    return this.workspaceGroups

                } catch (error) {
                    this.error =
                        this.getErrorMessage(
                            error,
                            '获取项目会话失败'
                        )

                    throw error

                } finally {
                    this.loading = false
                }
            },

            async fetchSessions() {
                // 统一走聚合接口，保证会话按项目分组
                return this.fetchWorkspaceSessions()
            },

            async openSession(sessionId) {
                if (!sessionId) {
                    return null;
                }

                this.chatLoading = true;
                this.error = null;

                try {
                    const response = await getSessionChat(sessionId);
                    const data = response?.data ?? response;
                    this.currentSessionId = sessionId;
                    this.chat = data;
                    return data;
                } catch (error) {
                    this.error =
                        this.getErrorMessage(
                            error,
                            "打开对话失败"
                        );

                    throw error;

                } finally {
                    this.chatLoading = false;
                }
            },

            newConversation() {
                this.currentSessionId = ''
                this.chat = null
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
    }
)