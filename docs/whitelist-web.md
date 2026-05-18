# XiceMCServer Web 页面

Web 页面用于承载白名单注册、玩家登录、个人信息展示和审计查询。

当前服务器游戏版本：`Minecraft Java 版 1.21.11`。

Web 图标来自仓库内 `server/assets/xicemc-logo.png`，浏览器会通过 `/favicon.png` 和 `/favicon.ico` 读取。

## 页面结构

1. `/`：公开首页。未登录访客看到个人技术开发随记、目录、技术实现、插件动态、运维记录和更新日志；右上角提供“后台登录”和“注册入口”两个折叠弹层，两个弹层互斥打开。
2. `/register`：兼容入口，展示同一套公开首页与注册入口。玩家填写 Minecraft ID 和 5 分钟内有效的验证码后，Web 服务写入服务器 `whitelist.json`，再通过本机 RCON 执行 `whitelist reload` 使白名单立即生效。
3. `/home`：登录后的首页，以固定比例个人 ID 卡形式展示玩家皮肤、身份、个人简介、UUID、注册时间、累计游玩时间和上次登出地点，并提供编辑个人简介和修改密码入口。按钮下方展示该玩家拥有的领地和被授权的领地，数据来自 `XiceClaim` 运行时 `claims.yml`。编辑个人简介通过首页对话框完成。
4. `/profile`：个人简介保存接口。玩家在首页对话框中提交后，Web 会更新展示在个人 ID 卡上的简介，默认简介为“一名普通的Minecraft玩家”。
5. `/password`：修改密码页。玩家输入原密码和两次新密码后更新 Web 登录密码。
6. `/status`：服务器状态页面，使用颜色展示开服状态，并以当前值 / 最大值（百分比）展示在线玩家、磁盘空间和内存占用。备份记录仅展示备份时间和备份大小，同时保留日志 ERROR 情况。
7. `/docs`：服务器文档首页。仅登录玩家可见，内容来自运行时 Markdown 文档并渲染为 HTML；未登录访客访问时会返回公开首页。“管理员”和“服主”登录后可在右上角点击“编辑”进入 Markdown 编辑模式。
8. `/docs/edit`：服务器文档编辑页。仅“管理员”和“服主”可见，页面左侧为原始 Markdown，右侧为渲染预览，保存后写入运行时 Markdown 文件。
9. `/audit`：操作查询页，用于查询 `XiceAuditLog` 写入 PostgreSQL 的审计记录。“操作来源”支持输入时自动补全，可从白名单玩家和当前生效黑名单玩家中按玩家 ID 或 UUID 搜索。
10. `/report`：举报页面。玩家填写被举报者游戏 ID 和文字举报理由后提交举报；被举报者输入框支持按玩家 ID 或 UUID 自动补全，并在选择后填入玩家 ID。
11. `/reports`：举报受理页面。仅“管理员”和“服主”身份可见，可查看举报并选择“不予处理”或“封禁”。
12. `/players`：玩家列表页面。所有登录玩家可见，数据来自服务器白名单，展示玩家 ID、UUID、身份、注册时间和玩家 ID 卡弹窗。仅“服主”可以修改其他玩家身份，且不能修改自己的身份。
13. `/blacklist`：黑名单列表页面。所有登录玩家可见，可查看黑名单记录；仅“管理员”和“服主”可以手动添加黑名单或解除黑名单。手动添加黑名单时，玩家游戏 ID 输入框支持自动补全，封禁时间和单位并排展示，勾选“永久”后隐藏。
14. `/permissions`：权限管理页面，左侧菜单位于“玩家列表”下方。仅“管理员”和“服主”可见，可按指令筛选拥有对应权限的玩家，默认展示 `/creative`。添加权限时支持输入玩家 ID 或 UUID 并自动补全，保存后写入对应插件运行时配置并通过 RCON 重载。

## Web 后端结构

Web 后端已经全面迁移到 Go，旧 Python 页面服务已从仓库中移除。

1. `tools/whitelist_go/main.go`：服务启动、配置、会话、公开首页、注册登录、状态页和通用工具。
2. `tools/whitelist_go/admin.go`：玩家、权限、举报、黑名单、审计和文档编辑等后台业务逻辑。
3. `tools/whitelist_go/admin_templates.go`：后台管理页面模板。
4. `tools/whitelist_go/README.md`：Go Web 本地运行和构建说明。

线上 systemd 服务 `xicemc-whitelist.service` 仍沿用原服务名，但实际运行 `/opt/xicemc/bin/xicemc-web-go`。部署脚本 `scripts/deploy.sh` 会构建 Go 二进制并安装到该路径。

## 域名与反向代理

备案完成后的 Web 入口域名：

```text
https://xicemc.site/
https://www.xicemc.site/
```

当前备案完成前临时使用 HTTP IP 入口：

```text
http://150.158.93.80/
```

该入口由 Nginx 的默认 HTTP server 转发到 Web 后端，仅用于域名备案完成前的小范围测试。HTTPS 域名入口和 `443` 监听可在备案完成后再补齐。

DNS 解析：

1. `xicemc.site` A 记录指向 `150.158.93.80`。
2. `www.xicemc.site` A 记录指向 `150.158.93.80`。

当前服务器侧由 Nginx 监听 `80`，再反向代理到本机 Web 服务；备案完成后再启用 `443`：

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

`web_players.role` 用于记录玩家 Web 身份。默认身份为“玩家”；`ExamplePlayer` 的身份为“服主”；另有“管理员”身份。`web_players.profile_bio` 用于记录玩家个人简介，默认值为“一名普通的Minecraft玩家”。举报受理菜单仅对“管理员”和“服主”可见。
“玩家列表”对所有登录玩家可见，但身份修改仅限“服主”操作，且“服主”不能修改自己的身份。“黑名单列表”对所有登录玩家可见，但新增和解除黑名单仅对“管理员”和“服主”可见。

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
6. 登录与注册入口位于公开首页右上角，玩家可通过“注册入口”提交白名单验证码。

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
XICEMC_CLAIMS_PATH=/opt/xicemc/runtime/plugins/XiceClaim/claims.yml
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
