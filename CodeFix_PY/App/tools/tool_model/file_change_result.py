from pydantic import BaseModel, Field
from typing import Literal

# 文件操作结果对象
class FileChangeResult(BaseModel):
    type: Literal["file_change"] = "file_change"
    file_path: str = Field(alias="filePath")
    operation: Literal[
        "created",
        "modified",
        "deleted",
        "renamed"
    ]
    added_lines: int = Field(default=0, alias="addedLines")
    removed_lines: int = Field(default=0, alias="removedLines")
    diff: str = ""
    model_config = {
        "populate_by_name": True
    }
