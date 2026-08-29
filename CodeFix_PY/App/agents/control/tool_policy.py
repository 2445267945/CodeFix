# App/agents/control/tool_policy.py

from enum import Enum


class ToolPermission(str, Enum):
    AUTO = "AUTO"
    CONFIRM = "CONFIRM"


TOOL_PERMISSION = {
    "read_file": ToolPermission.CONFIRM,
    "list_files": ToolPermission.CONFIRM,
    "search_file": ToolPermission.CONFIRM,
    "write_file": ToolPermission.CONFIRM,
    "apply_patch": ToolPermission.CONFIRM,

    "delete_file": ToolPermission.CONFIRM,
    "run_explorer": ToolPermission.CONFIRM,
    "run_fixer": ToolPermission.CONFIRM,
    "search_manual": ToolPermission.CONFIRM,
    "verify_java_syntax": ToolPermission.CONFIRM,
}


def get_tool_permission(tool_name: str) -> ToolPermission:
    return TOOL_PERMISSION.get(tool_name, ToolPermission.CONFIRM)