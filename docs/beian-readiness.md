# 备案通过后访问策略与备案页脚

## 当前策略

ICP备案已通过，Web 端公开首页恢复公网访问。后台页面仍由登录态、用户身份和页面权限控制。

Nginx 配置位于：

```text
deploy/nginx/xicemc-web.conf
```

## 当前访问入口

正式 Web 入口：

```text
https://xicemc.site/
https://www.xicemc.site/
```

资源包入口：

```text
https://xicemc.site/resourcepacks/xiceclaim.zip
```

IP 入口仍可由 Nginx default server 转发到 Web 服务，但公开展示和资源地址以域名为准。

## 备案页脚

Web 端页脚显示 ICP 与公安联网备案信息。当前 ICP 备案号：

```text
粤ICP备2026065077号
```

运行时环境变量可覆盖默认值：

```env
XICEMC_PUBLIC_SITE_BASE_URL=https://xicemc.site
XICEMC_PUBLIC_SITE_DOMAIN=xicemc.site
XICEMC_ICP_RECORD_NO=粤ICP备2026065077号
XICEMC_ICP_RECORD_URL=https://beian.miit.gov.cn/
XICEMC_PUBLIC_SECURITY_RECORD_NO=粤公网安备xxxxxxxxxxxxxx号
XICEMC_PUBLIC_SECURITY_RECORD_URL=https://beian.mps.gov.cn/#/query/webSearch?code=xxxxxxxxxxxxxx
```

## 公安联网备案

公安联网备案通过后，将公安备案号和查询链接填入运行时环境变量，并重启 Web 服务：

```bash
sudo systemctl restart xicemc-whitelist.service
```
