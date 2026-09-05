import asyncio
import platform
import shutil
from pathlib import Path
from typing import Sequence


class RipgrepManager:

    def __init__(self, timeout_seconds: int = 10):
        self.timeout_seconds = timeout_seconds
        self.project_root = self.find_project_root()
        self.command, self.provider = self.resolve_command()

    def find_project_root(self) -> Path:
        """
        从当前文件向上寻找项目根目录。
        项目根目录应包含 App 和 bin。
        """
        current = Path(__file__).resolve()
        for parent in [current.parent, *current.parents]:
            if ((parent / "App").is_dir() and (parent / "bin").is_dir()):
                return parent

        raise RuntimeError("无法定位项目根目录，请确认项目结构包含 App/ 和 bin/")

    def resolve_command(self) -> tuple[str, str]:
        bundled_rg = self.find_bundled_rg()
        if bundled_rg is not None:
            return str(bundled_rg), "bundled"
        system_rg = shutil.which("rg")
        if system_rg:
            return system_rg, "system"
        raise RuntimeError("当前环境未找到 ripgrep（rg）。请将对应平台的 rg 放入项目 bin/rg，或确保系统 PATH 中存在 rg。")

    def find_bundled_rg(self) -> Path | None:
        system = platform.system().lower()
        machine = platform.machine().lower()
        platform_dir = self.resolve_platform_dir(system, machine)
        if platform_dir is None:
            return None
        executable = "rg.exe" if system == "windows" else "rg"
        path = (self.project_root / "bin" / "rg" / platform_dir / executable)
        if path.is_file():
            return path
        return None

    @staticmethod
    def resolve_platform_dir(system: str, machine: str) -> str | None:
        if system == "windows":
            if machine in {"amd64", "x86_64"}:
                return "windows-x64"
            if machine in {"arm64", "aarch64"}:
                return "windows-arm64"
            return None

        if system == "linux":
            if machine in {"x86_64", "amd64"}:
                return "linux-x64"
            if machine in {"arm64", "aarch64"}:
                return "linux-arm64"
            return None

        if system == "darwin":
            if machine in {"x86_64", "amd64"}:
                return "darwin-x64"
            if machine in {"arm64", "aarch64"}:
                return "darwin-arm64"
            return None
        return None

    async def run(self, args: Sequence[str], search_root: Path | str, max_results: int | None = None) -> list[str]:
        """
        执行 rg。

        rg 返回码：
        0 -> 找到匹配
        1 -> 没有匹配
        其他 -> 执行失败

        max_results：
        - 限制最多读取多少行 stdout
        - 达到限制后主动终止 rg
        """
        search_root = Path(search_root).resolve()

        process = await asyncio.create_subprocess_exec(
            self.command,
            *args,
            str(search_root),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        lines = []
        try:
            while True:
                line = await process.stdout.readline()
                if not line:
                    break
                lines.append(line.decode("utf-8", errors="replace").rstrip("\r\n"))

                if max_results is not None and len(lines) >= max_results:
                    process.kill()
                    await process.communicate()
                    return lines

            stderr = await process.stderr.read()
            stderr_text = stderr.decode(
                "utf-8",
                errors="replace"
            ).strip()

            await process.wait()

            if process.returncode in (0, 1):
                return lines

            error_message = (f"rg 执行失败，exit_code={process.returncode}")
            if stderr_text:
                error_message += f", stderr={stderr_text}"

            raise RuntimeError(error_message)
        except asyncio.CancelledError:
            if process.returncode is None:
                process.kill()
            await process.communicate()
            raise

    def describe(self) -> dict:
        return {
            "ready": True,
            "provider": self.provider,
            "command": self.command,
            "platform": platform.system(),
            "machine": platform.machine(),
            "timeoutSeconds": self.timeout_seconds,
        }