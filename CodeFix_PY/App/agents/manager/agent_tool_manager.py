class AgentToolSet:
    SUPERVISOR = (
        "list_files",
        "search_file",
        "read_file",
        "write_file",
        "delete_file",
        "run_explorer",
        "run_fixer",
    )
    EXPLORER = (
        "list_files",
        "search_file",
        "read_file",
        "parse_java_code",
    )
    FIXER = (
        "list_files",
        "search_file",
        "read_file",
        "write_file",
        "delete_file",
        "search_manual",
        "verify_java_syntax",
    )
