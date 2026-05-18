# 贡献与治理

本仓库用于维护 XiceMCServer 的插件、Web 后台、部署脚本、配置模板和运维文档。当前项目由仓库所有者维护，外部贡献会按安全性、可维护性和是否符合服务器实际需求来决定是否接受。

## 分支与合并

1. `main` 是唯一受保护主分支。
2. 不直接向 `main` 推送功能或修复。
3. 所有变更应通过 pull request 合入。
4. Pull request 必须通过 GitHub Actions 构建检查和 CodeQL 扫描。
5. 进入 `main` 的提交必须可验证签名。
6. 禁止 force push 和删除 `main`。

## 提交要求

提交应尽量保持范围清晰，避免把无关格式化、生成文件、运行数据和功能改动混在一起。

推荐提交前至少执行与改动相关的检查：

```bash
go test ./...
mvn -B -f plugins/XiceAuditLog/pom.xml package
mvn -B -f plugins/XiceClaim/pom.xml package
mvn -B -f plugins/XiceCommandControl/pom.xml package
mvn -B -f plugins/XiceTextArranger/pom.xml package
```

## 数据边界

不要提交以下内容：

1. 密码、Token、SSH 私钥、RCON 密钥、数据库凭据。
2. 世界存档、日志、备份、数据库文件。
3. 玩家隐私数据、真实运行数据、未脱敏截图。
4. 服务器上由插件生成的运行时数据。

如果需要提供示例配置，请使用明显的占位值，不要使用真实玩家、真实 IP、真实 UUID 或真实密钥。

## 代码风格

优先遵循仓库现有结构和写法。新增功能应保持部署脚本、插件配置、Web 后台和文档之间的边界清晰。

涉及玩家权限、领地保护、服务器安全、数据库写入和公开 Web 页面时，应同时考虑误操作、权限绕过和隐私泄露风险。
