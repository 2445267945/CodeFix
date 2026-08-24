# App/agents/control/tool_policy.py

from enum import Enum


class ToolPermission(str, Enum):
    AUTO = "AUTO"
    CONFIRM = "CONFIRM"


TOOL_PERMISSION = {
    "read_file": ToolPermission.AUTO,
    "list_files": ToolPermission.AUTO,
    "search_file": ToolPermission.AUTO,
    "write_file": ToolPermission.AUTO,
    "apply_patch": ToolPermission.AUTO,

    "delete_file": ToolPermission.CONFIRM,
    "run_explorer": ToolPermission.AUTO,
    "run_fixer": ToolPermission.AUTO,
    "search_manual": ToolPermission.AUTO,
    "verify_java_syntax": ToolPermission.AUTO,
}


def get_tool_permission(tool_name: str) -> ToolPermission:
    return TOOL_PERMISSION.get(tool_name, ToolPermission.CONFIRM)