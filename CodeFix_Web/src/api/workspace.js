import http from "./http";

export function getWorkspaceTree(workspaceId) {
  return http.get(
    `/api/agent/workspace/${workspaceId}/tree`
  );
}

export function getWorkspaceTreeByPath(workspacePath) {
  return http.get(
    "/api/agent/workspace/tree",
    {
      params: {
        path: workspacePath,
      },
    }
  );
}

/**
 * 懒加载：查询某个目录下的直接子节点。
 *
 * dirPath 为空时表示查询根目录第一层。
 */
export function getWorkspaceChildren(workspaceId, dirPath = "") {
  return http.get(
    `/api/agent/workspace/${workspaceId}/children`,
    {
      params: {
        path: dirPath,
      },
    }
  );
}

/**
 * 懒加载：按 Workspace 绝对路径查询某个目录下的直接子节点。
 */
export function getWorkspaceChildrenByPath(workspacePath, dirPath = "") {
  return http.get(
    "/api/agent/workspace/children",
    {
      params: {
        workspacePath,
        path: dirPath,
      },
    }
  );
}

export function getWorkspaceFile(
  workspaceId,
  filePath
) {
  return http.get(
    `/api/agent/workspace/${workspaceId}/file`,
    {
      params: {
        path: filePath,
      },
    }
  );
}

export function getWorkspaceFileByPath(
  workspacePath,
  filePath
) {
  return http.get(
    "/api/agent/workspace/file",
    {
      params: {
        workspacePath,
        path: filePath,
      },
    }
  );
}

export function getWorkspaces() {
  return http.get(
    "/api/agent/workspace"
  );
}

/**
 * 聚合查询所有 Workspace 及其关联的 Session。
 *
 * 后端：
 * GET /api/agent/workspace/sessions
 *
 * 返回：
 * [
 *   {
 *     workspaceId,
 *     workspaceName,
 *     sessions: [
 *       { sessionId, title, workspaceId, createdAt, updatedAt }
 *     ]
 *   }
 * ]
 */
export function getWorkspacesWithSessions() {
  return http.get(
    "/api/agent/workspace/sessions"
  );
}

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
  getWorkspaceTree,
  getWorkspaceTreeByPath,
  getWorkspaceChildren,
  getWorkspaceChildrenByPath,
  getWorkspaceFile,
  getWorkspaceFileByPath,
  getWorkspaces,
  getWorkspacesWithSessions,
  updateWorkspaceFile,
};