import re
from ..base_agent import BaseAgent
from App.agents.worker.explorer_agent import ExplorerAgent
from App.agents.worker.fixer_agent import FixerAgent

class SupervisorAgent(BaseAgent):
    def __init__(self, main_llm, compress_llm):
        super().__init__()
        self.name = "Supervisor"
        self.main_llm = main_llm
        self.compress_llm = compress_llm
        self.window_size = 10

    async def run(self, question: str):
        print("\n🧠 [主管] 收到任务，开始拆解...")
        # ----- 阶段一：结构侦查（Explorer） -----
        print("🔍 [主管] 派发任务给 Explorer（代码结构分析）...")
        explorer = ExplorerAgent(self.main_llm, self.compress_llm)
        analysis_report = await explorer.run(
            f"请分析以下 Java 代码的结构，提取包名、类名、方法、循环和字段信息，并总结潜在风险：\n\n{question}"
        )

        print(f"📊 [主管] Explorer 完成分析")

        # ----- 阶段二：精准修复（Fixer） -----
        print("🔧 [主管] 派发任务给 Fixer（代码修复）...")
        fixer = FixerAgent(self.main_llm, self.compress_llm)
        final_result = await fixer.run(
            f"【原始代码】\n{question}\n\n"
            f"【结构分析报告】\n{analysis_report}"
        )
        print(f"📊 [主管] Fixer 修复，完整代码: {final_result}\n")
        print(f"✅ [主管] Fixer 完成任务。\n")

        # ----- 返回最终结果 -----
        return final_result

    # 实现抽象方法
    async def step(self):
        pass

    def cleanup(self):
        pass