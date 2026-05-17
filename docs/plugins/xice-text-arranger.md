# XiceTextArranger

`XiceTextArranger` 是 XiceMCServer 的自制 Paper 插件，用于添加、重写或删除部分游戏系统消息。

## 当前功能

1. 重写白名单拒绝提示，引导玩家访问 Web 页面注册白名单。
2. 玩家被白名单拒绝时生成 Web 注册验证码。
3. 在服务端可捕获认证失败拒绝消息时，重写正版验证失败提示。
4. 管理玩家加入服务器消息。
5. 管理玩家离开服务器消息。
6. 提供控制台广播命令，用于每日维护前的系统提醒。
7. 读取 Web 维护的黑名单文件，在玩家登录时拒绝黑名单玩家。

## 白名单拒绝提示

当前提示内容：

```yaml
whitelist-denied:
  enabled: true
  message:
    - '&c你暂未加入 XiceMCServer 白名单。'
    - '&f服务器版本：&eMinecraft Java 版 1.21.11'
    - '&f你的注册验证码：&e{verificationCode}'
    - '&7验证码将在 {verificationExpiresMinutes} 分钟后失效。'
    - '&f请使用浏览器访问：'
    - '&bhttps://xicemc.site/'
    - '&7填写 Minecraft ID 和验证码后，再重新连接服务器。'

verification-codes:
  enabled: true
  path: plugins/XiceTextArranger/verification-codes.tsv
  ttl-seconds: 300
  length: 6
  alphabet: '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'
```

该功能只修改玩家被拒绝时看到的文本，不改变白名单判断逻辑，也不会绕过正版验证。
验证码在未过期时重复获取不会变化，被 Web 注册成功使用后会立刻失效。

## 正版验证失败提示

当前提示内容：

```yaml
auth-denied:
  enabled: true
  match-messages:
    - 'failed to verify username'
    - 'failed to authenticate your connection'
    - 'invalid session'
  message:
    - '&c你的登录未通过 Minecraft 正版验证。'
    - '&f请使用正版启动器登录 Microsoft 账号后重新进入。'
    - '&7如果你刚刚登录过账号，请重启启动器后再试。'
```

该功能只在 Paper 插件能捕获到登录拒绝事件，并且原始拒绝文本匹配 `match-messages` 时生效。部分认证失败可能发生在插件事件之前，此时服务器仍会拒绝连接，但提示可能保持原始文本。

## 加入与退出消息

`join.mode` 和 `quit.mode` 支持：

1. `keep`：保留原版或其他插件设置的消息。
2. `delete`：删除该系统消息。
3. `rewrite`：用配置中的 `message` 重写。
4. `add`：保留原消息，并追加配置中的 `message`。

可用占位符：

1. `{player}`：玩家名。
2. `{displayName}`：玩家显示名。
3. `{verificationCode}`：白名单注册验证码。
4. `{verificationExpiresMinutes}`：验证码剩余有效分钟数。

## 系统广播

插件提供 `xicebroadcast` 命令，用于从控制台或 RCON 发送配置化系统消息：

```text
xicebroadcast restart-warning time=10分钟
```

当前每日维护脚本会在停服前通过该命令发送 `10` 分钟、`5` 分钟、`1` 分钟、`10` 秒和 `3` 秒提醒。

默认配置：

```yaml
broadcasts:
  restart-warning:
    enabled: true
    message:
      - '&6[系统] &f服务器将在 &e{time} &f后进行每日备份与更新。'
      - '&7请尽快移动到安全位置，并停止重要操作。'
```

命令权限为 `xicetextarranger.broadcast`，默认只允许 OP 或控制台执行。每日维护通过 RCON 以控制台身份调用。

## 黑名单

Web 举报受理选择“封禁”后，会同步生成黑名单文件：

```text
plugins/XiceTextArranger/blacklist.tsv
```

插件会在玩家登录时读取该文件。若玩家 UUID 或玩家名命中未过期黑名单，则直接拒绝登录，即使该玩家仍在白名单内。

默认提示：

```yaml
blacklist:
  enabled: true
  path: plugins/XiceTextArranger/blacklist.tsv
  message:
    - '&c你已被加入 XiceMCServer 黑名单。'
    - '&f原因：&e{reason}'
    - '&f到期时间：&e{expiresAt}'
```
