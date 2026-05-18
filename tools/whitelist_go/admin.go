package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	reportPending = "待处理"
	reportHandled = "已处理"
	reportIgnored = "不予处理"
	reportBan     = "封禁"
)

var permissionMu sync.Mutex

type playerRow struct {
	UUID         string
	Name         string
	Role         string
	ProfileBio   string
	RegisteredAt int64
	UpdatedAt    int64
	IsSelf       bool
}

type reportRow struct {
	ID           int64
	ReporterName string
	TargetName   string
	TargetUUID   string
	Reason       string
	Status       string
	Action       string
	HandlerName  string
	CreatedAt    int64
	HandledAt    int64
	BanExpiresAt int64
	BanPermanent bool
}

type blacklistRow struct {
	PlayerUUID string
	PlayerName string
	Reason     string
	ReportID   int64
	CreatedAt  int64
	ExpiresAt  int64
	Permanent  bool
	Active     bool
}

type commandOption struct {
	ID       string
	Label    string
	Selected bool
}

type permissionRow struct {
	UUID string
	Name string
	Role string
}

type permissionPageData struct {
	SelectedCommand string
	CommandLabel    string
	Commands        []commandOption
	Rows            []permissionRow
}

type auditRow struct {
	ID         int64
	CreatedAt  int64
	Action     string
	PlayerUUID string
	PlayerName string
	World      string
	X          int
	Y          int
	Z          int
	TargetType string
	ItemType   string
	ItemAmount int
	Details    string
}

type auditPageData struct {
	Query     auditQuery
	Rows      []auditRow
	NextQuery string
}

type auditQuery struct {
	Submitted  bool
	Action     string
	Player     string
	World      string
	TargetType string
	ItemType   string
	TimeFrom   string
	TimeTo     string
	Radius     int
	X          string
	Y          string
	Z          string
	Cursor     int64
}

type suggestionItem struct {
	Label  string `json:"label"`
	Value  string `json:"value"`
	UUID   string `json:"uuid"`
	Match  string `json:"match"`
	Source string `json:"source"`
}

type permissionCommand struct {
	Label      string
	ConfigName string
	Token      string
	Reload     string
}

type permissionConfig struct {
	Default []string
	Players map[string]permissionConfigPlayer
}

type permissionConfigPlayer struct {
	Name     string
	Commands []string
}

var permissionCommands = map[string]permissionCommand{
	"creative":   {Label: "/creative", ConfigName: "command-control", Token: "creative", Reload: "xcc reload"},
	"xcc.reload": {Label: "/xcc reload", ConfigName: "command-control", Token: "xcc.reload", Reload: "xcc reload"},
	"xcc.list":   {Label: "/xcc list", ConfigName: "command-control", Token: "xcc.list", Reload: "xcc reload"},
	"claim.give": {Label: "/claim give", ConfigName: "claim", Token: "give", Reload: "claim reload"},
}

var permissionCommandOrder = []string{"creative", "xcc.reload", "xcc.list", "claim.give"}

func (a *app) handlePlayers(w http.ResponseWriter, r *http.Request, user *userSession) {
	a.renderPlayers(w, r, user, "", http.StatusOK)
}

func (a *app) renderPlayers(w http.ResponseWriter, r *http.Request, user *userSession, message string, status int) {
	players, err := a.allWebPlayers(r.Context(), user)
	if err != nil && message == "" {
		message = "玩家列表暂时不可用：" + err.Error()
	}
	a.render(w, status, "players", pageData{
		Title:   "玩家列表",
		User:    user,
		Active:  "players",
		Public:  a.publicData(),
		Message: message,
		Players: players,
	})
}

func (a *app) handlePlayerRole(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	playerUUID := strings.TrimSpace(r.FormValue("player_uuid"))
	role := strings.TrimSpace(r.FormValue("role"))
	message := "身份已更新。"
	status := http.StatusOK
	if err := a.updatePlayerRole(r.Context(), playerUUID, role); err != nil {
		message = "修改身份失败：" + err.Error()
		status = http.StatusBadRequest
	}
	a.renderPlayers(w, r, user, message, status)
}

func (a *app) handlePlayerPasswordReset(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	message := "密码已重置为 123456。"
	status := http.StatusOK
	if err := a.resetPlayerPassword(r.Context(), r.FormValue("player_uuid")); err != nil {
		message = "重置密码失败：" + err.Error()
		status = http.StatusBadRequest
	}
	a.renderPlayers(w, r, user, message, status)
}

func (a *app) handlePlayerSuggestions(w http.ResponseWriter, r *http.Request, user *userSession) {
	items := a.auditSourceSuggestions(r.URL.Query().Get("q"), 12)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(map[string]any{"items": items})
}

func (a *app) handlePermissions(w http.ResponseWriter, r *http.Request, user *userSession) {
	a.renderPermissions(w, r, user, r.URL.Query().Get("command"), "", http.StatusOK)
}

func (a *app) handlePermissionAdd(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	commandID := r.FormValue("command")
	message := "权限已添加并重载。"
	status := http.StatusOK
	if err := a.grantPlayerCommand(r.Context(), r.FormValue("player"), commandID); err != nil {
		message = "添加权限失败：" + err.Error()
		status = http.StatusBadRequest
	}
	a.renderPermissions(w, r, user, commandID, message, status)
}

func (a *app) handlePermissionRemove(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	commandID := r.FormValue("command")
	message := "权限已取消并重载。"
	status := http.StatusOK
	if err := a.revokePlayerCommand(r.Context(), r.FormValue("player_uuid"), commandID); err != nil {
		message = "取消权限失败：" + err.Error()
		status = http.StatusBadRequest
	}
	a.renderPermissions(w, r, user, commandID, message, status)
}

func (a *app) renderPermissions(w http.ResponseWriter, r *http.Request, user *userSession, commandID, message string, status int) {
	selected, err := normalizePermissionCommand(commandID)
	if err != nil {
		selected = "creative"
		if message == "" {
			message = "指令筛选不正确，已回到 /creative。"
		}
	}
	rows, err := a.readPermissionAssignments(r.Context(), selected)
	if err != nil && message == "" {
		message = "权限列表暂时不可用：" + err.Error()
	}
	options := []commandOption{}
	for _, id := range permissionCommandOrder {
		options = append(options, commandOption{ID: id, Label: permissionCommands[id].Label, Selected: id == selected})
	}
	a.render(w, status, "permissions", pageData{
		Title:   "权限管理",
		User:    user,
		Active:  "permissions",
		Public:  a.publicData(),
		Message: message,
		Permissions: permissionPageData{
			SelectedCommand: selected,
			CommandLabel:    permissionCommands[selected].Label,
			Commands:        options,
			Rows:            rows,
		},
	})
}

func (a *app) handleReportPage(w http.ResponseWriter, r *http.Request, user *userSession) {
	a.render(w, http.StatusOK, "report", pageData{Title: "举报", User: user, Active: "report", Public: a.publicData()})
}

func (a *app) handleReportSubmit(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	reason := strings.TrimSpace(r.FormValue("reason"))
	status := http.StatusOK
	message := "举报已提交，管理员会结合审计记录处理。"
	if len([]rune(reason)) < 5 {
		message = "举报理由至少需要 5 个字符。"
		status = http.StatusBadRequest
	} else if len([]rune(reason)) > 500 {
		message = "举报理由不能超过 500 个字符。"
		status = http.StatusBadRequest
	} else {
		target, err := a.resolvePlayerQuery(r.Context(), r.FormValue("target"))
		if err != nil {
			message = "提交举报失败：" + err.Error()
			status = http.StatusBadRequest
		} else if strings.EqualFold(canonicalUUID(target.UUID), canonicalUUID(user.UUID)) {
			message = "不能举报自己。"
			status = http.StatusBadRequest
		} else if err := a.createReport(r.Context(), user, target, reason); err != nil {
			message = "提交举报失败：" + err.Error()
			status = http.StatusInternalServerError
		}
	}
	a.render(w, status, "reportMessage", pageData{Title: "举报", User: user, Active: "report", Public: a.publicData(), Message: message})
}

func (a *app) handleReports(w http.ResponseWriter, r *http.Request, user *userSession) {
	a.renderReports(w, r, user, "", http.StatusOK)
}

func (a *app) handleReportProcess(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	reportID, _ := strconv.ParseInt(r.FormValue("report_id"), 10, 64)
	action := strings.TrimSpace(r.FormValue("action"))
	permanent := r.FormValue("permanent") == "true"
	var expiresAt int64
	if action == reportBan && !permanent {
		duration := atoi(r.FormValue("ban_duration"))
		if duration <= 0 {
			duration = 1
		}
		expiresAt = time.Now().Add(banDuration(duration, r.FormValue("ban_unit"))).UnixMilli()
	}
	message := "举报已处理。"
	status := http.StatusOK
	if err := a.processReport(r.Context(), reportID, user, action, expiresAt, permanent); err != nil {
		message = "处理举报失败：" + err.Error()
		status = http.StatusBadRequest
	}
	a.renderReports(w, r, user, message, status)
}

func (a *app) renderReports(w http.ResponseWriter, r *http.Request, user *userSession, message string, status int) {
	reports, err := a.listReports(r.Context(), 100)
	if err != nil && message == "" {
		message = "举报列表暂时不可用：" + err.Error()
	}
	a.render(w, status, "reports", pageData{Title: "举报受理", User: user, Active: "reports", Public: a.publicData(), Message: message, Reports: reports})
}

func (a *app) handleBlacklist(w http.ResponseWriter, r *http.Request, user *userSession) {
	a.renderBlacklist(w, r, user, "", http.StatusOK)
}

func (a *app) handleBlacklistAdd(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	entry, err := a.resolvePlayerQuery(r.Context(), r.FormValue("player_name"))
	message := "玩家已加入黑名单。"
	status := http.StatusOK
	if err != nil {
		message = "加入黑名单失败：" + err.Error()
		status = http.StatusBadRequest
	} else {
		permanent := r.FormValue("permanent") == "true"
		var expiresAt int64
		if !permanent {
			duration := atoi(r.FormValue("ban_duration"))
			if duration <= 0 {
				duration = 1
			}
			expiresAt = time.Now().Add(banDuration(duration, r.FormValue("ban_unit"))).UnixMilli()
		}
		reason := strings.TrimSpace(r.FormValue("reason"))
		if reason == "" {
			reason = "管理员手动加入黑名单"
		}
		if err := a.addManualBlacklist(r.Context(), entry, reason, expiresAt, permanent); err != nil {
			message = "加入黑名单失败：" + err.Error()
			status = http.StatusBadRequest
		}
	}
	a.renderBlacklist(w, r, user, message, status)
}

func (a *app) handleBlacklistRemove(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	message := "黑名单已解除。"
	status := http.StatusOK
	if err := a.deactivateBlacklist(r.Context(), r.FormValue("player_uuid")); err != nil {
		message = "解除黑名单失败：" + err.Error()
		status = http.StatusBadRequest
	}
	a.renderBlacklist(w, r, user, message, status)
}

func (a *app) renderBlacklist(w http.ResponseWriter, r *http.Request, user *userSession, message string, status int) {
	rows, err := a.allBlacklistEntries(r.Context(), 200)
	if err != nil && message == "" {
		message = "黑名单列表暂时不可用：" + err.Error()
	}
	a.render(w, status, "blacklist", pageData{Title: "黑名单列表", User: user, Active: "blacklist", Public: a.publicData(), Message: message, Blacklist: rows})
}

func (a *app) handleAudit(w http.ResponseWriter, r *http.Request, user *userSession) {
	query := auditQueryFromValues(r.URL.Query())
	rows := []auditRow{}
	message := ""
	next := ""
	if query.Submitted {
		var err error
		rows, next, err = a.queryAudit(r.Context(), query)
		if err != nil {
			message = "审计查询失败：" + err.Error()
		}
	}
	a.render(w, http.StatusOK, "audit", pageData{Title: "操作查询", User: user, Active: "audit", Public: a.publicData(), Message: message, Audit: auditPageData{Query: query, Rows: rows, NextQuery: next}})
}

func (a *app) handleDocsEditPage(w http.ResponseWriter, r *http.Request, user *userSession) {
	a.renderDocsEdit(w, user, a.readDocsText(), "", http.StatusOK)
}

func (a *app) handleDocsUpdate(w http.ResponseWriter, r *http.Request, user *userSession) {
	_ = r.ParseForm()
	text := r.FormValue("markdown")
	if len([]rune(text)) > a.cfg.ServerDocsMaxLength {
		a.renderDocsEdit(w, user, text, fmt.Sprintf("文档不能超过 %d 个字符。", a.cfg.ServerDocsMaxLength), http.StatusBadRequest)
		return
	}
	if err := os.MkdirAll(filepath.Dir(a.cfg.ServerDocsPath), 0o755); err != nil {
		a.renderDocsEdit(w, user, text, "保存失败："+err.Error(), http.StatusInternalServerError)
		return
	}
	tmp := a.cfg.ServerDocsPath + ".tmp"
	if err := os.WriteFile(tmp, []byte(text), 0o644); err != nil {
		a.renderDocsEdit(w, user, text, "保存失败："+err.Error(), http.StatusInternalServerError)
		return
	}
	if err := os.Rename(tmp, a.cfg.ServerDocsPath); err != nil {
		a.renderDocsEdit(w, user, text, "保存失败："+err.Error(), http.StatusInternalServerError)
		return
	}
	a.renderDocsEdit(w, user, text, "文档已保存。", http.StatusOK)
}

func (a *app) renderDocsEdit(w http.ResponseWriter, user *userSession, text, message string, status int) {
	a.render(w, status, "docsEdit", pageData{Title: "编辑服务器文档", User: user, Active: "docs", Public: a.publicData(), Message: message, DocsText: text})
}

func (a *app) readDocsText() string {
	data, err := os.ReadFile(a.cfg.ServerDocsPath)
	if err != nil || len(data) == 0 {
		return "服务器文档尚未初始化。"
	}
	return string(data)
}

func (a *app) allWebPlayers(ctx context.Context, viewer *userSession) ([]playerRow, error) {
	entries, err := a.readWhitelistEntries()
	if err != nil {
		return nil, err
	}
	players := map[string]webPlayer{}
	if a.db != nil {
		rows, err := a.db.QueryContext(ctx, `SELECT player_uuid, player_name, role, profile_bio, registered_at, updated_at FROM web_players`)
		if err != nil {
			return nil, err
		}
		defer rows.Close()
		for rows.Next() {
			var player webPlayer
			var updated int64
			if err := rows.Scan(&player.UUID, &player.Name, &player.Role, &player.ProfileBio, &player.RegisteredAt, &updated); err != nil {
				return nil, err
			}
			players[canonicalUUID(player.UUID)] = player
		}
	}
	result := []playerRow{}
	for _, entry := range entries {
		uuid := canonicalUUID(entry.UUID)
		player := players[uuid]
		role := player.Role
		if role == "" {
			role = playerRole
		}
		bio := player.ProfileBio
		if bio == "" {
			bio = defaultProfileBio
		}
		result = append(result, playerRow{UUID: uuid, Name: entry.Name, Role: role, ProfileBio: bio, RegisteredAt: player.RegisteredAt, IsSelf: strings.EqualFold(uuid, canonicalUUID(viewer.UUID))})
	}
	sort.SliceStable(result, func(i, j int) bool { return strings.ToLower(result[i].Name) < strings.ToLower(result[j].Name) })
	return result, nil
}

func (a *app) updatePlayerRole(ctx context.Context, uuid, role string) error {
	if role != playerRole && role != adminRole && role != ownerRole {
		return errors.New("身份不正确")
	}
	entry, ok := a.whitelistEntryByUUID(uuid)
	if !ok {
		return errors.New("玩家不存在或不在白名单中")
	}
	if role != ownerRole && strings.EqualFold(entry.Name, "ExamplePlayer") {
		return errors.New("不能将 ExamplePlayer 从服主身份降级")
	}
	if err := a.ensureWebPlayer(ctx, entry); err != nil {
		return err
	}
	result, err := a.db.ExecContext(ctx, `UPDATE web_players SET role = $1, updated_at = $2 WHERE player_uuid = $3`, role, time.Now().UnixMilli(), canonicalUUID(uuid))
	if err != nil {
		return err
	}
	rows, _ := result.RowsAffected()
	if rows != 1 {
		return errors.New("玩家 Web 记录不存在")
	}
	return nil
}

func (a *app) resetPlayerPassword(ctx context.Context, uuid string) error {
	result, err := a.db.ExecContext(ctx, `UPDATE web_players SET password_hash = $1, updated_at = $2 WHERE player_uuid = $3`, passwordHash(defaultPassword), time.Now().UnixMilli(), canonicalUUID(uuid))
	if err != nil {
		return err
	}
	rows, _ := result.RowsAffected()
	if rows != 1 {
		return errors.New("玩家 Web 记录不存在")
	}
	return nil
}

func (a *app) resolvePlayerQuery(ctx context.Context, query string) (whitelistEntry, error) {
	query = strings.TrimSpace(query)
	var entry whitelistEntry
	var ok bool
	if len(strings.ReplaceAll(query, "-", "")) == 32 {
		entry, ok = a.whitelistEntryByUUID(query)
	} else if usernameRE.MatchString(query) {
		entry, ok = a.whitelistEntry(query)
	}
	if !ok {
		return whitelistEntry{}, errors.New("玩家不存在或不在白名单中")
	}
	_ = a.ensureWebPlayer(ctx, entry)
	return whitelistEntry{UUID: canonicalUUID(entry.UUID), Name: entry.Name}, nil
}

func (a *app) createReport(ctx context.Context, user *userSession, target whitelistEntry, reason string) error {
	if a.db == nil {
		return errors.New("database unavailable")
	}
	_, err := a.db.ExecContext(ctx, `INSERT INTO web_reports (reporter_uuid, reporter_name, target_uuid, target_name, reason, status, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
		canonicalUUID(user.UUID), user.Name, canonicalUUID(target.UUID), target.Name, reason, reportPending, time.Now().UnixMilli())
	return err
}

func (a *app) listReports(ctx context.Context, limit int) ([]reportRow, error) {
	rows, err := a.db.QueryContext(ctx, `SELECT id, reporter_name, target_name, target_uuid, reason, status, action, handler_name, created_at, handled_at, ban_expires_at, ban_permanent FROM web_reports ORDER BY created_at DESC, id DESC LIMIT $1`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []reportRow{}
	for rows.Next() {
		var row reportRow
		var action, handler sql.NullString
		var handled, expires sql.NullInt64
		if err := rows.Scan(&row.ID, &row.ReporterName, &row.TargetName, &row.TargetUUID, &row.Reason, &row.Status, &action, &handler, &row.CreatedAt, &handled, &expires, &row.BanPermanent); err != nil {
			return nil, err
		}
		row.Action = action.String
		row.HandlerName = handler.String
		row.HandledAt = handled.Int64
		row.BanExpiresAt = expires.Int64
		result = append(result, row)
	}
	return result, rows.Err()
}

func (a *app) processReport(ctx context.Context, reportID int64, handler *userSession, action string, expiresAt int64, permanent bool) error {
	if action != reportIgnored && action != reportBan {
		return errors.New("处理动作不正确")
	}
	tx, err := a.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var report reportRow
	err = tx.QueryRowContext(ctx, `SELECT id, target_uuid, target_name, reason, status FROM web_reports WHERE id = $1`, reportID).Scan(&report.ID, &report.TargetUUID, &report.TargetName, &report.Reason, &report.Status)
	if err != nil {
		return errors.New("举报不存在")
	}
	if report.Status != reportPending {
		return errors.New("该举报已处理")
	}
	status := reportIgnored
	if action == reportBan {
		status = reportHandled
	}
	now := time.Now().UnixMilli()
	_, err = tx.ExecContext(ctx, `UPDATE web_reports SET status=$1, action=$2, handler_uuid=$3, handler_name=$4, handled_at=$5, ban_expires_at=$6, ban_permanent=$7 WHERE id=$8`,
		status, action, canonicalUUID(handler.UUID), handler.Name, now, nullInt64(expiresAt), permanent, reportID)
	if err != nil {
		return err
	}
	if action == reportBan {
		_, err = tx.ExecContext(ctx, `INSERT INTO web_blacklist (player_uuid, player_name, reason, report_id, created_at, expires_at, permanent, active) VALUES ($1,$2,$3,$4,$5,$6,$7,TRUE) ON CONFLICT (player_uuid) DO UPDATE SET player_name=EXCLUDED.player_name, reason=EXCLUDED.reason, report_id=EXCLUDED.report_id, created_at=EXCLUDED.created_at, expires_at=EXCLUDED.expires_at, permanent=EXCLUDED.permanent, active=TRUE`,
			canonicalUUID(report.TargetUUID), report.TargetName, report.Reason, reportID, now, nullInt64(expiresAt), permanent)
		if err != nil {
			return err
		}
	}
	if err := tx.Commit(); err != nil {
		return err
	}
	if err := a.syncBlacklistFile(ctx); err != nil {
		return err
	}
	if action == reportBan {
		_, _ = a.rcon("kick " + report.TargetName + " 你已被加入服务器黑名单。")
	}
	return nil
}

func nullInt64(value int64) any {
	if value <= 0 {
		return nil
	}
	return value
}

func banDuration(value int, unit string) time.Duration {
	switch unit {
	case "days":
		return time.Duration(value) * 24 * time.Hour
	case "months":
		return time.Duration(value) * 30 * 24 * time.Hour
	case "years":
		return time.Duration(value) * 365 * 24 * time.Hour
	default:
		return time.Duration(value) * time.Hour
	}
}

func (a *app) allBlacklistEntries(ctx context.Context, limit int) ([]blacklistRow, error) {
	rows, err := a.db.QueryContext(ctx, `SELECT player_uuid, player_name, reason, COALESCE(report_id,0), created_at, COALESCE(expires_at,0), permanent, active FROM web_blacklist ORDER BY active DESC, created_at DESC LIMIT $1`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []blacklistRow{}
	for rows.Next() {
		var row blacklistRow
		if err := rows.Scan(&row.PlayerUUID, &row.PlayerName, &row.Reason, &row.ReportID, &row.CreatedAt, &row.ExpiresAt, &row.Permanent, &row.Active); err != nil {
			return nil, err
		}
		result = append(result, row)
	}
	return result, rows.Err()
}

func (a *app) activeBlacklistEntries(ctx context.Context) ([]blacklistRow, error) {
	rows, err := a.db.QueryContext(ctx, `SELECT player_uuid, player_name, reason, COALESCE(report_id,0), created_at, COALESCE(expires_at,0), permanent, active FROM web_blacklist WHERE active = TRUE AND (permanent = TRUE OR expires_at > $1) ORDER BY player_name`, time.Now().UnixMilli())
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []blacklistRow{}
	for rows.Next() {
		var row blacklistRow
		if err := rows.Scan(&row.PlayerUUID, &row.PlayerName, &row.Reason, &row.ReportID, &row.CreatedAt, &row.ExpiresAt, &row.Permanent, &row.Active); err != nil {
			return nil, err
		}
		result = append(result, row)
	}
	return result, rows.Err()
}

func (a *app) addManualBlacklist(ctx context.Context, entry whitelistEntry, reason string, expiresAt int64, permanent bool) error {
	_, err := a.db.ExecContext(ctx, `INSERT INTO web_blacklist (player_uuid, player_name, reason, report_id, created_at, expires_at, permanent, active) VALUES ($1,$2,$3,NULL,$4,$5,$6,TRUE) ON CONFLICT (player_uuid) DO UPDATE SET player_name=EXCLUDED.player_name, reason=EXCLUDED.reason, report_id=NULL, created_at=EXCLUDED.created_at, expires_at=EXCLUDED.expires_at, permanent=EXCLUDED.permanent, active=TRUE`,
		canonicalUUID(entry.UUID), entry.Name, reason, time.Now().UnixMilli(), nullInt64(expiresAt), permanent)
	if err != nil {
		return err
	}
	return a.syncBlacklistFile(ctx)
}

func (a *app) deactivateBlacklist(ctx context.Context, uuid string) error {
	_, err := a.db.ExecContext(ctx, `UPDATE web_blacklist SET active = FALSE WHERE player_uuid = $1`, canonicalUUID(uuid))
	if err != nil {
		return err
	}
	return a.syncBlacklistFile(ctx)
}

func (a *app) syncBlacklistFile(ctx context.Context) error {
	entries, err := a.activeBlacklistEntries(ctx)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(a.cfg.BlacklistPath), 0o755); err != nil {
		return err
	}
	tmp := a.cfg.BlacklistPath + ".tmp"
	var builder strings.Builder
	builder.WriteString("# key\tuuid\tplayer\texpiresAtMillis\tpermanent\treason\n")
	for _, entry := range entries {
		reason := strings.NewReplacer("\t", " ", "\n", " ").Replace(entry.Reason)
		expiresAt := entry.ExpiresAt
		if entry.Permanent {
			expiresAt = -1
		}
		builder.WriteString(strings.Join([]string{strings.ToLower(entry.PlayerName), canonicalUUID(entry.PlayerUUID), entry.PlayerName, strconv.FormatInt(expiresAt, 10), strconv.FormatBool(entry.Permanent), reason}, "\t"))
		builder.WriteByte('\n')
	}
	if err := os.WriteFile(tmp, []byte(builder.String()), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, a.cfg.BlacklistPath)
}

func normalizePermissionCommand(commandID string) (string, error) {
	normalized := strings.ToLower(strings.TrimSpace(commandID))
	if normalized == "" {
		normalized = "creative"
	}
	normalized = strings.TrimPrefix(normalized, "/")
	normalized = strings.ReplaceAll(normalized, " ", ".")
	if _, ok := permissionCommands[normalized]; !ok {
		return "", errors.New("指令不在可管理范围内")
	}
	return normalized, nil
}

func (a *app) readPermissionAssignments(ctx context.Context, commandID string) ([]permissionRow, error) {
	commandID, err := normalizePermissionCommand(commandID)
	if err != nil {
		return nil, err
	}
	command := permissionCommands[commandID]
	config, err := a.readPermissionConfig(command.ConfigName)
	if err != nil {
		return nil, err
	}
	rows := []permissionRow{}
	for uuid, entry := range config.Players {
		if !containsFold(entry.Commands, command.Token) {
			continue
		}
		player := a.permissionPlayerRecord(ctx, uuid, entry.Name)
		rows = append(rows, player)
	}
	sort.SliceStable(rows, func(i, j int) bool {
		if roleSortOrder(rows[i].Role) == roleSortOrder(rows[j].Role) {
			return strings.ToLower(rows[i].Name) < strings.ToLower(rows[j].Name)
		}
		return roleSortOrder(rows[i].Role) < roleSortOrder(rows[j].Role)
	})
	return rows, nil
}

func (a *app) permissionPlayerRecord(ctx context.Context, uuid, fallbackName string) permissionRow {
	entry, ok := a.whitelistEntryByUUID(uuid)
	if !ok {
		return permissionRow{UUID: canonicalUUID(uuid), Name: fallbackName, Role: "未知"}
	}
	player, err := a.webPlayerByEntry(ctx, entry)
	role := playerRole
	if err == nil && player.Role != "" {
		role = player.Role
	}
	return permissionRow{UUID: canonicalUUID(entry.UUID), Name: entry.Name, Role: role}
}

func roleSortOrder(role string) int {
	switch role {
	case ownerRole:
		return 0
	case adminRole:
		return 1
	case playerRole:
		return 2
	default:
		return 3
	}
}

func (a *app) grantPlayerCommand(ctx context.Context, playerQuery, commandID string) error {
	entry, err := a.resolvePlayerQuery(ctx, playerQuery)
	if err != nil {
		return err
	}
	commandID, err = normalizePermissionCommand(commandID)
	if err != nil {
		return err
	}
	command := permissionCommands[commandID]
	permissionMu.Lock()
	defer permissionMu.Unlock()
	config, err := a.readPermissionConfig(command.ConfigName)
	if err != nil {
		return err
	}
	player := config.Players[canonicalUUID(entry.UUID)]
	player.Name = entry.Name
	if !containsFold(player.Commands, command.Token) {
		player.Commands = append(player.Commands, command.Token)
	}
	config.Players[canonicalUUID(entry.UUID)] = player
	if err := a.writePermissionConfig(command.ConfigName, config); err != nil {
		return err
	}
	_, err = a.rcon(command.Reload)
	return err
}

func (a *app) revokePlayerCommand(ctx context.Context, uuid, commandID string) error {
	commandID, err := normalizePermissionCommand(commandID)
	if err != nil {
		return err
	}
	command := permissionCommands[commandID]
	permissionMu.Lock()
	defer permissionMu.Unlock()
	config, err := a.readPermissionConfig(command.ConfigName)
	if err != nil {
		return err
	}
	key := canonicalUUID(uuid)
	player := config.Players[key]
	filtered := []string{}
	for _, value := range player.Commands {
		if !strings.EqualFold(value, command.Token) {
			filtered = append(filtered, value)
		}
	}
	if len(filtered) == 0 {
		delete(config.Players, key)
	} else {
		player.Commands = filtered
		config.Players[key] = player
	}
	if err := a.writePermissionConfig(command.ConfigName, config); err != nil {
		return err
	}
	_, err = a.rcon(command.Reload)
	return err
}

func containsFold(values []string, token string) bool {
	for _, value := range values {
		if strings.EqualFold(value, token) {
			return true
		}
	}
	return false
}

func (a *app) permissionConfigPath(configName string) (string, error) {
	switch configName {
	case "command-control":
		return a.cfg.CommandControlConfigPath, nil
	case "claim":
		return a.cfg.ClaimConfigPath, nil
	default:
		return "", errors.New("未知权限配置")
	}
}

func (a *app) readPermissionConfig(configName string) (permissionConfig, error) {
	path, err := a.permissionConfigPath(configName)
	if err != nil {
		return permissionConfig{}, err
	}
	data, _ := os.ReadFile(path)
	text := string(data)
	if configName == "claim" {
		text = extractTopLevelBlock(text, "access")
		return permissionConfig{Default: parseNestedList(text, "default-allowed-actions"), Players: parseNestedPlayersBlock(text, "players", "actions")}, nil
	}
	return permissionConfig{Default: parseTopLevelList(text, "default-allowed-commands"), Players: parsePlayersBlock(text, "players", "commands")}, nil
}

func (a *app) writePermissionConfig(configName string, config permissionConfig) error {
	path, err := a.permissionConfigPath(configName)
	if err != nil {
		return err
	}
	data, _ := os.ReadFile(path)
	text := string(data)
	var updated string
	if configName == "claim" {
		updated = replaceTopLevelBlock(text, "access", renderClaimAccessBlock(config))
	} else {
		updated = replaceTopLevelBlock(text, "players", renderPermissionPlayersBlock(config.Players, "players", "commands"))
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, []byte(strings.TrimRight(updated, "\n")+"\n"), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

func parseTopLevelList(text, key string) []string {
	lines := strings.Split(text, "\n")
	values := []string{}
	for i, line := range lines {
		if strings.TrimSpace(line) != key+":" {
			continue
		}
		for _, child := range lines[i+1:] {
			indent := len(child) - len(strings.TrimLeft(child, " "))
			stripped := strings.TrimSpace(child)
			if stripped == "" || strings.HasPrefix(stripped, "#") {
				continue
			}
			if indent == 0 {
				break
			}
			if strings.HasPrefix(stripped, "- ") {
				values = append(values, yamlScalar(strings.TrimPrefix(stripped, "- ")))
			}
		}
		break
	}
	return values
}

func parseNestedList(text, key string) []string {
	lines := strings.Split(text, "\n")
	values := []string{}
	for i, line := range lines {
		if !strings.HasPrefix(line, "  "+key+":") {
			continue
		}
		inline := strings.TrimSpace(strings.SplitN(line, ":", 2)[1])
		if inline != "" && inline != "[]" {
			for _, item := range strings.Split(strings.Trim(inline, "[]"), ",") {
				if strings.TrimSpace(item) != "" {
					values = append(values, yamlScalar(item))
				}
			}
			return values
		}
		for _, child := range lines[i+1:] {
			indent := len(child) - len(strings.TrimLeft(child, " "))
			stripped := strings.TrimSpace(child)
			if stripped == "" || strings.HasPrefix(stripped, "#") {
				continue
			}
			if indent <= 2 {
				break
			}
			if strings.HasPrefix(stripped, "- ") {
				values = append(values, yamlScalar(strings.TrimPrefix(stripped, "- ")))
			}
		}
		break
	}
	return values
}

func parsePlayersBlock(text, blockKey, listKey string) map[string]permissionConfigPlayer {
	return parsePlayersBlockWithIndent(text, blockKey, listKey, 0)
}

func parseNestedPlayersBlock(text, blockKey, listKey string) map[string]permissionConfigPlayer {
	return parsePlayersBlockWithIndent(text, blockKey, listKey, 2)
}

func parsePlayersBlockWithIndent(text, blockKey, listKey string, baseIndent int) map[string]permissionConfigPlayer {
	lines := strings.Split(text, "\n")
	players := map[string]permissionConfigPlayer{}
	inBlock := false
	currentUUID := ""
	currentList := false
	for _, raw := range lines {
		if strings.TrimSpace(raw) == "" || strings.HasPrefix(strings.TrimLeft(raw, " "), "#") {
			continue
		}
		indent := len(raw) - len(strings.TrimLeft(raw, " "))
		stripped := strings.TrimSpace(raw)
		if indent <= baseIndent {
			inBlock = indent == baseIndent && stripped == blockKey+":"
			currentUUID = ""
			currentList = false
			continue
		}
		if !inBlock {
			continue
		}
		if indent == baseIndent+2 && strings.HasSuffix(stripped, ":") {
			currentUUID = canonicalUUID(strings.TrimSuffix(stripped, ":"))
			players[currentUUID] = permissionConfigPlayer{}
			currentList = false
			continue
		}
		if currentUUID == "" {
			continue
		}
		player := players[currentUUID]
		if indent == baseIndent+4 && strings.Contains(stripped, ":") {
			parts := strings.SplitN(stripped, ":", 2)
			key, value := strings.TrimSpace(parts[0]), strings.TrimSpace(parts[1])
			currentList = false
			if key == "name" {
				player.Name = yamlScalar(value)
			} else if key == listKey {
				currentList = true
				if value != "" && value != "[]" {
					for _, item := range strings.Split(strings.Trim(value, "[]"), ",") {
						if strings.TrimSpace(item) != "" {
							player.Commands = append(player.Commands, yamlScalar(item))
						}
					}
				}
			}
			players[currentUUID] = player
			continue
		}
		if indent >= baseIndent+6 && currentList && strings.HasPrefix(stripped, "- ") {
			player.Commands = append(player.Commands, yamlScalar(strings.TrimPrefix(stripped, "- ")))
			players[currentUUID] = player
		}
	}
	return players
}

func extractTopLevelBlock(text, key string) string {
	lines := strings.Split(text, "\n")
	start, end := -1, len(lines)
	for i, line := range lines {
		if strings.TrimSpace(line) == key+":" && len(line)-len(strings.TrimLeft(line, " ")) == 0 {
			start = i
			continue
		}
		if start >= 0 && i > start && strings.TrimSpace(line) != "" && len(line)-len(strings.TrimLeft(line, " ")) == 0 {
			end = i
			break
		}
	}
	if start < 0 {
		return ""
	}
	return strings.Join(lines[start:end], "\n")
}

func replaceTopLevelBlock(text, key, replacement string) string {
	lines := strings.Split(text, "\n")
	start, end := -1, len(lines)
	for i, line := range lines {
		if strings.TrimSpace(line) == key+":" && len(line)-len(strings.TrimLeft(line, " ")) == 0 {
			start = i
			continue
		}
		if start >= 0 && i > start && strings.TrimSpace(line) != "" && len(line)-len(strings.TrimLeft(line, " ")) == 0 {
			end = i
			break
		}
	}
	if start < 0 {
		if strings.TrimSpace(text) == "" {
			return replacement
		}
		return strings.TrimRight(text, "\n") + "\n\n" + replacement
	}
	return strings.Join(append(append([]string{}, lines[:start]...), append(strings.Split(replacement, "\n"), lines[end:]...)...), "\n")
}

func renderPermissionPlayersBlock(players map[string]permissionConfigPlayer, blockKey, listKey string) string {
	keys := make([]string, 0, len(players))
	for uuid, player := range players {
		if len(player.Commands) > 0 {
			keys = append(keys, uuid)
		}
	}
	sort.SliceStable(keys, func(i, j int) bool {
		return strings.ToLower(players[keys[i]].Name) < strings.ToLower(players[keys[j]].Name)
	})
	lines := []string{blockKey + ":"}
	for _, uuid := range keys {
		player := players[uuid]
		commands := uniqueSorted(player.Commands)
		lines = append(lines, "  "+canonicalUUID(uuid)+":", "    name: "+yamlQuote(player.Name), "    "+listKey+":")
		for _, command := range commands {
			lines = append(lines, "      - "+yamlQuote(command))
		}
	}
	return strings.Join(lines, "\n")
}

func renderClaimAccessBlock(config permissionConfig) string {
	lines := []string{"access:"}
	defaultActions := uniqueSorted(config.Default)
	if len(defaultActions) == 0 {
		lines = append(lines, "  default-allowed-actions: []")
	} else {
		lines = append(lines, "  default-allowed-actions:")
		for _, action := range defaultActions {
			lines = append(lines, "    - "+yamlQuote(action))
		}
	}
	for _, line := range strings.Split(renderPermissionPlayersBlock(config.Players, "players", "actions"), "\n") {
		lines = append(lines, "  "+line)
	}
	return strings.Join(lines, "\n")
}

func uniqueSorted(values []string) []string {
	seen := map[string]string{}
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value != "" {
			seen[strings.ToLower(value)] = value
		}
	}
	result := make([]string, 0, len(seen))
	for _, value := range seen {
		result = append(result, value)
	}
	sort.SliceStable(result, func(i, j int) bool { return strings.ToLower(result[i]) < strings.ToLower(result[j]) })
	return result
}

func yamlQuote(value string) string {
	if value == "" {
		return "''"
	}
	for _, ch := range value {
		if (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.' {
			continue
		}
		return "'" + strings.ReplaceAll(value, "'", "''") + "'"
	}
	return value
}

func (a *app) auditSourceSuggestions(term string, limit int) []suggestionItem {
	query := strings.ToLower(strings.TrimSpace(term))
	if query == "" {
		return nil
	}
	type aggregate struct {
		name    string
		uuid    string
		sources map[string]bool
	}
	players := map[string]*aggregate{}
	add := func(name, uuid, source string) {
		if name == "" || uuid == "" {
			return
		}
		key := strings.ToLower(canonicalUUID(uuid))
		if players[key] == nil {
			players[key] = &aggregate{name: name, uuid: canonicalUUID(uuid), sources: map[string]bool{}}
		}
		players[key].sources[source] = true
	}
	if entries, err := a.readWhitelistEntries(); err == nil {
		for _, entry := range entries {
			add(entry.Name, entry.UUID, "白名单")
		}
	}
	if a.db != nil {
		if entries, err := a.activeBlacklistEntries(context.Background()); err == nil {
			for _, entry := range entries {
				add(entry.PlayerName, entry.PlayerUUID, "黑名单")
			}
		}
	}
	compact := strings.ReplaceAll(query, "-", "")
	items := []suggestionItem{}
	for _, player := range players {
		nameMatch := strings.Contains(strings.ToLower(player.name), query)
		uuidMatch := strings.Contains(strings.ToLower(player.uuid), query) || strings.Contains(strings.ReplaceAll(strings.ToLower(player.uuid), "-", ""), compact)
		if !nameMatch && !uuidMatch {
			continue
		}
		source := []string{}
		for value := range player.sources {
			source = append(source, value)
		}
		sort.Strings(source)
		match := "玩家ID"
		value := player.name
		if uuidMatch && !nameMatch {
			match = "UUID"
			value = player.uuid
		}
		items = append(items, suggestionItem{Label: player.name, Value: value, UUID: player.uuid, Match: match, Source: strings.Join(source, "、")})
	}
	sort.SliceStable(items, func(i, j int) bool {
		left := strings.HasPrefix(strings.ToLower(items[i].Label), query)
		right := strings.HasPrefix(strings.ToLower(items[j].Label), query)
		if left != right {
			return left
		}
		return strings.ToLower(items[i].Label) < strings.ToLower(items[j].Label)
	})
	if limit > 0 && len(items) > limit {
		items = items[:limit]
	}
	return items
}

func auditQueryFromValues(values url.Values) auditQuery {
	return auditQuery{
		Submitted:  values.Get("run") == "1" || values.Get("submitted") == "1",
		Action:     strings.TrimSpace(values.Get("action")),
		Player:     strings.TrimSpace(values.Get("player")),
		World:      strings.TrimSpace(values.Get("world")),
		TargetType: strings.TrimSpace(values.Get("target_type")),
		ItemType:   strings.TrimSpace(values.Get("item_type")),
		TimeFrom:   strings.TrimSpace(values.Get("time_from")),
		TimeTo:     strings.TrimSpace(values.Get("time_to")),
		Radius:     atoi(values.Get("radius")),
		X:          strings.TrimSpace(values.Get("x")),
		Y:          strings.TrimSpace(values.Get("y")),
		Z:          strings.TrimSpace(values.Get("z")),
		Cursor:     int64(atoi(values.Get("cursor"))),
	}
}

func (a *app) queryAudit(ctx context.Context, query auditQuery) ([]auditRow, string, error) {
	if a.db == nil {
		return nil, "", errors.New("database unavailable")
	}
	clauses := []string{"created_at >= $1"}
	args := []any{time.Now().Add(-time.Duration(a.cfg.AuditRetentionDays) * 24 * time.Hour).UnixMilli()}
	add := func(clause string, value any) {
		args = append(args, value)
		clauses = append(clauses, strings.ReplaceAll(clause, "?", "$"+strconv.Itoa(len(args))))
	}
	if query.Action != "" {
		add("action = ?", query.Action)
	}
	if query.Player != "" {
		player := strings.ToLower(query.Player)
		if len(strings.ReplaceAll(player, "-", "")) == 32 {
			add("lower(player_uuid) = ?", strings.ToLower(canonicalUUID(player)))
		} else {
			add("lower(player_name) = ?", player)
		}
	}
	if query.World != "" {
		add("world = ?", query.World)
	}
	if query.TargetType != "" {
		add("target_type = ?", query.TargetType)
	}
	if query.ItemType != "" {
		add("item_type ILIKE ?", "%"+query.ItemType+"%")
	}
	if from := parseDatetimeLocal(query.TimeFrom); from > 0 {
		add("created_at >= ?", from)
	}
	if to := parseDatetimeLocal(query.TimeTo); to > 0 {
		add("created_at <= ?", to)
	}
	if query.Cursor > 0 {
		add("id < ?", query.Cursor)
	}
	sqlText := `SELECT id, created_at, action, player_uuid, player_name, world, x, y, z, target_type, COALESCE(item_type,''), item_amount, COALESCE(details,'') FROM audit_log WHERE ` + strings.Join(clauses, " AND ") + ` ORDER BY id DESC LIMIT 51`
	rows, err := a.db.QueryContext(ctx, sqlText, args...)
	if err != nil {
		return nil, "", err
	}
	defer rows.Close()
	result := []auditRow{}
	for rows.Next() {
		var row auditRow
		if err := rows.Scan(&row.ID, &row.CreatedAt, &row.Action, &row.PlayerUUID, &row.PlayerName, &row.World, &row.X, &row.Y, &row.Z, &row.TargetType, &row.ItemType, &row.ItemAmount, &row.Details); err != nil {
			return nil, "", err
		}
		result = append(result, row)
	}
	next := ""
	if len(result) > 50 {
		last := result[49]
		result = result[:50]
		values := url.Values{}
		values.Set("run", "1")
		values.Set("cursor", strconv.FormatInt(last.ID, 10))
		for key, value := range map[string]string{"action": query.Action, "player": query.Player, "world": query.World, "target_type": query.TargetType, "item_type": query.ItemType, "time_from": query.TimeFrom, "time_to": query.TimeTo, "radius": strconv.Itoa(query.Radius), "x": query.X, "y": query.Y, "z": query.Z} {
			if value != "" && value != "0" {
				values.Set(key, value)
			}
		}
		next = values.Encode()
	}
	return result, next, rows.Err()
}

func parseDatetimeLocal(value string) int64 {
	if value == "" {
		return 0
	}
	layouts := []string{"2006-01-02T15:04", "2006-01-02 15:04:05", "2006-01-02"}
	for _, layout := range layouts {
		if parsed, err := time.ParseInLocation(layout, value, time.Local); err == nil {
			return parsed.UnixMilli()
		}
	}
	return 0
}
