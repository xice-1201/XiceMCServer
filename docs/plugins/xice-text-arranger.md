# XiceTextArranger

`XiceTextArranger` 是 XiceMCServer 的自制 Paper 插件，用于添加、重写或删除部分游戏系统消息。

## 当前功能

1. 重写白名单拒绝提示，引导玩家访问 Web 页面注册白名单。
2. 管理玩家加入服务器消息。
3. 管理玩家离开服务器消息。

## 白名单拒绝提示

当前提示内容：

```yaml
whitelist-denied:
  enabled: true
  message:
    - '&c你暂未加入 XiceMCServer 白名单。'
    - '&f请使用浏览器访问：'
    - '&bhttp://150.158.93.80/'
    - '&7填写 Minecraft ID 和邀请码后，再重新连接服务器。'
```

该功能只修改玩家被拒绝时看到的文本，不改变白名单判断逻辑，也不会绕过正版验证。

## 加入与退出消息

`join.mode` 和 `quit.mode` 支持：

1. `keep`：保留原版或其他插件设置的消息。
2. `delete`：删除该系统消息。
3. `rewrite`：用配置中的 `message` 重写。
4. `add`：保留原消息，并追加配置中的 `message`。

可用占位符：

1. `{player}`：玩家名。
2. `{displayName}`：玩家显示名。
