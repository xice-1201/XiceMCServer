# XiceMCServer Web 页面

Web 页面用于承载白名单注册、玩家登录、个人信息展示和审计查询。

## 页面结构

1. `/`：登录页。玩家使用 Minecraft Java 版 ID 登录，且该 ID 必须已经在白名单内。
2. `/register`：白名单注册页。玩家填写 Minecraft ID 和 5 分钟内有效的验证码后，Web 服务写入服务器 `whitelist.json`，再通过本机 RCON 执行 `whitelist reload` 使白名单立即生效。
3. `/home`：登录后的首页，展示玩家 UUID、注册时间、累计游玩时间和上次登出地点。
4. `/audit`：操作查询页，用于查询 `XiceAuditLog` 写入 PostgreSQL 的审计记录。
5. `/report`：举报页面。当前只提供入口，页面暂留空。
6. `/status`：服务器状态页面，展示开服状态、磁盘空间、内存占用、备份文件列表、日志 ERROR 情况和在线玩家信息。

## 域名与反向代理

当前 Web 入口域名：

```text
https://xicemc.site/
https://www.xicemc.site/
```

DNS 解析：

1. `xicemc.site` A 记录指向 `150.158.93.80`。
2. `www.xicemc.site` A 记录指向 `150.158.93.80`。

服务器侧由 Nginx 监听 `80/443`，再反向代理到本机 Web 服务：

```text
127.0.0.1:8080
```

仓库内 Nginx 配置模板：

```text
deploy/nginx/xicemc-web.conf
```

## 登录说明

当前登录只用于白名单玩家进入 Web 后台。登录依据是：

1. 玩家填写的 Minecraft ID 格式合法。
2. 该 ID 存在于服务器 `whitelist.json`。
3. 登录后浏览器保存签名 Cookie。

注册验证码由 `XiceTextArranger` 在玩家被白名单拒绝时生成：

1. 验证码有效期为 `5` 分钟。
2. 同一玩家在验证码过期前重复尝试进服，会继续得到同一个验证码。
3. 验证码在 Web 注册成功使用后会立刻失效。
4. 验证码文件默认位于 `/opt/xicemc/runtime/plugins/XiceTextArranger/verification-codes.tsv`。
5. Web 注册不会执行 `whitelist add` 命令，避免 Paper 拒绝异步执行该命令；注册流程直接写入白名单文件并刷新白名单。

## 审计查询保护

`/audit` 查询页的保护策略：

1. 默认不展示任何记录。
2. 点击查询后允许无条件查询最新记录。
3. 查询使用参数化 SQL。
4. 每页固定 `50` 条。
5. 使用游标分页，不使用大 `OFFSET`。
6. 不执行总数统计。
7. 查询只访问 PostgreSQL，不在 Minecraft 主线程执行。
8. 时间选择窗口限制在最近 `3` 天内，玩家可在该范围内自由选择更细粒度时间段。

## systemd 服务

仓库内服务文件：

```text
deploy/systemd/xicemc-whitelist.service
```

服务器安装位置：

```text
/etc/systemd/system/xicemc-whitelist.service
```

服务名沿用 `xicemc-whitelist`，但实际已经是 XiceMCServer Web Portal。

## 环境变量

白名单 Web 私密配置：

```text
/opt/xicemc/secrets/whitelist-web.env
```

Minecraft/数据库共享私密配置：

```text
/opt/xicemc/secrets/xicemc.env
```

需要的关键环境变量：

```env
WHITELIST_WEB_HOST=127.0.0.1
WHITELIST_WEB_PORT=8080
WHITELIST_WEB_SESSION_SECRET=change-this-secret
XICEMC_RCON_HOST=127.0.0.1
XICEMC_RCON_PORT=25575
XICEMC_RCON_PASSWORD=change-this-password
XICEMC_VERIFY_CODES_PATH=/opt/xicemc/runtime/plugins/XiceTextArranger/verification-codes.tsv
XICE_AUDIT_DB_PASSWORD=change-this-password
XICE_AUDIT_RETENTION_DAYS=3
XICEMC_BACKUP_DIR=/opt/xicemc/backups
XICEMC_SERVICE_NAME=xicemc.service
XICEMC_SERVER_LOG_PATH=/opt/xicemc/runtime/logs/latest.log
```

`XICE_AUDIT_DB_PASSWORD` 也会被 `XiceAuditLog` 插件读取。

## 常用命令

```bash
sudo systemctl start xicemc-whitelist
sudo systemctl stop xicemc-whitelist
sudo systemctl restart xicemc-whitelist
sudo systemctl status xicemc-whitelist
sudo journalctl -u xicemc-whitelist -n 100 --no-pager
```
