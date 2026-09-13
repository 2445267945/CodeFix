class AgentToolSet:
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
        "verify_java_syntax",
    )