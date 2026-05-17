# XiceMCServer Web 页面

Web 页面用于承载白名单注册、玩家登录、个人信息展示和审计查询。

## 页面结构

1. `/`：登录页。玩家使用 Minecraft Java 版 ID 登录，且该 ID 必须已经在白名单内；启用 Microsoft 身份验证后，可改用 Microsoft 登录。
2. `/register`：白名单注册页。玩家填写 Minecraft ID 和邀请码后，Web 服务通过本机 RCON 执行 `whitelist add <玩家ID>`；启用 Microsoft 身份验证后，可用 Microsoft 验证出的正版 Java Profile 自动注册白名单。
3. `/home`：登录后的首页，展示玩家 UUID、注册时间、累计游玩时间和上次登出地点。
4. `/audit`：操作查询页，用于查询 `XiceAuditLog` 写入 PostgreSQL 的审计记录。
5. `/report`：举报页面。当前只提供入口，页面暂留空。
6. `/status`：服务器状态页面，展示开服状态、磁盘空间、内存占用、备份文件列表、日志 ERROR 情况和在线玩家信息。
7. `/auth/microsoft/start`：Microsoft 身份验证起点，默认未启用。
8. `/auth/microsoft/callback`：Microsoft OAuth 回调地址，域名审批和 HTTPS 准备好后配置到 Microsoft 应用注册中。

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

该机制不是强身份认证。若未来公开服需要更严格的账户安全，应启用 Microsoft OAuth 或游戏内确认码。

## Microsoft 身份验证

Microsoft 身份验证框架已经接入，但默认关闭。启用后流程如下：

1. 玩家点击“使用 Microsoft 登录”或“使用 Microsoft 注册白名单”。
2. Web 服务生成一次性 `state` 和 PKCE `code_verifier`，并跳转到 Microsoft 授权页。
3. Microsoft 回调 `/auth/microsoft/callback`。
4. Web 服务用授权码换取 Microsoft access token。
5. Web 服务继续换取 Xbox Live、XSTS 和 Minecraft access token。
6. Web 服务读取 Minecraft Java Profile，得到真实玩家名和 UUID。
7. 登录模式要求该 UUID 已在白名单内；注册模式会通过 RCON 执行 `whitelist add <玩家名>`。

当前保留邀请码限制：`MICROSOFT_REGISTER_INVITE_REQUIRED=true` 时，玩家必须先在注册页填写正确邀请码，才能进入 Microsoft 注册流程。

启用前置条件：

1. 准备域名和 HTTPS。
2. 在 Microsoft 应用注册中配置回调地址，例如 `https://你的域名/auth/microsoft/callback`。
3. 将 Microsoft 应用的 Client ID 和必要的 Client Secret 写入服务器私密环境文件。

相关环境变量：

```env
MICROSOFT_AUTH_ENABLED=false
MICROSOFT_CLIENT_ID=change-this-client-id
MICROSOFT_CLIENT_SECRET=change-this-client-secret
MICROSOFT_REDIRECT_URI=https://xicemc.site/auth/microsoft/callback
MICROSOFT_SCOPE=XboxLive.signin offline_access
MICROSOFT_REGISTER_INVITE_REQUIRED=true
```

`MICROSOFT_CLIENT_SECRET` 只允许写入 `/opt/xicemc/secrets/whitelist-web.env` 这类服务器私密文件，不允许提交到 Git。

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
WHITELIST_INVITE_CODE=change-this-code
WHITELIST_WEB_SESSION_SECRET=change-this-secret
XICEMC_RCON_HOST=127.0.0.1
XICEMC_RCON_PORT=25575
XICEMC_RCON_PASSWORD=change-this-password
XICE_AUDIT_DB_PASSWORD=change-this-password
XICE_AUDIT_RETENTION_DAYS=3
XICEMC_BACKUP_DIR=/opt/xicemc/backups
XICEMC_SERVICE_NAME=xicemc.service
XICEMC_SERVER_LOG_PATH=/opt/xicemc/runtime/logs/latest.log
MICROSOFT_AUTH_ENABLED=false
MICROSOFT_CLIENT_ID=change-this-client-id
MICROSOFT_CLIENT_SECRET=change-this-client-secret
MICROSOFT_REDIRECT_URI=https://xicemc.site/auth/microsoft/callback
MICROSOFT_REGISTER_INVITE_REQUIRED=true
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
