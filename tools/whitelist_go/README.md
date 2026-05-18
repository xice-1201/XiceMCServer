# XiceMCServer Go Web Portal

Web 端已经从旧 Python 标准库服务迁移到 Go。当前线上 systemd 服务 `xicemc-whitelist.service` 直接运行 Go 二进制：

```text
/opt/xicemc/bin/xicemc-web-go
```

## 技术选型

- Go `net/http`：服务端渲染和后台表单保持低复杂度。
- `html/template`：统一渲染公开首页、玩家后台和管理页面。
- `database/sql` + `github.com/lib/pq`：沿用现有 PostgreSQL 表结构。
- 原生 RCON 客户端：兼容白名单刷新、权限重载和封禁踢出流程。
- Nginx：继续作为公网入口和访问控制层。

## 已迁移功能

1. 公开首页 `/`。
2. 注册入口 `/register`。
3. 登录 `/login`、退出 `/logout`。
4. 白名单验证码消费、`whitelist.json` 写入、RCON `whitelist reload`。
5. 会话 Cookie `xicemc_session`。
6. 图标与资源包静态文件。
7. 登录后首页 `/home`。
8. 修改密码 `/password`。
9. 编辑简介 `/profile`。
10. 首页领地列表读取 `XiceClaim/claims.yml`。
11. 服务器状态 `/status`。
12. 玩家列表 `/players`、身份管理与密码重置。
13. 权限管理 `/permissions`。
14. 举报提交 `/report` 与举报受理 `/reports`。
15. 黑名单 `/blacklist`。
16. 操作查询 `/audit`。
17. 文档查看 `/docs` 与编辑 `/docs/edit`。

## 本地运行

```powershell
$env:WHITELIST_WEB_HOST = "127.0.0.1"
$env:WHITELIST_WEB_PORT = "18081"
$env:XICEMC_RCON_PASSWORD = "local-test"
go run .
```

访问：

```text
http://127.0.0.1:18081/
```

## 构建

```powershell
go build -o xicemc-web-go.exe .
```

Linux 服务器部署时由 `scripts/deploy.sh` 构建并安装：

```bash
go build -o /opt/xicemc/bin/xicemc-web-go /opt/xicemc/repo/tools/whitelist_go
```
