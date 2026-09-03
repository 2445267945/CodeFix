from dataclasses import dataclass


@dataclass
class CompressionResult:
    compressed: False
    before_tokens: 0
    after_tokens: 0