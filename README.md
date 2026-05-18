# XiceMCServer

当前版本：`0.0.40`

XiceMCServer 是一个 Java 版 Minecraft 服务器项目仓库，用于记录和维护服务器插件、Web 后台、部署脚本、配置模板与运维文档。

这个仓库公开用于展示项目实现和开发记录，但它不是通用开源发行版。真实运行数据、玩家隐私数据、密钥、数据库、备份和世界存档不属于本仓库管理范围。

## 项目定位

XiceMCServer 以朋友私下游玩为主要场景，围绕原版生存、建筑、轻量管理工具和自制插件能力逐步扩展。

项目当前重点是：

1. 维护一组适合服务器实际运行的自制 Paper 插件。
2. 提供用于白名单、玩家资料、权限和审计查询的 Go Web 后台。
3. 使用 Git 记录配置、代码和部署脚本变更，便于审查、回滚和自动化部署。
4. 将服务器运行数据与公开代码仓库隔离，避免泄露隐私或凭据。

## 仓库内容

```text
plugins/              自制 Minecraft 插件源码
tools/whitelist_go/   Go Web 后台源码
server/               服务端配置模板与资源
deploy/               Nginx、systemd 等部署配置
scripts/              部署、备份、维护脚本
docs/                 运维、备案、插件和规则文档
.github/              GitHub Actions、CodeQL、CODEOWNERS
```

## 自制插件

仓库目前包含以下自制插件：

| 插件 | 作用 |
| --- | --- |
| XiceAuditLog | 记录关键玩家行为和容器变化，用于审计与争议处理。 |
| XiceClaim | 提供领地创建、范围展示、权限控制和领地保护。 |
| XiceCommandControl | 管理指定玩家可使用的服务器指令权限。 |
| XiceTextArranger | 管理文本整理、验证码和黑名单相关数据。 |

插件的详细说明见 `docs/plugins/`。

## Web 后台

Web 后台位于 `tools/whitelist_go/`，用于支持服务器管理工作，包括白名单、玩家资料、权限管理、审计查询等页面。

Web 后台面向服务器实际运维，不是独立通用产品。运行时依赖的数据库、环境变量、管理员账号和玩家数据不会提交到仓库。

## Git 管理范围

适合提交到 Git 的内容：

1. 插件源码与资源文件。
2. Go Web 后台源码。
3. 配置模板和部署脚本。
4. Nginx、systemd 等基础设施配置。
5. 项目文档、运维说明和规则文本。
6. 不含真实隐私与凭据的示例配置。

不应提交到 Git 的内容：

1. `world/`、`world_nether/`、`world_the_end/` 等世界存档。
2. 日志、崩溃报告、数据库文件和备份包。
3. SSH 私钥、API 密钥、Token、RCON 密钥、数据库密码。
4. 玩家 UUID、IP、联系方式、聊天记录等隐私数据。
5. 插件在服务器运行时生成的大量数据。

## 安全与质量

`main` 分支启用了保护规则。进入 `main` 的变更需要通过 GitHub Actions 构建检查、CodeQL 扫描和签名提交要求。

仓库启用了 Dependabot、secret scanning 和 push protection，用于降低依赖漏洞和凭据误提交风险。

## 版权与使用

本仓库公开可见，但目前未授予开源许可证。公开展示不等于允许复制、分发、商用、重命名发布或用于第三方服务器。

版权与使用限制见 `COPYRIGHT.md`。

贡献、分支治理和数据边界见 `CONTRIBUTING.md`。

安全问题报告方式见 `SECURITY.md`。
