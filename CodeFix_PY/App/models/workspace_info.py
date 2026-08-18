from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class WorkspaceInfo:
    workspace_id: str
    root_path: Path
    file_name: str = ""

    def resolve(self, file_name: str) -> Path:
        target = (self.root_path / file_name).resolve()
        root = self.root_path.resolve()
        if not target.is_relative_to(root):
            raise ValueError(f"非法文件路径: {file_name}")
        return target
