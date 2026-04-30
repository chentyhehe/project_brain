# Chroma 实施计划

## 使用说明

- 本文件是 `docs/chroma/chroma_spec.md` 的唯一实施清单。
- 后续所有实现、验证、回流都以本文件为准。
- 每完成一项就把对应复选框从 `[ ]` 改为 `[x]`。

## 完成标准

- 代码已落库，或接口/结构已经在源码中稳定落位。
- 相关文档已同步，可指导后续维护。
- 需要验证的项，已经有明确的验证记录。

## Phase 0 文档与边界冻结

- [x] 确认 `docs/chroma/chroma_spec.md` 为当前基线设计文档，不再存在冲突版本。
- [x] 创建并维护 `docs/chroma/chroma_plan.md`，作为唯一实施清单。
- [x] 在 `docs/chroma/chroma_spec.md` 中补充“实施以 `chroma_plan.md` 为准”的说明。
- [x] 明确首批实现范围：只做 `local-md` 与 `hybrid-chroma`，不做 `chroma-only`。
- [x] 明确首期不做完整模型上下文全量入库、复杂权限系统、多数据库适配层。

## Phase 1 主存储抽象改造

- [x] 梳理现有 `ContextLoader`、`FlowbackWriter`、`KnowledgeWriter` 的职责边界。
- [x] 设计主知识存储抽象接口，覆盖 `start_task`、`finish_task`、`list_modules` 的现有能力。
- [x] 设计灾备存储抽象接口，覆盖 `.knowledge_local` 快照、事件、上下文副本能力。
- [x] 设计向量存储抽象接口，覆盖健康检查、写入、查询、重建能力。
- [x] 新增统一运行时协调器设计，负责模式选择、降级、补录、恢复编排。
- [x] 确认命名、包结构、类职责，并写回设计文档。

## Phase 2 `.knowledge_local` 强制灾备层

- [x] 定义 `.knowledge_local` 目录结构。
- [x] 定义 `.knowledge_local` 的写入时机：`init_project`、`start_task`、`record_plan`、`record_progress`、`record_gotcha`、`finish_task`。
- [x] 实现 `.knowledge_local` 基础目录初始化逻辑。
- [x] 实现 `.knowledge` 到 `.knowledge_local` 的正式文件镜像逻辑。
- [x] 实现 `.knowledge_local/events/` 的过程事件写入逻辑。
- [x] 实现 `.knowledge_local/context/` 的上下文快照与上下文摘要写入结构。
- [x] 明确 `.knowledge_local` 默认不参与读取，只用于恢复、补录、重建。
- [x] 更新模板与说明文档，写明 `.knowledge_local` 的定位。

## Phase 3 运行模式与配置体系

- [x] 定义运行模式枚举：`local-md`、`hybrid-chroma`。
- [x] 定义 Chroma 配置项。
- [x] 定义 embedding 配置项。
- [x] 实现配置读取逻辑。
- [x] 实现缺省配置下自动回退 `local-md` 的规则。
- [x] 定义模式状态与降级原因枚举。
- [x] 约定工具返回中如何暴露 `store_mode`、`store_status`、`degrade_reason`。

## Phase 4 Chroma 与 Embedding 健康门控

- [x] 设计 Chroma 健康检查接口。
- [x] 设计 embedding 健康检查接口。
- [x] 实现启动期健康检查。
- [x] 实现运行期缓存健康检查。
- [x] 实现真实写入前的最终确认检查。
- [x] 实现“任一依赖失败立即降级 local-md”的逻辑。
- [x] 明确 `hybrid-chroma` 仅在 Chroma 与 embedding 同时健康时启用。
- [x] 补充异常与降级日志格式。

## Phase 5 Chroma 客户端与数据模型

- [x] 设计 Chroma 客户端封装类。
- [x] 设计 embedding 客户端封装类。
- [x] 定义 collection 划分：事件、摘要、上下文摘要、压缩记录。
- [x] 定义 Chroma metadata 结构。
- [x] 定义文档 ID 规则。
- [x] 定义 chunk 切分策略。
- [x] 定义向量入库失败时的重试或放弃策略。
- [x] 记录 Chroma / embedding HTTP 接口约定。

## Phase 6 Ingestion Manifest 与补录状态跟踪

- [x] 定义 `.knowledge/ingestion_manifest.json` 结构。
- [x] 定义 `.knowledge_local/ingestion_manifest.json` 结构。
- [x] 定义内容 hash 生成规则。
- [x] 定义 `ingested`、`compacted`、`chroma_doc_ids` 等字段语义。
- [x] 实现 manifest 读写逻辑。
- [x] 实现根据 manifest 判定“是否已入库”的逻辑。
- [x] 实现根据 manifest 判定“是否允许精简”的逻辑。

## Phase 7 新增过程沉淀工具

- [x] 设计 `record_plan` 工具 schema。
- [x] 设计 `record_progress` 工具 schema。
- [x] 设计 `record_gotcha` 工具 schema。
- [x] 实现 `record_plan` 工具。
- [x] 实现 `record_progress` 工具。
- [x] 实现 `record_gotcha` 工具。
- [x] 确保三者都同步写 `.knowledge_local`。
- [x] 在 `BrainServer.tools` 中注册新工具。
- [x] 更新 `BrainServer.instructions` 说明新流程。

## Phase 8 现有工具接入新运行时

- [x] 改造 `start_task`，接入运行时协调器。
- [x] 让 `start_task` 返回结构化状态信息。
- [x] 改造 `finish_task`，接入主存储、灾备层、向量层。
- [x] 保持 `list_modules` 兼容现有行为。
- [x] 保持 `get_file` 仍只读 `.knowledge`。
- [x] 明确 `init_project` 与新存储结构的关系。
- [x] 校验旧调用方在新返回结构下仍可兼容。

## Phase 9 自动补录 `sync_to_chroma`

- [x] 设计 `sync_to_chroma` 工具 schema。
- [x] 明确补录扫描来源：`.knowledge` 与 `.knowledge_local`。
- [x] 实现未入库内容发现逻辑。
- [x] 实现内容切 chunk、embedding、写 Chroma 的流程。
- [x] 实现成功后更新 manifest。
- [x] 实现从 `DEGRADED` 恢复到 `AVAILABLE` 时的自动补录触发策略。
- [x] 明确失败时不影响主流程，只记录结果并保留本地内容。

## Phase 10 自动瘦身 `compact_knowledge`

- [x] 设计 `compact_knowledge` 工具 schema。
- [x] 定义允许精简的文件类型与段落范围。
- [x] 定义禁止精简的文件类型与段落范围。
- [x] 实现“先备份到 `.knowledge_local`、再确认已入 Chroma、再精简”的安全门槛。
- [x] 实现精简后的 `archive_ref` / `backup_ref` / `summary` 写法。
- [x] 实现压缩记录写入本地事件与 manifest。
- [x] 校验精简后 `.knowledge` 仍可被 `get_file` 与 `start_task` 正常使用。

## Phase 11 恢复与重建工具

- [x] 设计 `restore_knowledge` 工具 schema。
- [x] 设计 `restore_chroma` 工具 schema。
- [x] 设计 `rebuild_knowledge` 工具 schema。
- [x] 实现从 `.knowledge_local` 恢复 `.knowledge`。
- [x] 实现从 `.knowledge_local` 重建 Chroma。
- [x] 实现从 `.knowledge_local` 与 Chroma 重建精简版 `.knowledge`。
- [x] 明确恢复流程中的覆盖规则、冲突策略和安全提示。

## Phase 12 模型上下文摘要策略

- [x] 明确哪些上下文不入库。
- [x] 明确哪些上下文只做结构化摘要后入库。
- [x] 明确哪些上下文允许写入 `.knowledge_local/context/`。
- [x] 定义 `task_context_summary` 数据结构。
- [x] 定义 `task_context_snapshot` 数据结构。
- [x] 首期仅接入上下文摘要，不做完整上下文全量入库。

## Phase 13 模板与项目协议同步

- [x] 更新根 `AGENTS.md` 模板，补充 `.knowledge_local` 灾备约束。
- [x] 更新 `tasks/README.md` 模板，说明主目录可能被精简、完整原始记录保存在 `.knowledge_local`。
- [x] 更新相关模块知识模板，补充 Chroma / backup / recovery 流程说明。
- [x] 校验新模板与当前源码能力一致，不出现“文档先于实现太多”的脱节。

## Phase 14 Skill 与工作流约束

- [x] 更新 `project-brain-workflow` skill 说明。
- [x] 明确 skill 中对 `start_task` 的优先调用要求。
- [x] 明确 skill 对 `store_mode` 与 `store_status` 的处理要求。
- [x] 明确 skill 在补录、恢复、重建场景下应调用哪些工具。
- [x] 保持 skill 只负责调用顺序约束，不承载底层存储细节。

## Phase 15 验证与回归

- [x] 验证纯 `local-md` 模式可正常工作。
- [x] 验证 embedding 缺失配置时自动退回 `local-md`。
- [x] 验证 Chroma 不可达时自动退回 `local-md`。
- [x] 验证 `hybrid-chroma` 健康时可正常写入事件和摘要。
- [x] 验证 `.knowledge_local` 在所有关键工具调用后都被同步维护。
- [x] 验证 `sync_to_chroma` 可补录历史本地沉淀。
- [x] 验证 `compact_knowledge` 不会破坏主知识目录可读性。
- [x] 验证 `restore_knowledge` 可恢复主知识目录。
- [x] 验证 `restore_chroma` 可重建向量库。
- [x] 验证 `rebuild_knowledge` 可生成新的精简主知识目录。

## Phase 16 知识回流与收尾

- [x] 每完成一个实现阶段，按项目协议调用 `finish_task` 回流稳定知识。
- [x] 将实际踩坑同步到受影响模块的 `AGENTS.md`。
- [x] 如实现过程改动了接口契约，同步更新相关 `SPEC.md` 或 `api.md`。
- [x] 在全部完成后，回看 `chroma_spec.md` 与本计划，补齐偏差说明。
- [x] 将本计划文件中所有已完成项打勾，并确认没有遗留未归档事项。
