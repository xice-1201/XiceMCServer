# XiceCustomItem

`XiceCustomItem` 是自定义物品、自定义展示方块和自定义配方的公共能力插件。业务插件负责玩法逻辑，公共插件负责注册表、物品标记、展示实体、挖掘进度和配方注册/解锁等通用行为。

## 当前能力

1. `CustomItemService`
   - 注册自定义物品定义。
   - 根据定义创建 `ItemStack`。
   - 使用保留的 `PersistentDataContainer` key 识别自定义物品。
   - 统一设置 `ItemMeta#setItemModel`、显示名、描述和发光覆盖。
   - 统一注册、移除和解锁自定义配方。
   - 统一记录“玩家已获得过某物品/知识”的配方解锁状态。
   - 统一阻止已注册自定义物品进入普通原版合成配方；业务插件可显式允许某个配方使用自定义物品作为材料。

2. `CustomBlockService`
   - 注册自定义方块定义。
   - 统一保存承载方块、展示材质、展示模型、展示名、信息名、信息描述、展示尺寸、硬度、所需工具和最低工具等级。
   - 统一生成和清理基于 `ItemDisplay` 的展示方块。
   - 统一计算自定义方块正面方向、yaw/pitch 和生存模式挖掘进度。
   - 注册自定义多方块结构定义。
   - 统一保存多方块结构的部件 ID、相对坐标、朝向变体、承载方块数据和 `BlockDisplay` 展示方块数据。
   - 统一检查多方块结构是否可放置、设置承载方块、刷新多部件展示实体并清理旧展示实体。
   - `infoName` / `infoLore` 作为 Jade/玉类客户端信息展示的统一元数据源；当前服务端已集中保存，后续接客户端桥接时由这里读取。

3. `/xicecustomitem give <物品ID> [玩家|选择器] [数量]`
   - 统一发放所有已接入的自定义物品。
   - `物品ID` 支持完整 ID，例如 `xicerpg:magic_dust`；若短 ID 唯一，也支持 `magic_dust`。
   - 数量限制为 `1-64`。
   - 发放权限由 `XiceCustomItem/config.yml` 的 `access` 管理，并接入 Web 权限页。

4. `/xicecustomitem list`
   - 查看当前已注册的自定义物品。

5. `/xicecustomitem checkpack [资源包根目录]`
   - 检查已注册自定义物品的 `assets/<namespace>/items/<key>.json` 是否存在。

## 已接入内容

- `XiceClaim`
  - 物品：领地戒指、领地图腾、图腾核心、混沌传送核心。
  - 多方块结构：领地图腾。
  - 配方：统一由 `CustomItemService` 注册和解锁。
- `XiceEconomy`
  - 物品：实体货币、虚拟货币机。
  - 方块：虚拟货币机。
  - 配方：统一由 `CustomItemService` 注册和解锁。
- `XiceSimpleIndustry`
  - 物品：简易刷石机、简易村民繁殖机、亡灵粉尘、亡灵核心。
  - 方块：简易刷石机、简易村民繁殖机。
  - 配方：统一由 `CustomItemService` 注册和解锁。
- `XiceRPG`
  - 物品：魔法粉尘、魔法砧、魔法砂轮、魔塔密钥、副本启动仪。
  - 方块：魔法砧、魔法砂轮、副本启动仪。
  - 配方：统一由 `CustomItemService` 注册和解锁。

## 部署

源码目录：

```text
plugins/XiceCustomItem
```

Maven 构建产物：

```text
/opt/xicemc/runtime/plugins/XiceCustomItem.jar
```

运行时配置：

```text
/opt/xicemc/runtime/plugins/XiceCustomItem/config.yml
```

使用自定义物品、展示方块或统一配方能力的插件需要在 `plugin.yml` 中依赖 `XiceCustomItem`。
