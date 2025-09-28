# -*- coding: utf-8 -*-
"""
dev_agent.py - 本地代码代理（离线 LLM + 自动改/编/发）
用法：
  1) 在项目根目录创建 dev_inbox.md，写任务（下面有模板）
  2) 运行：  py -3 -m pip install requests
             py -3 dev_agent.py
  3) 成功后看 dev_outbox.md 的结果；JAR 会自动复制进你的 mods 目录
"""

from __future__ import annotations
import os, re, json, shutil, subprocess, time
from pathlib import Path
from typing import Optional, Tuple
import requests  # pip install requests

# ========= 路径与参数 =========
PROJECT_DIR = Path(__file__).resolve().parent          # 工程根目录
JAR_NAME    = "observer-0.1.0.jar"                     # 你的 mod 输出名
# 修改成你的 Forge 实际 mods 目录（注意路径里有空格，建议保留 r'' 原样字符串）
MODS_DIR    = Path(r"C:\Users\localuser\Downloads\PCL 正式版 2.10.8\.minecraft\versions\1.20.1-Forge_47.2.0\mods")

# Ollama（本地）推理服务
OLLAMA_CHAT_URL = "http://localhost:11434/api/chat"
MODEL_NAME = "qwen2.5-coder:14b"                 # 你已 pull 好的模型

# 收件箱 / 发件箱
INBOX_FILE  = PROJECT_DIR / "dev_inbox.md"
OUTBOX_FILE = PROJECT_DIR / "dev_outbox.md"

SYSTEM_PROMPT = (
    "你是资深的 Minecraft Forge 1.20.1 Java 开发工程师，任务是：\n"
    "根据给定【目标说明】与【当前文件内容】，生成**完整的新文件内容**。\n"
    "输出格式必须是一个**单独的 fenced code block**，形如：\n\n"
    "```java filename=相对项目根目录的路径\n"
    "<这里放整份文件的新内容>\n"
    "```\n\n"
    "要求：\n"
    "1) 只输出一个代码块，不要任何额外文字；\n"
    "2) 保持 import 与包名正确，能通过 gradle build；\n"
    "3) 如果需要新增/修改命令注册或事件订阅，务必兼容 Forge 1.20.1 + Java 17；\n"
    "4) 若目标涉及新增方法/命令（如 /heal、/feed、/healIfLow），要考虑 ServerLevel / ServerPlayer API；\n"
    "5) 不生成伪代码，不留 TODO；直接给可编译的完整文件。"
)

# ========= 工具函数 =========
def read_task(md_path: Path) -> Optional[Tuple[str, str]]:
    """
    解析 dev_inbox.md：两行最少即可
      file: src/main/java/...
      goal: 用一句话中文描述要改什么
    其余文字会拼成说明一起传给模型
    """
    if not md_path.exists():
        return None
    text = md_path.read_text(encoding="utf-8")

    m_file = re.search(r"(?mi)^file:\s*(.+)$", text)
    m_goal = re.search(r"(?mi)^goal:\s*(.+)$", text)
    if not m_file or not m_goal:
        return None

    rel_file = m_file.group(1).strip()
    goal     = m_goal.group(1).strip()
    return (rel_file, goal)

def load_current_file(rel_path: str) -> Tuple[Path, str]:
    abs_path = (PROJECT_DIR / rel_path).resolve()
    if not abs_path.exists():
        raise FileNotFoundError(f"目标文件不存在: {abs_path}")
    return abs_path, abs_path.read_text(encoding="utf-8")

def ask_llm(system_prompt: str, user_prompt: str) -> str:
    payload = {
        "model": MODEL_NAME,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user",   "content": user_prompt}
        ],
        "stream": False,
        # 为编码任务，调小温度更稳
        "options": {"temperature": 0.2}
    }
    r = requests.post(OLLAMA_CHAT_URL, json=payload, timeout=600)
    r.raise_for_status()
    data = r.json()
    # 适配 /api/chat 返回结构
    msg = data.get("message", {}) or data.get("choices", [{}])[0].get("message", {})
    return msg.get("content", "")

def extract_code_block_with_filename(text: str) -> Tuple[str, str]:
    """
    从模型输出中提取：
      ```lang filename=xxx
      <code>
      ```
    返回 (filename, code)
    """
    m = re.search(r"```[a-zA-Z0-9_\-+]*\s+filename=([^\n]+)\n(.*?)```", text, re.S)
    if not m:
        raise ValueError("LLM 输出不含合法的代码块（需要 ```lang filename=... ...``` ）")
    return m.group(1).strip(), m.group(2)

def write_file(rel_path: str, new_code: str):
    abs_path = (PROJECT_DIR / rel_path).resolve()
    abs_path.parent.mkdir(parents=True, exist_ok=True)
    abs_path.write_text(new_code, encoding="utf-8")

def run(cmd: str, cwd: Optional[Path] = None) -> int:
    print(f"[run] {cmd}")
    p = subprocess.run(cmd, cwd=cwd or PROJECT_DIR, shell=True)
    return p.returncode

def gradle_build() -> bool:
    # Windows：gradlew.bat；Linux/Mac：./gradlew
    gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
    code = run(f"{gradlew} clean build", cwd=PROJECT_DIR)
    return code == 0

def copy_jar_to_mods() -> None:
    build_libs = PROJECT_DIR / "build" / "libs"
    jar = build_libs / JAR_NAME
    if not jar.exists():
        # 容错：找第一个以 observer- 开头的 jar
        cand = sorted(build_libs.glob("observer-*.jar"))
        if not cand:
            raise FileNotFoundError("未找到构建产物 JAR")
        jar = cand[0]
    MODS_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, MODS_DIR / jar.name)

def log_outbox(msg: str):
    OUTBOX_FILE.write_text(msg, encoding="utf-8")

# ========= 主流程 =========
def main():
    print(f"[agent] 项目根：{PROJECT_DIR}")
    if not INBOX_FILE.exists():
        log_outbox("dev_inbox.md 不存在；请先创建任务。")
        print("[agent] dev_inbox.md 不存在")
        return

    task = read_task(INBOX_FILE)
    if not task:
        log_outbox("dev_inbox.md 需至少包含：\nfile: <相对路径>\ngoal: <一句话目标>")
        print("[agent] 任务格式不正确")
        return

    rel_file, goal = task
    print(f"[agent] 目标文件: {rel_file}")
    print(f"[agent] 任务目标: {goal}")

    try:
        abs_file, current_code = load_current_file(rel_file)
    except Exception as e:
        log_outbox(f"读取文件失败：{e}")
        print(f"[agent] 读取文件失败：{e}")
        return

    user_prompt = (
            f"【目标说明】\n{goal}\n\n"
            f"【当前文件（完整）】\n"
            f"路径: {rel_file}\n"
            "```java\n" + current_code + "\n```"
    )

    print("[agent] 正在调用本地模型，请稍候 ...")
    try:
        llm_out = ask_llm(SYSTEM_PROMPT, user_prompt)
    except Exception as e:
        log_outbox(f"调用本地模型失败：{e}")
        print(f"[agent] 模型失败：{e}")
        return

    try:
        out_filename, out_code = extract_code_block_with_filename(llm_out)
    except Exception as e:
        log_outbox(f"解析 LLM 输出失败：{e}\n\n原始输出：\n{llm_out[:1000]}")
        print(f"[agent] 解析失败：{e}")
        return

    # 如果模型输出的 filename 与任务文件不同，仍以模型给的为准（它可能新建/改另一个文件）
    print(f"[agent] 准备写入文件：{out_filename}")
    try:
        write_file(out_filename, out_code)
    except Exception as e:
        log_outbox(f"写文件失败：{e}")
        print(f"[agent] 写文件失败：{e}")
        return

    print("[agent] 开始 gradle build ...")
    ok = gradle_build()
    if not ok:
        log_outbox("构建失败，请打开 IDEA 看报错；若需要，重写 dev_inbox.md 再次运行。")
        print("[agent] 构建失败")
        return

    print("[agent] 构建成功，复制 JAR 到 mods ...")
    try:
        copy_jar_to_mods()
    except Exception as e:
        log_outbox(f"复制 JAR 失败：{e}")
        print(f"[agent] 复制 JAR 失败：{e}")
        return

    log_outbox(f"成功：已修改 {out_filename}，完成构建并复制到 {MODS_DIR}")
    print("[agent] 完成 ✅")

if __name__ == "__main__":
    main()
