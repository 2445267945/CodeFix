项目结构
CodeFix_PY/
│
├── agents/                          # Agent 核心逻辑层
│   ├── supervisor/                  # 监督者 Agent
│   │   ├── __init__.py
│   │   └── supervisor_agent.py
│   ├── worker/                      # 工作型 Agent
│   │   ├── __init__.py
│   │   ├── explorer_agent.py
│   │   └── fixer_agent.py
│   ├── __init__.py
│   ├── agent_state.py               # Agent 状态枚举
│   ├── audit_agent.py               # CodeAuditAgent 主类
│   ├── base_agent.py                # Agent 基类（抽象）
│   ├── prompts.py                   # Prompt 模板
│   ├── react_agent.py               # ReAct 抽象类
│   └── tool_executor.py             # ReAct 执行器
│
├── api/                             # 表现层（HTTP 接口）
│   ├── __init__.py
│   ├── routes.py                    # 路由注册（/analyze, /health 等）
│   └── schemas.py                   # Pydantic 请求/响应模型
│
├── infrastructure/                  # 基础设施层（技术细节）
│   ├── message/                     # 消息定义
│   │   ├── __init__.py
│   │   ├── base.py
│   │   ├── message.py
│   │   └── types.py
│   ├── mq/                          # 消息队列（RocketMQ）
│   │   ├── __init__.py
│   │   ├── consumer.py
│   │   ├── event_bus.py
│   │   └── handler.py
│   └── memory/                      # 内存/会话管理
│       ├── __init__.py
│       └── message_manager.py
│
├── models/                          # 领域模型（数据实体）
│   ├── __init__.py
│   ├── agent_message.py
│   ├── audit_report.py
│   ├── code_smell.py
│   └── tool_schemas.py
│
├── services/                        # 业务服务层
│   ├── impl/                        # 服务实现
│   │   ├── __init__.py
│   │   └── agent_msg_service.py
│   ├── __init__.py
│   ├── base_handler.py
│   ├── llm_factory.py
│   ├── llm_service.py
│   └── rag_service.py
│
├── tools/                           # Agent 可调用的工具
│   ├── __init__.py
│   ├── java_checker.py
│   ├── rag_retriever.py
│   ├── code_fixer.py
│   └── tool_registry.py
│
├── utils/                           # 工具函数（纯函数）
│   ├── __init__.py
│   ├── config.py
│   └── main.py                      # 可能是项目入口
│
├── .env                             # 环境变量（不提交）
├── .env.example                     # 环境变量模板
├── .gitignore
├── requirements.txt
├── Dockerfile
├── docker-compose.yml
└── README.md