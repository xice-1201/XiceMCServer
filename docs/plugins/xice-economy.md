# XiceEconomy

`XiceEconomy` 是 XiceMCServer 的自制 Paper 经济插件，用于提供基础虚拟货币账户、余额查询、玩家转账、管理员调账、流水记录和实体货币物品定义。

当前只启用一种名为“货币”的虚拟货币。它只是一串数据库中的整数数值，不使用任何游戏物品作为存储媒介，也不会因为背包、死亡、掉落或容器交互发生变化。

插件另定义了实体货币物品，也叫“货币”。该物品以绿宝石为基底，并带有 `XiceEconomy` 持久化标记。普通绿宝石不会自动视为实体货币。玩家可以通过“虚拟货币机”在实体货币和账户余额之间存取转换。

## 功能范围

1. 按玩家 UUID 创建经济账户，玩家名只作为显示和查询辅助。
2. 余额使用整数 `BIGINT` 保存，默认不允许负数。
3. 每次余额变化都会写入流水表，包括初始余额、玩家转账、管理员发放、扣除和设置余额。
4. 支持余额排行榜和最近流水查询。
5. 表结构包含 `currency_code` 字段，便于未来扩展多货币。
6. 定义一个绿宝石基底的实体货币物品，只能通过自定义物品发放命令或虚拟货币机取出获得。
7. 插件启动时会移除结果为实体货币物品的配方，避免实体货币通过合成获得；普通绿宝石和绿宝石块原版配方保持可用。
8. 提供“虚拟货币机”自定义方块，允许玩家存入实体货币增加账户余额，也允许按账户余额取出实体货币。
9. 虚拟货币机使用展示实体渲染外观，实际承载方块带有插件数据，放置、破坏、掉落和显示实体恢复均由插件处理。

## 命令

玩家命令：

```text
/money
/money top [数量]
/money log [数量]
/pay <玩家> <金额>
```

管理员命令：

```text
/money <玩家>
/money log <玩家> [数量]
/eco balance <玩家>
/eco give <玩家> <金额> [原因]
/eco take <玩家> <金额> [原因]
/eco set <玩家> <金额> [原因]
/eco top [数量]
/eco log <玩家> [数量]
/eco reload
```

实体货币发放命令：

```text
/xicecustomitem give xiceeconomy:physical_currency [玩家|选择器] [数量]
/xicecustomitem give xiceeconomy:virtual_currency_machine [玩家|选择器] [数量]
/economy reload
```

`/xiceeconomy` 是完整主命令，`/economy` 是别名。`/xicecustomitem give xiceeconomy:physical_currency` 发放的是实体货币物品，不会修改玩家数据库余额。

## 虚拟货币机

虚拟货币机是以自定义物品形式发放、以展示实体形式显示的机器方块。玩家放置后右键打开 GUI，可进入“存入”或“取出”页面：

1. 存入页面只接受实体货币物品。确认后删除输入槽内的实体货币，并按数量增加该玩家账户余额。
2. 取出页面会先显示当前账户余额。确认后扣除指定数量余额，并在输出槽生成对应数量的实体货币。
3. 单次取出最多 64 个实体货币，余额不足时不会扣款。
4. 机器坐标、朝向和展示实体会保存到 `virtual-currency-machines.yml`，插件重载或服务器重启后会恢复。
5. 配方为：

```text
铁锭  玻璃  铁锭
金锭  红石  金锭
石英  石英  石英
```

玩家获得下界石英后会自动解锁虚拟货币机的配方书配方；如果上线时背包中已有石英，也会补发解锁。

## 权限

```text
xiceeconomy.use
xiceeconomy.pay
xiceeconomy.admin
```

`xiceeconomy.use` 和 `xiceeconomy.pay` 默认开放给所有玩家，`xiceeconomy.admin` 默认只开放给 OP。

实体货币和虚拟货币机的发放由 `XiceCustomItem` 统一处理。`/xicecustomitem give` 不直接依赖 Bukkit 权限节点，而是使用 `XiceCustomItem/config.yml` 中的 `access` 授权，便于 Web 权限页管理：

```yaml
access:
  default-allowed-actions: []
  players:
    <uuid>:
      name: ExamplePlayer
      actions:
        - give
```

## 数据库

插件使用 PostgreSQL。默认配置复用当前服务器本机 PostgreSQL：

```yaml
storage:
  type: postgresql
  postgresql:
    host: 127.0.0.1
    port: 5432
    database: xicemc_audit
    username: xicemc_audit
    password-env: XICE_AUDIT_DB_PASSWORD
```

插件会自动建表：

```text
xice_economy_accounts
xice_economy_transactions
```

账户表以 `currency_code + player_uuid` 作为主键。流水表记录每次变动的类型、金额、变动后余额、操作人、交易对方和原因。

## 部署

源码目录：

```text
plugins/XiceEconomy
```

Maven 构建产物：

```text
/opt/xicemc/runtime/plugins/XiceEconomy.jar
```

运行时配置：

```text
/opt/xicemc/runtime/plugins/XiceEconomy/config.yml
/opt/xicemc/runtime/plugins/XiceEconomy/virtual-currency-machines.yml
```

部署脚本会自动发现 `plugins/XiceEconomy/pom.xml`，构建 jar 并安装到运行目录。
