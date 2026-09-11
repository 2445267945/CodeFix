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
  getWorkspaceFile,
  getWorkspaceFileByPath,
  getWorkspaces,
  updateWorkspaceFile,
};