# XiceClaim

`XiceClaim` 是 XiceMCServer 的自制 Paper 领地保护插件，用于在玩家建设前提供基础安全感。第一版目标不是做复杂领地经济，而是先让玩家能明确“这里是谁的建筑区域”，并阻止未授权操作。

## 当前功能

1. 玩家可用两个角点创建矩形领地。
2. 领地默认从配置的最低 Y 保护到最高 Y，当前为 `-64` 到 `320`。
3. 领地所有者可以授权或取消授权成员。
4. 非所有者、非成员不能在领地内破坏方块、放置方块、交互方块、打开容器、交互实体或使用桶。
5. 领地内方块受火焰、爆炸和活塞基础保护，展示框、盔甲架等实体也受基础保护。
6. 管理员权限 `xiceclaim.admin` 可绕过保护并管理所有领地。

## 玩家命令

```text
/claim pos1
/claim pos2
/claim create <名称>
/claim info
/claim list
/claim trust <玩家>
/claim untrust <玩家>
/claim delete <名称>
```

使用方式：

1. 站在建筑区域的一个角落，执行 `/claim pos1`。
2. 站到对角，执行 `/claim pos2`。
3. 执行 `/claim create home` 创建领地。
4. 站在领地内执行 `/claim trust 玩家名` 授权朋友共同建设。
5. 站在领地内执行 `/claim info` 查看当前领地信息。

领地名称只能包含英文、数字、下划线和短横线，长度 `1-24`。

## 默认限制

```yaml
limits:
  max-claims-per-player: 5
  max-area-blocks: 40000
  world-min-y: -64
  world-max-y: 320
```

`max-area-blocks` 只按 X/Z 平面面积计算。当前 `40000` 相当于一个 `200 x 200` 的最大单块领地。

## 数据存储

领地数据存储在插件运行时目录：

```text
/opt/xicemc/runtime/plugins/XiceClaim/claims.yml
```

该文件属于运行时数据，不提交到 Git。日常备份脚本会随插件运行时配置一起备份。

## 第一版限制

1. 暂不提供 Web 可视化地图。
2. 暂不提供领地转让、子领地、租售和经济系统。
3. 暂不提供告示牌或工具选区，当前通过命令设置角点。
4. 活塞保护采用保守策略：若活塞或被推动方块处于领地内，则取消推动。
5. 爆炸保护会移除爆炸列表中的领地方块，不区分爆炸来源。
