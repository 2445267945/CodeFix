import http from './http'

/**
 * Workspace 文件树。
 * 后端接口尚未接入时不会被 Workspace 页面自动调用；
 * V2 只把接口入口预留好。
 */
export function getWorkspaceTree(workspaceId) {
  return http.get(
    `/api/agent/workspace/${workspaceId}/tree`
  );
}

export function getWorkspaceFile(workspaceId, filePath) {
  return http.get(`/api/agent/workspace/${workspaceId}/file`, {
    params: {
      path: filePath
    }
  }
  )
}

/**
 * 查询所有 Workspace
 */
export function getWorkspaces() {
  return http.get(
    `/api/agent/workspace`
  );
}

export function createWorkspace(name) {
  return http.post(
    "/api/agent/workspace",
    {
      name,
    }
  );
}

/**
 * 保存 Workspace 文件
 *
 * PUT /api/agent/workspace/{workspaceId}/file
 *
 * @param {string} workspaceId
 * @param {string} filePath
 * @param {string} content
 */
export function updateWorkspaceFile(
  workspaceId,
  filePath,
  content
) {
  return http.put(
    `/api/agent/workspace/${workspaceId}/file`,
    {
      path: filePath,
      content,
    }
  );
}

export default {
  getWorkspaceFile,
  getWorkspaceTree,
  getWorkspaces,
  createWorkspace,
  updateWorkspaceFile
}
