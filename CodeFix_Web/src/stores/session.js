import { defineStore } from 'pinia'

import {
    getSessions,
    getSessionChat
} from '../api/session'

export const useSessionStore = defineStore(
    'session',
    {
        state: () => ({
            sessions: [],

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

            async fetchSessions() {
                this.loading = true
                this.error = null

                try {
                    const response =
                        await getSessions()

                    this.sessions =
                        Array.isArray(response)
                            ? response
                            : []

                    return this.sessions

                } catch (error) {
                    this.error =
                        this.getErrorMessage(
                            error,
                            '获取历史对话失败'
                        )

                    throw error

                } finally {
                    this.loading = false
                }
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