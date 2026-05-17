# 云服务器初始化说明

## 目标布局

云服务器上采用仓库与运行目录分离的布局：

```text
/opt/xicemc/
  repo/       # 完整 Git 仓库，用于保存运维文档、脚本、配置、自制插件源码等
  runtime/    # Minecraft 实际运行目录，只放服务端核心、运行配置、插件 jar、世界和日志
  backups/    # 本机备份目录
```

这样做是为了避免未来将自制插件源码、文档、测试文件等非运行内容直接部署进服务器运行目录。

## 当前服务器

当前测试服务器：

1. 云厂商：腾讯云轻量应用服务器。
2. 系统：OpenCloudOS 9.4。
3. 规格：2核 4GB 内存，60GB SSD。
4. Java：使用 `java-21-konajdk-headless`。
5. 运行用户：`minecraft`。

## 部署边界

会进入运行目录的内容：

1. Paper 核心：`runtime/paper.jar`。
2. 运行配置：`runtime/server.properties`。
3. EULA 文件：`runtime/eula.txt`。
4. 后续需要运行的插件 jar：`runtime/plugins/*.jar`。
5. 世界、日志和插件运行时数据。

不会直接进入运行目录的内容：

1. 文档。
2. 自制插件源码。
3. 构建脚本。
4. 测试文件。
5. Git 元数据。
6. 未经明确列入部署白名单的仓库内容。

## 服务管理

systemd 服务文件位于：

```text
deploy/systemd/xicemc.service
```

部署到服务器后，服务名为：

```bash
xicemc
```

常用命令：

```bash
sudo systemctl start xicemc
sudo systemctl stop xicemc
sudo systemctl restart xicemc
sudo systemctl status xicemc
sudo journalctl -u xicemc -n 100 --no-pager
```

## 首次启动

首次启动会生成 `runtime/eula.txt`，默认是：

```text
eula=false
```

必须阅读并同意 Minecraft EULA 后，手动改为：

```text
eula=true
```

然后再启动服务。
