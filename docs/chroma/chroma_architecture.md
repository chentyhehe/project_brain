# Chroma 架构设计

## 目标

- 固化 `Phase 1` 的接口级设计。
- 为后续 Java 代码实现提供稳定命名、包结构和职责边界。

## 包结构建议

```text
src/main/java/com/brain/knowledge/
  store/
    PrimaryKnowledgeStore.java
    BackupKnowledgeStore.java
    VectorKnowledgeStore.java
    StoreMode.java
    StoreStatus.java
    DegradeReason.java
  runtime/
    KnowledgeRuntimeCoordinator.java
    RuntimeResolution.java
    StoreHealthChecker.java
  chroma/
    ChromaClient.java
    ChromaDocument.java
    ChromaMetadata.java
    ChromaQuery.java
  embedding/
    EmbeddingClient.java
    EmbeddingRequest.java
    EmbeddingResponse.java
  backup/
    KnowledgeLocalWriter.java
    BackupEvent.java
    ContextSnapshot.java
  ingest/
    IngestionService.java
    IngestionBatch.java
    IngestionManifest.java
    IngestionRecord.java
  compact/
    CompactionService.java
    CompactionRecord.java
  restore/
    RestoreService.java
    RestoreCommand.java
    RestoreResult.java
```

## 核心接口

### `PrimaryKnowledgeStore`

职责：

- 负责主知识目录 `.knowledge` 的日常读取与写入。
- 对外承接当前 `ContextLoader`、`FlowbackWriter` 的主要能力。

建议接口：

```java
public interface PrimaryKnowledgeStore {
    StartTaskContext loadStartContext(Path projectRoot, String taskDescription) throws IOException;
    List<ModuleInfo> listModules(Path projectRoot) throws IOException;
    String readKnowledgeFile(Path projectRoot, String filePath) throws IOException;
    FinishTaskResult finishTask(FinishTaskCommand command) throws IOException;
}
```

### `BackupKnowledgeStore`

职责：

- 负责 `.knowledge_local` 的镜像、事件、上下文快照。
- 默认不参与日常读取。

建议接口：

```java
public interface BackupKnowledgeStore {
    void ensureInitialized(Path projectRoot) throws IOException;
    void snapshotKnowledge(Path projectRoot) throws IOException;
    void appendEvent(BackupEvent event) throws IOException;
    void writeContextSnapshot(ContextSnapshot snapshot) throws IOException;
}
```

### `VectorKnowledgeStore`

职责：

- 负责 Chroma 健康状态、写入、查询、重建。
- 不直接决定是否启用，由运行时协调器调度。

建议接口：

```java
public interface VectorKnowledgeStore {
    StoreStatus status();
    DegradeReason lastDegradeReason();
    QueryResult query(QueryRequest request) throws IOException;
    IngestionResult ingest(IngestionBatch batch) throws IOException;
    RestoreResult rebuildFromBackup(Path projectRoot) throws IOException;
}
```

## 运行时协调器

### `KnowledgeRuntimeCoordinator`

职责：

1. 决定本次调用运行在 `local-md` 还是 `hybrid-chroma`
2. 决定当前状态是 `AVAILABLE` 还是 `DEGRADED`
3. 统一编排主知识目录、灾备目录、向量库
4. 在需要时触发补录、恢复和精简

建议接口：

```java
public interface KnowledgeRuntimeCoordinator {
    RuntimeResolution resolve(Path projectRoot);
}
```

### `RuntimeResolution`

职责：

- 返回本次调用的最终决议结果。

建议字段：

```java
public record RuntimeResolution(
        StoreMode storeMode,
        StoreStatus storeStatus,
        DegradeReason degradeReason,
        boolean chromaEnabled,
        boolean backupEnabled
) {}
```

## 枚举建议

### `StoreMode`

```java
public enum StoreMode {
    LOCAL_MD,
    HYBRID_CHROMA
}
```

### `StoreStatus`

```java
public enum StoreStatus {
    AVAILABLE,
    DEGRADED
}
```

### `DegradeReason`

```java
public enum DegradeReason {
    NONE,
    MISSING_CHROMA_CONFIG,
    MISSING_EMBEDDING_CONFIG,
    EMBEDDING_UNHEALTHY,
    CHROMA_UNHEALTHY,
    NETWORK_TIMEOUT
}
```

## 工具返回约定

所有 MCP 工具返回仍保持 `McpSchema.CallToolResult`，但统一补充两层运行时信息：

1. `_meta`

```json
{
  "store_mode": "local-md | hybrid-chroma",
  "store_status": "AVAILABLE | DEGRADED",
  "degrade_reason": "NONE | MISSING_CHROMA_CONFIG | MISSING_EMBEDDING_CONFIG | ...",
  "chroma_enabled": true,
  "backup_enabled": true
}
```

2. 文本尾部补充 `## store_runtime` 摘要，便于 skill 和人工直接读取。

## 健康检查与降级日志约定

- 启动时先执行一次 `runtimeCoordinator.resolve(...)`，完成轻量预热探测。
- 运行期默认缓存最近一次健康结果，缓存 TTL 当前实现为 60 秒。
- 真正写入 Chroma 前，应调用强制刷新版决议，例如 `resolveForWrite(...)`。
- 当前降级日志格式先统一写入 `.knowledge_local/events/*.md` 的 detail 字段：
  `isError=<bool>, storeMode=<mode>, storeStatus=<status>, degradeReason=<reason>`
- 后续如果引入正式 logger，沿用同一字段命名，不再重新发明格式。

## Chroma 数据模型

### collection 划分

- `pb_knowledge_events`
- `pb_knowledge_summaries`
- `pb_task_context_summaries`
- `pb_compaction_records`

### metadata 结构

建议字段与当前代码骨架保持一致：

```json
{
  "project_path": "F:\\McpProjects\\project_brain",
  "project_name": "project_brain",
  "module": "knowledge",
  "stage": "start|plan|progress|gotcha|finish|summary|context",
  "task_session_id": "uuid",
  "source_file": ".knowledge/tasks/2026-04-30-xxx.md",
  "backup_file": ".knowledge_local/tasks/2026-04-30-xxx.md",
  "content_hash": "sha256:...",
  "stable": true,
  "compacted": false,
  "created_at": "2026-04-30T10:00:00"
}
```

### 文档 ID 规则

- 统一格式：`<collection>__<task_session_id>__<stage>__<content_hash>`
- `content_hash` 去掉 `sha256:` 前缀
- 统一转小写
- 非 `[a-z0-9._-]` 字符统一替换为 `-`
- 缺失值写成 `na`

### chunk 切分策略

- 第一版按“单条沉淀记录”优先，不急着把短文本继续切碎。
- 对大文本按段落切分，保留段落顺序。
- 单 chunk 目标控制在便于 embedding 服务稳定处理的中等长度，避免超长一次性提交。
- chunk metadata 保留原始 `source_file`、`task_session_id`、`stage`，避免后续无法追溯。

### 入库失败策略

- 默认最多重试 1 次瞬时失败。
- 如果仍失败，当前条目标记为未入库，留待 `sync_to_chroma` 补录。
- 未成功入库前，不允许触发 `.knowledge` 精简。

### HTTP 接口约定

- Chroma 健康检查使用 `GET {chroma_url}/api/v2/heartbeat`。
- embedding 健康检查优先使用配置的 `PROJECT_BRAIN_EMBEDDING_URL` 直接探测。
- `ollama` provider 默认兼容 `/api/embed`。
- 其他 provider 先按“已给定最终 embeddings URL”处理，后续再扩展更细的 provider 适配。

## Ingestion Manifest

### 文件位置

- `.knowledge/ingestion_manifest.json`
- `.knowledge_local/ingestion_manifest.json`

### 结构

```json
{
  "version": 1,
  "updatedAt": "2026-04-30T10:00:00Z",
  "records": [
    {
      "sourceFile": ".knowledge/tasks/2026-04-30-xxx.md",
      "backupFile": ".knowledge_local/tasks/2026-04-30-xxx.md",
      "taskSessionId": "uuid",
      "stage": "finish",
      "contentHash": "sha256:...",
      "ingested": true,
      "compacted": false,
      "chromaDocIds": ["pb_knowledge_events__..."],
      "lastIngestedAt": "2026-04-30T10:00:00Z",
      "lastCompactedAt": null
    }
  ]
}
```

### 字段语义

- `ingested=true`：当前 `sourceFile + contentHash` 对应的原文已经成功入库 Chroma。
- `compacted=true`：当前 `.knowledge` 主文件中的对应内容已经执行过精简。
- `chromaDocIds`：该条记录实际写入的 Chroma 文档 ID 列表，支持单条或多 chunk。
- `contentHash`：基于 UTF-8 原文内容计算 `sha256`，用于判断同一路径内容是否已发生变化。

### 判定规则

- `isIngested`：manifest 中存在同 `sourceFile + contentHash` 的记录，且 `ingested=true`。
- `canCompact`：在 `isIngested` 基础上，`chromaDocIds` 非空。
- 如果 `sourceFile` 不变但 `contentHash` 变化，应视为一条新的待补录内容，而不是覆盖旧状态。

## 过程沉淀工具

### `record_plan`

- 必填：`plan_summary`、`steps`
- 选填：`modules_affected`、`project_path`
- 落盘：
  - `.knowledge_local/events/`
  - `.knowledge_local/context/`

### `record_progress`

- 必填：`summary`
- 选填：`details`、`modules_affected`、`project_path`
- 落盘：
  - `.knowledge_local/events/`

### `record_gotcha`

- 必填：`title`、`details`
- 选填：`modules_affected`、`project_path`
- 落盘：
  - `.knowledge_local/events/`

### 第一版约束

- 三个工具当前只写灾备层，不直接膨胀主 `.knowledge`。
- 过程事件先保存在 `.knowledge_local`，后续通过 manifest + `sync_to_chroma` 补录到 Chroma。
- `BrainServer` 统一注册这些工具，并保持自动灾备快照逻辑不变。

## 现有工具接入约定

- `start_task`、`finish_task`、`list_modules`、`get_file`、`init_project` 当前继续保留原有主文本语义。
- 运行时协调器先由 `BrainServer` 包装层统一接入，避免每个工具重复拼接状态字段。
- 所有工具返回统一补充：
  - `_meta.store_mode`
  - `_meta.store_status`
  - `_meta.degrade_reason`
  - `structuredContent` 中的同名字段
  - 文本尾部 `## store_runtime`
- `get_file` 仍只读 `.knowledge`，不改成读取 `.knowledge_local`。
- `init_project` 当前仍负责主知识目录初始化；`.knowledge_local` 的同步由初始化后的镜像逻辑补齐。
- 旧调用方如果只读取原有文本内容，仍然可用；新增状态信息仅以追加方式暴露。

## `sync_to_chroma` 第一版

### 工具 schema

- `project_path`：可选项目根目录
- `source_paths`：可选相对或绝对路径列表；为空时执行全量默认扫描

### 默认扫描范围

- `.knowledge/tasks/*.md`
- `.knowledge_local/tasks/*.md`
- `.knowledge_local/events/*.md`
- `.knowledge_local/context/*.md`

### 执行流程

1. `resolveForWrite(...)` 强制确认当前是否允许进入 `hybrid-chroma`
2. 读取主/灾备 manifest
3. 过滤已入库内容
4. 按段落切 chunk
5. 调用 embedding 接口
6. 写入 Chroma
7. 更新 `.knowledge/ingestion_manifest.json` 与 `.knowledge_local/ingestion_manifest.json`

### 自动触发

- `BrainServer` 维护上一轮 `RuntimeResolution`
- 当某项目从 `DEGRADED` 恢复到 `AVAILABLE` 时，自动尝试一次 `sync_to_chroma`
- 自动补录失败不影响主工具调用，只写灾备事件记录

### 当前边界

- 第一版以补录 `tasks/events/context` 为主，暂不主动补录模块规则文件
- Chroma `query` 仍未实现，当前只完成 `upsert`
- 运行期尚未接入正式 logger，补录结果先写 `.knowledge_local/events`

## `compact_knowledge` 第一版

### 工具 schema

- `project_path`：可选项目根目录
- `source_paths`：可选相对或绝对路径列表；为空时默认扫描 `.knowledge/tasks/*.md`
- `threshold_kb`：可选阈值，默认 `1024`
- `force`：可选布尔值，允许忽略阈值限制

### 允许精简范围

- 仅允许 `.knowledge/tasks/*.md`

### 禁止精简范围

- 根 `AGENTS.md`
- `.knowledge/global/AGENTS.md`
- `.knowledge/modules/*/*.md`
- `.knowledge_local/**`

### 安全门槛

只有同时满足以下条件才允许精简：

1. 原文件位于允许范围内
2. `.knowledge_local` 中存在对应原文副本
3. manifest 中存在 `sourceFile + contentHash` 对应记录
4. 该记录满足 `ingested=true`
5. `chromaDocIds` 非空

### 精简后写法

第一版改写为：

```md
# <原标题>

## 历史详细记录
- 本段详细过程已归档至 Chroma
- archive_ref: task_session_id=..., chroma_doc_ids=[...]
- backup_ref: ./.knowledge_local/tasks/xxx.md
- summary: ...
```

### 压缩记录

- 第一版先写 `.knowledge_local/events/compact_knowledge-*.md`
- 同时更新主/灾备 manifest 的 `compacted=true` 与 `lastCompactedAt`
- 精简后的摘要文件会被 `sync_to_chroma` 识别并跳过，避免重复补录

### 当前验证

- 已在临时测试项目下完成最小化本地验收
- 验证结论：精简后的 `.knowledge/tasks/*.md` 仍保持 Markdown 可读文本结构，适合继续被 `get_file` / `start_task` 读取

## 与现有类的映射关系

### `ContextLoader`

- 保留现有实现逻辑
- 后续下沉为 `PrimaryKnowledgeStore` 的本地 Markdown 实现依赖

### `FlowbackWriter`

- 保留现有任务记录写入逻辑
- 后续拆分为：
- 主知识回流写入
- 灾备同步写入

### `KnowledgeWriter`

- 保持为初始化入口
- 后续补充 `.knowledge_local` 初始化能力

## 实现顺序建议

1. 先落接口和枚举
2. 再落 `local-md` 实现
3. 再接入 `.knowledge_local`
4. 最后接入 Chroma 与 embedding

## 当前结论

- `Phase 1` 先冻结接口命名和包结构，再开始写代码。
- 在代码层面暂不急着做复杂继承体系，优先保证接口清晰和实现可替换。
