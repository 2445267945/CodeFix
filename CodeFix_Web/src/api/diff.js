import http from './http'


export function getFileDiff(diffId) {
    return http.get(`/api/agent/diffs/${diffId}`)
}