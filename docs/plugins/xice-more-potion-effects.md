# XiceMorePotionEffects

`XiceMorePotionEffects` 是 XiceMCServer 的自制 Paper 插件，用于承载原版药水之外的自定义状态效果和自定义附魔。它当前主要服务于传送冷却、副本状态、RPG 装备效果和侧边栏状态显示。

## 功能范围

1. 自定义状态效果不写入原版药水系统，而是由插件记录到期时间，并通过 `XiceHUD` 在侧边栏显示名称和剩余秒数。
2. “跃迁抑制”会阻止玩家再次传送。插件会在部分传送完成后自动施加短时抑制，`XiceClaim` 的领地图腾传送也会通过软依赖调用该效果。
3. “强效封禁”用于让牛奶不清除自定义负面效果；玩家喝奶时只会消耗牛奶并保留效果。
4. “剑士的记忆”用于 RPG 副本祝福，持续期间禁止弓、弩、三叉戟等远程攻击，并提升近战伤害。
5. 插件提供若干自定义附魔：凋亡之刃、苦痛之刃、自生、饱腹活力、延伸之手和沉稳。
6. 同一件物品当前只允许携带一种插件自定义附魔，避免多个自定义效果叠加后难以平衡。

## 自定义附魔

```text
/morepotioneffects enchant <附魔> [等级]
```

可用附魔：

1. `withering_blade` / 凋亡之刃：用于剑类武器，近战命中时给目标施加凋零，最高 5 级。
2. `pain_blade` / 苦痛之刃：用于武器，目标已处于负面状态时提高伤害，最高 5 级。
3. `self_growing` / 自生：用于有耐久的物品，每秒自动修复少量耐久；不能与经验修补共存。
4. `satiety_vigor` / 饱腹活力：用于胸甲，饱食度足够时提高最大生命值，最高 2 级。
5. `extending_hand` / 延伸之手：用于手持物品，提高方块和实体交互距离，最高 3 级。
6. `steady` / 沉稳：用于护腿，提供击退免疫。

## 命令

```text
/morepotioneffects give <玩家|选择器> <效果> [时长]
/morepotioneffects clear <玩家|选择器> <效果>
/morepotioneffects check <玩家|选择器> <效果>
/morepotioneffects enchant <附魔> [等级]
/morepotioneffects reload
```

`/mpe` 和 `/xicemorepotioneffects` 是别名。效果时长支持纯秒数，也支持 `s`、`m`、`h` 后缀。

## 权限

```text
xicemorepotioneffects.admin
```

完整管理权限默认只开放给 OP。`give` 和 `enchant` 动作还会读取运行时配置中的 `access` 授权，便于 Web 权限页为具体玩家开放部分操作：

```yaml
access:
  default-allowed-actions: []
  players:
    <uuid>:
      name: ExamplePlayer
      actions:
        - give
        - enchant
```

## 部署

源码目录：

```text
plugins/XiceMorePotionEffects
```

Maven 构建产物：

```text
/opt/xicemc/runtime/plugins/XiceMorePotionEffects.jar
```

运行时配置：

```text
/opt/xicemc/runtime/plugins/XiceMorePotionEffects/config.yml
```

插件依赖 `XiceHUD` 提供侧边栏显示服务。`XiceClaim` 和 `XiceRPG` 会以软依赖方式检测并调用它；缺少该插件时，对应的额外状态效果会跳过，但不会阻止那些插件加载。
