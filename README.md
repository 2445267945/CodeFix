# AI Coding Agent

一个面向个人开发者的长任务 Coding Agent / Agent IDE。

基于 **Java + Python + Vue** 构建，支持 Session / Task / Run / Event 生命周期、
Tool Calling、Human Approval、Workspace 文件操作、Event 持久化、
长任务执行以及实时工作过程展示与历史恢复。

> Java 是 Agent 平台的**控制面与产品后端**，Python 是 Agent 的**推理与执行运行时**，
> Web 前端负责把结构化的执行状态呈现为"用户可以看懂的 Agent 工作过程"。

---

## Demo

**运行结果展示：**

![CodeFix](C:\Users\86151\Desktop\CodeFix.gif)

---

## 项目简介

这个系统解决的是这样一件事：**用户用自然语言提出一个 Coding Task，Agent 真正去把它做完**，
而不是只生成一段回答。

用户可以在界面中发起任务，例如"检查当前项目结构并定位 N+1 查询"，Agent 会：

- 浏览 Workspace（工作区）；
- 读取 / 搜索 / 修改 / 删除文件；
- 执行 Java 语法校验与结构解析；
- 按需委派 Explorer（侦查）与 Fixer（修复）子 Agent；
- 在涉及文件修改等敏感操作时等待人工审批；
- 长时间持续执行多轮 ReAct 循环；
- 最后把工作过程与结果展示给用户。

系统由三个独立代码库组成：

| 代码库         | 角色                                                         |
| -------------- | ------------------------------------------------------------ |
| `CodeFix_Java` | 平台控制面 / 产品后端：Task、Run、状态、事件、审批、Workspace、聚合、SSE |
| `CodeFix_PY`   | Agent Runtime：Supervisor、ReAct、LLM、Tools、Context、审批门控 |
| `CodeFix_Web`  | 前端：任务、会话、聊天过程、文件变更、Diff、Workspace 可视化 |

---

## 为什么做这个项目

普通 Chat 是"一次性问答"：

```text
User
 ↓
LLM
 ↓
Answer
```

而 Coding Agent 是一个**持续执行的工程任务**：

```text
User
 ↓
Task
 ↓
Run
 ↓
Think
 ↓
Tool Call
 ↓
Tool Result
 ↓
Think
 ↓
Tool Call
 ↓
...
 ↓
Finish
```

因此工程难点并不只是"如何调用 LLM"，还包括：

- 长任务生命周期（Task / Run / Event）；
- 多次 Tool Call 的状态管理与上下文；
- 失败 / Retry / Resume / Cancel；
- 需要人工介入时的审批（Approval）；
- Workspace 的读取、修改与变更记录；
- 执行过程的可观察性（实时 UI）与可恢复性（页面刷新后历史恢复）；
- Java、Python、前端三方之间的异步消息协同。

> 这是一个 **Agent Engineering / Agent Platform** 项目，而不仅是 LLM Demo。

---

## 核心能力

| 能力                 | 说明                                                         |
| -------------------- | ------------------------------------------------------------ |
| Long-running Task    | 通过 MQ 异步下发，Java 只做编排，不阻塞 HTTP，支持多轮持续执行 |
| Task / Run Lifecycle | Task 与 Run 分离；一次 Task 可对应多次 Run（Retry / Resume） |
| State Machine        | Java 侧状态机驱动 CREATED → QUEUED → THINKING → EXECUTING → WAITING_HUMAN → FINISHED / ERROR / CANCELLED |
| Event Persistence    | Python 上报的每个 Agent Event 都落库并做幂等判重             |
| Run State History    | 记录每次状态迁移的 from → to、触发来源与原因                 |
| Tool Calling         | 文件读写 / 搜索 / 语法校验 / 解析 / 子 Agent 委派等 12 个工具 |
| Human Approval       | 高风险操作进入 WAITING_HUMAN，Java APPROVE / REJECT 后放行或拒绝 |
| Permission Profile   | READ_ONLY / WORKSPACE / FULL_AUTO 三档策略                   |
| Workspace            | Python 工具把文件路径限制在 Workspace 根内，变更被记录并生成 diffId |
| Realtime UI          | SSE 实时推送 BLOCK_APPEND / BLOCK_UPDATE / RESULT_REFRESH    |
| History Restore      | 页面刷新后从持久化 Event 按同一语义重新聚合展示              |
| Activity Aggregation | 底层 Event 被聚合为用户可理解的 Activity / Phase             |
| Multi-Agent          | Supervisor + Explorer + Fixer                                |
| RAG 规范查询         | 检索《阿里巴巴 Java 开发手册》条款，为修复建议提供权威依据   |

---

## 整体架构

```text
                    ┌─────────────────────────────┐
                    │       CodeFix_Web (Vue)     │
                    │  Workspace / Task / Chat    │
                    │  AgentChat / Diff / Review  │
                    └──────────────┬──────────────┘
                                   │ HTTP (REST) / SSE
                                   ▼
                    ┌─────────────────────────────┐
                    │   CodeFix_Java (Spring Boot)│
                    │                             │
                    │ Session · Task / Run        │
                    │ State Machine · Event       │
                    │ Approval / Permission       │
                    │ Workspace API · Activity    │
                    │ Phase 聚合 · SSE / History  │
                    └──────────────┬──────────────┘
                                   │ RocketMQ (异步)
                                   ▼
                    ┌─────────────────────────────┐
                    │   CodeFix_PY (Agent Runtime)│
                    │                             │
                    │ Supervisor · ReAct · Tool   │
                    │ LLM · Context · Execution   │
                    │ Gate · Worker Agent         │
                    │ Heartbeat · Working Memory  │
                    └─────────────────────────────┘
```

### 协作方式

- **Java**：负责控制面与产品后端 —— "系统如何运行、如何被用户控制"。
- **Python**：负责 Agent Runtime 与智能执行 —— "Agent 如何思考并执行"。
- **RocketMQ**：Java 与 Python 之间的异步消息总线（任务下发 / 状态回报 / 心跳）。
- **Frontend**：用户交互与 Agent 工作过程可视化。

---

## Java / Python 职责划分

### Java Backend（`CodeFix_Java`）

- Session / Task / Run 的创建与生命周期管理；
- Agent 状态机与迁移守卫（Event / Command / ActionCommand 三类迁移表）；
- Event 持久化与幂等判重；
- Run 状态历史记录；
- MQ Producer / Consumer 与消息分发（按 `type` 路由到不同 Handler）；
- 审批与权限决策（ALLOW / ASK / DENY，READ_ONLY / WORKSPACE / FULL_AUTO）；
- 文件变更解析、落库、生成 `diffId`；
- Activity / Phase 聚合（`AgentChatBlockAssembler` → `AgentPhaseAggregator` → `AgentChatAssemblerService`）；
- SSE 实时推送与历史 REST 恢复；
- Workspace 树 / 文件读取与文件更新 API；
- 心跳维护与 Task 看门狗框架。

### Python Agent Runtime（`CodeFix_PY`）

- SupervisorAgent（总控）、ExplorerAgent / FixerAgent（Worker）；
- ReAct 执行循环（Think → Tool Call → Tool Result）；
- Tool Registry / Tool Schema / 参数校验；
- Tool Execution 与路径隔离（限制在 Workspace 根内）；
- ExecutionGate：真正阻塞当前 Agent 协程并等待 Java 的 APPROVE / REJECT；
- Tool Policy：声明工具是 AUTO 还是 CONFIRM；
- Context / Working Memory：消息窗口、压缩与 Redis 工作记忆（TTL 6h）；
- Heartbeat 上报；
- LLM 客户端（DeepSeek / Ollama）与 LLM Factory；
- RAG（chromadb + 《阿里巴巴 Java 开发手册》）用于 `search_manual`。

> 一句话总结：**Java 负责"系统如何运行和如何被用户控制"，Python 负责"Agent 如何思考并执行"。**

---

## 核心领域模型

```text
Session
  └── Task
        └── Run
              └── Event

Session ─── Workspace
```

### 关系

```text
Session : Task   = 1 : N
Task    : Run    = 1 : N  
Retry → 创建新的 Run
Resume → 恢复当前 Run
Cancel → 终止当前 Run
Run     : Event  = 1 : N
Session : Workspace = N : 1（目前暂时为1:1，后续将继续迭代）
```

### 实体说明（Java DO，MySQL `code_fix` 库）

- **Session**（`AgentSessionDO`）：一次持续对话的上下文容器，记录 `workspaceId` 与标题。
- **Task**（`AgentTaskDO`）：用户提出的一个具体工作任务（`question`、`status`、`lastHeartbeatAt`）。
- **Run**（`AgentRunDO`）：Task 的一次实际执行（`runId`、`actionId`、`permissionProfile`、`attempt`、`startedAt` / `endedAt`）。
- **Run State History**（`AgentRunStateHistoryDO`）：每次状态迁移的 from / to / trigger / reason。
- **Event**（`AgentEventDO`）：Agent 执行过程中产生的原始事实（`event`、`step`、`agentName`、`parentAgent`、`output`），以 `messageId` 保证幂等。
- **File Change**（`AgentFileChangeDO`）：Agent 对 Workspace 文件的变更记录（`diffId`、`filePath`、`operation`、`addedLines` / `removedLines`、`diffText`）。
- **Chat Message**（`ChatMessageDO`）：USER / ASSISTANT 级别的对话消息，用于最终答案与上下文构建。
- **Workspace**（`WorkspaceDO`）：Agent 实际读取 / 修改代码的工作空间。

---

## Agent 执行流程

```text
User
 ↓
Java 创建 Task + Run（createTaskWithRun）
 ↓
Java 组装 AGENT_TASK 消息并投递 agent_task_topic
 ↓
Python SupervisorAgent 启动 Run
 ↓
Think（THINK 事件上报）
 ↓
Tool Call（TOOL_CALL 事件上报）
 ↓
Tool Result（TOOL_RESULT 事件上报）
 ↓
Think ...
 ↓
Finish（FINISH 事件上报 → Java 落 ASSISTANT 消息）
```

### Human Approval 流程

```text
Tool Call
     ↓
需要审批？
 ┌───┴────┐
 No       Yes
 │         │
执行      Python ExecutionGate 阻塞（TOOL_WAITING）
 │         ↓
 │      Java 落 WAITING_HUMAN 并推送审批 Block
 │         ↓
 │      Java APPROVE / REJECT（AGENT_COMMAND + actionId）
 │         ↓
 │      ExecutionGate.resolve(actionId, ALLOW/DENY)
 │         ↓
 │      继续执行 / 拒绝
```

`actionId` 的作用：把**当前待审批的 Tool Call** 与 Java 下发的 APPROVE / REJECT 命令严格绑定
（Python `ExecutionGate` 以 `action_id` 为 key 的 Future 等待唤醒），避免"批了 A 却执行了 B"。

---

## 长任务设计

系统不是简单"HTTP → Python Agent → 同步等待"：

```text
HTTP
 ↓
Python Agent
 ↓
等待结束
 ↓
Response
```

而是将任务建模为可持久化、可恢复、可审批的异步执行单元：

```text
Task
 └── Run
      ├── State（状态机）
      ├── Event（原始执行事实，落库）
      ├── Tool execution（受 ExecutionGate 控制）
      └── lifecycle（Retry / Resume / Cancel）
```

已实现的机制：

- **Task / Run 分离**：`createTaskWithRun` 一次消息即创建 Session / Task / Run / Workspace 关联。
- **状态机驱动**：Java `AgentTaskStateMachine` 以三张迁移表管理 Event / Command / ActionCommand 触发的迁移。
- **Event 持久化**：所有 Python 上报事件先落 `agent_event`，`DuplicateKeyException` 判重，天然幂等。
- **Run 状态历史**：状态真正变化时才写 `agent_run_state_history`。
- **Retry / Resume / Cancel**：`POST /api/agent/tasks/{taskId}/retry|resume|cancel`，Java 先切状态再发 MQ 命令。
- **自动放行**：命中权限策略（ALLOW / REJECT）的工具调用由 Java 直接内部下发命令，快速结束，不打断 Agent。
- **心跳**：Python 周期性上报 `AGENT_HEARTBEAT`，Java 更新 `lastHeartbeatAt`；`TaskWatchdog` 每 5s 扫描超过 15s 未心跳的运行中任务。



---

## Tool System

工具注册在 Python `ToolRegistry`（`App/agent_boost/tools/tool_registry.py`），当前共 13 个工具：

| Tool                 | Purpose                                            |
| -------------------- | -------------------------------------------------- |
| `list_files`         | 列出 Workspace 中的文件和目录                      |
| `read_file`          | 读取文件                                           |
| `glob`               | 按文件匹配模式查找文件                             |
| `grep`               | 在 Workspace 中搜索文本 / 类名 / 方法名 / 配置项   |
| `write_file`         | 创建新文件                                         |
| `delete_file`        | 删除文件                                           |
| `apply_patch`        | 精确修改已有文件（生成 Unified Diff）              |
| `verify_java_syntax` | 调用 Java `/api/validate` 校验 Java 语法           |
| `parse_java_code`    | 调用 Java `/api/parse`（JavaParser）解析 Java 结构 |
| `search_manual`      | 检索《阿里巴巴 Java 开发手册》编码规范条款         |
| `run_explorer`       | 委派 Explorer 子 Agent 做代码结构分析              |
| `run_fixer`          | 委派 Fixer 子 Agent 修复代                         |

不同 Agent 拥有不同的工具集合（`AgentToolSet`）：

- `SUPERVISOR`：`list_files` / `glob` / `grep` / `read_file` / `write_file` / `apply_patch` / `delete_file` / `run_explorer` / `run_fixer`
- `EXPLORER`：`list_files` / `glob` / `grep` / `read_file` / `parse_java_code`
- `FIXER`：`list_files` / `glob` / `grep` / `read_file` / `write_file` / `apply_patch` / `delete_file` / `search_manual` / `verify_java_syntax`

---

## Permission / Approval / ActionId

### 权限档位

```text
READ_ONLY   只允许读取与查询，不允许修改文件
WORKSPACE   允许在指定 Workspace 内修改文件
FULL_AUTO   允许 Agent 自动执行全部已支持操作
```

（`PermissionProfileEnum`，记录在 Run 上。）

### 决策与审批

```text
Agent
 ↓
Tool Call
 ↓
Permission Policy Evaluator（tool + path 规则 + profile）
 ↓
ALLOW ────────> Java 自动下发 APPROVE，Agent 直接执行
ASK  ─────────> 进入 WAITING_HUMAN，SSE 推送审批 Block
DENY ─────────> Java 自动下发 REJECT，Agent 被拒绝
```

- Python 端声明每个工具的默认权限（`tool_policy.py`，默认 `CONFIRM`）；
- Java 端 `PermissionService.evaluate` 综合 Run 的 `permissionProfile` 与运行时权限策略/缓存做出决策；
- 需要人工时，Python 的 `ExecutionGate` 以 `actionId` 为 key 阻塞当前 Agent 协程；
- 用户在前端 APPROVE / REJECT → Java `AgentCommandService` → MQ `AGENT_COMMAND` → Python 唤醒对应 Future。

> 说明：当前权限控制是"产品层的工具/路径审批"机制，仓库中并未实现 OS 级沙箱 / 容器隔离，不应声称具备沙箱能力。

---

## Event → Activity → Phase → UI

底层 Event **不会**被直接展示给用户，而是经过多层聚合：

```text
Agent Event（TOOL_CALL / TOOL_WAITING / TOOL_RESULT / THINK / ERROR ...）
     ↓
AgentChatBlockAssembler
     ↓
Activity Block（type: action / file_change / review）
     ↓
AgentPhaseAggregator
     ↓
Phase（ANALYSIS / IMPLEMENTATION / VERIFICATION / SUBTASK / ERROR）
     ↓
AgentChatAssemblerService → AgentChatViewVO / AgentChatStreamVO
     ↓
Frontend
```

### Phase 的定位

**Phase 是后端内部的聚合容器，而不是用户必须看到的 UI 层级。**

- `AgentChatBlockAssembler` 将单个 Event 转换为 Activity Block（含 `summary`、`action`、`status`、`actionId`、`requiresApproval` 等）；
- `AgentPhaseAggregator` 按工作阶段把 Block 聚合成 Phase，例如：

```text
Phase "分析问题"（ANALYSIS）
 ├── narration（Agent 文字叙述）
 ├── action（读取 src/...）
 ├── action（搜索 ...）
 └── narration
Phase "修改代码"（IMPLEMENTATION）
 ├── narration
 ├── file_change
 └── narration
```

前端按 Block 顺序渲染，形成类似"Agent 在干活"的叙述流。

### Narration 与 Reasoning

- Agent 事件中面向用户的 `content` 是**工作叙述**，会进入展示；
- 模型内部 `reasoning` 属于内部推理过程，**不会**作为产品 UI 的叙述展示。

---

## Realtime / History 一致性

实时链路与历史链路使用**同一套产品语义**进行 Activity 聚合：

```text
实时（SSE）：
Python Agent Event → Java 落库 → AgentChatStreamAssembler → SSE → Frontend

历史（REST）：
MySQL AgentEvent → AgentChatBlockAssembler → AgentChatAssemblerService → REST → Frontend
```

- 前端在任务进行中通过 `EventSource` 连接 `GET /api/agent/tasks/{taskId}/stream`；
- SSE 事件类型：`BLOCK_APPEND` / `BLOCK_UPDATE` / `RESULT_REFRESH`（`AgentChatStreamVO.type`）；
- 页面刷新后可调用 `GET /api/agent/sessions/{sessionId}/chat` 拉取完整 `AgentChatViewVO`（turns + phases）恢复到同一视觉状态。

---

## Workspace

Agent 的一切文件操作都发生在**专属 Workspace** 内：

```text
Workspace（根目录，例如 /data/workspaces/<id>）
    ↓
Tool（list_files / read_file / glob / grep / write_file / apply_patch / delete_file）
    ↓
路径隔离（Python resolve_workspace_path 强制限制在根内，越界抛 PermissionError）
    ↓
File Change（created / modified / deleted，记录 added/removed 行与 Unified Diff）
    ↓
Java 落 agent_file_change 并生成 diffId
    ↓
Activity / Diff 展示
```

- Python 侧每个 Run 通过 `run_context.workspace` 绑定工作区；
- Java 侧暴露 Workspace API：
	- `GET /api/agent/workspace`（列出 Workspace）
	- `GET /api/agent/workspace/{workspaceId}/tree`
	- `GET /api/agent/workspace/{workspaceId}/file?path=...`
	- `PUT /api/agent/workspace/{workspaceId}/file`
- 文件变更可通过 `GET /api/agent/diffs/{diffId}` 查看 Diff。

> 当前实现不包含容器 / 沙箱隔离，仅做应用层的路径隔离与变更记录。

---

## Quick Start

### Requirements

| 依赖     | 版本 / 说明                                           |
| -------- | ----------------------------------------------------- |
| Java     | 17（`pom.xml` `java.version=17`）                     |
| Maven    | 用于构建 `CodeFix_Java`                               |
| Python   | 3.x（依赖见 `CodeFix_PY/requirements.txt`）           |
| Node.js  | 用于构建 / 运行 `CodeFix_Web`（Vite）                 |
| MySQL    | 数据库名 `code_fix`（见 `application.yaml`）          |
| Redis    | Java / Python 均使用（session、缓存、Working Memory） |
| RocketMQ | Namesrv + Broker（Java 与 Python 通过它通信）         |

### 目录结构

```text
project/
├── CodeFix_Java/     # Spring Boot 平台后端（控制面）
├── CodeFix_PY/       # Python Agent Runtime
├── CodeFix_Web/      # Vue 前端
└── README.md
```

### Backend（CodeFix_Java）

1. 准备 MySQL（建库 `code_fix`）、Redis、RocketMQ；
2. 修改 `CodeFix_Java/src/main/resources/application.yaml` 中的连接配置
	（MySQL 地址 / 账号、Redis、`mq.rocketmq.name-server`、consumer / producer group 与 topic）；
3. 启动：

```bash
cd CodeFix_Java
mvn spring-boot:run
```

默认监听 `http://localhost:8080`。

### Agent（CodeFix_PY）

1. 准备 Python 环境并安装依赖：

```bash
cd CodeFix_PY
python -m venv .venv
pip install -r requirements.txt
```

2. 从模板复制并填写环境变量（LLM、MySQL/Redis/RocketMQ、Ollama、Embedding、Workspace 根等）：

```bash
cp .env.example .env
```

3. 启动（默认 `0.0.0.0:8000`，启动时会订阅 RocketMQ 消息）：

```bash
python App/main.py
```

### Frontend（CodeFix_Web）

1. 安装依赖：

```bash
cd CodeFix_Web
npm install
```

2. 开发环境后端地址默认 `http://localhost:8080`（见 `.env.development`）：

```bash
npm run dev
```

> 注意：`CodeFix_Java`、`CodeFix_PY`、`CodeFix_Web` 中的 Dockerfile 与
> `CodeFix_PY/docker-compose.yml` 当前为占位文件，尚未包含可用的容器化编排内容。

---

## Configuration

核心配置项（**敏感信息请使用环境变量或本地配置，不要提交到仓库**）：

| 配置域           | 位置 / 示例                                        | 说明                          |
| ---------------- | -------------------------------------------------- | ----------------------------- |
| Java 端口        | `server.port`（默认 8080）                         | 后端服务端口                  |
| MySQL            | `spring.datasource`（库 `code_fix`）               | 持久化                        |
| Redis            | `spring.data.redis` / `REDIS_URL`                  | 缓存 / Working Memory         |
| RocketMQ         | `mq.rocketmq.name-server`、group、topic            | 任务 / 状态 / 心跳消息        |
| LLM Provider     | `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_TIMEOUT`     | DeepSeek 等 OpenAI 兼容接口   |
| Ollama           | `OLLAMA_BASE_URL`                                  | 本地推理 / Embedding          |
| Embedding        | `EMBEDDING_BASE_URL` / `EMBEDDING_MODEL`           | chromadb 向量库 Embedding     |
| Workspace 根目录 | `WORKSPACE_ROOT`（默认 `/data/workspaces`）        | Python 工具访问的工作区根目录 |
| Java Backend URL | `BACKEND_BASE_URL`（默认 `http://localhost:8080`） | Python 调用 Java 辅助接口     |

> `CodeFix_PY/.env.example` 已存在模板文件，具体取值请按本地环境填写。
> README 不包含任何真实 API Key / Password / Token / 私网地址。

---

## Project Structure

### Java（CodeFix_Java）

```text
src/main/java/com/xd/
├── controller/        # REST / SSE 接口（sessions、tasks、workspace、diffs、parse、validate）
├── service/
│   ├── impl/          # 会话/任务/运行/事件/审批/SSE/聚合等实现
│   ├── TaskDispatcher # 按 type 路由 MQ 消息
│   └── ...
├── assembler/         # AgentChatBlockAssembler / AgentPhaseAggregator / AgentChatStreamAssembler ...
├── runtime/
│   ├── state/         # 状态机 + 迁移守卫
│   └── permission/    # 权限策略评估器 / 运行时缓存
├── mq/                # MQProducer / Listener / TopicInitializer / MessageHandler
├── mapper/            # MyBatis Mapper（Java 接口）
├── model/
│   ├── entity/        # DO（session/task/run/event/file_change/chat_message/workspace...）
│   ├── dto/           # 消息与请求 DTO（AgentTaskMessage / AgentMessageDTO ...）
│   ├── vo/            # 展示 VO（AgentChatViewVO / PhaseVO / BlockVO / StreamVO ...）
│   ├── enums/         # 状态 / Event / Command / Permission 枚举
│   └── context/       # SessionContext / TaskRunContext ...
├── scheduleder/       # TaskWatchdog（心跳看门狗）
├── config/            # Web / Redis / RocketMQ / Transaction 配置
└── resources/
    ├── application.yaml
    └── com/xd/mapper/*.xml   # MyBatis SQL
```

### Python（CodeFix_PY）

```text
App/
├── main.py              # FastAPI 入口（lifespan 启动/停止 MQ）
├── config.py            # 环境变量配置（LLM / MQ / Redis / Workspace / VectorDB）
├── bootstrap/
│   └── mq_bootstrap.py  # RocketMQ Consumer 引导与消息类型注册
├── agents/
│   ├── base_agent.py / react_agent.py / tool_executor.py   # ReAct 执行基座
│   ├── supervisor/supervisor_agent.py                      # 总控 Agent
│   ├── worker/explorer_agent.py / fixer_agent.py           # Worker Agent
│   ├── control/execution_gate.py / tool_policy.py / agent_command_service.py
│   ├── manager/agent_run_manager.py / agent_tool_manager.py
│   ├── memory/             # context / message / token / working memory
│   ├── agent_model/        # LLMMessage / ToolCall / LLMResponse ...
│   ├── context/            # AgentContext / AgentRunContext
│   └── agent_state.py
├── agent_boost/
│   ├── tools/tool_registry.py     # 13 个工具
│   ├── tool_model/tool_schemas.py # 工具参数 Schema
│   └── rag/
├── services/
│   ├── agent_msg_service.py       # AGENT_TASK 处理与 AGENT_STATUS 回传
│   ├── llm_service/ (ollama / deepseek)
│   ├── factory/llm_factory.py
│   ├── rag_service.py             # chromadb 检索《阿里巴巴 Java 手册》
│   └── redis_working_memory_store.py
├── infrastructure/        # mq / redis / heartbeat / files_search / message / handler
└── models/                # 消息 / 事件 / 命令 / workspace / session 等数据模型
```

### Frontend（CodeFix_Web）

```text
src/
├── api/          # http 封装 + task / session / workspace / diff
├── stores/       # Pinia（task：任务 + SSE；session：会话与聊天）
├── views/        # Workspace.vue（主界面）/ TaskList / TaskCreate
├── components/   # AgentChat / AgentActionBlock / FileChangeBlock / ReviewBlock
│                 # DiffViewer / CodeViewer / Editor / WorkspaceTree ...
├── router/       # Vue Router
├── types/        # task / event / agentChat 类型
└── config/       # API / SSE 地址（读取 .env）
```

---

## Roadmap

### Current（当前已实现）

- [x] Session / Task / Run 生命周期与关系建模
- [x] Agent 状态机（Event / Command / ActionCommand 迁移）+ 迁移守卫
- [x] Agent Event 持久化与幂等判重
- [x] Run 状态历史记录
- [x] Retry / Resume / Cancel
- [x] Tool Registry 与 ReAct 执行循环（Python）
- [x] Tool Permission（AUTO / CONFIRM）与 Permission Profile（READ_ONLY / WORKSPACE / FULL_AUTO）
- [x] Human Approval：ExecutionGate 阻塞 + Java APPROVE / REJECT + actionId 绑定
- [x] Workspace 文件操作与路径隔离
- [x] File Change 记录（diffId / unified diff）与 Diff 查看
- [x] Activity / Phase 聚合展示（Event → Block → Phase → UI）
- [x] SSE 实时推送与历史恢复（同一聚合语义）
- [x] 子 Agent：Supervisor + Explorer + Fixer
- [x] RAG 检索《阿里巴巴 Java 开发手册》（search_manual）
- [x] Python Working Memory（Redis，消息序列化存储）
- [x] Heartbeat 上报与 Java 侧看门狗扫描框架

### Next / 规划

- [ ] Workspace 创建 / 关联的产品级完整流程（前端已预留创建入口，后端 CRUD 完善中）
- [ ] sandbox等隔离机制的实现
- [ ] agent 流式输出
- [ ] agent 操作回滚
- [ ] 有效信息的展示（token消耗、耗时等）
- [ ] 用户 / 登录 / 账号级权限体系
- [ ] 更完整的 Docker / docker-compose 一键编排（当前为占位文件）
- [ ] 单元 / 集成测试覆盖（当前 `CodeFix_Java` 仅包含启动冒烟测试）
- [ ] 会话级长任务历史归档与检索
- [ ] 更多 LLM Provider 适配与上下文压缩策略调优

> 说明：以上 "Next" 均为仓库当前**尚未完成**的内容。

---

## Tech Stack

| Layer                     | Technology                                         |
| ------------------------- | -------------------------------------------------- |
| Frontend                  | Vue 3 / Pinia / Vue Router / Element Plus / Axios  |
| Editor / Diff             | Monaco Editor / highlight.js / vue-markdown-render |
| Backend                   | Java 17 / Spring Boot 3.5                          |
| ORM                       | MyBatis（mybatis-spring-boot-starter 3.0.3）       |
| Messaging                 | RocketMQ（client 5.3.3，FIFO status topic）        |
| Database                  | MySQL（库 `code_fix`）                             |
| Cache / Working Memory    | Redis                                              |
| Agent Runtime             | Python / FastAPI / Uvicorn                         |
| Agent Models / Validation | Pydantic                                           |
| Code Analysis             | JavaParser（core 3.28.0）/ JavaSyntaxValidator     |
| Realtime                  | SSE（SseEmitter + EventSource）                    |
| RAG / Embedding           | ChromaDB / Ollama Embedding / pypdf                |
| JSON 处理                 | fastjson2 / Jackson                                |

---

## License

This project is licensed under the MIT License.