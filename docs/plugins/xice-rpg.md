# XiceRPG

XiceRPG 用于管理 RPG 模板世界、临时副本实例、自定义怪物和魔法装备交互。当前版本已经从“模板世界管理”扩展为魔塔副本的基础玩法层：模板世界用于编辑内容，魔塔密钥用于进入玩家专属临时副本，副本启动仪用于配置波次、奖励、准入条件和启动试炼。

## 指令

```text
/module create <名称>
/module enter <名称>
/module exit
/module delete <名称>
/module reload
/rpgmob spawn <rotten_guard|pus_bug|training_dummy> [数量]
```

1. `/module create <名称>` 会打开创建 UI。UI 内可调整新模板世界的边界距离、模板重生点的 X/Z 坐标、面向玩家展示的“副本名称”和“挣脱诅咒”允许死亡次数；Y 坐标使用配置中的虚空模板默认重生高度。点击确认后，插件会创建并登记新的模板世界。
2. `/module enter <名称>` 会加载对应模板世界，并将玩家传送到该模板世界的重生点。玩家从主服务器进入时，插件会记录进入前的位置。
3. `/module exit` 只在模板世界内生效，会将玩家传送回进入前的位置；如果没有记录，则传送回 `main` 世界出生点。
4. `/module delete <名称>` 会打开二次确认 UI。确认后，插件会将世界内玩家送回主服务器，卸载并删除对应模板世界目录。
5. `/module reload` 重新加载配置与模板登记数据，默认仅 OP 可用，也供 Web 权限页通过 RCON 重载使用。

模板世界目录使用 `xicerpg_module_` 前缀，模板登记信息保存在插件运行时目录的 `modules.yml` 中。删除模板世界时，插件只会删除已登记且带有该前缀的世界目录。

新建的模板世界使用虚空生成器，不生成地形、洞穴、装饰、结构或生物。创建完成后，插件只会在重生点正下方放置 1 个基岩方块作为落脚点，其余区域保持空气。

模板世界在创建和进入时会自动关闭自然怪物生成、日夜循环与天气循环，并将时间固定在正午。

## 魔塔密钥

“魔塔密钥”是以回响碎片为基底的自定义物品，拥有插件标记，不能作为普通回响碎片参与原版合成。玩家获得钻石后，会解锁魔塔密钥配方：

```text
铁锭 骨头 空气
线   钻石 烈焰棒
铁锭 骨头 空气
```

玩家手持魔塔密钥右键会打开魔塔副本页面。页面列出所有模板名以 `tower_` 开头的模板世界，但显示文本使用模板配置中的“副本名称”，例如 `tower_1` 可显示为“魔塔第1层”。

点击副本后，插件会复制对应模板世界，生成玩家专属的临时副本世界并传送玩家进入。进入流程会准备模板快照、复制世界、加载世界并预热目标区块；传送倒计时期间会显示粒子，玩家移动会取消进入。玩家在副本中死亡后会立刻自动复活，并按该副本的“挣脱诅咒”计数，超过允许死亡次数时会被强制送回主服务器。玩家离开该临时副本世界、下线或在副本中手持魔塔密钥右键返回主服务器时，临时副本世界会被卸载并删除，模板世界本体不会被修改。`tower_1` 和 `tower_2` 默认允许死亡 2 次，第 3 次死亡会被强制送出。

魔塔页面还提供祝福选择、副本介绍、怪物图鉴和离开确认。当前祝福包含“剑士的记忆”：进入副本后通过 `XiceMorePotionEffects` 施加状态，限制远程攻击并提高近战伤害。

## 副本启动仪

“副本启动仪”是模板世界内用于标记副本开场点和配置试炼内容的自定义方块。拥有物品后，在模板世界中放置并右键可打开配置 UI：

1. 设置当前模板的副本启动位置。
2. 编辑副本波次，每个波次可配置怪物类型、数量和休整等待时间。
3. 编辑通关奖励，奖励会保存为物品列表。
4. 配置准入条件，例如需要先完成指定模板。

玩家进入临时副本后，右键副本启动仪可以开始试炼。试炼启动后插件会按波次刷新怪物，并使用 BossBar 展示当前波次、剩余怪物和休整状态。通关后发放配置的奖励并记录完成状态；该完成状态可作为后续副本的进入条件。

## 自定义怪物

`/rpgmob spawn <类型> [数量]` 可生成当前已接入的自定义怪物：

1. `rotten_guard` / 朽败的卫兵：以隐形僵尸作为逻辑核心，负责碰撞、寻路、攻击和受伤；外观由多个 `ItemDisplay` 部件跟随渲染。
2. `pus_bug` / 脓包虫：以小型实体承载逻辑，拥有自定义外观和毒性表现，死亡或触发时会产生黏液粒子、击退和区域残留。
3. `training_dummy` / 测试木桩：用于测试伤害与标签表现，提供自定义外观、血量显示和交互菜单。

自定义怪物会显示悬浮血条。副本中还会应用伤害拆分与短暂无敌规则，避免多段展示实体或异常命中造成不符合预期的伤害结算。

## 自定义物品

当前接入 `XiceCustomItem` 的 RPG 物品：

```text
/xicecustomitem give xicerpg:magic_dust [玩家] [数量]
/xicecustomitem give xicerpg:magic_anvil [玩家] [数量]
/xicecustomitem give xicerpg:magic_grindstone [玩家] [数量]
/xicecustomitem give xicerpg:magic_tower_key [玩家] [数量]
/xicecustomitem give xicerpg:dungeon_starter [玩家] [数量]
```

“魔法粉末”是基础材料。“魔法砧”用于消耗魔法粉末给装备写入插件自定义附魔，存在失败计数和可选附魔列表；“魔法砂轮”用于移除插件自定义附魔并回收部分魔法粉末。两者都是自定义展示方块，放置后由插件保存位置和展示实体，破坏后按自定义物品掉落。

魔法砧、魔法砂轮和附魔金苹果配方会在玩家获得魔法粉末后解锁；附魔金苹果配方使用 8 个金苹果围绕 1 个魔法粉末，产出 8 个附魔金苹果。魔法砂轮使用普通砂轮和魔法粉末合成。自定义物品带有插件标记，不能作为普通基底物品参与原版合成。

附魔金苹果配方：

```text
金苹果 金苹果 金苹果
金苹果 魔法粉末 金苹果
金苹果 金苹果 金苹果
```

## 权限

`XiceRPG` 依赖 `XiceCustomItem`。安装 `XiceMorePotionEffects` 时，魔塔祝福和部分副本状态会调用其中的自定义效果；缺少该插件时相关额外效果会跳过。

`/module create`、`/module enter`、`/module delete` 和 `/rpgmob spawn` 不直接依赖 Bukkit 权限节点，而是使用运行时配置中的 `access` 授权，便于 Web 权限页管理：

```yaml
access:
  default-allowed-actions: []
  players:
    <uuid>:
      name: PlayerName
      actions:
        - module
        - mob
```

`/module exit` 在模板世界内始终可用，避免玩家因权限变化被困在模板世界中。

## 部署

源码目录：

```text
plugins/XiceRPG
```

Maven 构建产物：

```text
/opt/xicemc/runtime/plugins/XiceRPG.jar
```

运行时配置和数据：

```text
/opt/xicemc/runtime/plugins/XiceRPG/config.yml
/opt/xicemc/runtime/plugins/XiceRPG/modules.yml
/opt/xicemc/runtime/plugins/XiceRPG/magic-anvils.yml
/opt/xicemc/runtime/plugins/XiceRPG/magic-grindstones.yml
/opt/xicemc/runtime/plugins/XiceRPG/magic-anvil-enchants.yml
```

该插件依赖 `XiceCustomItem` 和 `XiceHUD`，软依赖 `XiceMorePotionEffects`。`XiceHUD` 用于托管副本波次 BossBar；`XiceMorePotionEffects` 用于魔塔祝福和部分副本状态。世界目录使用 `xicerpg_module_`、`xicerpg_snapshot_` 和 `xicerpg_instance_` 前缀分别保存模板世界、模板快照和临时副本实例。
