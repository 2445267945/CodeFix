项目结构
CodeFix/
├── app/                          # 主应用目录
│   ├── __init__.py
│   ├── main.py                   # FastAPI 应用入口
│   ├── config.py                 # 配置文件（API Key、端口、模型参数等）
│   │
│   ├── api/                      # API层：处理HTTP请求与响应
│   │   ├── __init__.py
│   │   ├── routes.py             # 路由注册（/analyze, /health）
│   │   └── schemas.py            # Pydantic请求/响应模型定义
│   │
│   ├── agents/                   # Agent核心：ReAct推理逻辑
│   │   ├── worker     
│   │   │   ├── explorer_agent.py
│   │   │   └── fixer_agent.py
│   │   ├── supervisor
│   │   │   └── supervisor_agent.py
│   │   ├── __init__.py
│   │   ├── agent_state           # 状态枚举（Agent状态）
│   │   ├── audit_agent.py        # CodeAuditAgent主类（Agent值初始化）
│   │   ├── base_agent            # Agent基类（最外层抽象类，调用入口，控制最大迭代次数）
│   │   ├── react_agent           # ReAct抽象类（控制ReAct的执行模式）
│   │   ├── prompts.py            # Prompt模板（System Prompt + 工具描述）
│   │   └── tool_executor.py      # Agent执行器（实现ReAct模式的功能）
│   │
│   ├── tools/                    # Agent可调用的工具集合
│   │   ├── __init__.py
│   │   ├── java_checker.py       # 调用Java端编译校验接口
│   │   ├── rag_retriever.py      # 从Chroma向量库检索规范文档
│   │   ├── code_fixer.py         # 调用LLM生成修复代码
│   │   └── tool_registry.py      # 工具注册表（Agent根据名称查找工具）
│   │
│   ├── services/                 # 业务服务层：封装复杂逻辑
│   │   ├── __init__.py
│   │   ├── rag_service.py        # 向量库初始化、加载文档、检索
│   │   └── llm_service.py        # LLM API调用封装（统一接口，支持切换模型）
│   │
│   ├── models/                   # 数据模型（内部使用的领域对象）
│   │   ├── __init__.py
│   │   ├── code_smell.py         # CodeSmell实体（行号、类型、严重程度）
│   │   └── audit_report.py       # AuditReport实体（issues + fixedCode）
│   │
│   └── utils/                    # 工具函数
│       ├── __init__.py
│       ├── logger.py             # 统一日志配置
│       └── json_parser.py        # 解析LLM返回的JSON（含容错处理）
│
├── data/                         # 本地数据目录
│   ├── chroma_db/                # Chroma向量库持久化存储
│   └── docs/                     # 原始规范文档（《阿里Java手册》等txt/md）
│
├── tests/                        # 单元测试
│   ├── __init__.py
│   ├── test_agent.py
│   ├── test_tools.py
│   └── test_api.py
│
├── .env                          # 环境变量（OPENAI_API_KEY等，不提交）
├── .env.example                  # 环境变量模板（提交到git）
├── .gitignore
├── requirements.txt              # Python依赖清单
├── Dockerfile                    # Python服务镜像构建文件
├── docker-compose.yml            # （可选）多容器编排
└── README.md                     # 项目说明文档