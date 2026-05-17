# 服务器安全加固

本页记录 XiceMCServer 当前基础安全加固策略。

## SSH

1. 为运维创建本地私钥，并将公钥安装到服务器。
2. 创建独立运维用户 `xicemc-admin`，用于日常 SSH 登录和 `sudo` 运维。
3. 确认密钥登录可用后，禁用 SSH 密码登录。
4. `root` 保留密钥登录能力，但禁止使用密码直接登录。

## 服务暴露面

公网只应暴露：

1. `22/tcp`：SSH。
2. `80/tcp`：HTTP，用于跳转 HTTPS 和证书校验。
3. `443/tcp`：Web HTTPS。
4. `25565/tcp`：Minecraft。

不应对公网暴露：

1. PostgreSQL。
2. RCON。
3. Web 后端的本机端口 `8080`。

当前使用 `xicemc-rcon-firewall.service` 在本机 iptables 中限制 RCON：只允许 `127.0.0.1` 和 `::1` 访问 `25575/tcp`，拒绝其他来源。

## 私密文件权限

服务器私密配置目录：

```text
/opt/xicemc/secrets
```

建议权限：

1. 目录所有者为 `root:minecraft`。
2. 目录权限为 `750`。
3. 环境变量文件权限为 `640`。
4. Web 服务和 Minecraft 服务通过 `minecraft` 组读取必要配置。

## 当前限制

本加固不替代云控制台安全组配置。腾讯云侧仍需要只放行必要端口，并确保数据库、RCON、内部 Web 端口没有公网入站规则。
