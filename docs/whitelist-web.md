# 白名单注册页面

## 目标

白名单注册页面用于降低朋友服添加白名单的维护成本。玩家访问网页后填写 Minecraft Java 版 ID 和邀请码，服务端通过本机 RCON 执行：

```text
whitelist add <玩家ID>
```

## 安全边界

1. 网页必须使用邀请码。
2. RCON 只由本机 Web 服务访问，不对外公布。
3. RCON 密码和邀请码只保存在服务器 `/opt/xicemc/secrets/whitelist-web.env`。
4. 密码、邀请码和 RCON 密钥不提交到 Git。
5. Web 服务只负责添加白名单，不提供 OP、执行任意命令或管理功能。

## 服务文件

systemd 服务：

```text
deploy/systemd/xicemc-whitelist.service
```

服务器上安装为：

```text
/etc/systemd/system/xicemc-whitelist.service
```

## 环境变量

服务器私密配置文件：

```text
/opt/xicemc/secrets/whitelist-web.env
```

示例：

```env
WHITELIST_WEB_HOST=0.0.0.0
WHITELIST_WEB_PORT=80
WHITELIST_INVITE_CODE=change-this-code
XICEMC_RCON_HOST=127.0.0.1
XICEMC_RCON_PORT=25575
XICEMC_RCON_PASSWORD=change-this-password
```

## Minecraft 配置要求

`server.properties` 需要启用 RCON：

```properties
enable-rcon=true
rcon.port=25575
rcon.password=<服务器私密密码>
```

RCON 端口不要在腾讯云安全组中对公网开放。

## 常用命令

```bash
sudo systemctl start xicemc-whitelist
sudo systemctl stop xicemc-whitelist
sudo systemctl restart xicemc-whitelist
sudo systemctl status xicemc-whitelist
sudo journalctl -u xicemc-whitelist -n 100 --no-pager
```
