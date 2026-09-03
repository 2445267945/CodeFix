from dataclasses import dataclass, field
from typing import Optional
import time


@dataclass
class RuntimeMetrics:
    """
    单个 Agent Run 的运行指标。

    注意：
    这里保存的是 Runtime 事实，不负责持久化。
    后续可以由 Java 或其他 Metrics Collector 持久化。
    """
    # Run
    started_at: Optional[int] = None
    finished_at: Optional[int] = None
    # Agent
    steps: int = 0
    llm_calls: int = 0
    tool_calls: int = 0
    # Tool
    tool_success: int = 0
    tool_failed: int = 0
    # Permission
    permission_ask: int = 0
    permission_allow: int = 0
    permission_deny: int = 0
    # Token
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0
    # Context
    compression_count: int = 0
    compression_input_tokens: int = 0
    compression_output_tokens: int = 0
    # Error
    error_count: int = 0

    # Helpers
    def start(self) -> None:
        self.started_at = int(time.time() * 1000)

    def finish(self) -> None:
        self.finished_at = int(time.time() * 1000)

    @property
    def duration_ms(self) -> int:
        if self.started_at is None or self.finished_at is None:
            return 0
        return max(0, self.finished_at - self.started_at)

    def record_step(self, step: int) -> None:
        self.steps = max(self.steps, step)

    def record_llm_call(self, usage: dict | None = None) -> None:
        self.llm_calls += 1
        if not usage:
            return
        prompt_tokens = self._to_int(usage.get("prompt_tokens"))
        completion_tokens = self._to_int(usage.get("completion_tokens"))
        total_tokens = self._to_int(usage.get("total_tokens"))
        self.prompt_tokens += prompt_tokens
        self.completion_tokens += completion_tokens
        # Provider 有 total 就使用 Provider 的真实值。
        # 没有则自行计算。
        if total_tokens > 0:
            self.total_tokens += total_tokens
        else:
            self.total_tokens += (prompt_tokens + completion_tokens)

    def record_tool_call(self, tool_result: object) -> None:
        self.tool_calls += 1
        if self.is_tool_success(tool_result):
            self.tool_success += 1
        else:
            self.tool_failed += 1


    @staticmethod
    def is_tool_success(tool_result: object) -> bool:
        """
        根据统一 Tool Result 判断 Tool 是否成功。

        当前约定：
        - 非 dict：视为正常工具返回
        - dict 无 error_type：视为成功
        - dict 存在 error_type：视为失败
        """
        if not isinstance(tool_result, dict):
            return True
        return not bool(tool_result.get("error_type"))


    def record_permission(self, decision: str) -> None:
        normalized = (decision or "").strip().upper()
        if normalized == "ASK":
            self.permission_ask += 1
        elif normalized == "ALLOW":
            self.permission_allow += 1
        elif normalized == "DENY":
            self.permission_deny += 1

    def record_compression(self, before_tokens: int, after_tokens: int) -> None:
        self.compression_count += 1
        self.compression_input_tokens += max(0, before_tokens)

        self.compression_output_tokens += max(0, after_tokens)

    def record_error(self) -> None:
        self.error_count += 1

    @staticmethod
    def _to_int(value) -> int:
        if value is None:
            return 0
        if isinstance(value, bool):
            return int(value)
        if isinstance(value, int):
            return value
        if isinstance(value, float):
            return int(value)
        try:
            return int(value)
        except (TypeError, ValueError):
            return 0

    def to_dict(self) -> dict:
        return {
            "startedAt": self.started_at,
            "finishedAt": self.finished_at,
            "durationMs": self.duration_ms,
            "steps": self.steps,
            "llmCalls": self.llm_calls,
            "toolCalls": self.tool_calls,
            "toolSuccess": self.tool_success,
            "toolFailed": self.tool_failed,
            "permissionAsk": self.permission_ask,
            "permissionAllow": self.permission_allow,
            "permissionDeny": self.permission_deny,
            "promptTokens": self.prompt_tokens,
            "completionTokens": self.completion_tokens,
            "totalTokens": self.total_tokens,
            "compressionCount": self.compression_count,
            "compressionInputTokens": (self.compression_input_tokens),
            "compressionOutputTokens": (self.compression_output_tokens),
            "errorCount": self.error_count,
        }