# XiceCommandControl

`XiceCommandControl` 是 XiceMCServer 的自制 Paper 插件，用于用配置文件控制玩家可使用的受控指令。

## 当前指令

1. `/survival`：将自己切换为生存模式。
2. `/creative`：将自己切换为创造模式。

这两个指令都只作用于执行者本人，不提供传送、给予物品、封禁、踢出、切换他人模式等 OP 功能。

## 当前权限策略

默认所有玩家可使用：

```yaml
default-allowed-commands:
  - survival
```

仅 `ExamplePlayer` 可额外使用：

```yaml
players:
  ExamplePlayer:
    commands:
      - creative
```

因此：

1. 所有玩家都可以执行 `/survival`。
2. 只有 `ExamplePlayer` 可以执行 `/creative`。
3. 插件不提供观察者模式和冒险模式切换指令。
4. 不需要给玩家 OP，也不需要授予原版 `/gamemode` 权限。

## 部署位置

源码目录：

```text
plugins/XiceCommandControl
```

构建产物部署到服务器：

```text
/opt/xicemc/runtime/plugins/XiceCommandControl.jar
```

运行时配置由插件首次启动生成：

```text
/opt/xicemc/runtime/plugins/XiceCommandControl/config.yml
```
