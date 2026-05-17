# XiceMCServer Web 页面

Web 页面用于承载白名单注册、玩家登录、个人信息展示和审计查询。

## 页面结构

1. `/`：登录页。玩家使用 Minecraft Java 版 ID 登录，且该 ID 必须已经在白名单内。
2. `/register`：白名单注册页。玩家填写 Minecraft ID 和邀请码后，Web 服务通过本机 RCON 执行 `whitelist add <玩家ID>`。
3. `/home`：登录后的首页，展示玩家 UUID、注册时间、累计游玩时间和上次登出地点。
4. `/audit`：操作查询页，用于查询 `XiceAuditLog` 写入 PostgreSQL 的审计记录。

## 登录说明

当前登录只用于白名单玩家进入 Web 后台。登录依据是：

1. 玩家填写的 Minecraft ID 格式合法。
2. 该 ID 存在于服务器 `whitelist.json`。
3. 登录后浏览器保存签名 Cookie。

该机制不是强身份认证。若未来公开服需要更严格的账户安全，应加入 Microsoft OAuth、一次性验证码或游戏内确认码。

## 审计查询保护

`/audit` 查询页的保护策略：

1. 默认不展示任何记录。
2. 必须至少设置一个筛选条件。
3. 查询使用参数化 SQL。
4. 每页默认 `50` 条，最多 `100` 条。
5. 使用游标分页，不使用大 `OFFSET`。
6. 不执行总数统计。
7. 查询只访问 PostgreSQL，不在 Minecraft 主线程执行。

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
WHITELIST_WEB_HOST=0.0.0.0
WHITELIST_WEB_PORT=80
WHITELIST_INVITE_CODE=change-this-code
WHITELIST_WEB_SESSION_SECRET=change-this-secret
XICEMC_RCON_HOST=127.0.0.1
XICEMC_RCON_PORT=25575
XICEMC_RCON_PASSWORD=change-this-password
XICE_AUDIT_DB_PASSWORD=change-this-password
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
