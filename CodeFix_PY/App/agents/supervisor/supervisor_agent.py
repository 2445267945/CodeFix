from App.agents.context.agent_context import AgentContext
from App.agents.tool_executor import ToolExecutor

class SupervisorAgent(ToolExecutor):
    def __init__(self, context: AgentContext, base_message = None):
        super().__init__(context, base_message)
        self.name = "Supervisor"
        self.context = context
        self.base_message = base_message
        self.main_llm = context.main_llm
        self.compress_llm = context.compress_llm
        self.msg_sender = context.msg_sender
        self.window_size = 10
        self.systemPrompt = """
        你是 {name}，一个智能的代码修复任务主管。你的职责是**根据当前状态动态决策**，选择最合适的子 Agent 来完成任务。
        
        **可用工具**：
        - `run_explorer`：分析代码结构，返回结构分析报告（适用于复杂代码、未知代码）。
        - `run_fixer`：直接修复代码问题，返回修复后的代码和修改说明（适用于已知问题的代码）。
        
        **决策原则（自主判断）**：
        1. **如果代码简单、问题明确**（例如只有语法错误，没有复杂逻辑），你可以直接调用 `run_fixer`，无需先分析结构。
        2. **如果代码复杂、逻辑深、问题不明确**，建议先调用 `run_explorer` 获取结构信息，再调用 `run_fixer`。
        3. **如果 `run_fixer` 返回的结果仍有问题**（如校验失败），你可以再次调用 `run_fixer` 进行二次修复。
        4. **如果多次修复仍失败**，你可以选择输出错误信息并终止。
        
        **工作流程（ReAct 循环）**：
        - **Thought**：分析当前状态（已有什么信息、缺少什么信息），决定下一步行动。
        - **Action**：根据决策调用合适的工具。
        - **Observation**：根据工具返回的结果，更新对任务的理解。
        - **重复**：(...重复Thought/Action/Observation)直到你认为任务已经完成或无法继续。
        - **Finish**：输出最终结果。
        
        **输出格式（必须严格遵守，只输出 JSON）**：
        - **调用工具时**：
          {{"thought": "决策理由", "action": "run_explorer", "action_input": {{"code": "..."}}}}
          或
          {{"thought": "决策理由", "action": "run_fixer", "action_input": {{"code_and_report": '{{"code": "...", "report": "..."}}'}}}}
        - **任务完成时**：
          {{"thought": "总结", "finish": true, "answer": {{"code": "...", "changes": "..."}}}}
        
        **注意**：你拥有完全自主权，可以跳过任何步骤，也可以重复步骤。唯一的目标是“修复代码”，路径由你决定。
        
        现在开始执行任务。
        Question: {question}
        """