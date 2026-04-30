# Chroma 验证记录

## 验证时间

- 2026-04-30

## 验证范围

- Phase 15 全量验证
- `project-brain-workflow` skill 快速校验
- 主源码全量编译校验

## 实际执行

### 1. 主源码编译

使用仓库内已有 shaded jar 作为依赖类路径，对当前源码重新编译：

```powershell
javac --release 17 -encoding UTF-8 -cp target\project-brain-0.1.0.jar -d target\javac-check @target\javac-sources.txt
```

结果：

- 通过

说明：

- 本地环境仍然没有 `mvn` / `mvnw`，因此继续使用 `javac` 做源码级回归校验。

### 2. Skill 快速校验

执行：

```powershell
python -X utf8 C:\Users\22397\.codex\skills\.system\skill-creator\scripts\quick_validate.py F:\McpProjects\project_brain\docs\chroma\project-brain-workflow
```

结果：

- `Skill is valid!`

踩坑：

- 直接运行 `quick_validate.py` 会使用系统默认 `gbk` 解码，读取 UTF-8 的 `SKILL.md` 时会抛 `UnicodeDecodeError`。
- 解决方式是显式使用 `python -X utf8`。

### 3. Chroma 联调验证

临时验证程序：

- `target/ChromaValidationCheck.java`

执行方式：

```powershell
javac --release 17 -encoding UTF-8 -cp "target\javac-check;target\project-brain-0.1.0.jar" -d target\validation-check target\ChromaValidationCheck.java
java -cp "target\validation-check;target\javac-check;target\project-brain-0.1.0.jar" ChromaValidationCheck
```

验证程序会：

- 启动本地 mock Chroma 服务
- 启动本地 mock embedding 服务
- 初始化临时项目 `target/chroma-validation-project`
- 依次验证运行模式门控、过程沉淀、补录、精简、恢复、重建全链路

## 验证结果

- [x] `local-md` 模式可正常解析
- [x] embedding 缺失配置时自动退回 `local-md`
- [x] Chroma 不可达时自动退回 `local-md`
- [x] `hybrid-chroma` 健康时可正常写入事件和摘要
- [x] `.knowledge_local` 在关键调用后持续同步维护
- [x] `sync_to_chroma` 可补录历史本地沉淀
- [x] `compact_knowledge` 不会破坏主知识目录可读性
- [x] `restore_knowledge` 可恢复主知识目录
- [x] `restore_chroma` 可从 `.knowledge_local` 重建向量库
- [x] `rebuild_knowledge` 可恢复、重建并重新生成精简主知识目录

## 联调输出摘要

```text
[OK] Phase 15.1 - local-md 模式可正常解析
[OK] Phase 15.2 - embedding 缺失配置时自动退回 local-md
[OK] Phase 15.3 - Chroma 不可达时自动退回 local-md
[OK] Phase 15.4-15.5 - hybrid-chroma 可正常写入事件/摘要，且 .knowledge_local 在关键调用后持续同步
[OK] Phase 15.6 - sync_to_chroma 可补录历史本地沉淀
[OK] Phase 15.7 - compact_knowledge 不会破坏主知识目录可读性
[OK] Phase 15.8 - restore_knowledge 可恢复主知识目录
[OK] Phase 15.9 - restore_chroma 可从 .knowledge_local 重建向量库
[OK] Phase 15.10 - rebuild_knowledge 可恢复、重建并重新生成精简主知识目录
MOCK_CHROMA_DOCUMENTS=9
VALIDATION_PROJECT=F:\McpProjects\project_brain\target\chroma-validation-project
```

## 结论

- `Phase 15` 已完成，可以支撑把 `Phase 11-16` 全部收口。
- 当前实现已经具备 `local-md / hybrid-chroma` 双模式、强制 `.knowledge_local` 灾备、历史补录、主知识瘦身、知识恢复、向量重建与工作流 skill 约束。
- 后续如果要接真实服务器联调，只需要把 mock URL 换成真实 Chroma / embedding 地址，再补一轮端到端环境验证即可。
