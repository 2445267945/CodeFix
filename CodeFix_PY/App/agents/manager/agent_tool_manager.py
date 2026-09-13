class AgentToolSet:
    # 主 Supervisor（Cando）：
    # 1. 任务编排：把复杂分析 / 复杂修改委派给子 Agent；
    # 2. 简单任务：直接使用 Workspace Tool / run_command 完成；
    # 3. 验证：修改后可直接执行 run_command 做验证。
    SUPERVISOR = (
        "run_explorer",
        "run_fixer",
    )
    EXPLORER = (
        "list_files",
        "glob",
        "grep",
        "read_file",
        "parse_java_code",
    )
    FIXER = (
        "list_files",
        "glob",
        "grep",
        "read_file",
        "write_file",
        "apply_patch",
        "delete_file",
        "search_manual",
        "run_command",
        "verify_java_syntax",
    )