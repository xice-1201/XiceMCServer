# Paper 内核准备说明

## 当前锁定版本

服务器当前锁定 Paper：

1. Minecraft 版本：`1.21.11`
2. Paper 构建：`69`
3. 构建通道：`STABLE`
4. Java 版本：`21`
5. 核心文件：`paper-1.21.11-69.jar`

版本信息记录在 `server/core/paper.json`。服务端核心 jar 不提交到 Git，运行时下载到 `server/runtime/paper.jar`。

## 下载 Paper

Linux 云服务器：

```bash
./scripts/download-paper.sh
```

脚本会下载 Paper jar，并使用 `server/core/paper.json` 中记录的 SHA256 校验值验证文件。

## 启动测试服

Linux：

```bash
./scripts/start-server.sh
```

首次启动前，脚本会从模板生成：

1. `server/runtime/server.properties`
2. `server/runtime/eula.txt`

必须阅读并同意 Minecraft EULA 后，将 `server/runtime/eula.txt` 中的 `eula=false` 改为 `eula=true`，服务器才会继续启动。

## 第一阶段基础配置

`server/config/server.properties.template` 当前采用：

1. 正版验证：开启。
2. 白名单：开启。
3. 强制白名单：开启。
4. PVP：关闭。
5. 主世界名：`main`。
6. 难度：困难。
7. 最大玩家数：`6`。
8. 世界种子：`20021201`。
9. 出生点保护：`0`。
10. 命令方块：开启。
11. 普通飞行：开启。
12. 视距：`8`。
13. 模拟距离：`8`。
14. RCON：开启，仅用于白名单注册页等运维功能。

`server/config/paper-global.yml.template` 当前记录 Paper 全局特殊开关：

1. 允许活塞复制。
2. 允许永久破坏基岩等方块破坏漏洞。

`server/config/paper-world-defaults.yml.template` 当前记录 Paper 世界默认开关：

1. 开启 Anti-Xray 反矿透。

## 更新 Paper

更新 Paper 时不要直接覆盖 jar 后启动。建议流程：

1. 查询 Paper 官方 API 的目标版本和构建。
2. 更新 `server/core/paper.json`。
3. 运行下载脚本并确认 SHA256 校验通过。
4. 在测试环境启动验证。
5. 备份正式服。
6. 在维护窗口部署。
