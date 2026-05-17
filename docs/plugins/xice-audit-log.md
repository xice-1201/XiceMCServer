# XiceAuditLog

`XiceAuditLog` 是 XiceMCServer 的自制审计插件，用于记录关键玩家行为。第一版目标是先稳定回答“谁在什么时间动过这里”。

## 当前记录内容

1. 方块破坏：`BLOCK_BREAK`。
2. 方块放置：`BLOCK_PLACE`。
3. 容器放入：`CONTAINER_ADD`。
4. 容器取出：`CONTAINER_REMOVE`。
5. 玩家进入：`PLAYER_JOIN`。
6. 玩家退出：`PLAYER_QUIT`，`item_amount` 记录本次在线秒数。
7. 爆炸破坏方块：仍记录为 `BLOCK_BREAK`，操作来源记录为直接破坏者，例如 `TNT` 或 `CREEPER`，`details` 记录 `cause=explosion` 和爆炸来源。

当前版本不记录漏斗等非玩家自动搬运。

## 存储后端

当前版本直接使用 PostgreSQL，不再使用 SQLite。

默认连接本机数据库：

```yaml
storage:
  type: postgresql
  batch-size: 100
  queue-capacity: 20000
  postgresql:
    host: 127.0.0.1
    port: 5432
    database: xicemc_audit
    username: xicemc_audit
    password-env: XICE_AUDIT_DB_PASSWORD
```

数据库密码不写入 Git，由环境变量 `XICE_AUDIT_DB_PASSWORD` 提供。

## 性能策略

审计插件不应该拖慢 Minecraft 主线程。当前设计采用：

1. 主线程只收集事件并放入内存队列。
2. 数据库写入由独立线程完成。
3. 写入线程使用批量提交，默认每批最多 `100` 条。
4. PostgreSQL JDBC 启用 `reWriteBatchedInserts=true`。
5. 队列有上限，默认 `20000` 条。
6. 队列满时丢弃审计记录并限频告警，而不是阻塞服务器主线程。
7. 当前插件只写入，不在玩家操作路径上执行数据库查询。
8. Web 查询使用参数化 SQL、游标分页和固定页大小，不做大范围总数统计。

这意味着数据库短暂卡顿时，服务器玩法优先；代价是极端情况下可能丢失部分审计记录。

## PostgreSQL 是否会影响服务器性能

在当前 2 核 4GB 服务器上，本机 PostgreSQL 可以作为测试阶段方案，但要控制内存和连接数。插件只使用一个持久写入连接，正常情况下负担较轻。

未来开放服务器后，建议把 PostgreSQL 迁移到独立数据库实例或另一台机器，原因是：

1. Minecraft 主进程最怕 CPU 抢占和磁盘 I/O 抖动。
2. 审计数据会持续增长，需要独立磁盘、备份和清理策略。
3. 后续如果加入 Web 查询面板、复杂筛选、回滚预览，数据库查询压力会明显上升。

## 第一版限制

1. 容器审计按物品类型和数量做差量记录，不记录物品 NBT、耐久、附魔和自定义名称。
2. 容器记录依赖 Bukkit 容器事件前后快照，适合玩家手动取放。
3. 当前版本只记录，不提供游戏内查询和回滚。
4. 查询功能由 Web 后台提供，不在游戏内执行数据库查询。
