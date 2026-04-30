# Chroma 模糊点记录

## 使用说明

- 本文件只记录在实现过程中仍然模糊、需要补充确认或需要在文档中显式落锤的问题。
- 已经确定结论的问题不删除，保留“当前结论”，便于后续追溯。
- 本轮实现收尾后没有新增未解决模糊点。

## 当前状态

### [已解决] 1. Embedding provider 的首期实现目标

- 当前结论：首版先以 `ollama` / openai-compatible URL 形式兼容 embedding 接口。
- 只要 `url + model` 可用并通过健康检查，就允许进入 `hybrid-chroma`。

### [已解决] 2. Chroma collection 是否需要环境前缀

- 当前结论：首版不额外加环境前缀。
- 如仓库存在 Git 分支隔离需求，优先通过 `namespace` 或未来的分支前缀扩展解决。

### [已解决] 3. `.knowledge_local` 的镜像粒度

- 当前结论：`.knowledge_local` 是完整灾备副本，不优先压缩体积。
- 恢复能力优先于磁盘节省。

### [已解决] 4. `compact_knowledge` 的触发阈值

- 当前结论：默认阈值 `1024KB`，同时支持通过参数覆盖。
- 验证与重建流程中允许使用 `force=true` 或 `threshold_kb=0` 做显式测试。

### [已解决] 5. `rebuild_knowledge` 的冲突策略

- 当前结论：`.knowledge_local` 是恢复时的事实源。
- Chroma 负责检索、补录、重建向量索引，不反向替代 `.knowledge_local` 的原始备份地位。

### [已解决] 6. 模型上下文的保存边界

- 当前结论：首期只保存结构化上下文摘要。
- 完整上下文默认不入 Chroma；必要时只写入 `.knowledge_local/context/` 作为灾备快照。

## 本轮补充

- 无新增未决项。
- 新踩坑已通过验证文档与知识回流记录，不再单独挂起。
