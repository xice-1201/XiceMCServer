# 备案审核期访问控制与备案页脚

## 当前临时策略

备案审核期间，Web 端仅允许服主当前公网 IP 访问：

```text
203.0.113.10
203.0.113.11
```

Nginx 配置位于：

```text
deploy/nginx/xicemc-web.conf
```

该限制只作用于 HTTP Web 入口，不影响 Minecraft Java 服务端口 `25565`。

## 当前访问入口

资源和 Web 入口仍保留 IP 访问：

```text
http://150.158.93.80/
http://150.158.93.80/resourcepacks/xiceclaim.zip
```

域名访问通道已经在 Nginx 中预留：

```text
http://xicemc.site/
http://www.xicemc.site/
```

备案通过前，即使域名已解析，也会受到同一份 IP 白名单限制。

## 备案页脚预留项

Web 端已经准备好 ICP 与公安联网备案页脚。备案号尚未下发时，会显示“备案号待下发”；备案号下发后，通过环境变量填入即可：

```env
XICEMC_PUBLIC_SITE_BASE_URL=http://150.158.93.80
XICEMC_PUBLIC_SITE_DOMAIN=xicemc.site
XICEMC_ICP_RECORD_NO=粤ICP备xxxxxxxx号
XICEMC_ICP_RECORD_URL=https://beian.miit.gov.cn/
XICEMC_PUBLIC_SECURITY_RECORD_NO=粤公网安备xxxxxxxxxxxxxx号
XICEMC_PUBLIC_SECURITY_RECORD_URL=https://beian.mps.gov.cn/#/query/webSearch?code=xxxxxxxxxxxxxx
```

备案通过后建议将 `XICEMC_PUBLIC_SITE_BASE_URL` 切换为正式域名，并将 `server/config/server.properties.template` 中的资源包地址同步切换到域名。

## 解除临时限制

备案通过且页脚配置完成后，可以从 `deploy/nginx/xicemc-web.conf` 中移除临时 `allow/deny` 白名单，重新加载 Nginx：

```bash
sudo nginx -t
sudo systemctl reload nginx
```
