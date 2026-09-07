from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class WorkspaceInfo:
    workspace_id: str
    root_path: Path
    file_name: str = ""

    def resolve(self, file_name: str) -> Path:
        path = file_name
        workspace_prefix = self.workspace_id.replace("\\", "/") + "/"
        normalized = path.replace("\\", "/")
        if normalized == self.workspace_id:
            path = "."
        elif normalized.startswith(workspace_prefix):
            path = normalized[len(workspace_prefix):]
        target = (self.root_path / path).resolve()
        root = self.root_path.resolve()
        if not target.is_relative_to(root):
            raise ValueError(f"路径越界: {file_name}")
        return target
