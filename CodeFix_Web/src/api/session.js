// src/api/session.js

import http from './http'

/**
 * 查询当前所有 Session
 */
export function getSessions() {
    return http.get('/api/agent/sessions')
}

/**
 * 查询一个 Session 的完整 Agent Chat
 */
export function getSessionChat(sessionId) {
    return http.get(
        `/api/agent/sessions/${sessionId}/chat`
    )
}