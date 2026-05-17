# XiceMCServer Web 页面

Web 页面用于承载白名单注册、玩家登录、个人信息展示和审计查询。

当前服务器游戏版本：`Minecraft Java 版 1.21.11`。

## 页面结构

1. `/`：登录与注册页。页面使用两张卡片分别展示 Web 登录和白名单注册。登录卡片提示初始 Web 密码 `123456`；注册卡片用于提交白名单验证码。
2. `/register`：兼容入口，展示同一套登录与注册页面。玩家填写 Minecraft ID 和 5 分钟内有效的验证码后，Web 服务写入服务器 `whitelist.json`，再通过本机 RCON 执行 `whitelist reload` 使白名单立即生效。
3. `/home`：登录后的首页，展示玩家皮肤、UUID、注册时间、累计游玩时间和上次登出地点，并提供修改密码入口。
4. `/password`：修改密码页。玩家输入原密码和两次新密码后更新 Web 登录密码。
5. `/audit`：操作查询页，用于查询 `XiceAuditLog` 写入 PostgreSQL 的审计记录。
6. `/report`：举报页面。玩家填写被举报者游戏 ID 和文字举报理由后提交举报。
7. `/reports`：举报受理页面。仅“管理员”和“服主”身份可见，可查看举报并选择“不予处理”或“封禁”。
8. `/players`：玩家管理页面。仅“管理员”和“服主”身份可见，可查看玩家列表、修改身份、重置密码。
9. `/blacklist`：黑名单管理页面。仅“管理员”和“服主”身份可见，可查看、手动添加和解除黑名单。
10. `/status`：服务器状态页面，展示开服状态、磁盘空间、内存占用、备份文件列表、日志 ERROR 情况和在线玩家信息。

## 域名与反向代理

当前 Web 入口域名：

```text
https://xicemc.site/
https://www.xicemc.site/
```

备案完成前可临时使用 HTTP IP 入口：

```text
http://150.158.93.80/
```

该入口由 Nginx 的默认 HTTP server 转发到 Web 后端，仅用于域名备案完成前的小范围测试。

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
3. 玩家填写的 Web 密码正确。
4. 登录后浏览器保存签名 Cookie。

所有成功注册白名单的玩家都会获得默认 Web 密码 `123456`。密码保存在 PostgreSQL 的 `web_players.password_hash` 字段中，当前按项目要求使用 MD5 摘要保存。
玩家登录后可以在首页点击“修改密码”，新密码长度不得低于 `6` 位，且只能包含英文、数字和下划线。

`web_players.role` 用于记录玩家 Web 身份。默认身份为“玩家”；`ExamplePlayer` 的身份为“服主”；另有“管理员”身份。举报受理菜单仅对“管理员”和“服主”可见。
“玩家管理”和“黑名单管理”同样仅对“管理员”和“服主”可见。玩家管理可以修改身份，并将任意玩家的 Web 密码重置为 `123456`。

## 举报与黑名单

玩家提交举报时，被举报者必须能关联到服务器白名单内玩家，否则会提示玩家不存在。

举报受理支持两种处置：

1. `不予处理`：记录处理结果，不改变玩家进服权限。
2. `封禁`：将被举报者加入 Web 黑名单，并同步到 `XiceTextArranger` 的黑名单文件。

封禁时间支持正整数和单位：

1. 小时。
2. 天。
3. 月，按 `30` 天计算。
4. 年，按 `365` 天计算。
5. 永久。

黑名单文件默认位于：

```text
/opt/xicemc/runtime/plugins/XiceTextArranger/blacklist.tsv
```

被加入黑名单的玩家即使仍在白名单内，也会在登录时被插件拒绝。

黑名单管理页面支持：

1. 查看当前和历史黑名单记录。
2. 查看封禁原因、到期时间和关联举报编号。
3. 解除当前生效的黑名单。
4. 手动添加黑名单。

举报受理页的“查最近操作”会跳转到 `/audit`，自动带入被举报者和最近 `3` 天时间范围。

注册验证码由 `XiceTextArranger` 在玩家被白名单拒绝时生成：

1. 验证码有效期为 `5` 分钟。
2. 同一玩家在验证码过期前重复尝试进服，会继续得到同一个验证码。
3. 验证码在 Web 注册成功使用后会立刻失效。
4. 验证码文件默认位于 `/opt/xicemc/runtime/plugins/XiceTextArranger/verification-codes.tsv`。
5. Web 注册不会执行 `whitelist add` 命令，避免 Paper 拒绝异步执行该命令；注册流程直接写入白名单文件并刷新白名单。
6. 登录与注册页采用双卡片布局，玩家可在同一页面完成登录或提交白名单验证码。

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
