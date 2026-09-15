<div align="center">
  <pre>
 ██████╗ █████╗ ███╗   ██╗██████╗  ██████╗
██╔════╝██╔══██╗████╗  ██║██╔══██╗██╔═══██╗
██║     ███████║██╔██╗ ██║██║  ██║██║   ██║
██║     ██╔══██║██║╚██╗██║██║  ██║██║   ██║
╚██████╗██║  ██║██║ ╚████║██████╔╝╚██████╔╝
 ╚═════╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═════╝  ╚═════╝
  </pre>

  <h3>🤖 Long-running Coding Agent / Agent IDE</h3>

  <p>
    A Coding Agent project for exploring the runtime, control plane, and product experience behind long-running AI coding tasks.
  </p>

  <p>
    <a href="README.md">🇨🇳 中文</a> | <a href="README_EN.md">🇺🇸 English</a>
  </p>
</div>

---

## Overview

Cando is a **long-running Coding Agent / Agent IDE** designed for individual developers.

It is not intended to simply build "a stronger Codex". Instead, it explores a different question:

> **What kind of engineering system is needed behind a Coding Agent once it starts executing real, long-running software engineering tasks?**

Cando is built with **Java + Python + Vue**, separating the agent's intelligence and execution from the system responsible for controlling and managing it:

* **Java** acts as the Control Plane and product backend, managing Tasks, Runs, Events, Permissions, Workspaces, history, and runtime state.
* **Python** provides the Agent Runtime, including ReAct execution, LLM interaction, Tool Calling, sub-agent collaboration, and tool execution.
* **Vue / Electron** turns structured execution events into an IDE-style interface that users can understand, interact with, and recover.
* **RocketMQ** connects Java and Python through asynchronous messaging for task dispatch, status reporting, commands, and heartbeats.

The project has evolved from an early Single-Agent architecture into a **Multi-Agent architecture based on Supervisor + Explorer + Fixer**, with long-running execution, Human-in-the-loop control, Workspace management, and Realtime UI as its main capabilities.

---

## Why Cando?

Modern Coding Agents are already capable of completing many complex software engineering tasks.

Cando is less about making the model "smarter" and more about exploring **why an Agent can reliably complete a long-running engineering task in the first place**.

For example:

* How should a natural-language request become a persistent Task?
* Why should a Task be separated from a Run?
* How should Tool Calls, approvals, and execution results be represented?
* How can an Agent pause for human approval and then continue execution?
* How can Java and Python collaborate asynchronously instead of waiting for the entire Agent execution through a synchronous HTTP request?
* How can the system restore an Agent's working process after the page is refreshed?
* How should multiple Agents share the same Permission, Action, and Workspace infrastructure?
* How can low-level Agent events be transformed into a UI that users can actually understand?

Because of this, Cando focuses on **Agent Engineering / Agent Runtime / Control Plane**, rather than simply wrapping an LLM API.

---

## Demo

### Coding Agent

![Agent Demo](Images/agent.gif)

### Multi-Agent

The current Multi-Agent mode uses **Supervisor / Explorer / Fixer** to collaboratively complete tasks.

![Multi-Agent Demo](Images/sub_agent.gif)

---

## Core Capabilities

| Capability           | Description                                                                                     |
| -------------------- | ----------------------------------------------------------------------------------------------- |
| Long-running Task    | Models coding work as persistent asynchronous execution instead of a single synchronous request |
| Task / Run Lifecycle | Separates Tasks and Runs, with Retry / Resume / Cancel support                                  |
| State Machine        | Centralizes task state management and transitions on the Java side                              |
| Event Persistence    | Persists Agent runtime events with idempotent processing                                        |
| Human-in-the-loop    | Allows sensitive Tool Actions to pause for human approval                                       |
| ActionId             | Binds approval commands to a specific Tool Action                                               |
| Permission           | Supports READ_ONLY / WORKSPACE / FULL_AUTO execution policies                                   |
| Workspace            | Restricts Agent file operations to a Workspace and records file changes                         |
| Diff / File Change   | Tracks file modifications and exposes reviewable diffs                                          |
| Multi-Agent          | Supervisor + Explorer + Fixer collaboration                                                     |
| Context / Memory     | Supports context selection, message management, and Working Memory                              |
| Realtime UI          | Streams Agent execution state to the frontend through SSE                                       |
| History Restore      | Reconstructs the Agent working process from persisted data                                      |
| Activity Aggregation | Converts low-level Events into user-oriented Activities / Phases                                |
| RAG                  | Supports document and coding-standard retrieval                                                 |

---

## Architecture

![Architecture](Images/architecture.png)

```text
                         ┌─────────────────────────────┐
                         │     Cando Web / Electron  │
                         │                             │
                         │ Workspace / Task / Chat    │
                         │ Agent Activity / Diff      │
                         │ Approval / File Review     │
                         └──────────────┬──────────────┘
                                        │ HTTP / SSE
                                        ▼
                         ┌─────────────────────────────┐
                         │    Cando Java / Control   │
                         │          Plane              │
                         │                             │
                         │ Session / Task / Run        │
                         │ State / Event / Action      │
                         │ Permission / Workspace      │
                         │ History / Aggregation / SSE │
                         └──────────────┬──────────────┘
                                        │ RocketMQ
                                        ▼
                         ┌─────────────────────────────┐
                         │    Cando Python Runtime   │
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

### Java / Python Responsibilities

**Java Control Plane**

* Session / Task / Run lifecycle
* State machine and state transitions
* Event / Action persistence
* Permission and Human Approval
* Workspace and File Change management
* SSE / History / Activity Aggregation
* MQ message routing and runtime control
* Heartbeats and task watchdog

**Python Agent Runtime**

* Supervisor / Explorer / Fixer
* ReAct execution loop
* LLM interaction and context management
* Tool Registry / Tool Calling
* Workspace file operations
* ExecutionGate
* Working Memory
* Agent Heartbeat

In one sentence:

> **Java controls how the Agent runs, is recorded, and is controlled; Python controls how the Agent reasons and executes.**

---

## Core Domain Model

```text
Session
  └── Task
        └── Run
              ├── Action
              └── Event

Session ─── Workspace
```

Relationships:

```text
Session : Task       = 1 : N
Task    : Run        = 1 : N
Run     : Action     = 1 : N
Run     : Event      = 1 : N
Session : Workspace  = N : 1
```

### Core Concepts

* **Session**: A container for long-lived conversational context.
* **Task**: A concrete coding task submitted by the user.
* **Run**: One execution attempt of a Task.
* **Action**: A concrete Tool Action that can be controlled, approved, executed, and tracked independently.
* **Event**: A raw execution fact reported by the Agent Runtime.
* **Workspace**: The working directory where the Agent reads and modifies files.

The goal of this model is to separate the user's request from a specific execution attempt, while giving each Tool Action an explicit lifecycle and control boundary.

---

## Agent Execution Flow

A typical execution looks like this:

```text
User
 ↓
Java creates Task + Run
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
Sub Agent (when needed)
 ↓
...
 ↓
Finish
 ↓
Java persists the result
 ↓
SSE / History
 ↓
Frontend
```

### Long-running Tasks

Cando does not simply implement:

```text
HTTP → Agent → Response
```

Instead, execution is modeled as a persistent, recoverable, and controllable runtime:

```text
Task
 └── Run
      ├── State
      ├── Action
      ├── Event
      ├── Tool Execution
      └── Retry / Resume / Cancel
```

This allows an Agent to pause for human intervention, recover from failures, retry execution, resume a previous Run, and reconstruct its working process after a page refresh.

---

## Human-in-the-loop

Cando separates Tool execution permission from the Agent's local decision-making and lets the Java Control Plane participate in centralized runtime control.

```text
Tool Call
   ↓
Permission Evaluator
   ├── ALLOW ──────→ Auto approve
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
   └── DENY ───────→ Reject
```

### Permission Profiles

```text
READ_ONLY   Read and query only
WORKSPACE   Allow modifications inside the assigned Workspace
FULL_AUTO   Automatically execute supported operations
```

### Why ActionId?

In a Multi-Agent environment, a single Run may contain multiple Agents and multiple Tool Actions.

Approval therefore cannot simply be tied to "the current Run". It must be bound to a concrete `actionId`:

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

This prevents control ambiguity such as approving one action while another action is actually executed.

> The current permission system provides application-level Tool / Path approval. It is not an OS-level sandbox or container isolation mechanism.

---

## Multi-Agent

The current Multi-Agent architecture uses a simple role separation:

```text
                  Supervisor
                      │
              ┌───────┴───────┐
              ▼               ▼
          Explorer          Fixer
              │               │
         Exploration        Modification
          / Analysis         / Validation
```

### Supervisor

Responsible for task orchestration rather than directly handling every low-level operation.

Main responsibilities:

* Understand the user's task
* Decide whether delegation is needed
* Select Explorer / Fixer
* Aggregate sub-agent results
* Drive the overall execution forward

### Explorer

Responsible for:

* Exploring the Workspace
* Finding relevant code
* Analyzing structure and dependencies
* Returning a compressed analysis result

Explorer does not modify business files.

### Fixer

Responsible for:

* Locating modification points
* Making minimal necessary changes
* Performing validation when needed
* Returning the modification result and supporting evidence

The purpose of this separation is not to make the system "more Agentic" by simply adding more agents. The goal is to let different roles share the same Runtime, Workspace, Permission, and Action control infrastructure.

---

## Event → Activity → UI

Cando does not expose raw Python Runtime Events directly to the user.

Execution information is transformed into product-level semantics:

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

For example, the runtime may generate:

```text
TOOL_CALL
TOOL_RESULT
TOOL_CALL
TOOL_RESULT
TOOL_CALL
TOOL_RESULT
```

while the frontend presents a more user-oriented Coding Agent workflow:

```text
● Analyze project structure
  ├─ Read xxx
  ├─ Search xxx
  └─ Inspect xxx

● Delegate to Explorer
  └─ Explorer is analyzing the relevant code

● Modify code
  └─ Update xxx

● Verify result
```

### Realtime / History Consistency

Realtime execution and history restoration use the same product semantics:

```text
Realtime:
Python Event
    ↓
Java persistence
    ↓
Stream Assembler
    ↓
SSE
    ↓
Frontend

History:
MySQL Event
    ↓
Block Assembler
    ↓
Chat View Assembly
    ↓
Frontend
```

This prevents the realtime and historical views from becoming two completely different presentation systems.

---

## Workspace

Agent file operations are always performed inside a Workspace:

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

Current capabilities include:

* File and directory browsing
* File reading
* Search and pattern matching
* File creation / modification / deletion
* Diff viewing
* File Change tracking
* Workspace path isolation

> The current implementation primarily provides application-level path isolation and change tracking. Container-level / OS-level sandboxing is not implemented yet.

---

## Context / Memory

One of the central problems of a long-running Coding Agent is:

> **What information should be carried into the next step of the task?**

Cando provides context retrieval on the Java side, with Workspace-scoped historical task retrieval and reconstruction of actual USER / ASSISTANT messages for Agent context.

The Python runtime also maintains Working Memory for execution-time message management, context handling, and compression.

The goal is not to keep indefinitely accumulating history, but to provide the Agent with **more relevant and higher-value context** during long-running tasks.

---

## Quick Start

### Requirements

| Dependency   | Description                                        |
| ------------ | -------------------------------------------------- |
| Java         | 17                                                 |
| Maven        | Build and run the Java backend                     |
| Python       | 3.x                                                |
| Node.js      | Vue / Vite / Electron                              |
| MySQL        | `code_fix` database                                |
| Redis        | Cache / Working Memory / Context-related features  |
| RocketMQ     | Asynchronous communication between Java and Python |
| LLM Provider | DeepSeek / OpenAI-compatible providers             |
| Ollama       | Local model / Embedding scenarios (optional)       |

### Repository Structure

```text
Cando/
├── Cando_Java/      # Spring Boot Control Plane
├── Cando_PY/        # Python Agent Runtime
├── Cando_Web/       # Vue / Electron Frontend
├── Images/            # README images and demos
└── README.md
```

### 1. Start Infrastructure

Prepare MySQL, Redis, and RocketMQ, then update the Java and Python configuration for your local environment.

Java configuration:

```text
Cando_Java/src/main/resources/application.yaml
```

Python configuration:

```text
Cando_PY/.env.example
```

Copy the example environment file to `.env` and fill in the required values.

### 2. Start Java

```bash
cd Cando_Java
mvn spring-boot:run
```

The server port is configured through `server.port` in `application.yaml`.

### 3. Start the Python Agent Runtime

```bash
cd Cando_PY
python -m venv .venv

# Windows
.venv\Scripts\activate

# Linux / macOS
# source .venv/bin/activate

pip install -r requirements.txt
python App/main.py
```

### 4. Start the Frontend

```bash
cd Cando_Web
npm install
npm run dev
```

Use the project's existing Electron scripts for desktop development.

### Configuration Notes

Do not commit real API keys, passwords, tokens, private addresses, or other sensitive information to the repository.

---

## Project Structure

### Cando_Java

```text
src/main/java/com/xd/
├── controller/       # REST / SSE
├── service/          # Session / Task / Run / Event / Permission logic
├── runtime/          # State Machine / Permission Runtime
├── assembler/        # Event → Activity / Phase / Chat View
├── mq/               # RocketMQ Producer / Consumer / Handler
├── mapper/            # MyBatis Mapper
├── model/             # DO / DTO / VO / Enum / Context
├── scheduleder/      # Task Watchdog / Heartbeat
└── config/            # Web / MQ / Redis / Transaction configuration
```

### Cando_PY

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

### Cando_Web

```text
src/
├── electron/         # Electron desktop shell
├── api/              # HTTP / SSE API
├── stores/           # Pinia
├── views/            # Main UI / Task / Workspace
├── components/       # Agent Chat / Activity / Diff / Review
├── router/
├── types/
└── config/
```

---

## Tech Stack

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

## Current Status

### V2.7

The main goal of V2.7 was to evolve Cando from **Single-Agent → Multi-Agent** while allowing sub-agents to operate inside the same Runtime control system.

Current capabilities include:

* [x] Session / Task / Run lifecycle model
* [x] Task / Run state management
* [x] Event persistence and idempotent processing
* [x] Run State History
* [x] Retry / Resume / Cancel
* [x] Tool Registry + ReAct Runtime
* [x] Permission Profiles
* [x] Human Approval + ExecutionGate
* [x] actionId-based approval binding
* [x] Workspace file operations and path isolation
* [x] File Change / Diff
* [x] SSE realtime execution stream
* [x] History Restore
* [x] Event → Activity / Phase aggregation
* [x] Supervisor + Explorer + Fixer Multi-Agent
* [x] Context / Memory foundation
* [x] Working Memory
* [x] Heartbeat / Watchdog foundation

---

## Roadmap

Future versions focus more on **runtime reliability, observability, and execution capabilities** than on simply adding more Agents.

### V2.8

* [ ] Reliability and edge-case improvements
* [ ] Workspace / Multi-Agent collaboration refinement
* [ ] Context Selection / Memory tuning
* [ ] Docker Compose one-click infrastructure startup

### V2.9

* [ ] Verification Loop
* [ ] Token / Cost / Latency Metrics
* [ ] Agent Evaluation
* [ ] More complete execution quality feedback

### V3.0 / Exploring

* [ ] Sandbox / execution isolation
* [ ] A more complete Agent Runtime
* [ ] Dynamic Agent / Skill Registry
* [ ] More LLM providers and context strategies

> The roadmap represents current exploration directions and does not imply that every item will be implemented exactly as listed.

---

## Project Philosophy

Cando is not trying to prove that its Coding Agent is "better than Codex".

Instead, the project is an attempt to understand what happens after an Agent moves beyond "calling an LLM" and starts performing real software engineering tasks:

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
A Controllable Coding Agent Runtime
```

That is the core direction of Cando:

> **Not building a stronger Coding Agent from scratch, but exploring the engineering architecture behind a Coding Agent.**

---

## License

MIT License
