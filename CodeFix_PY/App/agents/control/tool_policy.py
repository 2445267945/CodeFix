from enum import Enum


class ToolPermission(str, Enum):
    AUTO = "AUTO"
    CONFIRM = "CONFIRM"


TOOL_PERMISSION = {
    # 只读工具
    "list_files": ToolPermission.CONFIRM,
    "read_file": ToolPermission.CONFIRM,
    "glob": ToolPermission.CONFIRM,
    "grep": ToolPermission.CONFIRM,
    "search_manual": ToolPermission.CONFIRM,
    "parse_java_code": ToolPermission.CONFIRM,
    "verify_java_syntax": ToolPermission.CONFIRM,

    # 修改工具
    "write_file": ToolPermission.CONFIRM,
    "apply_patch": ToolPermission.CONFIRM,
    "delete_file": ToolPermission.CONFIRM,

    # 子 Agent
    "run_explorer": ToolPermission.CONFIRM,
    "run_fixer": ToolPermission.CONFIRM,

    # 普通执行
    "get_length": ToolPermission.CONFIRM,
}


def get_tool_permission(tool_name: str) -> ToolPermission:
    return TOOL_PERMISSION.get(tool_name, ToolPermission.CONFIRM)