# 运维说明

## 维护节奏

服务器按低维护成本设计，日常管理工作尽量集中到每周一次的固定维护窗口。

每周检查清单：

1. 确认每日备份和每周备份是否成功。
2. 检查磁盘空间。
3. 查看崩溃报告和近期控制台错误。
4. 如有争议，检查 CoreProtect 等审计记录。
5. 应用已经测试过的配置变更。
6. 插件更新前先阅读更新日志，重要插件尽量先在测试环境验证。

## 部署原则

配置变更应遵循以下流程：

1. 在本地修改配置或文档。
2. 提交到 Git。
3. 推送到 GitHub 远程仓库。
4. 通过部署脚本或受控工作流同步到云服务器。
5. 部署前自动备份，部署后重启并检查日志。

正式服不建议每次 `git push` 后立刻自动重启。更稳妥的方式是手动触发部署，或在固定维护窗口部署。

## Git 管理边界

由 Git 管理：

1. 服务端配置模板。
2. 插件配置。
3. 数据包和资源包。
4. 权限配置。
5. 部署、备份和重启脚本。
6. 规则、世界规划和运维文档。

不由 Git 管理：

1. 世界存档。
2. 玩家数据。
3. 日志和崩溃报告。
4. 备份文件。
5. 数据库。
6. 密钥、Token、密码和 RCON 凭据。

## 备份原则

建议备份策略：

1. 每日自动备份一次。
2. 每周保留一份完整备份。
3. 部署、插件升级、世界重置前手动或自动创建额外备份。
4. 备份至少同步到服务器外部的位置，例如对象存储、另一台机器或网盘。
5. 每月至少测试一次恢复流程。

未测试过恢复的备份不能视为可靠备份。

## 重启与更新

常规更新流程：

1. 提前通知玩家。
2. 执行 `save-all`。
3. 停止服务器。
4. 创建备份。
5. 同步配置、插件或数据包变更。
6. 启动服务器。
7. 检查控制台日志、端口状态和基础功能。

服务端核心、Java 版本和重要插件升级应单独测试，不应和大量玩法配置变更混在同一次部署中。

## 每日自动备份与更新

服务器使用 `xicemc-maintenance.timer` 在每天 `15:50` 触发每日维护任务。
脚本会先等待并广播 `10` 分钟倒计时提醒，实际停服备份与更新仍在每天 `16:00` 左右开始。

每日维护流程：

1. 通过 RCON 调用 `XiceTextArranger` 的 `xicebroadcast` 命令，分别在维护前 `10` 分钟、`5` 分钟、`1` 分钟、`10` 秒和 `3` 秒向在线玩家广播重启提醒。
2. 关闭 `xicemc.service`。
3. 执行 `scripts/renew-certificates.sh`，当 HTTPS 证书有效期不足 `7` 天时自动续期。
4. 在停服状态下执行 `scripts/backup.sh`，备份运行目录中的世界、玩家数据、白名单、封禁列表、服务端配置和插件配置。
5. 执行 `scripts/prune-backups.sh` 清理过期备份。
6. 执行 `scripts/prune-audit-log.sh` 清理超过 `3` 天的审计日志。
7. 执行 `scripts/deploy.sh` 从 GitHub 拉取最新内容，并部署配置、Paper 核心和自制插件。
8. 重新启动 `xicemc.service`。

备份保留策略：

1. 普通每日备份保留 `3` 天。
2. 星期一生成的备份额外保留至第 `3` 周。
3. 星期一备份进入第 `4` 周后删除。

审计日志保留策略：

1. `audit_log` 记录默认保留 `3` 天。
2. 过期审计记录会被每日维护任务删除，容器取放记录中的细节字段会随记录一并清理。
3. Web 审计查询的时间选择范围限制在最近 `3` 天内。

HTTPS 证书续期策略：

1. 不使用 Certbot 独立自动续期 timer。
2. 每日维护停服后检查 `xicemc.site` 证书有效期。
3. 证书有效期不足 `7` 天时执行 `certbot renew`，并在续期后重载 Nginx。

备份文件位于：

```text
/opt/xicemc/backups
```

当前备份文件结构：

1. 备份文件名格式为 `xicemc-backup-YYYYMMDD-HHMMSS-wN.tar.gz`，其中 `wN` 是生成当天的星期编号。
2. 备份内容以运行目录 `/opt/xicemc/runtime` 为根目录打包，包内路径均为相对路径。
3. 已包含 `main`、`main_nether`、`main_the_end` 三个世界目录。
4. 已包含 `server.properties`、`bukkit.yml`、`spigot.yml`、`commands.yml`、`permissions.yml`、`config/`、`plugins/*/config.yml`、`whitelist.json`、`ops.json`、封禁列表、玩家缓存和玩家数据。
5. 已排除 `cache`、`libraries`、`versions`、`logs`、`crash-reports`、`paper.jar` 和 `plugins/spark`。
6. 当前备份仍会包含 `plugins/.paper-remapped` 这类 Paper 可再生成缓存。该目录不影响整包恢复，但后续可以考虑从备份中排除以减少体积。

备份恢复可行性：

1. 可以完成停服后的整包恢复：停止 `xicemc.service`，将当前运行目录移走或另存，再把目标备份解压回 `/opt/xicemc/runtime`，修正目录所有者为 `minecraft:minecraft`，最后启动服务。
2. 可以做谨慎的局部恢复，例如只恢复 `whitelist.json`、某个插件配置或某个世界目录；恢复世界目录时必须停服，避免 `session.lock` 和区块文件状态不一致。
3. 备份不包含 PostgreSQL 审计数据库，因此无法通过该备份恢复 Web 审计日志。若未来要恢复审计数据，需要额外增加数据库导出备份。
4. 备份不包含 Git 仓库、Paper 核心 jar 和可重新下载的依赖，因此恢复后仍应保留 `/opt/xicemc/repo` 并运行部署脚本校准服务端核心和插件 jar。

维护任务带有失败保护：只要任务已经停服，即使备份、清理或更新步骤失败，也会尽量重新启动 `xicemc.service`，避免服务器长时间保持关闭状态。

常用检查命令：

```bash
systemctl status xicemc-maintenance.timer
systemctl list-timers xicemc-maintenance.timer
journalctl -u xicemc-maintenance.service -n 200 --no-pager
```
