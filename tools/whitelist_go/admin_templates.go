package main

var adminTemplatesHTML = `
{{define "players"}}{{template "pageStart" .}}{{template "playersContent" .}}{{template "pageEnd" .}}{{end}}
{{define "playersContent"}}
<h1>玩家列表</h1>
{{if .Message}}<p class="message">{{.Message}}</p>{{end}}
<section class="panel">
  <div class="table-wrap">
    <table>
      <thead><tr><th>玩家</th><th>UUID</th><th>身份</th><th>注册时间</th><th>简介</th><th>操作</th></tr></thead>
      <tbody>
        {{if .Players}}{{range .Players}}
        <tr>
          <td>{{.Name}}</td>
          <td class="mono">{{.UUID}}</td>
          <td><span class="identity-role {{roleClass .Role}}">{{.Role}}</span></td>
          <td>{{formatTimeMillis .RegisteredAt}}</td>
          <td>{{.ProfileBio}}</td>
          <td>
            {{if and (eq $.User.Role "服主") (not .IsSelf)}}
            <form method="post" action="/players/role" class="inline-form">
              <input type="hidden" name="player_uuid" value="{{.UUID}}">
              <select name="role">
                <option value="玩家" {{if eq .Role "玩家"}}selected{{end}}>玩家</option>
                <option value="管理员" {{if eq .Role "管理员"}}selected{{end}}>管理员</option>
                <option value="服主" {{if eq .Role "服主"}}selected{{end}}>服主</option>
              </select>
              <button type="submit">修改身份</button>
            </form>
            <form method="post" action="/players/reset-password" class="inline-form">
              <input type="hidden" name="player_uuid" value="{{.UUID}}">
              <button class="secondary" type="submit">重置邀请码</button>
            </form>
            {{else}}<span class="message">-</span>{{end}}
          </td>
        </tr>
        {{end}}{{else}}<tr><td colspan="6" class="message">暂无玩家</td></tr>{{end}}
      </tbody>
    </table>
  </div>
</section>
{{end}}

{{define "permissions"}}{{template "pageStart" .}}{{template "permissionsContent" .}}{{template "pageEnd" .}}{{end}}
{{define "permissionsContent"}}
<h1>权限管理</h1>
{{if .Message}}<p class="message">{{.Message}}</p>{{end}}
<section class="panel">
  <form method="get" action="/permissions">
    <label for="command">指令</label>
    <select id="command" name="command">
      {{range .Permissions.Commands}}<option value="{{.ID}}" {{if .Selected}}selected{{end}}>{{.Label}}</option>{{end}}
    </select>
    <div class="actions"><button type="submit">查看</button></div>
  </form>
</section>
<section class="panel">
  <h2>添加权限</h2>
  <form method="post" action="/permissions/add">
    <input type="hidden" name="command" value="{{.Permissions.SelectedCommand}}">
    <label for="permission-player">玩家 ID 或 UUID</label>
    <input id="permission-player" name="player" required minlength="3" maxlength="36" list="player-suggestion-list" autocomplete="off">
    <div class="actions"><button type="submit">添加 {{.Permissions.CommandLabel}} 权限</button></div>
  </form>
</section>
<section class="panel">
  <h2>{{.Permissions.CommandLabel}} 权限玩家</h2>
  <div class="table-wrap">
    <table>
      <thead><tr><th>玩家名称</th><th>UUID</th><th>身份</th><th>操作</th></tr></thead>
      <tbody>
        {{if .Permissions.Rows}}{{range .Permissions.Rows}}
        <tr>
          <td>{{.Name}}</td><td class="mono">{{.UUID}}</td><td><span class="identity-role {{roleClass .Role}}">{{.Role}}</span></td>
          <td>
            <form method="post" action="/permissions/remove" class="inline-form">
              <input type="hidden" name="command" value="{{$.Permissions.SelectedCommand}}">
              <input type="hidden" name="player_uuid" value="{{.UUID}}">
              <button class="secondary" type="submit">取消权限</button>
            </form>
          </td>
        </tr>
        {{end}}{{else}}<tr><td colspan="4" class="message">暂无玩家拥有该指令的单独权限</td></tr>{{end}}
      </tbody>
    </table>
  </div>
</section>
{{template "suggestionScript" .}}
{{end}}

{{define "report"}}{{template "pageStart" .}}{{template "reportContent" .}}{{template "pageEnd" .}}{{end}}
{{define "reportContent"}}
<h1>举报</h1>
<section class="panel">
  <form method="post" action="/report">
    <label for="target">被举报玩家 ID 或 UUID</label>
    <input id="target" name="target" required minlength="3" maxlength="36" list="player-suggestion-list" autocomplete="off">
    <label for="reason">举报理由</label>
    <textarea id="reason" name="reason" rows="6" maxlength="500" required></textarea>
    <div class="actions"><button type="submit">提交举报</button></div>
  </form>
</section>
{{template "suggestionScript" .}}
{{end}}

{{define "reportMessage"}}{{template "pageStart" .}}<h1>举报</h1><section class="panel"><p class="message">{{.Message}}</p><div class="actions"><a class="button secondary" href="/report">继续举报</a></div></section>{{template "pageEnd" .}}{{end}}

{{define "reports"}}{{template "pageStart" .}}{{template "reportsContent" .}}{{template "pageEnd" .}}{{end}}
{{define "reportsContent"}}
<h1>举报受理</h1>
{{if .Message}}<p class="message">{{.Message}}</p>{{end}}
<section class="panel">
  <div class="table-wrap">
    <table>
      <thead><tr><th>ID</th><th>状态</th><th>举报者</th><th>被举报者</th><th>理由</th><th>提交时间</th><th>处理结果</th><th>操作</th></tr></thead>
      <tbody>
        {{if .Reports}}{{range .Reports}}
        <tr>
          <td>{{.ID}}</td><td>{{.Status}}</td><td>{{.ReporterName}}</td><td>{{.TargetName}}</td><td>{{.Reason}}</td><td>{{formatTimeMillis .CreatedAt}}</td>
          <td>{{if .Action}}{{.Action}}{{if .HandlerName}} / {{.HandlerName}} / {{formatTimeMillis .HandledAt}}{{end}}{{if .BanPermanent}} / 永久{{else if .BanExpiresAt}} / 至 {{formatTimeMillis .BanExpiresAt}}{{end}}{{else}}-{{end}}</td>
          <td>
            {{if eq .Status "待处理"}}
            <form method="post" action="/reports/process" class="inline-form">
              <input type="hidden" name="report_id" value="{{.ID}}">
              <select name="action"><option value="不予处理">不予处理</option><option value="封禁">封禁</option></select>
              <input class="small-input" name="ban_duration" type="number" min="1" step="1" placeholder="时长">
              <select name="ban_unit"><option value="hours">小时</option><option value="days">天</option><option value="months">月</option><option value="years">年</option></select>
              <label class="checkbox-inline"><input type="checkbox" name="permanent" value="true"> 永久</label>
              <button type="submit">处理</button>
            </form>
            <a class="button secondary" href="/audit?run=1&player={{.TargetName}}">查最近操作</a>
            {{else}}<a class="button secondary" href="/audit?run=1&player={{.TargetName}}">查操作</a>{{end}}
          </td>
        </tr>
        {{end}}{{else}}<tr><td colspan="8" class="message">暂无举报</td></tr>{{end}}
      </tbody>
    </table>
  </div>
</section>
{{end}}

{{define "blacklist"}}{{template "pageStart" .}}{{template "blacklistContent" .}}{{template "pageEnd" .}}{{end}}
{{define "blacklistContent"}}
<h1>黑名单列表</h1>
{{if .Message}}<p class="message">{{.Message}}</p>{{end}}
{{if or (eq .User.Role "管理员") (eq .User.Role "服主")}}
<section class="panel">
  <h2>添加黑名单</h2>
  <form method="post" action="/blacklist/add">
    <label for="blacklist-player">玩家 ID 或 UUID</label>
    <input id="blacklist-player" name="player_name" required minlength="3" maxlength="36" list="player-suggestion-list" autocomplete="off">
    <label for="blacklist-reason">原因</label>
    <textarea id="blacklist-reason" name="reason" rows="3"></textarea>
    <label>封禁时长</label>
    <div class="inline-form"><input class="small-input" name="ban_duration" type="number" min="1" step="1" value="1"><select name="ban_unit"><option value="hours">小时</option><option value="days">天</option><option value="months">月</option><option value="years">年</option></select><label class="checkbox-inline"><input type="checkbox" name="permanent" value="true"> 永久</label></div>
    <div class="actions"><button type="submit">加入黑名单</button></div>
  </form>
</section>
{{end}}
<section class="panel">
  <div class="table-wrap">
    <table>
      <thead><tr><th>玩家</th><th>UUID</th><th>状态</th><th>原因</th><th>创建时间</th><th>到期时间</th><th>操作</th></tr></thead>
      <tbody>
        {{if .Blacklist}}{{range .Blacklist}}
        <tr>
          <td>{{.PlayerName}}</td><td class="mono">{{.PlayerUUID}}</td><td>{{if .Active}}生效中{{else}}已解除{{end}}</td><td>{{.Reason}}</td><td>{{formatTimeMillis .CreatedAt}}</td><td>{{if .Permanent}}永久{{else}}{{formatTimeMillis .ExpiresAt}}{{end}}</td>
          <td>{{if and .Active (or (eq $.User.Role "管理员") (eq $.User.Role "服主"))}}<form method="post" action="/blacklist/remove" class="inline-form"><input type="hidden" name="player_uuid" value="{{.PlayerUUID}}"><button class="secondary" type="submit">解除</button></form>{{else}}-{{end}}</td>
        </tr>
        {{end}}{{else}}<tr><td colspan="7" class="message">暂无黑名单记录</td></tr>{{end}}
      </tbody>
    </table>
  </div>
</section>
{{template "suggestionScript" .}}
{{end}}

{{define "audit"}}{{template "pageStart" .}}{{template "auditContent" .}}{{template "pageEnd" .}}{{end}}
{{define "auditContent"}}
<h1>操作查询</h1>
{{if .Message}}<p class="message">{{.Message}}</p>{{end}}
<section class="panel">
  <form method="get" action="/audit">
    <input type="hidden" name="run" value="1">
    <div class="form-grid">
      <div><label for="audit-action">操作</label><select id="audit-action" name="action"><option value="">全部</option><option value="BLOCK_PLACE">放置方块</option><option value="BLOCK_BREAK">破坏方块</option><option value="CONTAINER_ADD">存入物品</option><option value="CONTAINER_REMOVE">取出物品</option><option value="PLAYER_JOIN">玩家进入</option><option value="PLAYER_QUIT">玩家退出</option></select></div>
      <div><label for="audit-player">玩家 ID 或 UUID</label><input id="audit-player" name="player" value="{{.Audit.Query.Player}}" list="player-suggestion-list" autocomplete="off"></div>
      <div><label for="audit-world">世界</label><input id="audit-world" name="world" value="{{.Audit.Query.World}}"></div>
      <div><label for="audit-target">目标类型</label><input id="audit-target" name="target_type" value="{{.Audit.Query.TargetType}}"></div>
      <div><label for="audit-item">物品</label><input id="audit-item" name="item_type" value="{{.Audit.Query.ItemType}}"></div>
      <div><label for="audit-from">开始时间</label><input id="audit-from" name="time_from" type="datetime-local" value="{{.Audit.Query.TimeFrom}}"></div>
      <div><label for="audit-to">结束时间</label><input id="audit-to" name="time_to" type="datetime-local" value="{{.Audit.Query.TimeTo}}"></div>
    </div>
    <div class="actions"><button type="submit">查询</button><a class="button secondary" href="/audit">清空</a></div>
  </form>
</section>
<section class="panel">
  <div class="table-wrap">
    <table>
      <thead><tr><th>时间</th><th>玩家</th><th>操作</th><th>位置</th><th>目标</th><th>物品</th><th>数量</th><th>详情</th></tr></thead>
      <tbody>
        {{if .Audit.Rows}}{{range .Audit.Rows}}
        <tr><td>{{formatTimeMillis .CreatedAt}}</td><td>{{.PlayerName}}</td><td>{{.Action}}</td><td>{{.World}} {{.X}},{{.Y}},{{.Z}}</td><td>{{.TargetType}}</td><td>{{.ItemType}}</td><td>{{.ItemAmount}}</td><td>{{.Details}}</td></tr>
        {{end}}{{else}}<tr><td colspan="8" class="message">{{if .Audit.Query.Submitted}}没有查到记录{{else}}请选择条件后查询{{end}}</td></tr>{{end}}
      </tbody>
    </table>
  </div>
  {{if .Audit.NextQuery}}<div class="actions"><a class="button secondary" href="/audit?{{.Audit.NextQuery}}">下一页</a></div>{{end}}
</section>
{{template "suggestionScript" .}}
{{end}}

{{define "docsEdit"}}{{template "pageStart" .}}{{template "docsEditContent" .}}{{template "pageEnd" .}}{{end}}
{{define "docsEditContent"}}
<div class="page-heading"><h1>编辑服务器文档</h1><a class="button secondary" href="/docs">退出编辑</a></div>
{{if .Message}}<p class="message">{{.Message}}</p>{{end}}
<form method="post" action="/docs/edit">
  <section class="panel">
    <label for="docs-markdown">原始 Markdown</label>
    <textarea id="docs-markdown" name="markdown" rows="24">{{.DocsText}}</textarea>
    <div class="actions"><button type="submit">保存文档</button><a class="button secondary" href="/docs">取消</a></div>
  </section>
</form>
{{end}}

{{define "suggestionScript"}}
<datalist id="player-suggestion-list"></datalist>
<script>
  document.querySelectorAll('input[list="player-suggestion-list"]').forEach((input) => {
    let timer = null;
    input.addEventListener("input", () => {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        const query = input.value.trim();
        if (!query) return;
        const response = await fetch("/players/suggestions?q=" + encodeURIComponent(query), {headers: {"Accept": "application/json"}});
        if (!response.ok) return;
        const data = await response.json();
        const list = document.getElementById("player-suggestion-list");
        list.innerHTML = "";
        (data.items || []).forEach((item) => {
          const name = document.createElement("option");
          name.value = item.label;
          name.label = item.uuid + " / " + item.source;
          list.appendChild(name);
          const uuid = document.createElement("option");
          uuid.value = item.uuid;
          uuid.label = item.label + " / " + item.source;
          list.appendChild(uuid);
        });
      }, 140);
    });
  });
</script>
{{end}}
`
