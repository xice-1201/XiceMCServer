# XiceMCServer Go Web Portal

这是 Web 端从 Python 标准库服务迁移到 Go 的新实现。当前目标是分阶段替换 `tools/whitelist_web/app.py`，避免一次性切换导致后台功能缺失。

## 技术选型

- Go `net/http`：保持服务端渲染和小型后台的低复杂度。
- `html/template`：替代 Python 中手写 HTML 字符串，后续可继续拆分模板。
- `database/sql` + `github.com/lib/pq`：沿用现有 PostgreSQL 表结构。
- 原生 RCON 客户端：兼容现有白名单刷新流程。
- Nginx 仍作为公网入口和访问控制层。

## 当前已迁移

1. 公开首页 `/`。
2. 兼容注册入口 `/register`。
3. 登录 `/login`、退出 `/logout`。
4. 白名单验证码消费与 `whitelist.json` 写入。
5. RCON 执行 `whitelist reload`。
6. 会话 Cookie `xicemc_session`。
7. 图标与资源包静态文件。
8. 未登录访问 `/docs` 返回公开首页。

## 暂未迁移

以下页面已有路由占位，但不能替换线上 Python 服务：

1. `/status`
2. `/players`
3. `/permissions`
4. `/report`
5. `/reports`
6. `/blacklist`
7. `/audit`
8. `/docs/edit`
9. 个人简介和密码修改 POST 流程

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

Linux 服务器构建时输出二进制即可，不需要 Python 运行时：

```bash
go build -o /opt/xicemc/bin/xicemc-web-go /opt/xicemc/repo/tools/whitelist_go
```

## 切换原则

只有当所有现有后台页面迁移完成并通过对照验证后，才修改 `deploy/systemd/xicemc-whitelist.service` 的 `ExecStart` 指向 Go 二进制。
