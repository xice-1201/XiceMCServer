# XiceHUD

`XiceHUD` 是 XiceMCServer 的通用 HUD 显示插件。它统一管理 action bar、sidebar scoreboard 和 BossBar 这些客户端显示通道，业务插件通过 `HudService` 提交显示内容，避免多个插件直接写同一个显示位置造成互相覆盖。

插件使用 Minecraft 原版 action bar、sidebar scoreboard 和 BossBar 显示内容，因此不需要客户端安装 Mod。经济余额的金币图标依赖服务器资源包字体，其它文本显示通道仍可使用普通原版文本。

## 功能范围

1. 周期性重发在线玩家 action bar，避免 action bar 自然淡出。
2. 当前启用经济模块，读取并缓存 `XiceEconomy` 的余额。
3. 玩家可以用 `/hud on` 和 `/hud off` 控制自己的 HUD 显示。
4. 管理员可以用 `/hud reload` 重新加载配置。
5. 统一托管 sidebar scoreboard 和 BossBar 的底层对象，业务插件只负责计算要显示的内容。

## 与 XiceEconomy 的关系

`XiceHUD` 声明 `XiceEconomy` 为软依赖。经济插件可用时，HUD 会在玩家进服、HUD 开启、插件重载和余额变动时调用经济插件公开的余额查询接口：

```text
requestFormattedBalanceValue(UUID playerUuid, String playerName, Consumer<String> success, Consumer<Exception> failure)
```

查询结果会缓存在 `XiceHUD` 内。定时任务只把缓存文本重新发送给玩家，不会每次都查询数据库。`XiceEconomy` 负责账户、余额和数据库查询，`XiceHUD` 只负责显示。这样以后 HUD 可以继续接入领地、状态效果、活动提示等模块。

## 与业务插件的关系

`XiceHUD` 通过 Bukkit `ServicesManager` 暴露 `HudService`。当前调用方：

1. `XiceMorePotionEffects`：提交自定义效果列表，由 `XiceHUD` 统一渲染 sidebar。
2. `XiceRPG`：提交副本波次 BossBar，由 `XiceHUD` 托管 BossBar 句柄。
3. `XiceSimpleIndustry`：提交亡灵潮 BossBar，由 `XiceHUD` 托管 BossBar 句柄。

业务插件不再直接创建 sidebar scoreboard 或 Bukkit BossBar；如果以后有新的 action bar 临时提示，也应通过 `HudService` 提交 owner、优先级和有效时间。

## 显示方式

当前经济 HUD 使用资源包字体图标。资源包把私有字符 `U+E000` 渲染为金币图标，HUD 配置中的 `{coin}` 会替换为该字符。

由于 action bar 本身是原版客户端固定在快捷栏上方居中显示的文本区域，插件不能真正指定像素坐标。当前通过 `offset` 前置空白把余额推到快捷栏偏右位置。这个方案不需要客户端 Mod，但不同分辨率和 GUI 缩放下位置会有轻微差异。

## 命令

```text
/hud on
/hud off
/hud reload
```

完整主命令为 `/xicehud`，`/hud` 是别名。

## 权限

```text
xicehud.use
xicehud.admin
```

`xicehud.use` 默认开放给所有玩家，`xicehud.admin` 默认只开放给 OP。

## 配置

运行时配置：

```text
/opt/xicemc/runtime/plugins/XiceHUD/config.yml
```

默认配置：

```yaml
hud:
  enabled-by-default: true
  update-interval-ticks: 40
  retry-after-error-ticks: 200

modules:
  economy:
    enabled: true
    plugin-name: XiceEconomy
    offset: '                        '
    format: '{offset}&f{coin} &e{balance}'
    unavailable: '&7货币: 暂不可用'
```

其中 `{coin}` 会替换为金币图标，`{offset}` 用于把 action bar 文本推向快捷栏右侧，`{balance}` 会替换为 `XiceEconomy` 返回的格式化余额数字，例如 `1,000`。

## 部署

源码目录：

```text
plugins/XiceHUD
```

Maven 构建产物：

```text
/opt/xicemc/runtime/plugins/XiceHUD.jar
```

部署脚本会自动发现 `plugins/XiceHUD/pom.xml`，构建 jar 并安装到运行目录。
