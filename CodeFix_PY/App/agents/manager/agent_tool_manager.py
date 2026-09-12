class AgentToolSet:
    SUPERVISOR = (
        "list_files",
        "glob",
        "grep",
        "read_file",
        "write_file",
        "apply_patch",
        "delete_file",
        "run_explorer",
        "run_fixer",
        "run_command",
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
        "verify_java_syntax",
    )