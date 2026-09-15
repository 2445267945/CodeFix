<div align="center">
  <pre>
 ██████╗ █████╗ ███╗   ██╗██████╗  ██████╗
██╔════╝██╔══██╗████╗  ██║██╔══██╗██╔═══██╗
██║     ███████║██╔██╗ ██║██║  ██║██║   ██║
██║     ██╔══██║██║╚██╗██║██║  ██║██║   ██║
╚██████╗██║  ██║██║ ╚████║██████╔╝╚██████╔╝
 ╚═════╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═════╝  ╚═════╝
  </pre>


  <h3>🤖 长任务 Coding Agent / Agent IDE</h3>

  <p>
    一个面向个人开发者的 Coding Agent 实验项目，重点探索 Agent 背后的运行时、控制面与产品化交互。
  </p>


  <p>
    <a href="README.md">🇨🇳 中文</a> | <a href="README_EN.md">🇺🇸 English</a>
  </p>

</div>

---



## 项目简介

CodeFix 是一个面向个人开发者的 **长任务 Coding Agent / Agent IDE**。

它不是为了重新实现一个“更强的 Codex”，而是希望回答另一个问题：

> **一个 Coding Agent 真正进入工程执行阶段后，背后的运行时系统应该如何设计？**

项目基于 **Java + Python + Vue** 构建，将 Agent 的“智能执行”和“系统控制”拆成两个相对独立的部分：

- **Java**：负责 Control Plane / 产品后端，管理 Task、Run、Event、Permission、Workspace、历史与实时状态。
- **Python**：负责 Agent Runtime，执行 ReAct 循环、LLM 调用、Tool Calling、子 Agent 协作以及工具执行。
- **Vue / Electron**：负责把 Agent 的执行过程转化为用户可以理解、可以介入、可以恢复的 IDE 式交互。
- **RocketMQ**：连接 Java 与 Python，承担任务下发、状态回报、命令控制与心跳等异步通信。

当前项目已经从早期的单 Agent 逐步演进到 **Supervisor + Explorer + Fixer 的 Multi-Agent 模式**，并围绕长任务执行、Human-in-the-loop、Workspace、Realtime UI 等能力形成较完整的运行链路。

---

## 为什么做 CodeFix

成熟的 Coding Agent 已经可以完成很多复杂的软件开发任务。

CodeFix 关注的不是“模型还能不能更聪明”，而是 **Agent 为什么能够稳定地完成一个持续数十步甚至更久的工程任务**。

例如：

- 一个自然语言需求如何变成可持久化的 Task？
- 一次 Task 为什么需要独立的 Run？
- Tool Call、审批、执行结果应该如何记录？
- Agent 运行到一半如何等待人工确认，再继续执行？
- Java 与 Python 如何异步协同，而不是 HTTP 同步等待整个 Agent？
- 页面刷新后，为什么还能恢复之前的 Agent 工作过程？
- 多 Agent 的子任务如何接入同一套 Permission / Action / Workspace 体系？
- Agent 的底层事件如何变成用户真正看得懂的产品 UI？

因此，CodeFix 更关注 **Agent Engineering / Agent Runtime / Control Plane**，而不只是 LLM API 调用。

---

## Demo

### Coding Agent

![Agent Demo](Images/agent.gif)

### Multi-Agent

当前 Multi-Agent 模式由 **Supervisor / Explorer / Fixer** 协作完成任务。

![Multi-Agent Demo](Images/sub_agent.gif)

---

## 核心能力

| 能力                 | 说明                                                         |
| -------------------- | ------------------------------------------------------------ |
| Long-running Task    | 将 Coding Task 建模为可持续执行的异步任务，而不是一次同步问答 |
| Task / Run Lifecycle | Task 与 Run 分离，支持 Retry / Resume / Cancel               |
| State Machine        | Java 侧统一管理任务状态与状态迁移                            |
| Event Persistence    | Agent 运行事件持久化，并进行幂等处理                         |
| Human-in-the-loop    | 敏感 Tool Action 可进入人工审批流程                          |
| ActionId             | 将审批命令与具体 Tool Action 严格绑定                        |
| Permission           | 支持 READ_ONLY / WORKSPACE / FULL_AUTO 等运行策略            |
| Workspace            | Agent 文件操作受 Workspace 根目录约束，并记录文件变化        |
| Diff / File Change   | 将文件修改记录为可查看的变更与 Diff                          |
| Multi-Agent          | Supervisor + Explorer + Fixer 协同执行                       |
| Context / Memory     | 支持上下文选择、消息管理与 Working Memory                    |
| Realtime UI          | 通过 SSE 将执行状态实时推送到前端                            |
| History Restore      | 页面刷新后从持久化数据恢复 Agent 工作过程                    |
| Activity Aggregation | 将底层 Event 聚合为用户可理解的 Activity / Phase             |
| RAG                  | 支持文档 / 规范检索等扩展能力                                |

---

## 整体架构

![Architecture](Images/architecture.png)

```text
                         ┌─────────────────────────────┐
                         │     CodeFix Web / Electron  │
                         │                             │
                         │ Workspace / Task / Chat    │
                         │ Agent Activity / Diff      │
                         │ Approval / File Review     │
                         └──────────────┬──────────────┘
                                        │ HTTP / SSE
                                        ▼
                         ┌─────────────────────────────┐
                         │    CodeFix Java / Control   │
                         │          Plane              │
                         │                             │
                         │ Session / Task / Run        │
                         │ State / Event / Action      │
                         │ Permission / Workspace      │
                         │ History / Aggregation / SSE  │
                         └──────────────┬──────────────┘
                                        │ RocketMQ
                                        ▼
                         ┌─────────────────────────────┐
                         │    CodeFix Python Runtime   │
                         │                             │
                         │ Supervisor / Worker Agent   │
                         │ ReAct / LLM / Tool Calling  │
                         │ Context / Memory            │
                         │ ExecutionGate / Heartbeat   │
                         └─────────────────────────────┘

                    ┌──────────────┐      ┌──────────────┐
                    │    MySQL     │      │    Redis     │
                    │ Persistence  │      │ Cache/Memory │
                    └──────────────┘      └──────────────┘
```

### Java / Python 的职责边界

**Java Control Plane** 负责：

- Session / Task / Run 生命周期
- 状态机与状态迁移
- Event / Action 持久化
- Permission / Human Approval
- Workspace 与 File Change
- SSE / History / Activity Aggregation
- MQ 消息分发与运行控制
- 心跳与任务看门狗

**Python Agent Runtime** 负责：

- Supervisor / Explorer / Fixer
- ReAct 执行循环
- LLM 调用与上下文组织
- Tool Registry / Tool Calling
- Workspace 文件操作
- ExecutionGate
- Working Memory
- Agent Heartbeat

一句话总结：

> **Java 决定 Agent 如何被运行、记录和控制；Python 决定 Agent 如何思考并执行。**

---

## 核心领域模型

```text
Session
  └── Task
        └── Run
              ├── Action
              └── Event

Session ─── Workspace
```

关系：

```text
Session : Task       = 1 : N
Task    : Run        = 1 : N
Run     : Action     = 1 : N
Run     : Event      = 1 : N
Session : Workspace  = N : 1
```

### 核心概念

- **Session**：持续对话上下文的容器。
- **Task**：用户提出的一次具体 Coding Task。
- **Run**：Task 的一次实际执行尝试。
- **Action**：一个可以被控制、审批、执行和追踪的具体 Tool Action。
- **Event**：Agent Runtime 上报的原始执行事实。
- **Workspace**：Agent 实际读取和修改文件的工作空间。

这种拆分的核心目的，是让“用户的一次请求”和“Agent 的一次执行尝试”解耦，同时让每一个具体 Tool Action 都能够被独立控制。

---

## Agent 执行流程

典型执行过程：

```text
User
 ↓
Java 创建 Task + Run
 ↓
RocketMQ
 ↓
Supervisor Agent
 ↓
Think
 ↓
Tool Call
 ↓
Permission Decision
 ├── ALLOW  → Execute
 ├── ASK    → Human Approval
 └── DENY   → Reject
 ↓
Tool Result
 ↓
Think ...
 ↓
Sub Agent（按需）
 ↓
...
 ↓
Finish
 ↓
Java 持久化结果
 ↓
SSE / History
 ↓
Frontend
```

### Long-running Task

CodeFix 不采用简单的：

```text
HTTP → Agent → Response
```

而是将一次执行建模成可持久化、可恢复、可干预的运行过程：

```text
Task
 └── Run
      ├── State
      ├── Action
      ├── Event
      ├── Tool Execution
      └── Retry / Resume / Cancel
```

这样 Agent 可以在执行过程中等待人工、失败重试、恢复执行，并且在页面刷新后重新构建之前的工作过程。

---

## Human-in-the-loop

CodeFix 将“是否允许执行某个 Tool”从 Python Agent 的单纯本地判断中抽离出来，由 Java Control Plane 参与统一控制。

```text
Tool Call
   ↓
Permission Evaluator
   ├── ALLOW ──────→ 自动放行
   │
   ├── ASK ────────→ WAITING_HUMAN
   │                      ↓
   │                 Frontend Approval
   │                      ↓
   │                 APPROVE / REJECT
   │                      ↓
   │                 RocketMQ Command
   │                      ↓
   │                 ExecutionGate
   │
   └── DENY ───────→ 拒绝执行
```

### Permission Profile

```text
READ_ONLY   只允许读取与查询
WORKSPACE   允许在指定 Workspace 内修改
FULL_AUTO   自动执行当前支持的操作
```

### 为什么需要 ActionId？

在 Multi-Agent 环境中，同一个 Run 可能存在多个 Agent、多个 Tool Action。

因此审批不能简单地绑定到“当前 Run”，而必须绑定到一个明确的 `actionId`：

```text
Tool Call A → actionId=A
Tool Call B → actionId=B

User Approve(A)
      ↓
Java
      ↓
AGENT_COMMAND(actionId=A)
      ↓
ExecutionGate.resolve(A)
```

这样可以避免“批准了 A，但实际执行了 B”之类的控制问题。

> 当前权限机制属于应用层 Tool / Path 审批，不等同于 OS 级沙箱或容器隔离。

---

## Multi-Agent

当前 Multi-Agent 模式采用一个简单的职责拆分：

```text
                  Supervisor
                      │
              ┌───────┴───────┐
              ▼               ▼
          Explorer          Fixer
              │               │
          分析 / 探索       修改 / 验证
```

### Supervisor

负责任务编排，不直接承担所有底层工具操作。

主要职责：

- 理解用户任务
- 决定是否需要委派子 Agent
- 选择 Explorer / Fixer
- 汇总子 Agent 结果
- 推动整体任务继续执行

### Explorer

主要负责：

- 探索 Workspace
- 查找相关代码
- 分析结构与依赖
- 输出压缩后的分析结果

Explorer 不负责修改业务文件。

### Fixer

主要负责：

- 根据任务定位修改点
- 进行最小必要修改
- 按需执行验证
- 返回修改结果与证据

这种拆分的意义不是“Agent 越多越高级”，而是让不同职责能够复用同一套 Runtime、Workspace、Permission 和 Action 控制机制。

---

## Event → Activity → UI

CodeFix 不会把 Python Runtime 的原始 Event 直接展示给用户。

底层执行信息会经过一层产品语义转换：

```text
Agent Event
(THINK / TOOL_CALL / TOOL_WAITING / TOOL_RESULT / ERROR ...)
       ↓
Event → Activity Block
       ↓
Phase Aggregation
       ↓
Agent Chat View
       ↓
Frontend
```

例如底层可能连续产生：

```text
TOOL_CALL
TOOL_RESULT
TOOL_CALL
TOOL_RESULT
TOOL_CALL
TOOL_RESULT
```

前端最终看到的是更接近 Coding Agent 产品的工作过程：

```text
● 分析项目结构
  ├─ 读取 xxx
  ├─ 搜索 xxx
  └─ 查看 xxx

● 委派 Explorer
  └─ Explorer 正在分析相关代码

● 修改代码
  └─ 更新 xxx

● 验证结果
```

### Realtime / History 一致性

实时执行和历史恢复使用同一套产品语义：

```text
实时：Python Event
        ↓
      Java 落库
        ↓
   Stream Assembler
        ↓
       SSE
        ↓
     Frontend

历史：MySQL Event
        ↓
    Block Assembler
        ↓
  Chat View Assembly
        ↓
     Frontend
```

这使得页面刷新前后不会出现两套完全不同的展示逻辑。

---

## Workspace

Agent 的文件操作始终围绕 Workspace 展开：

```text
Workspace
   ↓
Tool
   ↓
Path Isolation
   ↓
File Change
   ↓
Diff
   ↓
Activity / Review
```

当前主要能力包括：

- 文件 / 目录浏览
- 文件读取
- 搜索与匹配
- 文件创建 / 修改 / 删除
- Diff 查看
- File Change 记录
- Workspace 路径隔离

> 当前实现主要是应用层路径隔离与变更记录，尚未提供容器级 / OS 级 Sandbox。

---

## Context / Memory

长任务 Coding Agent 的一个核心问题是：**什么信息应该进入下一轮上下文？**

CodeFix 在 Java 侧提供 Context Retrieval，结合 Workspace 范围进行历史任务检索，并将真正的 USER / ASSISTANT 消息重新组装进 Agent Context。

Python 侧同时维护运行时 Working Memory，用于处理 Agent 执行过程中的消息窗口、上下文管理与压缩。

目标不是无限堆积历史，而是尽量让 Agent 在长任务中拿到 **更相关、更高价值的上下文**。

---

## Quick Start

### 环境要求

| 依赖         | 说明                                     |
| ------------ | ---------------------------------------- |
| Java         | 17                                       |
| Maven        | Java 项目构建与启动                      |
| Python       | 3.x                                      |
| Node.js      | Vue / Vite / Electron                    |
| MySQL        | `code_fix` 数据库                        |
| Redis        | 缓存 / Working Memory / Context 等能力   |
| RocketMQ     | Java 与 Python 的异步通信                |
| LLM Provider | DeepSeek / OpenAI-compatible Provider 等 |
| Ollama       | 本地模型 / Embedding 场景（按需）        |

### 仓库结构

```text
CodeFix/
├── CodeFix_Java/      # Spring Boot Control Plane
├── CodeFix_PY/        # Python Agent Runtime
├── CodeFix_Web/       # Vue / Electron Frontend
├── Images/            # README 图片与 Demo
└── README.md
```

### 1. 启动基础设施

先准备 MySQL、Redis、RocketMQ，并根据本地环境修改 Java / Python 配置。

Java 端主要配置位于：

```text
CodeFix_Java/src/main/resources/application.yaml
```

Python 端建议使用：

```text
CodeFix_PY/.env.example
```

复制为 `.env` 后填写本地配置。

### 2. 启动 Java

```bash
cd CodeFix_Java
mvn spring-boot:run
```

Java 服务端口以 `application.yaml` 中的 `server.port` 为准。

### 3. 启动 Python Agent Runtime

```bash
cd CodeFix_PY
python -m venv .venv

# Windows
.venv\Scripts\activate

# Linux / macOS
# source .venv/bin/activate

pip install -r requirements.txt
python App/main.py
```

### 4. 启动 Frontend

```bash
cd CodeFix_Web
npm install
npm run dev
```

Electron 开发环境可按项目现有脚本启动。

### 配置注意事项

不要把真实的 API Key、Password、Token、私网地址等敏感信息提交到仓库。

---

## 项目结构

### CodeFix_Java

```text
src/main/java/com/xd/
├── controller/       # REST / SSE
├── service/          # Session / Task / Run / Event / Permission 等业务逻辑
├── runtime/          # State Machine / Permission Runtime
├── assembler/        # Event → Activity / Phase / Chat View
├── mq/               # RocketMQ Producer / Consumer / Handler
├── mapper/           # MyBatis Mapper
├── model/            # DO / DTO / VO / Enum / Context
├── scheduleder/      # Task Watchdog / Heartbeat
└── config/           # Web / MQ / Redis / Transaction 等配置
```

### CodeFix_PY

```text
App/
├── agents/
│   ├── base_agent.py
│   ├── react_agent.py
│   ├── supervisor/
│   ├── worker/
│   ├── control/
│   ├── memory/
│   └── context/
├── agent_boost/
│   ├── tools/
│   └── rag/
├── services/
├── infrastructure/
└── models/
```

### CodeFix_Web

```text
src/
├── electron/         # Electron 桌面壳
├── api/              # HTTP / SSE API
├── stores/           # Pinia
├── views/             # 主界面 / Task / Workspace
├── components/        # Agent Chat / Activity / Diff / Review
├── router/
├── types/
└── config/
```

---

## 技术栈

| Layer                  | Technology                                           |
| ---------------------- | ---------------------------------------------------- |
| Frontend               | Vue 3 / Pinia / Vue Router / Element Plus / Electron |
| Editor / Diff          | Monaco Editor / highlight.js / Markdown Renderer     |
| Backend                | Java 17 / Spring Boot 3.5                            |
| ORM                    | MyBatis                                              |
| Messaging              | RocketMQ                                             |
| Database               | MySQL                                                |
| Cache / Working Memory | Redis                                                |
| Agent Runtime          | Python / FastAPI / Uvicorn                           |
| Agent Model            | Pydantic                                             |
| Realtime               | SSE                                                  |
| RAG / Embedding        | ChromaDB / Ollama Embedding                          |

---

## 当前状态

### V2.7

V2.7 的核心目标是完成从 **Single-Agent → Multi-Agent** 的架构演进，并让子 Agent 在统一的 Runtime 控制体系中工作。

当前已完成的核心能力包括：

- [x] Session / Task / Run 生命周期模型
- [x] Task / Run 状态管理
- [x] Event 持久化与幂等处理
- [x] Run State History
- [x] Retry / Resume / Cancel
- [x] Tool Registry + ReAct Runtime
- [x] Permission Profile
- [x] Human Approval + ExecutionGate
- [x] actionId 精确审批绑定
- [x] Workspace 文件操作与路径隔离
- [x] File Change / Diff
- [x] SSE 实时执行流
- [x] History Restore
- [x] Event → Activity / Phase 聚合
- [x] Supervisor + Explorer + Fixer Multi-Agent
- [x] Context / Memory 基础能力
- [x] Working Memory
- [x] Heartbeat / Watchdog 基础机制

---

## Roadmap

后续版本更关注稳定性、可观测性和运行时能力，而不是无限增加 Agent 数量。

### V2.8

- [ ] 稳定性与边界场景优化
- [ ] Workspace / Multi-Agent 协作细节优化
- [ ] Context Selection / Memory 调优
- [ ] Docker Compose 一键启动基础设施

### V2.9

- [ ] Verification Loop
- [ ] Token / Cost / Latency Metrics
- [ ] Agent Evaluation
- [ ] 更完整的执行结果与质量反馈

### V3.0 / Exploring

- [ ] Sandbox / 隔离执行
- [ ] 更完善的 Agent Runtime
- [ ] Dynamic Agent / Skill Registry
- [ ] 更丰富的 LLM Provider 与上下文策略

> Roadmap 表示当前探索方向，并不代表所有功能都会严格按版本实现。

---

## 项目定位

CodeFix 的目标不是证明“自己做的 Coding Agent 比 Codex 更强”。

相反，我希望通过自己实现一套完整的 Agent Runtime，理解一个 Coding Agent 从“调用 LLM”走向“真正执行软件工程任务”之后，需要解决的工程问题：

```text
LLM
 ↓
Agent Loop
 ↓
Tool Execution
 ↓
Task / Run Lifecycle
 ↓
Permission / Human Approval
 ↓
Workspace / File Change
 ↓
Event Persistence
 ↓
Realtime UI / History
 ↓
A controllable Coding Agent Runtime
```

这也是 CodeFix 最核心的探索方向：

> **不是重新做一个更强的 Coding Agent，而是探索一个 Coding Agent 背后的工程架构。**

---

## License

MIT License
