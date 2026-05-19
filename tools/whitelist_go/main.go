package main

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/md5"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"html/template"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"

	_ "github.com/lib/pq"
)

const (
	playerRole        = "玩家"
	adminRole         = "管理员"
	ownerRole         = "服主"
	minecraftVersion  = "1.21.11"
	defaultPassword   = "123456"
	defaultProfileBio = "一名普通的Minecraft玩家"
	sessionCookieName = "xicemc_session"
	sessionSeconds    = 7 * 24 * 60 * 60
)

var (
	usernameRE   = regexp.MustCompile(`^[A-Za-z0-9_]{3,16}$`)
	passwordRE   = regexp.MustCompile(`^[A-Za-z0-9_]{6,64}$`)
	onlineListRE = regexp.MustCompile(`There are\s+(\d+)\s+of a max of\s+(\d+)\s+players online`)
)

type config struct {
	Host                        string
	Port                        string
	RconHost                    string
	RconPort                    string
	RconPassword                string
	RuntimeDir                  string
	BackupDir                   string
	WhitelistPath               string
	VerifyCodesPath             string
	BlacklistPath               string
	ClaimsPath                  string
	ServerLogPath               string
	ServiceName                 string
	CommandControlConfigPath    string
	ClaimConfigPath             string
	MorePotionEffectsConfigPath string
	WebIconPath                 string
	WebFaviconPath              string
	ClaimTotemConceptPath       string
	ResourcePackPath            string
	ServerDocsPath              string
	ServerDocsMaxLength         int
	SessionSecret               string
	PublicSiteBaseURL           string
	PublicSiteDomain            string
	ICPRecordNo                 string
	ICPRecordURL                string
	PublicSecurityRecordNo      string
	PublicSecurityRecordURL     string
	ProtectedOwnerUUID          string
	DBHost                      string
	DBPort                      string
	DBName                      string
	DBUser                      string
	DBPassword                  string
	AuditRetentionDays          int
}

type app struct {
	cfg           config
	db            *sql.DB
	whitelistLock sync.Mutex
	codeLock      sync.Mutex
	templates     *template.Template
}

type whitelistEntry struct {
	UUID string `json:"uuid"`
	Name string `json:"name"`
}

type userSession struct {
	UUID string
	Name string
	Role string
}

type webPlayer struct {
	UUID         string
	Name         string
	PasswordHash string
	Role         string
	ProfileBio   string
	RegisteredAt int64
}

type verificationCode struct {
	Key       string
	UUID      string
	Name      string
	Code      string
	ExpiresAt int64
}

type pageData struct {
	Title        string
	User         *userSession
	Active       string
	Public       publicData
	Message      string
	Error        string
	LoginOpen    bool
	RegisterOpen bool
	Profile      webPlayer
	Claims       claimGroups
	Status       serverStatus
	Players      []playerRow
	Reports      []reportRow
	Blacklist    []blacklistRow
	Permissions  permissionPageData
	Audit        auditPageData
	DocsText     string
}

type claimGroups struct {
	Owned   []claim
	Trusted []claim
}

type claim struct {
	ID         string
	Name       string
	OwnerUUID  string
	OwnerName  string
	World      string
	MinX       int
	MaxX       int
	MinY       int
	MaxY       int
	MinZ       int
	MaxZ       int
	SizeX      int
	SizeY      int
	SizeZ      int
	Members    []string
	MemberName map[string]string
	Totem      *claimTotem
}

type claimTotem struct {
	ID    string
	World string
	X     int
	Y     int
	Z     int
}

type publicData struct {
	SiteBaseURL             string
	SiteDomain              string
	ICPRecordNo             string
	ICPRecordURL            string
	PublicSecurityRecordNo  string
	PublicSecurityRecordURL string
}

type serverStatus struct {
	ServerState   statusState
	OnlinePlayers statusMetric
	Disk          statusMetric
	Memory        statusMetric
	Backups       []backupFile
	LogErrors     []string
	LogErrorCount int
	LogScanLines  int
}

type statusState struct {
	Label  string
	Class  string
	Detail string
}

type statusMetric struct {
	Value   string
	Detail  string
	Percent float64
	Level   string
}

type backupFile struct {
	Name  string
	Size  string
	MTime string
}

func main() {
	cfg := loadConfig()
	db, err := openDB(cfg)
	if err != nil {
		log.Printf("database unavailable: %v", err)
	}
	a := &app{cfg: cfg, db: db, templates: mustTemplates()}
	if db != nil {
		if err := a.initWebTables(context.Background()); err != nil {
			log.Printf("failed to initialize web tables: %v", err)
		}
	}

	mux := http.NewServeMux()
	a.routes(mux)
	addr := net.JoinHostPort(cfg.Host, cfg.Port)
	log.Printf("Xice Go web listening on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatal(err)
	}
}

func loadConfig() config {
	repoRoot := filepath.Clean(filepath.Join(".", ""))
	runtimeDir := env("XICEMC_RUNTIME_DIR", "/opt/xicemc/runtime")
	rconPassword := env("XICEMC_RCON_PASSWORD", "")
	return config{
		Host:                        env("WHITELIST_WEB_HOST", "0.0.0.0"),
		Port:                        env("WHITELIST_WEB_PORT", "8080"),
		RconHost:                    env("XICEMC_RCON_HOST", "127.0.0.1"),
		RconPort:                    env("XICEMC_RCON_PORT", "25575"),
		RconPassword:                rconPassword,
		RuntimeDir:                  runtimeDir,
		BackupDir:                   env("XICEMC_BACKUP_DIR", "/opt/xicemc/backups"),
		WhitelistPath:               env("XICEMC_WHITELIST_PATH", filepath.Join(runtimeDir, "whitelist.json")),
		VerifyCodesPath:             env("XICEMC_VERIFY_CODES_PATH", filepath.Join(runtimeDir, "plugins", "XiceTextArranger", "verification-codes.tsv")),
		BlacklistPath:               env("XICEMC_BLACKLIST_PATH", filepath.Join(runtimeDir, "plugins", "XiceTextArranger", "blacklist.tsv")),
		ClaimsPath:                  env("XICEMC_CLAIMS_PATH", filepath.Join(runtimeDir, "plugins", "XiceClaim", "claims.yml")),
		ServerLogPath:               env("XICEMC_SERVER_LOG_PATH", filepath.Join(runtimeDir, "logs", "latest.log")),
		ServiceName:                 env("XICEMC_SERVICE_NAME", "xicemc.service"),
		CommandControlConfigPath:    env("XICEMC_COMMAND_CONTROL_CONFIG_PATH", filepath.Join(runtimeDir, "plugins", "XiceCommandControl", "config.yml")),
		ClaimConfigPath:             env("XICEMC_CLAIM_CONFIG_PATH", filepath.Join(runtimeDir, "plugins", "XiceClaim", "config.yml")),
		MorePotionEffectsConfigPath: env("XICEMC_MORE_POTION_EFFECTS_CONFIG_PATH", filepath.Join(runtimeDir, "plugins", "XiceMorePotionEffects", "config.yml")),
		WebIconPath:                 env("XICEMC_WEB_ICON_PATH", filepath.Join(repoRoot, "server", "assets", "xicemc-logo.png")),
		WebFaviconPath:              env("XICEMC_WEB_FAVICON_PATH", filepath.Join(repoRoot, "server", "assets", "favicon.ico")),
		ClaimTotemConceptPath:       env("XICEMC_CLAIM_TOTEM_CONCEPT_PATH", filepath.Join(repoRoot, "server", "assets", "xiceclaim-totem-concept.png")),
		ResourcePackPath:            env("XICEMC_RESOURCE_PACK_PATH", filepath.Join(repoRoot, "server", "resourcepacks", "xiceclaim.zip")),
		ServerDocsPath:              env("XICEMC_DOCS_HOME_PATH", filepath.Join(runtimeDir, "web", "server-docs.md")),
		ServerDocsMaxLength:         envInt("XICEMC_DOCS_MAX_LENGTH", 100000),
		SessionSecret:               env("WHITELIST_WEB_SESSION_SECRET", rconPassword),
		PublicSiteBaseURL:           strings.TrimRight(env("XICEMC_PUBLIC_SITE_BASE_URL", "http://150.158.93.80"), "/"),
		PublicSiteDomain:            env("XICEMC_PUBLIC_SITE_DOMAIN", "xicemc.site"),
		ICPRecordNo:                 env("XICEMC_ICP_RECORD_NO", ""),
		ICPRecordURL:                env("XICEMC_ICP_RECORD_URL", "https://beian.miit.gov.cn/"),
		PublicSecurityRecordNo:      env("XICEMC_PUBLIC_SECURITY_RECORD_NO", ""),
		PublicSecurityRecordURL:     env("XICEMC_PUBLIC_SECURITY_RECORD_URL", ""),
		ProtectedOwnerUUID:          canonicalUUID(env("XICEMC_PROTECTED_OWNER_UUID", "")),
		DBHost:                      env("XICE_AUDIT_DB_HOST", "127.0.0.1"),
		DBPort:                      env("XICE_AUDIT_DB_PORT", "5432"),
		DBName:                      env("XICE_AUDIT_DB_NAME", "xicemc_audit"),
		DBUser:                      env("XICE_AUDIT_DB_USER", "xicemc_audit"),
		DBPassword:                  env("XICE_AUDIT_DB_PASSWORD", ""),
		AuditRetentionDays:          envInt("XICE_AUDIT_RETENTION_DAYS", 3),
	}
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func envInt(name string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback
	}
	parsed := atoi(value)
	if parsed == 0 && value != "0" {
		return fallback
	}
	return parsed
}

func openDB(cfg config) (*sql.DB, error) {
	if cfg.DBPassword == "" {
		return nil, errors.New("XICE_AUDIT_DB_PASSWORD is empty")
	}
	dsn := fmt.Sprintf(
		"host=%s port=%s dbname=%s user=%s password=%s sslmode=disable connect_timeout=3",
		cfg.DBHost, cfg.DBPort, cfg.DBName, cfg.DBUser, cfg.DBPassword,
	)
	db, err := sql.Open("postgres", dsn)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	db.SetMaxOpenConns(8)
	db.SetMaxIdleConns(4)
	db.SetConnMaxLifetime(30 * time.Minute)
	return db, nil
}

func (a *app) routes(mux *http.ServeMux) {
	mux.HandleFunc("GET /favicon.png", a.fileHandler(a.cfg.WebIconPath, "image/png"))
	mux.HandleFunc("GET /favicon.ico", a.fileHandler(a.cfg.WebFaviconPath, "image/x-icon"))
	mux.HandleFunc("GET /assets/xiceclaim-totem-concept.png", a.fileHandler(a.cfg.ClaimTotemConceptPath, "image/png"))
	mux.HandleFunc("GET /resourcepacks/xiceclaim.zip", a.fileHandler(a.cfg.ResourcePackPath, "application/zip"))
	mux.HandleFunc("GET /", a.handlePublicHome)
	mux.HandleFunc("GET /tech", a.handlePublicStatic("publicTech", "技术实现", "public-tech"))
	mux.HandleFunc("GET /plugins", a.handlePublicStatic("publicPlugins", "插件介绍", "public-plugins"))
	mux.HandleFunc("GET /plugins/xiceclaim", a.handlePublicStatic("pluginXiceClaim", "XiceClaim 领地插件", "public-plugins"))
	mux.HandleFunc("GET /plugins/xiceauditlog", a.handlePublicStatic("pluginXiceAuditLog", "XiceAuditLog 审计插件", "public-plugins"))
	mux.HandleFunc("GET /plugins/xicecommandcontrol", a.handlePublicStatic("pluginXiceCommandControl", "XiceCommandControl 指令权限插件", "public-plugins"))
	mux.HandleFunc("GET /plugins/xicemorepotioneffects", a.handlePublicStatic("pluginXiceMorePotionEffects", "XiceMorePotionEffects 更多药水效果插件", "public-plugins"))
	mux.HandleFunc("GET /plugins/xicetextarranger", a.handlePublicStatic("pluginXiceTextArranger", "XiceTextArranger 文本交互插件", "public-plugins"))
	mux.HandleFunc("GET /ops", a.handlePublicStatic("publicOps", "运维记录", "public-ops"))
	mux.HandleFunc("GET /changelog", a.handlePublicStatic("publicChangelog", "更新日志", "public-changelog"))
	mux.HandleFunc("GET /register", a.handleRegisterPage)
	mux.HandleFunc("POST /login", a.handleLogin)
	mux.HandleFunc("POST /register", a.handleRegister)
	mux.HandleFunc("POST /logout", a.handleLogout)
	mux.HandleFunc("GET /home", a.requireUser(a.handleHome))
	mux.HandleFunc("GET /password", a.requireUser(a.handlePasswordPage))
	mux.HandleFunc("POST /password", a.requireUser(a.handlePasswordUpdate))
	mux.HandleFunc("POST /profile", a.requireUser(a.handleProfileUpdate))
	mux.HandleFunc("GET /docs", a.requireUser(a.handleDocs))
	mux.HandleFunc("GET /status", a.requireUser(a.handleStatus))
	mux.HandleFunc("GET /players", a.requireUser(a.handlePlayers))
	mux.HandleFunc("GET /players/suggestions", a.requireUser(a.handlePlayerSuggestions))
	mux.HandleFunc("GET /audit/source-suggestions", a.requireUser(a.handlePlayerSuggestions))
	mux.HandleFunc("POST /players/role", a.requireOwner(a.handlePlayerRole))
	mux.HandleFunc("POST /players/reset-password", a.requireOwner(a.handlePlayerPasswordReset))
	mux.HandleFunc("GET /audit", a.requireUser(a.handleAudit))
	mux.HandleFunc("GET /report", a.requireUser(a.handleReportPage))
	mux.HandleFunc("POST /report", a.requireUser(a.handleReportSubmit))
	mux.HandleFunc("GET /reports", a.requireManager(a.handleReports))
	mux.HandleFunc("POST /reports/process", a.requireManager(a.handleReportProcess))
	mux.HandleFunc("GET /blacklist", a.requireUser(a.handleBlacklist))
	mux.HandleFunc("POST /blacklist/add", a.requireManager(a.handleBlacklistAdd))
	mux.HandleFunc("POST /blacklist/remove", a.requireManager(a.handleBlacklistRemove))
	mux.HandleFunc("GET /permissions", a.requireManager(a.handlePermissions))
	mux.HandleFunc("POST /permissions/add", a.requireManager(a.handlePermissionAdd))
	mux.HandleFunc("POST /permissions/remove", a.requireManager(a.handlePermissionRemove))
	mux.HandleFunc("GET /docs/edit", a.requireManager(a.handleDocsEditPage))
	mux.HandleFunc("POST /docs/edit", a.requireManager(a.handleDocsUpdate))
}

func (a *app) fileHandler(path, contentType string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		data, err := os.ReadFile(path)
		if err != nil {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", contentType)
		w.Header().Set("Cache-Control", "public, max-age=3600")
		w.Header().Set("Content-Length", fmt.Sprint(len(data)))
		_, _ = w.Write(data)
	}
}

func (a *app) handlePublicHome(w http.ResponseWriter, r *http.Request) {
	if user := a.currentUser(r); user != nil {
		http.Redirect(w, r, "/home", http.StatusSeeOther)
		return
	}
	a.render(w, http.StatusOK, "public", pageData{Title: "个人技术开发随记", Public: a.publicData(), Active: "public-home"})
}

func (a *app) handlePublicStatic(templateName, title, active string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		a.render(w, http.StatusOK, templateName, pageData{
			Title:  title,
			Public: a.publicData(),
			Active: active,
		})
	}
}

func (a *app) handleRegisterPage(w http.ResponseWriter, r *http.Request) {
	a.render(w, http.StatusOK, "public", pageData{
		Title:        "个人技术开发随记",
		Public:       a.publicData(),
		Active:       "public-home",
		RegisterOpen: true,
	})
}

func (a *app) handleHome(w http.ResponseWriter, r *http.Request, user *userSession) {
	profile, _ := a.webPlayerByUUID(r.Context(), user.UUID)
	claims := a.playerClaims(user)
	a.render(w, http.StatusOK, "home", pageData{
		Title:   "首页",
		User:    user,
		Active:  "home",
		Public:  a.publicData(),
		Profile: profile,
		Claims:  claims,
	})
}

func (a *app) handlePasswordPage(w http.ResponseWriter, r *http.Request, user *userSession) {
	a.render(w, http.StatusOK, "password", pageData{
		Title:  "修改密码",
		User:   user,
		Active: "home",
		Public: a.publicData(),
	})
}

func (a *app) handlePasswordUpdate(w http.ResponseWriter, r *http.Request, user *userSession) {
	if err := r.ParseForm(); err != nil {
		a.renderPassword(w, user, "请求格式不正确。", http.StatusBadRequest)
		return
	}
	oldPassword := strings.TrimSpace(r.FormValue("old_password"))
	newPassword := strings.TrimSpace(r.FormValue("new_password"))
	confirmPassword := strings.TrimSpace(r.FormValue("confirm_password"))
	if !passwordRE.MatchString(oldPassword) {
		a.renderPassword(w, user, "原密码格式不正确。", http.StatusBadRequest)
		return
	}
	if !passwordRE.MatchString(newPassword) {
		a.renderPassword(w, user, "新密码长度不得低于 6 位，且只能包含英文、数字和下划线。", http.StatusBadRequest)
		return
	}
	if newPassword != confirmPassword {
		a.renderPassword(w, user, "两次输入的新密码不一致。", http.StatusBadRequest)
		return
	}
	player, err := a.webPlayerByUUID(r.Context(), user.UUID)
	if err != nil {
		a.renderPassword(w, user, "密码服务暂时不可用。", http.StatusInternalServerError)
		return
	}
	if !hmac.Equal([]byte(player.PasswordHash), []byte(passwordHash(oldPassword))) {
		a.renderPassword(w, user, "原密码不正确。", http.StatusForbidden)
		return
	}
	if err := a.updatePlayerPassword(r.Context(), user.UUID, newPassword); err != nil {
		a.renderPassword(w, user, "密码更新失败。", http.StatusInternalServerError)
		return
	}
	a.renderPassword(w, user, "密码已更新。", http.StatusOK)
}

func (a *app) renderPassword(w http.ResponseWriter, user *userSession, message string, status int) {
	a.render(w, status, "password", pageData{
		Title:   "修改密码",
		User:    user,
		Active:  "home",
		Public:  a.publicData(),
		Message: message,
	})
}

func (a *app) handleProfileUpdate(w http.ResponseWriter, r *http.Request, user *userSession) {
	if err := r.ParseForm(); err != nil {
		a.renderHomeMessage(w, r, user, "请求格式不正确。", http.StatusBadRequest)
		return
	}
	value := strings.TrimSpace(r.FormValue("profile_bio"))
	if value == "" {
		value = defaultProfileBio
	}
	if len([]rune(value)) > 120 {
		a.renderHomeMessage(w, r, user, "个人简介不能超过 120 个字符。", http.StatusBadRequest)
		return
	}
	if err := a.updateProfileBio(r.Context(), user.UUID, value); err != nil {
		a.renderHomeMessage(w, r, user, "简介更新失败。", http.StatusInternalServerError)
		return
	}
	a.renderHomeMessage(w, r, user, "简介已更新。", http.StatusOK)
}

func (a *app) renderHomeMessage(w http.ResponseWriter, r *http.Request, user *userSession, message string, status int) {
	profile, _ := a.webPlayerByUUID(r.Context(), user.UUID)
	a.render(w, status, "home", pageData{
		Title:   "首页",
		User:    user,
		Active:  "home",
		Public:  a.publicData(),
		Profile: profile,
		Claims:  a.playerClaims(user),
		Message: message,
	})
}

func (a *app) handleStatus(w http.ResponseWriter, r *http.Request, user *userSession) {
	a.render(w, http.StatusOK, "status", pageData{
		Title:  "服务器状态",
		User:   user,
		Active: "status",
		Public: a.publicData(),
		Status: a.loadServerStatus(r.Context()),
	})
}

func (a *app) handleDocs(w http.ResponseWriter, r *http.Request, user *userSession) {
	data, err := os.ReadFile(a.cfg.ServerDocsPath)
	if err != nil || len(data) == 0 {
		data = []byte("服务器文档尚未初始化。")
	}
	a.render(w, http.StatusOK, "docs", pageData{
		Title:   "服务器文档",
		User:    user,
		Active:  "docs",
		Public:  a.publicData(),
		Message: string(data),
	})
}

func (a *app) handleLogin(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		a.renderPublicError(w, "请求格式不正确。", false)
		return
	}
	username := strings.TrimSpace(r.FormValue("username"))
	password := strings.TrimSpace(r.FormValue("password"))
	if !usernameRE.MatchString(username) {
		a.renderPublicError(w, "Minecraft ID 格式不正确。", false)
		return
	}
	if !passwordRE.MatchString(password) {
		a.renderPublicError(w, "密码格式不正确。", false)
		return
	}
	entry, ok := a.whitelistEntry(username)
	if !ok {
		a.renderPublicError(w, "该 ID 暂无后台权限，请使用注册入口完成登记。", false)
		return
	}
	player, err := a.webPlayerByEntry(r.Context(), entry)
	if err != nil {
		a.renderPublicError(w, "登录服务暂时不可用。", false)
		return
	}
	if !hmac.Equal([]byte(player.PasswordHash), []byte(passwordHash(password))) {
		a.renderPublicError(w, "Minecraft ID 或密码不正确。", false)
		return
	}
	sessionValue, err := a.makeSession(entry)
	if err != nil {
		a.renderPublicError(w, "登录服务暂时不可用。", false)
		return
	}
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookieName,
		Value:    sessionValue,
		Path:     "/",
		MaxAge:   sessionSeconds,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	})
	http.Redirect(w, r, "/home", http.StatusSeeOther)
}

func (a *app) handleRegister(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		a.renderPublicError(w, "请求格式不正确。", true)
		return
	}
	username := strings.TrimSpace(r.FormValue("username"))
	code := strings.TrimSpace(r.FormValue("verification_code"))
	if !usernameRE.MatchString(username) {
		a.renderPublicError(w, "Minecraft ID 格式不正确。", true)
		return
	}
	entry, ok := a.findVerificationCode(username, code)
	if !ok {
		a.renderPublicError(w, "验证码不正确或已过期。请重新进入服务器获取新的验证码。", true)
		return
	}
	added, err := a.addWhitelistEntry(whitelistEntry{UUID: entry.UUID, Name: entry.Name})
	if err != nil {
		a.renderPublicError(w, "白名单写入失败。", true)
		return
	}
	if _, err := a.rcon("whitelist reload"); err != nil {
		a.renderPublicError(w, "白名单已写入，但刷新服务暂时失败，请稍后重试。", true)
		return
	}
	_ = a.ensureWebPlayer(r.Context(), whitelistEntry{UUID: entry.UUID, Name: entry.Name})
	_ = a.consumeVerificationCode(username, code)
	message := "白名单已提交。Web 默认登录密码为 123456，登录后请修改密码。"
	if !added {
		message = "该玩家已在白名单内，白名单已刷新。Web 默认登录密码为 123456。"
	}
	a.render(w, http.StatusOK, "public", pageData{
		Title:        "个人技术开发随记",
		Public:       a.publicData(),
		RegisterOpen: true,
		Message:      message,
	})
}

func (a *app) handleLogout(w http.ResponseWriter, r *http.Request) {
	http.SetCookie(w, &http.Cookie{Name: sessionCookieName, Value: "", Path: "/", MaxAge: 0, HttpOnly: true, SameSite: http.SameSiteLaxMode})
	http.Redirect(w, r, "/", http.StatusSeeOther)
}

func (a *app) renderPublicError(w http.ResponseWriter, message string, register bool) {
	data := pageData{Title: "个人技术开发随记", Public: a.publicData(), Active: "public-home", Error: message}
	if register {
		data.RegisterOpen = true
	} else {
		data.LoginOpen = true
	}
	a.render(w, http.StatusBadRequest, "public", data)
}

func (a *app) render(w http.ResponseWriter, status int, name string, data pageData) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	if err := a.templates.ExecuteTemplate(w, name, data); err != nil {
		log.Printf("render %s failed: %v", name, err)
	}
}

func (a *app) publicData() publicData {
	return publicData{
		SiteBaseURL:             a.cfg.PublicSiteBaseURL,
		SiteDomain:              a.cfg.PublicSiteDomain,
		ICPRecordNo:             a.cfg.ICPRecordNo,
		ICPRecordURL:            a.cfg.ICPRecordURL,
		PublicSecurityRecordNo:  a.cfg.PublicSecurityRecordNo,
		PublicSecurityRecordURL: a.cfg.PublicSecurityRecordURL,
	}
}

func (a *app) requireUser(next func(http.ResponseWriter, *http.Request, *userSession)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		user := a.currentUser(r)
		if user == nil {
			http.Redirect(w, r, "/", http.StatusSeeOther)
			return
		}
		next(w, r, user)
	}
}

func (a *app) requireManager(next func(http.ResponseWriter, *http.Request, *userSession)) http.HandlerFunc {
	return a.requireUser(func(w http.ResponseWriter, r *http.Request, user *userSession) {
		if user.Role != adminRole && user.Role != ownerRole {
			http.Error(w, "无权访问", http.StatusForbidden)
			return
		}
		next(w, r, user)
	})
}

func (a *app) requireOwner(next func(http.ResponseWriter, *http.Request, *userSession)) http.HandlerFunc {
	return a.requireUser(func(w http.ResponseWriter, r *http.Request, user *userSession) {
		if user.Role != ownerRole {
			http.Error(w, "无权访问", http.StatusForbidden)
			return
		}
		next(w, r, user)
	})
}

func (a *app) currentUser(r *http.Request) *userSession {
	cookie, err := r.Cookie(sessionCookieName)
	if err != nil || cookie.Value == "" {
		return nil
	}
	parts := strings.Split(cookie.Value, ".")
	if len(parts) != 2 || !hmac.Equal([]byte(a.sign(parts[0])), []byte(parts[1])) {
		return nil
	}
	payloadBytes, err := b64urlDecode(parts[0])
	if err != nil {
		return nil
	}
	var payload struct {
		UUID string `json:"uuid"`
		Name string `json:"name"`
		Exp  int64  `json:"exp"`
	}
	if err := json.Unmarshal(payloadBytes, &payload); err != nil || payload.Exp < time.Now().Unix() {
		return nil
	}
	entry, ok := a.whitelistEntryByUUID(payload.UUID)
	if !ok {
		entry, ok = a.whitelistEntry(payload.Name)
	}
	if !ok || canonicalUUID(entry.UUID) != canonicalUUID(payload.UUID) {
		return nil
	}
	role := playerRole
	if player, err := a.webPlayerByEntry(r.Context(), entry); err == nil && player.Role != "" {
		role = player.Role
	}
	return &userSession{UUID: canonicalUUID(entry.UUID), Name: entry.Name, Role: role}
}

func (a *app) makeSession(entry whitelistEntry) (string, error) {
	payload := struct {
		UUID string `json:"uuid"`
		Name string `json:"name"`
		Exp  int64  `json:"exp"`
	}{UUID: canonicalUUID(entry.UUID), Name: entry.Name, Exp: time.Now().Unix() + sessionSeconds}
	data, err := json.Marshal(payload)
	if err != nil {
		return "", err
	}
	body := b64url(data)
	return body + "." + a.sign(body), nil
}

func (a *app) sign(value string) string {
	mac := hmac.New(sha256.New, []byte(a.cfg.SessionSecret))
	_, _ = mac.Write([]byte(value))
	return hex.EncodeToString(mac.Sum(nil))
}

func b64url(data []byte) string {
	return strings.TrimRight(base64.URLEncoding.EncodeToString(data), "=")
}

func b64urlDecode(value string) ([]byte, error) {
	if rem := len(value) % 4; rem != 0 {
		value += strings.Repeat("=", 4-rem)
	}
	return base64.URLEncoding.DecodeString(value)
}

func passwordHash(password string) string {
	sum := md5.Sum([]byte(password))
	return hex.EncodeToString(sum[:])
}

func canonicalUUID(value string) string {
	compact := strings.ToLower(strings.ReplaceAll(value, "-", ""))
	if len(compact) != 32 {
		return value
	}
	return fmt.Sprintf("%s-%s-%s-%s-%s", compact[0:8], compact[8:12], compact[12:16], compact[16:20], compact[20:32])
}

func formatTimeMillis(value int64) string {
	if value <= 0 {
		return "暂无记录"
	}
	return time.UnixMilli(value).Local().Format("2006-01-02 15:04:05")
}

func (a *app) readWhitelistEntries() ([]whitelistEntry, error) {
	data, err := os.ReadFile(a.cfg.WhitelistPath)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var entries []whitelistEntry
	if err := json.Unmarshal(data, &entries); err != nil {
		return nil, err
	}
	return entries, nil
}

func (a *app) writeWhitelistEntries(entries []whitelistEntry) error {
	if err := os.MkdirAll(filepath.Dir(a.cfg.WhitelistPath), 0o755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(entries, "", "  ")
	if err != nil {
		return err
	}
	data = append(data, '\n')
	tmp := a.cfg.WhitelistPath + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, a.cfg.WhitelistPath)
}

func (a *app) whitelistEntry(name string) (whitelistEntry, bool) {
	entries, err := a.readWhitelistEntries()
	if err != nil {
		return whitelistEntry{}, false
	}
	for _, entry := range entries {
		if strings.EqualFold(entry.Name, name) && entry.UUID != "" && entry.Name != "" {
			entry.UUID = canonicalUUID(entry.UUID)
			return entry, true
		}
	}
	return whitelistEntry{}, false
}

func (a *app) whitelistEntryByUUID(uuid string) (whitelistEntry, bool) {
	entries, err := a.readWhitelistEntries()
	if err != nil {
		return whitelistEntry{}, false
	}
	normalized := canonicalUUID(uuid)
	for _, entry := range entries {
		if canonicalUUID(entry.UUID) == normalized && entry.UUID != "" && entry.Name != "" {
			entry.UUID = canonicalUUID(entry.UUID)
			return entry, true
		}
	}
	return whitelistEntry{}, false
}

func (a *app) addWhitelistEntry(entry whitelistEntry) (bool, error) {
	a.whitelistLock.Lock()
	defer a.whitelistLock.Unlock()
	entries, err := a.readWhitelistEntries()
	if err != nil {
		return false, err
	}
	entry.UUID = canonicalUUID(entry.UUID)
	added := true
	for i, current := range entries {
		if canonicalUUID(current.UUID) == entry.UUID || strings.EqualFold(current.Name, entry.Name) {
			entries[i] = entry
			added = false
			return added, a.writeWhitelistEntries(entries)
		}
	}
	entries = append(entries, entry)
	return added, a.writeWhitelistEntries(entries)
}

func (a *app) readVerificationCodes(now int64) ([]verificationCode, error) {
	data, err := os.ReadFile(a.cfg.VerifyCodesPath)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var codes []verificationCode
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		parts := strings.Split(line, "\t")
		if len(parts) != 5 {
			continue
		}
		var expires int64
		if _, err := fmt.Sscanf(parts[4], "%d", &expires); err != nil || expires <= now {
			continue
		}
		codes = append(codes, verificationCode{Key: parts[0], UUID: parts[1], Name: parts[2], Code: parts[3], ExpiresAt: expires})
	}
	return codes, nil
}

func (a *app) findVerificationCode(username, code string) (verificationCode, bool) {
	codes, err := a.readVerificationCodes(time.Now().UnixMilli())
	if err != nil {
		return verificationCode{}, false
	}
	for _, current := range codes {
		if strings.EqualFold(current.Name, username) && strings.EqualFold(current.Code, code) {
			return current, true
		}
	}
	return verificationCode{}, false
}

func (a *app) consumeVerificationCode(username, code string) error {
	a.codeLock.Lock()
	defer a.codeLock.Unlock()
	codes, err := a.readVerificationCodes(time.Now().UnixMilli())
	if err != nil {
		return err
	}
	kept := make([]verificationCode, 0, len(codes))
	for _, current := range codes {
		if strings.EqualFold(current.Name, username) && strings.EqualFold(current.Code, code) {
			continue
		}
		kept = append(kept, current)
	}
	if err := os.MkdirAll(filepath.Dir(a.cfg.VerifyCodesPath), 0o755); err != nil {
		return err
	}
	var buf bytes.Buffer
	buf.WriteString("# key\tuuid\tplayer\tcode\texpiresAtMillis\n")
	for _, current := range kept {
		fmt.Fprintf(&buf, "%s\t%s\t%s\t%s\t%d\n", current.Key, current.UUID, current.Name, current.Code, current.ExpiresAt)
	}
	tmp := a.cfg.VerifyCodesPath + ".tmp"
	if err := os.WriteFile(tmp, buf.Bytes(), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, a.cfg.VerifyCodesPath)
}

func (a *app) loadServerStatus(ctx context.Context) serverStatus {
	const logScanLines = 500
	errors := logErrorLines(a.cfg.ServerLogPath, logScanLines)
	if len(errors) > 10 {
		errors = errors[len(errors)-10:]
	}
	return serverStatus{
		ServerState:   a.serverState(ctx),
		OnlinePlayers: a.onlinePlayerSummary(),
		Disk:          diskSummary(a.cfg.RuntimeDir),
		Memory:        memorySummary(),
		Backups:       backupFiles(a.cfg.BackupDir, 20),
		LogErrors:     errors,
		LogErrorCount: len(logErrorLines(a.cfg.ServerLogPath, logScanLines)),
		LogScanLines:  logScanLines,
	}
}

func (a *app) serverState(ctx context.Context) statusState {
	result := runCommand(ctx, 3*time.Second, "systemctl", "is-active", a.cfg.ServiceName)
	if result.ok {
		state := strings.TrimSpace(result.stdout)
		switch state {
		case "active":
			return statusState{Label: "运行中", Class: "state-running", Detail: a.cfg.ServiceName}
		case "activating", "reloading":
			return statusState{Label: "启动中", Class: "state-warning", Detail: state}
		case "inactive", "failed", "deactivating":
			return statusState{Label: "未运行", Class: "state-stopped", Detail: state}
		default:
			if state == "" {
				state = "未知"
			}
			return statusState{Label: state, Class: "state-unknown", Detail: a.cfg.ServiceName}
		}
	}
	if _, err := a.rcon("list"); err == nil {
		return statusState{Label: "运行中", Class: "state-running", Detail: "RCON 可连接"}
	}
	return statusState{Label: "无法连接", Class: "state-stopped", Detail: "systemd 与 RCON 均不可用"}
}

func (a *app) onlinePlayerSummary() statusMetric {
	output, err := a.rcon("list")
	if err != nil {
		return metricUnknown("无法读取", err.Error())
	}
	return parseOnlinePlayerSummary(output)
}

func parseOnlinePlayerSummary(output string) statusMetric {
	match := onlineListRE.FindStringSubmatch(output)
	if match == nil {
		if output == "" {
			output = "RCON 未返回玩家列表"
		}
		return metricUnknown("无法解析在线人数", output)
	}
	current := atoi(match[1])
	maximum := atoi(match[2])
	percent := 0.0
	if maximum > 0 {
		percent = float64(current) / float64(maximum) * 100
	}
	return statusMetric{
		Value:   fmt.Sprintf("%d / %d (%.1f%%)", current, maximum, percent),
		Detail:  "当前在线 / 最大人数",
		Percent: percent,
		Level:   usageLevel(percent),
	}
}

type commandResult struct {
	ok     bool
	stdout string
	stderr string
}

func runCommand(ctx context.Context, timeout time.Duration, name string, args ...string) commandResult {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, name, args...)
	output, err := cmd.Output()
	if err == nil {
		return commandResult{ok: true, stdout: string(output)}
	}
	detail := err.Error()
	if exitErr, ok := err.(*exec.ExitError); ok {
		detail = string(exitErr.Stderr)
	}
	return commandResult{ok: false, stderr: strings.TrimSpace(detail)}
}

func diskSummary(path string) statusMetric {
	if path == "" {
		path = "/"
	}
	if _, err := os.Stat(path); err != nil {
		path = "/"
	}
	result := runCommand(context.Background(), 3*time.Second, "df", "-Pk", path)
	if !result.ok {
		return metricUnknown("无法读取", result.stderr)
	}
	lines := strings.Fields(result.stdout)
	if len(lines) < 12 {
		return metricUnknown("无法解析磁盘空间", result.stdout)
	}
	total := int64(atoi(lines[len(lines)-5])) * 1024
	used := int64(atoi(lines[len(lines)-4])) * 1024
	percent := 0.0
	if total > 0 {
		percent = float64(used) / float64(total) * 100
	}
	return statusMetric{
		Value:   fmt.Sprintf("%s / %s (%.1f%%)", formatBytes(used), formatBytes(total), percent),
		Detail:  "已用 / 总量",
		Percent: percent,
		Level:   usageLevel(percent),
	}
}

func memorySummary() statusMetric {
	data, err := os.ReadFile("/proc/meminfo")
	if err != nil {
		return metricUnknown("无法读取", "当前系统不支持 /proc/meminfo")
	}
	meminfo := map[string]int64{}
	for _, line := range strings.Split(string(data), "\n") {
		parts := strings.SplitN(line, ":", 2)
		if len(parts) != 2 {
			continue
		}
		fields := strings.Fields(parts[1])
		if len(fields) == 0 {
			continue
		}
		meminfo[parts[0]] = int64(atoi(fields[0])) * 1024
	}
	total := meminfo["MemTotal"]
	available := meminfo["MemAvailable"]
	used := total - available
	percent := 0.0
	if total > 0 {
		percent = float64(used) / float64(total) * 100
	}
	return statusMetric{
		Value:   fmt.Sprintf("%s / %s (%.1f%%)", formatBytes(used), formatBytes(total), percent),
		Detail:  "已用 / 总量",
		Percent: percent,
		Level:   usageLevel(percent),
	}
}

func usageLevel(percent float64) string {
	switch {
	case percent < 50:
		return "low"
	case percent < 75:
		return "medium"
	case percent < 90:
		return "high"
	default:
		return "critical"
	}
}

func metricUnknown(value, detail string) statusMetric {
	return statusMetric{Value: value, Detail: detail, Level: "unknown"}
}

func backupFiles(dir string, limit int) []backupFile {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	files := make([]backupFile, 0, len(entries))
	type backupEntry struct {
		info backupFile
		time time.Time
	}
	raw := []backupEntry{}
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".tar.gz") {
			continue
		}
		stat, err := entry.Info()
		if err != nil {
			continue
		}
		raw = append(raw, backupEntry{
			info: backupFile{
				Name:  entry.Name(),
				Size:  formatBytes(stat.Size()),
				MTime: stat.ModTime().Local().Format("2006-01-02 15:04:05"),
			},
			time: stat.ModTime(),
		})
	}
	sort.SliceStable(raw, func(i, j int) bool {
		return raw[i].time.After(raw[j].time)
	})
	for i, item := range raw {
		if limit > 0 && i >= limit {
			break
		}
		files = append(files, item.info)
	}
	return files
}

func logErrorLines(path string, scanLines int) []string {
	lines := tailLines(path, scanLines)
	matched := []string{}
	for _, line := range lines {
		upper := strings.ToUpper(line)
		if strings.Contains(upper, "ERROR") || strings.Contains(upper, "EXCEPTION") || strings.Contains(upper, "SEVERE") {
			matched = append(matched, line)
		}
	}
	return matched
}

func tailLines(path string, limit int) []string {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	lines := strings.Split(strings.ReplaceAll(string(data), "\r\n", "\n"), "\n")
	if len(lines) > 0 && lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}
	if limit > 0 && len(lines) > limit {
		lines = lines[len(lines)-limit:]
	}
	return lines
}

func formatBytes(value int64) string {
	units := []string{"B", "KB", "MB", "GB", "TB"}
	current := float64(value)
	for i, unit := range units {
		if current < 1024 || i == len(units)-1 {
			if unit == "B" {
				return fmt.Sprintf("%d B", value)
			}
			return fmt.Sprintf("%.1f %s", current, unit)
		}
		current /= 1024
	}
	return "0 B"
}

func (a *app) playerClaims(user *userSession) claimGroups {
	playerUUID := strings.ToLower(canonicalUUID(user.UUID))
	var groups claimGroups
	for _, current := range a.readClaims() {
		ownerUUID := strings.ToLower(canonicalUUID(current.OwnerUUID))
		if ownerUUID == playerUUID {
			groups.Owned = append(groups.Owned, current)
			continue
		}
		for _, member := range current.Members {
			if strings.ToLower(canonicalUUID(member)) == playerUUID {
				groups.Trusted = append(groups.Trusted, current)
				break
			}
		}
	}
	sortClaims(groups.Owned)
	sortClaims(groups.Trusted)
	return groups
}

func sortClaims(claims []claim) {
	sort.SliceStable(claims, func(i, j int) bool {
		if strings.EqualFold(claims[i].OwnerName, claims[j].OwnerName) {
			return strings.ToLower(claims[i].Name) < strings.ToLower(claims[j].Name)
		}
		return strings.ToLower(claims[i].OwnerName) < strings.ToLower(claims[j].OwnerName)
	})
}

func (a *app) readClaims() []claim {
	data, err := os.ReadFile(a.cfg.ClaimsPath)
	if err != nil {
		return nil
	}
	return parseClaimsYAML(string(data))
}

func parseClaimsYAML(text string) []claim {
	var claims []claim
	var current *claim
	currentList := ""
	currentMap := ""
	for _, raw := range strings.Split(text, "\n") {
		if strings.TrimSpace(raw) == "" || strings.HasPrefix(strings.TrimLeft(raw, " "), "#") {
			continue
		}
		indent := len(raw) - len(strings.TrimLeft(raw, " "))
		line := strings.TrimSpace(raw)
		if indent == 0 {
			current = nil
			currentList = ""
			currentMap = ""
			continue
		}
		if indent == 2 && strings.HasSuffix(line, ":") {
			if current != nil && current.Name != "" {
				normalizeClaim(current)
				claims = append(claims, *current)
			}
			current = &claim{ID: strings.TrimSuffix(line, ":"), MemberName: map[string]string{}}
			currentList = ""
			currentMap = ""
			continue
		}
		if current == nil {
			continue
		}
		if strings.HasPrefix(line, "- ") && currentList == "members" {
			current.Members = append(current.Members, yamlScalar(strings.TrimPrefix(line, "- ")))
			continue
		}
		if !strings.Contains(line, ":") {
			continue
		}
		parts := strings.SplitN(line, ":", 2)
		key := strings.TrimSpace(parts[0])
		value := strings.TrimSpace(parts[1])
		if indent == 4 {
			currentMap = ""
			currentList = ""
			switch key {
			case "members":
				currentList = "members"
				if value != "" && value != "[]" {
					for _, item := range strings.Split(strings.Trim(value, "[]"), ",") {
						item = strings.TrimSpace(item)
						if item != "" {
							current.Members = append(current.Members, yamlScalar(item))
						}
					}
				}
			case "member-names":
				currentMap = "member-names"
			case "totem":
				currentMap = "totem"
			default:
				setClaimScalar(current, key, yamlScalar(value))
			}
			continue
		}
		if indent >= 6 && currentMap == "member-names" {
			current.MemberName[canonicalUUID(key)] = yamlScalar(value)
			continue
		}
		if indent >= 6 && currentMap == "totem" {
			setClaimTotemScalar(current, key, yamlScalar(value))
		}
	}
	if current != nil && current.Name != "" {
		normalizeClaim(current)
		claims = append(claims, *current)
	}
	return claims
}

func setClaimScalar(c *claim, key, value string) {
	switch strings.ReplaceAll(key, "-", "_") {
	case "name":
		c.Name = value
	case "owner_uuid":
		c.OwnerUUID = value
	case "owner_name":
		c.OwnerName = value
	case "world":
		c.World = value
	case "min_x":
		c.MinX = atoi(value)
	case "max_x":
		c.MaxX = atoi(value)
	case "min_y":
		c.MinY = atoi(value)
	case "max_y":
		c.MaxY = atoi(value)
	case "min_z":
		c.MinZ = atoi(value)
	case "max_z":
		c.MaxZ = atoi(value)
	}
}

func setClaimTotemScalar(c *claim, key, value string) {
	if c.Totem == nil {
		c.Totem = &claimTotem{}
	}
	switch strings.ReplaceAll(key, "-", "_") {
	case "id":
		c.Totem.ID = value
	case "world":
		c.Totem.World = value
	case "x":
		c.Totem.X = atoi(value)
	case "y":
		c.Totem.Y = atoi(value)
	case "z":
		c.Totem.Z = atoi(value)
	}
}

func normalizeClaim(c *claim) {
	if c.OwnerName == "" {
		c.OwnerName = "unknown"
	}
	if c.World == "" {
		c.World = "main"
	}
	c.OwnerUUID = canonicalUUID(c.OwnerUUID)
	c.SizeX = c.MaxX - c.MinX + 1
	c.SizeY = c.MaxY - c.MinY + 1
	c.SizeZ = c.MaxZ - c.MinZ + 1
	if c.MemberName == nil {
		c.MemberName = map[string]string{}
	}
	if c.Totem != nil && c.Totem.World == "" {
		c.Totem.World = c.World
	}
}

func yamlScalar(value string) string {
	value = strings.TrimSpace(value)
	if len(value) >= 2 {
		first := value[0]
		last := value[len(value)-1]
		if (first == '\'' || first == '"') && last == first {
			return value[1 : len(value)-1]
		}
	}
	return value
}

func atoi(value string) int {
	var result int
	_, _ = fmt.Sscanf(value, "%d", &result)
	return result
}

func (a *app) initWebTables(ctx context.Context) error {
	if a.db == nil {
		return nil
	}
	statements := []string{
		`CREATE TABLE IF NOT EXISTS web_players (
			player_uuid TEXT PRIMARY KEY,
			player_name TEXT NOT NULL,
			registered_at BIGINT NOT NULL,
			updated_at BIGINT NOT NULL,
			password_hash TEXT NOT NULL DEFAULT 'e10adc3949ba59abbe56e057f20f883e',
			role TEXT NOT NULL DEFAULT '玩家',
			profile_bio TEXT NOT NULL DEFAULT '一名普通的Minecraft玩家'
		)`,
		`CREATE TABLE IF NOT EXISTS web_reports (
			id BIGSERIAL PRIMARY KEY,
			reporter_uuid TEXT NOT NULL,
			reporter_name TEXT NOT NULL,
			target_uuid TEXT NOT NULL,
			target_name TEXT NOT NULL,
			reason TEXT NOT NULL,
			status TEXT NOT NULL,
			action TEXT,
			handler_uuid TEXT,
			handler_name TEXT,
			created_at BIGINT NOT NULL,
			handled_at BIGINT,
			ban_expires_at BIGINT,
			ban_permanent BOOLEAN NOT NULL DEFAULT FALSE
		)`,
		`CREATE TABLE IF NOT EXISTS web_blacklist (
			player_uuid TEXT PRIMARY KEY,
			player_name TEXT NOT NULL,
			reason TEXT NOT NULL,
			report_id BIGINT,
			created_at BIGINT NOT NULL,
			expires_at BIGINT,
			permanent BOOLEAN NOT NULL DEFAULT FALSE,
			active BOOLEAN NOT NULL DEFAULT TRUE
		)`,
		`ALTER TABLE web_players ADD COLUMN IF NOT EXISTS password_hash TEXT`,
		`ALTER TABLE web_players ADD COLUMN IF NOT EXISTS role TEXT`,
		`ALTER TABLE web_players ADD COLUMN IF NOT EXISTS profile_bio TEXT`,
		`UPDATE web_players SET password_hash = 'e10adc3949ba59abbe56e057f20f883e' WHERE password_hash IS NULL OR password_hash = ''`,
		`UPDATE web_players SET role = '玩家' WHERE role IS NULL OR role = ''`,
		`UPDATE web_players SET profile_bio = '一名普通的Minecraft玩家' WHERE profile_bio IS NULL OR profile_bio = ''`,
		`UPDATE web_players SET role = '服主' WHERE lower(player_name) = 'exampleplayer'`,
	}
	for _, statement := range statements {
		if _, err := a.db.ExecContext(ctx, statement); err != nil {
			return err
		}
	}
	return nil
}

func (a *app) ensureWebPlayer(ctx context.Context, entry whitelistEntry) error {
	if a.db == nil {
		return nil
	}
	now := time.Now().UnixMilli()
	_, err := a.db.ExecContext(
		ctx,
		`INSERT INTO web_players (player_uuid, player_name, registered_at, updated_at, password_hash, role, profile_bio)
		 VALUES ($1, $2, $3, $4, $5, $6, $7)
		 ON CONFLICT (player_uuid)
		 DO UPDATE SET player_name = EXCLUDED.player_name, updated_at = EXCLUDED.updated_at`,
		canonicalUUID(entry.UUID), entry.Name, now, now, passwordHash(defaultPassword), playerRole, defaultProfileBio,
	)
	if err != nil {
		return err
	}
	_, err = a.db.ExecContext(ctx, `UPDATE web_players SET role = $1 WHERE lower(player_name) = 'exampleplayer'`, ownerRole)
	return err
}

func (a *app) webPlayerByEntry(ctx context.Context, entry whitelistEntry) (webPlayer, error) {
	if err := a.ensureWebPlayer(ctx, entry); err != nil {
		return webPlayer{}, err
	}
	return a.webPlayerByUUID(ctx, entry.UUID)
}

func (a *app) webPlayerByUUID(ctx context.Context, uuid string) (webPlayer, error) {
	if a.db == nil {
		return webPlayer{UUID: canonicalUUID(uuid), Role: playerRole, ProfileBio: defaultProfileBio}, nil
	}
	row := a.db.QueryRowContext(
		ctx,
		`SELECT player_uuid, player_name, password_hash, role, profile_bio, registered_at FROM web_players WHERE player_uuid = $1`,
		canonicalUUID(uuid),
	)
	var player webPlayer
	if err := row.Scan(&player.UUID, &player.Name, &player.PasswordHash, &player.Role, &player.ProfileBio, &player.RegisteredAt); err != nil {
		return webPlayer{}, err
	}
	return player, nil
}

func (a *app) updatePlayerPassword(ctx context.Context, uuid string, newPassword string) error {
	if a.db == nil {
		return errors.New("database unavailable")
	}
	result, err := a.db.ExecContext(
		ctx,
		`UPDATE web_players SET password_hash = $1, updated_at = $2 WHERE player_uuid = $3`,
		passwordHash(newPassword), time.Now().UnixMilli(), canonicalUUID(uuid),
	)
	if err != nil {
		return err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if rows != 1 {
		return errors.New("player not found")
	}
	return nil
}

func (a *app) updateProfileBio(ctx context.Context, uuid string, bio string) error {
	if a.db == nil {
		return errors.New("database unavailable")
	}
	result, err := a.db.ExecContext(
		ctx,
		`UPDATE web_players SET profile_bio = $1, updated_at = $2 WHERE player_uuid = $3`,
		bio, time.Now().UnixMilli(), canonicalUUID(uuid),
	)
	if err != nil {
		return err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if rows != 1 {
		return errors.New("player not found")
	}
	return nil
}

func (a *app) rcon(command string) (string, error) {
	conn, err := net.DialTimeout("tcp", net.JoinHostPort(a.cfg.RconHost, a.cfg.RconPort), 8*time.Second)
	if err != nil {
		return "", err
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(8 * time.Second))
	requestID := int32(100)
	if err := writeRconPacket(conn, requestID, 3, a.cfg.RconPassword); err != nil {
		return "", err
	}
	authID, _, _, err := readRconPacket(conn)
	if err != nil {
		return "", err
	}
	if authID == -1 {
		return "", errors.New("RCON authentication failed")
	}
	requestID++
	if err := writeRconPacket(conn, requestID, 2, command); err != nil {
		return "", err
	}
	_, _, payload, err := readRconPacket(conn)
	return strings.TrimSpace(payload), err
}

func writeRconPacket(w io.Writer, requestID, packetType int32, payload string) error {
	body := new(bytes.Buffer)
	_ = binary.Write(body, binary.LittleEndian, requestID)
	_ = binary.Write(body, binary.LittleEndian, packetType)
	body.WriteString(payload)
	body.Write([]byte{0, 0})
	if err := binary.Write(w, binary.LittleEndian, int32(body.Len())); err != nil {
		return err
	}
	_, err := w.Write(body.Bytes())
	return err
}

func readRconPacket(r io.Reader) (int32, int32, string, error) {
	var length int32
	if err := binary.Read(r, binary.LittleEndian, &length); err != nil {
		return 0, 0, "", err
	}
	data := make([]byte, length)
	if _, err := io.ReadFull(r, data); err != nil {
		return 0, 0, "", err
	}
	if len(data) < 10 {
		return 0, 0, "", errors.New("invalid RCON packet")
	}
	requestID := int32(binary.LittleEndian.Uint32(data[0:4]))
	packetType := int32(binary.LittleEndian.Uint32(data[4:8]))
	payload := string(data[8 : len(data)-2])
	return requestID, packetType, payload, nil
}

func mustTemplates() *template.Template {
	funcs := template.FuncMap{
		"active": func(current, active string) string {
			if current == active {
				return "active"
			}
			return ""
		},
		"roleClass": func(role string) string {
			switch role {
			case ownerRole:
				return "owner"
			case adminRole:
				return "admin"
			default:
				return "player"
			}
		},
		"formatTimeMillis": formatTimeMillis,
		"dict": func(values ...any) map[string]any {
			result := map[string]any{}
			for i := 0; i+1 < len(values); i += 2 {
				key, ok := values[i].(string)
				if ok {
					result[key] = values[i+1]
				}
			}
			return result
		},
	}
	return template.Must(template.New("root").Funcs(funcs).Parse(templatesHTML + adminTemplatesHTML))
}

var templatesHTML = `{{define "layout"}}<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{{.Title}}</title>
  <link rel="icon" type="image/png" href="/favicon.png">
  <link rel="shortcut icon" href="/favicon.ico">
  <style>
    :root { color-scheme: light; --bg:#fff; --panel:#fff; --line:#d8e0ea; --muted:#607084; --text:#142033; --accent:#1f6feb; --danger:#c24141; }
    * { box-sizing: border-box; }
    body { margin:0; min-height:100vh; font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; background:var(--bg); color:var(--text); }
    a { color:#1f6feb; text-decoration:none; }
    h1 { margin:0 0 18px; font-size:24px; }
    h2 { margin:0 0 12px; font-size:18px; }
    p { line-height:1.6; color:var(--muted); }
    main { padding:28px; width:100%; min-width:0; }
    .login-shell main { padding:0; }
    .app-shell { min-height:100vh; display:grid; grid-template-columns:240px 1fr; }
    .sidebar { border-right:1px solid var(--line); padding:22px; background:#fff; }
    .brand { font-weight:700; margin-bottom:8px; }
    .user { color:var(--muted); margin-bottom:22px; }
    .sidebar nav { display:grid; gap:8px; margin-bottom:24px; }
    .sidebar nav a { display:block; padding:10px 12px; border-radius:6px; color:var(--text); }
    .sidebar nav a.active,.sidebar nav a:hover { background:#eaf1ff; }
    .public-header { position:sticky; top:0; z-index:30; display:grid; grid-template-columns:auto 1fr auto; gap:18px; align-items:center; min-height:64px; padding:0 28px; border-bottom:1px solid var(--line); background:rgba(255,255,255,.96); }
    .public-brand { display:inline-flex; align-items:center; gap:9px; color:var(--text); font-weight:800; }
    .public-brand img { width:28px; height:28px; border-radius:6px; object-fit:cover; }
    .public-nav { display:flex; gap:6px; justify-content:center; }
    .public-nav a { display:inline-flex; align-items:center; min-height:34px; padding:6px 10px; border-radius:6px; color:var(--muted); font-size:14px; }
    .public-nav a.active,.public-nav a:hover { background:#eaf1ff; color:var(--text); }
    .public-auth { display:flex; gap:8px; justify-content:flex-end; position:relative; }
    .auth-popover { position:relative; }
    .auth-popover summary { list-style:none; display:inline-flex; align-items:center; min-height:36px; padding:7px 11px; border-radius:6px; background:#e7edf5; color:var(--text); font-size:14px; cursor:pointer; user-select:none; }
    .auth-popover summary::-webkit-details-marker { display:none; }
    .auth-popover[open] summary { background:var(--accent); color:#fff; }
    .auth-popover-panel { position:absolute; right:0; top:calc(100% + 10px); width:min(340px,calc(100vw - 32px)); padding:16px; border:1px solid var(--line); border-radius:8px; background:#fff; box-shadow:0 18px 50px rgba(20,32,51,.18); }
    .public-hero { display:grid; grid-template-columns:minmax(0,1fr) minmax(230px,300px); gap:36px; align-items:end; width:min(100% - 48px,1120px); min-height:58vh; margin:0 auto; padding:82px 0 54px; }
    .public-hero.compact { grid-template-columns:1fr; min-height:48vh; align-items:center; }
    .public-hero h1 { margin:0 0 18px; font-size:48px; line-height:1.08; }
    .public-hero p { max-width:720px; margin:0; font-size:18px; color:#405169; }
    .public-kicker { margin:0 0 10px; color:#2b6c9f; font-size:13px; font-weight:800; text-transform:uppercase; letter-spacing:0; }
    .public-directory { border-left:3px solid #d8e0ea; padding-left:18px; }
    .public-directory a { display:block; padding:7px 0; color:var(--muted); }
    .public-section { width:min(100% - 48px,1120px); margin:0 auto; padding:42px 0; border-top:1px solid var(--line); }
    .public-page { width:min(100% - 48px,980px); margin:0 auto; padding:54px 0; }
    .public-page h1 { font-size:34px; margin-bottom:12px; }
    .public-lead { max-width:760px; font-size:18px; color:#405169; }
    .section-heading { margin-bottom:18px; }
    .article-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
    .article-card { min-width:0; min-height:178px; padding:18px; border:1px solid var(--line); border-radius:8px; background:#fff; }
    .note-list { display:grid; border-top:1px solid var(--line); }
    .note-list article { display:grid; grid-template-columns:120px minmax(0,1fr); gap:18px; padding:18px 0; border-bottom:1px solid var(--line); }
    .markdown-doc { max-width:780px; }
    .markdown-doc h2 { margin:28px 0 10px; padding-top:18px; border-top:1px solid var(--line); font-size:22px; }
    .markdown-doc h3 { margin:20px 0 8px; font-size:17px; }
    .markdown-doc ul { margin:8px 0 18px; padding-left:22px; color:var(--muted); line-height:1.7; }
    .markdown-doc li { margin:4px 0; }
    .concept-figure { margin:30px 0 0; }
    .concept-figure img { display:block; width:min(100%,620px); height:auto; border:1px solid var(--line); border-radius:8px; background:#f8fafc; }
    .concept-figure figcaption { margin-top:10px; color:var(--muted); font-size:14px; line-height:1.6; }
    .plugin-table { width:100%; max-width:900px; margin-top:22px; border:1px solid var(--line); border-radius:8px; overflow:hidden; }
    .plugin-table table { margin:0; }
    .plugin-table tr:last-child td { border-bottom:0; }
    .plugin-table a { font-weight:800; }
    .panel { border:1px solid var(--line); border-radius:8px; background:var(--panel); padding:20px; margin-bottom:18px; }
    .identity-card { display:grid; grid-template-columns:minmax(170px,240px) 1fr; gap:24px; width:min(100%,860px); border:1px solid var(--line); border-radius:8px; background:#fff; padding:22px; margin-bottom:18px; }
    .identity-role { display:inline-flex; width:fit-content; padding:6px 10px; border-radius:999px; font-weight:700; font-size:13px; }
    .identity-role.owner { background:#fff4d6; color:#a15c00; }
    .identity-role.admin { background:#eafaf2; color:#176f48; }
    .identity-role.player { background:#eaf3ff; color:#1d5fa7; }
    label { display:block; margin:12px 0 6px; color:var(--muted); }
    input,textarea,select { width:100%; padding:10px 11px; border-radius:6px; border:1px solid #c7d2df; background:#fff; color:var(--text); font-size:15px; }
    button,.button { display:inline-block; border:0; border-radius:6px; padding:10px 13px; background:var(--accent); color:#fff; font-size:15px; cursor:pointer; text-align:center; }
    button.secondary,.button.secondary { background:#e7edf5; color:var(--text); }
    .page-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:18px; }
    .actions { display:flex; gap:10px; flex-wrap:wrap; margin-top:16px; }
    .inline-form { display:flex; gap:8px; flex-wrap:wrap; align-items:center; margin:4px 0; }
    .inline-form input,.inline-form select { width:auto; min-width:110px; }
    .small-input { max-width:110px; }
    .checkbox-inline { display:inline-flex; align-items:center; gap:6px; margin:0; white-space:nowrap; }
    .checkbox-inline input { width:auto; }
    .form-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }
    .message { color:var(--muted); }
    .register-message,.error { color:var(--danger); font-weight:700; border:1px solid #f1b7b7; background:#fff1f1; border-radius:6px; padding:10px 12px; }
    .field-hint { margin:6px 0 0; color:var(--muted); font-size:13px; line-height:1.5; }
    .compliance-footer { display:flex; flex-wrap:wrap; justify-content:center; gap:8px 16px; margin-top:26px; padding-top:16px; border-top:1px solid var(--line); color:var(--muted); font-size:13px; line-height:1.5; text-align:center; }
    .pre { white-space:pre-wrap; overflow-wrap:anywhere; }
    @media (max-width:760px) {
      .app-shell { grid-template-columns:1fr; }
      .sidebar { border-right:0; border-bottom:1px solid var(--line); }
      .public-header { position:static; grid-template-columns:1fr; align-items:start; gap:10px; padding:14px 18px; }
      .public-nav { justify-content:flex-start; overflow-x:auto; }
      .public-auth { justify-content:flex-start; flex-wrap:wrap; }
      .auth-popover-panel { left:0; right:auto; }
      .public-hero { grid-template-columns:1fr; min-height:0; width:min(100% - 36px,1120px); padding:48px 0 34px; }
      .public-hero h1 { font-size:34px; }
      .article-grid { grid-template-columns:1fr; }
      .note-list article { grid-template-columns:1fr; gap:6px; }
    }
  </style>
</head>
<body>
  <div class="{{if .User}}app-shell{{else}}login-shell{{end}}">
    {{if .User}}{{template "sidebar" .}}{{end}}
    <main>{{block "content" .}}{{end}}{{template "footer" .}}</main>
  </div>
</body>
</html>{{end}}

{{define "sidebar"}}<aside class="sidebar">
  <div class="brand">XiceMCServer</div>
  <div class="user">{{.User.Name}} · {{.User.Role}}</div>
  <nav>
    <a class="{{active "home" .Active}}" href="/home">首页</a>
    <a class="{{active "status" .Active}}" href="/status">服务器状态</a>
    <a class="{{active "docs" .Active}}" href="/docs">服务器文档</a>
    <a class="{{active "audit" .Active}}" href="/audit">操作查询</a>
    <a class="{{active "players" .Active}}" href="/players">玩家列表</a>
    {{if or (eq .User.Role "管理员") (eq .User.Role "服主")}}<a class="{{active "permissions" .Active}}" href="/permissions">权限管理</a>{{end}}
    <a class="{{active "blacklist" .Active}}" href="/blacklist">黑名单列表</a>
    <a class="{{active "report" .Active}}" href="/report">举报</a>
    {{if or (eq .User.Role "管理员") (eq .User.Role "服主")}}<a class="{{active "reports" .Active}}" href="/reports">举报受理</a>{{end}}
  </nav>
  <form method="post" action="/logout"><button class="secondary" type="submit">退出登录</button></form>
</aside>{{end}}

{{define "footer"}}<footer class="compliance-footer" aria-label="备案信息">
  <span>访问入口：{{.Public.SiteBaseURL}}</span>
  <span>ICP：{{if .Public.ICPRecordNo}}<a href="{{.Public.ICPRecordURL}}" target="_blank" rel="noopener noreferrer">{{.Public.ICPRecordNo}}</a>{{else}}ICP备案号待下发{{end}}</span>
  <span>公安联网备案：{{if and .Public.PublicSecurityRecordNo .Public.PublicSecurityRecordURL}}<a href="{{.Public.PublicSecurityRecordURL}}" target="_blank" rel="noopener noreferrer">{{.Public.PublicSecurityRecordNo}}</a>{{else}}公安联网备案号待下发{{end}}</span>
  {{if .Public.SiteDomain}}<span>域名通道：{{.Public.SiteDomain}}</span>{{end}}
</footer>{{end}}

{{define "pageStart"}}<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{{.Title}}</title>
  <link rel="icon" type="image/png" href="/favicon.png">
  <link rel="shortcut icon" href="/favicon.ico">
  <style>
    :root { color-scheme: light; --line:#d8e0ea; --muted:#607084; --text:#142033; --accent:#1f6feb; --danger:#c24141; }
    * { box-sizing:border-box; }
    body { margin:0; min-height:100vh; font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; color:var(--text); background:#fff; }
    a { color:#1f6feb; text-decoration:none; }
    h1 { margin:0 0 18px; font-size:24px; }
    h2 { margin:0 0 12px; font-size:18px; }
    p { line-height:1.6; color:var(--muted); }
    main { padding:28px; width:100%; min-width:0; }
    .login-shell main { padding:0; }
    .app-shell { min-height:100vh; display:grid; grid-template-columns:240px 1fr; }
    .sidebar { border-right:1px solid var(--line); padding:22px; background:#fff; }
    .brand { font-weight:700; margin-bottom:8px; }
    .user { color:var(--muted); margin-bottom:22px; }
    .sidebar nav { display:grid; gap:8px; margin-bottom:24px; }
    .sidebar nav a { display:block; padding:10px 12px; border-radius:6px; color:var(--text); }
    .sidebar nav a.active,.sidebar nav a:hover { background:#eaf1ff; }
    .public-header { position:sticky; top:0; z-index:30; display:grid; grid-template-columns:auto 1fr auto; gap:18px; align-items:center; min-height:64px; padding:0 28px; border-bottom:1px solid var(--line); background:rgba(255,255,255,.96); }
    .public-brand { display:inline-flex; align-items:center; gap:9px; color:var(--text); font-weight:800; }
    .public-brand img { width:28px; height:28px; border-radius:6px; object-fit:cover; }
    .public-nav { display:flex; gap:6px; justify-content:center; }
    .public-nav a { display:inline-flex; align-items:center; min-height:34px; padding:6px 10px; border-radius:6px; color:var(--muted); font-size:14px; }
    .public-nav a.active,.public-nav a:hover { background:#eaf1ff; color:var(--text); }
    .public-auth { display:flex; gap:8px; justify-content:flex-end; position:relative; }
    .auth-popover { position:relative; }
    .auth-popover summary { list-style:none; display:inline-flex; align-items:center; min-height:36px; padding:7px 11px; border-radius:6px; background:#e7edf5; color:var(--text); font-size:14px; cursor:pointer; user-select:none; }
    .auth-popover summary::-webkit-details-marker { display:none; }
    .auth-popover[open] summary { background:var(--accent); color:#fff; }
    .auth-popover-panel { position:absolute; right:0; top:calc(100% + 10px); width:min(340px,calc(100vw - 32px)); padding:16px; border:1px solid var(--line); border-radius:8px; background:#fff; box-shadow:0 18px 50px rgba(20,32,51,.18); }
    .public-hero { display:grid; grid-template-columns:minmax(0,1fr) minmax(230px,300px); gap:36px; align-items:end; width:min(100% - 48px,1120px); min-height:58vh; margin:0 auto; padding:82px 0 54px; }
    .public-hero.compact { grid-template-columns:1fr; min-height:48vh; align-items:center; }
    .public-hero h1 { margin:0 0 18px; font-size:48px; line-height:1.08; }
    .public-hero p { max-width:720px; margin:0; font-size:18px; color:#405169; }
    .public-kicker { margin:0 0 10px; color:#2b6c9f; font-size:13px; font-weight:800; text-transform:uppercase; letter-spacing:0; }
    .public-directory { border-left:3px solid #d8e0ea; padding-left:18px; }
    .public-directory a { display:block; padding:7px 0; color:var(--muted); }
    .public-section { width:min(100% - 48px,1120px); margin:0 auto; padding:42px 0; border-top:1px solid var(--line); }
    .public-page { width:min(100% - 48px,980px); margin:0 auto; padding:54px 0; }
    .public-page h1 { font-size:34px; margin-bottom:12px; }
    .public-lead { max-width:760px; font-size:18px; color:#405169; }
    .section-heading { margin-bottom:18px; }
    .article-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
    .article-card,.panel { border:1px solid var(--line); border-radius:8px; background:#fff; padding:18px; }
    .article-card { min-width:0; min-height:178px; }
    .plugin-subnav { display:flex; gap:8px; flex-wrap:wrap; margin:0 0 22px; }
    .plugin-subnav a { display:inline-flex; align-items:center; min-height:34px; padding:6px 10px; border:1px solid var(--line); border-radius:6px; color:var(--muted); }
    .plugin-subnav a:hover { background:#eaf1ff; color:var(--text); }
    .panel { padding:20px; margin-bottom:18px; }
    .status-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; }
    .status-card { min-width:0; padding:18px; border:1px solid var(--line); border-radius:8px; background:#fff; }
    .label { color:var(--muted); font-size:13px; margin-bottom:8px; }
    .status-value { font-size:20px; font-weight:800; overflow-wrap:anywhere; }
    .status-subtext { margin-top:8px; color:var(--muted); font-size:13px; overflow-wrap:anywhere; }
    .status-pill { display:inline-flex; align-items:center; min-height:30px; padding:5px 10px; border-radius:999px; font-size:14px; }
    .state-running { background:#eafaf2; color:#176f48; }
    .state-warning { background:#fff4d6; color:#a15c00; }
    .state-stopped { background:#fff1f1; color:#b42318; }
    .state-unknown { background:#e7edf5; color:#405169; }
    .level-low { color:#176f48; }
    .level-medium { color:#986a00; }
    .level-high { color:#b45309; }
    .level-critical { color:#b42318; }
    .level-unknown { color:var(--muted); }
    .meter { height:8px; overflow:hidden; margin-top:12px; border-radius:999px; background:#e7edf5; }
    .meter-fill { height:100%; border-radius:999px; background:#6b7a90; }
    .level-low-bg { background:#2eaf73; }
    .level-medium-bg { background:#d19a1d; }
    .level-high-bg { background:#e67e22; }
    .level-critical-bg { background:#c24141; }
    .level-unknown-bg { background:#9aa8b8; }
    .table-wrap { overflow-x:auto; }
    table { width:100%; border-collapse:collapse; }
    th,td { padding:10px 12px; border-bottom:1px solid var(--line); text-align:left; }
    th { color:var(--muted); font-size:13px; font-weight:700; }
    .mono { font-family:ui-monospace,SFMono-Regular,Consolas,"Liberation Mono",monospace; }
    .log-lines { max-height:320px; overflow:auto; white-space:pre-wrap; overflow-wrap:anywhere; padding:14px; border:1px solid var(--line); border-radius:8px; background:#f8fafc; color:#405169; }
    .note-list { display:grid; border-top:1px solid var(--line); }
    .note-list article { display:grid; grid-template-columns:120px minmax(0,1fr); gap:18px; padding:18px 0; border-bottom:1px solid var(--line); }
    .markdown-doc { max-width:780px; }
    .markdown-doc h2 { margin:28px 0 10px; padding-top:18px; border-top:1px solid var(--line); font-size:22px; }
    .markdown-doc h3 { margin:20px 0 8px; font-size:17px; }
    .markdown-doc ul { margin:8px 0 18px; padding-left:22px; color:var(--muted); line-height:1.7; }
    .markdown-doc li { margin:4px 0; }
    .concept-figure { margin:30px 0 0; }
    .concept-figure img { display:block; width:min(100%,620px); height:auto; border:1px solid var(--line); border-radius:8px; background:#f8fafc; }
    .concept-figure figcaption { margin-top:10px; color:var(--muted); font-size:14px; line-height:1.6; }
    .plugin-table { width:100%; max-width:900px; margin-top:22px; border:1px solid var(--line); border-radius:8px; overflow:hidden; }
    .plugin-table table { margin:0; }
    .plugin-table tr:last-child td { border-bottom:0; }
    .plugin-table a { font-weight:800; }
    .identity-card { display:grid; grid-template-columns:minmax(170px,240px) 1fr; gap:24px; width:min(100%,860px); border:1px solid var(--line); border-radius:8px; background:#fff; padding:22px; margin-bottom:18px; }
    .identity-role { display:inline-flex; width:fit-content; padding:6px 10px; border-radius:999px; font-weight:700; font-size:13px; }
    .identity-role.owner { background:#fff4d6; color:#a15c00; }
    .identity-role.admin { background:#eafaf2; color:#176f48; }
    .identity-role.player { background:#eaf3ff; color:#1d5fa7; }
    label { display:block; margin:12px 0 6px; color:var(--muted); }
    input,textarea { width:100%; padding:10px 11px; border-radius:6px; border:1px solid #c7d2df; background:#fff; color:var(--text); font-size:15px; }
    button,.button { display:inline-block; border:0; border-radius:6px; padding:10px 13px; background:var(--accent); color:#fff; font-size:15px; cursor:pointer; text-align:center; }
    button.secondary,.button.secondary { background:#e7edf5; color:var(--text); }
    .actions { display:flex; gap:10px; flex-wrap:wrap; margin-top:16px; }
    .message { color:var(--muted); }
    .error { color:var(--danger); font-weight:700; border:1px solid #f1b7b7; background:#fff1f1; border-radius:6px; padding:10px 12px; }
    .field-hint { margin:6px 0 0; color:var(--muted); font-size:13px; line-height:1.5; }
    .compliance-footer { display:flex; flex-wrap:wrap; justify-content:center; gap:8px 16px; margin-top:26px; padding-top:16px; border-top:1px solid var(--line); color:var(--muted); font-size:13px; line-height:1.5; text-align:center; }
    .pre { white-space:pre-wrap; overflow-wrap:anywhere; }
    @media (max-width:760px) {
      .app-shell { grid-template-columns:1fr; }
      .sidebar { border-right:0; border-bottom:1px solid var(--line); }
      .public-header { position:static; grid-template-columns:1fr; align-items:start; gap:10px; padding:14px 18px; }
      .public-nav { justify-content:flex-start; overflow-x:auto; }
      .public-auth { justify-content:flex-start; flex-wrap:wrap; }
      .auth-popover-panel { left:0; right:auto; }
      .public-hero { grid-template-columns:1fr; min-height:0; width:min(100% - 36px,1120px); padding:48px 0 34px; }
      .public-hero h1 { font-size:34px; }
      .article-grid { grid-template-columns:1fr; }
      .status-grid { grid-template-columns:1fr; }
      .form-grid { grid-template-columns:1fr; }
      .note-list article { grid-template-columns:1fr; gap:6px; }
    }
  </style>
</head>
<body>
  <div class="{{if .User}}app-shell{{else}}login-shell{{end}}">
    {{if .User}}{{template "sidebar" .}}{{end}}
    <main>{{end}}

{{define "pageEnd"}}{{template "footer" .}}</main></div></body></html>{{end}}

{{define "publicHeader"}}
<header class="public-header">
  <a class="public-brand" href="/"><img src="/favicon.png" alt="" aria-hidden="true">个人技术开发随记</a>
  <nav class="public-nav" aria-label="公开页面目录">
    <a class="{{if eq .Active "public-home"}}active{{end}}" href="/">首页</a>
    <a class="{{if eq .Active "public-plugins"}}active{{end}}" href="/plugins">插件介绍</a>
    <a class="{{if eq .Active "public-tech"}}active{{end}}" href="/tech">技术实现</a>
    <a class="{{if eq .Active "public-ops"}}active{{end}}" href="/ops">运维记录</a>
    <a class="{{if eq .Active "public-changelog"}}active{{end}}" href="/changelog">更新日志</a>
  </nav>
  <div class="public-auth">
    <details class="auth-popover" {{if .LoginOpen}}open{{end}}>
      <summary>后台登录</summary>
      <div class="auth-popover-panel">
        {{if and .Error .LoginOpen}}<p class="error">{{.Error}}</p>{{end}}
        <form method="post" action="/login">
          <label for="login-username">角色 ID</label>
          <input id="login-username" name="username" autocomplete="username" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]+">
          <label for="login-password">登录密码</label>
          <input id="login-password" name="password" type="password" autocomplete="current-password" required minlength="6" maxlength="64" pattern="[A-Za-z0-9_]+">
          <div class="actions"><button type="submit">进入后台</button></div>
        </form>
      </div>
    </details>
    <details class="auth-popover" {{if .RegisterOpen}}open{{end}}>
      <summary>注册入口</summary>
      <div class="auth-popover-panel">
        {{if .Message}}<p class="message">{{.Message}}</p>{{end}}
        {{if and .Error .RegisterOpen}}<p class="error">{{.Error}}</p>{{end}}
        <form method="post" action="/register">
          <label for="register-username">角色 ID</label>
          <input id="register-username" name="username" autocomplete="username" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]+">
          <label for="verification_code">验证码</label>
          <input id="verification_code" name="verification_code" autocomplete="off" required minlength="4" maxlength="16">
          <p class="field-hint">该入口仅用于非公开项目成员登记。</p>
          <div class="actions"><button type="submit">提交登记</button></div>
        </form>
      </div>
    </details>
  </div>
</header>
<script>
  document.querySelectorAll(".auth-popover").forEach((popover) => {
    popover.addEventListener("toggle", () => {
      if (!popover.open) return;
      document.querySelectorAll(".auth-popover[open]").forEach((current) => {
        if (current !== popover) current.removeAttribute("open");
      });
    });
  });
</script>
{{end}}

{{define "public"}}{{template "pageStart" .}}{{template "publicContent" .}}{{template "pageEnd" .}}{{end}}
{{define "publicContent"}}
{{template "publicHeader" .}}
<section class="public-hero compact" id="overview">
  <div>
    <p class="public-kicker">Minecraft Plugin Development Notes</p>
    <h1>个人技术开发随记</h1>
    <p>这里记录我为 Minecraft 服务器做过的一些 Java 插件。</p>
    <div class="actions">
      <a class="button" href="/plugins">查看插件介绍</a>
      <a class="button secondary" href="/tech">查看技术实现</a>
      <a class="button secondary" href="https://github.com/xice-1201/XiceMCServer" target="_blank" rel="noopener noreferrer">GitHub 仓库</a>
    </div>
  </div>
</section>
{{end}}

{{define "publicTech"}}{{template "pageStart" .}}{{template "publicTechContent" .}}{{template "pageEnd" .}}{{end}}
{{define "publicTechContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <p class="public-kicker">Implementation</p>
  <h1>技术实现</h1>
  <p class="public-lead">这里整理项目中的实现路线、技术取舍和后端维护经验，重点记录可复用的开发过程，而不是展示玩家后台数据。</p>
  <div class="section-heading"><p class="public-kicker">Implementation</p><h2>技术实现</h2></div>
  <div class="article-grid">
    <article class="article-card"><h3>Java 插件开发</h3><p>围绕权限、领地、审计和文本交互等模块整理实现思路，记录事件监听、配置持久化、命令控制和用户界面迭代。</p></article>
    <article class="article-card"><h3>Web 页面开发</h3><p>记录 Go 后端迁移、会话管理、权限页面、审计查询和后台页面拆分过程，关注小型项目的可维护性。</p></article>
    <article class="article-card"><h3>自动化部署</h3><p>整理从代码提交、构建、备份到服务重启的部署流程，保留每次调整背后的取舍与问题复盘。</p></article>
  </div>
</section>
{{end}}

{{define "publicPlugins"}}{{template "pageStart" .}}{{template "publicPluginsContent" .}}{{template "pageEnd" .}}{{end}}
{{define "publicPluginsContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <p class="public-kicker">Plugin Index</p>
  <h1>插件介绍</h1>
  <p class="public-lead">这里整理服务器当前使用的自制 Paper 插件。每个插件页面都分为功能概述和部署方式，便于同时查看玩家侧表现与服务器侧安装信息。</p>
  <div class="plugin-table table-wrap">
    <table>
      <thead><tr><th>插件</th><th>功能定位</th><th>页面</th></tr></thead>
      <tbody>
        <tr><td><a href="/plugins/xiceauditlog">XiceAuditLog</a></td><td>记录关键玩家行为、方块变化、容器变动和登录会话。</td><td><a href="/plugins/xiceauditlog">查看介绍</a></td></tr>
        <tr><td><a href="/plugins/xiceclaim">XiceClaim</a></td><td>提供三维领地保护、领地戒指 GUI、领地图腾、图腾核心光环和领地传送。</td><td><a href="/plugins/xiceclaim">查看介绍</a></td></tr>
        <tr><td><a href="/plugins/xicecommandcontrol">XiceCommandControl</a></td><td>用配置文件维护玩家可用的受控指令。</td><td><a href="/plugins/xicecommandcontrol">查看介绍</a></td></tr>
        <tr><td><a href="/plugins/xicemorepotioneffects">XiceMorePotionEffects</a></td><td>提供自定义药水效果、跃迁抑制和侧边栏剩余时间显示。</td><td><a href="/plugins/xicemorepotioneffects">查看介绍</a></td></tr>
        <tr><td><a href="/plugins/xicetextarranger">XiceTextArranger</a></td><td>重写白名单、正版验证、黑名单、进退服和维护广播等服务器提示。</td><td><a href="/plugins/xicetextarranger">查看介绍</a></td></tr>
      </tbody>
    </table>
  </div>
</section>
{{end}}

{{define "pluginXiceClaim"}}{{template "pageStart" .}}{{template "pluginXiceClaimContent" .}}{{template "pageEnd" .}}{{end}}
{{define "pluginXiceClaimContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <article class="markdown-doc">
    <p class="public-kicker">Claim Plugin</p>
    <h1>XiceClaim 领地插件</h1>
    <div class="actions"><a class="button secondary" href="/plugins">返回插件列表</a></div>
    <p class="public-lead">XiceClaim 用于把服务器领地保护做成可视化、物品化的交互功能，让玩家不用记复杂命令也能创建和管理自己的建设空间。</p>
    <h2>功能概述</h2>
    <p>XiceClaim 在服务器上表现为“领地戒指”“领地图腾”和三维领地保护系统。玩家手持领地戒指右键即可打开虚拟容器 GUI，完成领地创建、绑定已有领地、查看范围和进入管理菜单等操作，不再需要依赖一组复杂的领地命令。</p>
    <p>插件按立方体保护空间，领地大小会受最小尺寸、最大水平尺寸、世界边界和重叠检测限制。创建、预览、查询和进入领地时，插件会用仅该玩家可见的粒子短暂显示领地范围，帮助确认边界位置。</p>
    <p>领地保护会拦截未授权玩家的常见破坏与交互行为，并处理火焰、爆炸、活塞跨越边界等会影响领地安全的事件。领地所有者可以在戒指管理菜单中调整授权成员和领地功能权限；每项功能可以处于允许所有人、禁止未授权或全体禁止状态。</p>
    <p>领地图腾是领地的实体锚点。图腾放置后占用 <code>1 x 1 x 2</code> 空间，使用带插件标记的方块实体保存身份，并通过展示实体和资源包模型显示自定义外观。图腾放在领地内部时会自动绑定到该领地；绑定关系会写入领地数据，图腾被破坏、失去支撑或爆炸波及时会整体回收并同步解绑。</p>
    <p>绑定了领地图腾的领地支持传送。玩家通过绑定领地的戒指进入管理菜单后，可以传送到图腾正前方；传送会先进行倒计时和粒子施法，期间移动会取消，真正传送前还会再次检查图腾是否存在、目标空间是否安全以及玩家是否拥有对应领地权限。</p>
    <p>图腾核心是可放入领地图腾的增强物品。已放入核心且图腾仍完整绑定时，领地内玩家会周期性获得夜视、抗火、水下呼吸、速度、抗性提升、生命恢复、急迫、力量和伤害吸收等短时光环效果。该效果只在玩家位于对应领地内时刷新。</p>
    <h2>部署方式</h2>
    <p>插件基于 Paper API 运行，当前项目按 Paper 1.21.11 和 Java 21 构建。构建产物安装为 <code>/opt/xicemc/runtime/plugins/XiceClaim.jar</code>。插件声明了 <code>XiceMorePotionEffects</code> 软依赖：没有该插件时领地保护、戒指、图腾和核心光环仍可工作；有该插件时，领地图腾传送完成后会额外施加跃迁抑制，避免连续传送。</p>
    <p>插件运行时配置目录为 <code>/opt/xicemc/runtime/plugins/XiceClaim/</code>。主要配置文件是 <code>config.yml</code>，包含领地数量、尺寸限制、世界边界、粒子预览参数、默认保护策略、<code>/claim give</code> 发放权限、领地图腾/图腾核心提示文案和传送提示文案。领地数据保存为 <code>/opt/xicemc/runtime/plugins/XiceClaim/claims.yml</code>，其中包含领地、成员、权限状态、戒指绑定、图腾绑定和图腾核心状态。</p>
    <p>领地戒指、领地图腾和图腾核心使用服务器资源包中的自定义图标与模型。玩家侧能看到的物品和图腾外观由资源包提供，插件侧负责识别物品、保存绑定数据、处理右键交互、阻止原版合成误用并在玩家获得戒指后解锁后续配方。</p>
    <figure class="concept-figure">
      <img src="/assets/xiceclaim-totem-concept.png" alt="领地图腾概念图">
      <figcaption>领地图腾概念图：以石英、金质镶边、嵌入式末影珍珠和浮空能量碎片构成的 1 x 1 x 2 竖向图腾。</figcaption>
    </figure>
  </article>
</section>
{{end}}

{{define "pluginXiceAuditLog"}}{{template "pageStart" .}}{{template "pluginXiceAuditLogContent" .}}{{template "pageEnd" .}}{{end}}
{{define "pluginXiceAuditLogContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <article class="markdown-doc">
    <p class="public-kicker">Audit Plugin</p>
    <h1>XiceAuditLog 审计插件</h1>
    <div class="actions"><a class="button secondary" href="/plugins">返回插件列表</a></div>
    <p class="public-lead">XiceAuditLog 用于记录服务器里的关键操作，为问题排查、纠纷处理和操作回溯提供依据。</p>
    <h2>功能概述</h2>
    <p>XiceAuditLog 在服务器上不会向普通玩家展示额外界面，它主要在后台监听事件并写入审计数据库。插件的目标不是替代备份，也不是提供游戏内回滚，而是稳定记录“谁在什么时候对哪里做了什么”。</p>
    <p>记录写入由独立线程完成，主线程只收集事件并放入队列，避免数据库短暂波动直接拖慢服务器玩法。插件本身只负责建表、建索引和写入审计记录，不提供游戏内查询命令。</p>
    <h3>记录范围</h3>
    <ul>
      <li>方块破坏与放置，包括 TNT、苦力怕等爆炸造成的方块破坏。</li>
      <li>容器物品放入与取出，按物品类型和数量记录变化。</li>
      <li>玩家加入与退出，退出记录会携带本次在线时长。</li>
      <li>后续需要追踪的其它关键行为，可以继续沿用同一套审计表结构扩展。</li>
    </ul>
    <h2>部署方式</h2>
    <p>插件基于 Paper API 运行，当前项目按 Paper 1.21.11 和 Java 21 构建。它需要一个可用的 PostgreSQL 数据库服务，数据库连接参数写在运行时配置中。PostgreSQL JDBC 驱动通过 Maven Shade 打包进插件 jar，不需要额外把 JDBC jar 放入服务器插件目录。</p>
    <p>Maven 构建产物安装为 <code>/opt/xicemc/runtime/plugins/XiceAuditLog.jar</code>。运行时配置文件位于 <code>/opt/xicemc/runtime/plugins/XiceAuditLog/config.yml</code>，主要字段包括 <code>storage.type</code>、批量写入大小、队列容量、数据库 host/port/database/username，以及密码环境变量名。</p>
    <p>当前配置使用 PostgreSQL，默认数据库为 <code>xicemc_audit</code>，默认用户为 <code>xicemc_audit</code>，密码不写入配置文件，而是由环境变量 <code>XICE_AUDIT_DB_PASSWORD</code> 提供。</p>
  </article>
</section>
{{end}}

{{define "pluginXiceCommandControl"}}{{template "pageStart" .}}{{template "pluginXiceCommandControlContent" .}}{{template "pageEnd" .}}{{end}}
{{define "pluginXiceCommandControlContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <article class="markdown-doc">
    <p class="public-kicker">Command Permission</p>
    <h1>XiceCommandControl 指令权限插件</h1>
    <div class="actions"><a class="button secondary" href="/plugins">返回插件列表</a></div>
    <p class="public-lead">XiceCommandControl 用于管理需要额外信任的服务器指令，避免为了少量功能直接给玩家 OP 或开放原版高权限命令。</p>
    <h2>功能概述</h2>
    <p>XiceCommandControl 在服务器上表现为一组受控指令。普通玩家可以使用默认开放的指令，例如 <code>/survival</code>；需要额外信任的指令，例如 <code>/creative</code> 或插件维护指令，则必须在配置中为指定玩家 UUID 授权。</p>
    <p>插件把“谁能执行什么指令”从代码中抽离出来。玩家执行受控指令时，插件会读取配置并拦截无权限操作；管理员可以在游戏内通过维护命令重新加载配置或查看已配置玩家的指令列表。</p>
    <h3>当前用途</h3>
    <ul>
      <li><code>/survival</code>：切换自己为生存模式，默认允许所有玩家使用。</li>
      <li><code>/creative</code>：切换自己为创造模式，只允许配置中授权的玩家使用。</li>
      <li><code>/xcc reload</code>：重新加载指令权限配置。</li>
      <li><code>/xcc list [玩家名或 UUID]</code>：查询当前配置中的玩家指令权限。</li>
    </ul>
    <p>权限粒度保持在指令层，不继续细分到每个子动作。这样配置更简单，也符合当前服务器只给少数可信玩家开放特殊能力的实际需求。</p>
    <h2>部署方式</h2>
    <p>插件基于 Paper API 运行，当前项目按 Paper 1.21.11 和 Java 21 构建。构建产物安装为 <code>/opt/xicemc/runtime/plugins/XiceCommandControl.jar</code>。</p>
    <p>运行时配置文件位于 <code>/opt/xicemc/runtime/plugins/XiceCommandControl/config.yml</code>。配置中包含 <code>default-allowed-commands</code> 和 <code>players</code> 两部分：前者声明所有玩家默认可用的受控指令，后者按玩家 UUID 配置专属指令列表，<code>name</code> 字段只用于显示和查询。</p>
    <p>修改配置后，插件可通过 <code>/xcc reload</code> 重新加载；<code>/xcc list [玩家名或 UUID]</code> 可用于在游戏内查看配置中的玩家授权。</p>
  </article>
</section>
{{end}}

{{define "pluginXiceMorePotionEffects"}}{{template "pageStart" .}}{{template "pluginXiceMorePotionEffectsContent" .}}{{template "pageEnd" .}}{{end}}
{{define "pluginXiceMorePotionEffectsContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <article class="markdown-doc">
    <p class="public-kicker">Custom Potion Effects</p>
    <h1>XiceMorePotionEffects 更多药水效果插件</h1>
    <div class="actions"><a class="button secondary" href="/plugins">返回插件列表</a></div>
    <p class="public-lead">XiceMorePotionEffects 用于承载服务器自定义状态效果。当前版本重点实现“跃迁抑制”，让传送类能力可以被短时间冷却约束。</p>
    <h2>功能概述</h2>
    <p>插件目前提供一个自定义效果：<code>warp_suppression</code>，显示名称为“跃迁抑制”。玩家处于该效果期间再次触发传送会被拦截，并收到对应提示。插件会在玩家完成部分传送后自动施加短时跃迁抑制，例如领地图腾传送、传送门/末地折跃门传送，以及末影珍珠或消耗品类传送。</p>
    <p>自定义效果不是原版药水效果，插件会自行记录到期时间。在线玩家身上存在自定义效果时，插件会显示侧边栏，按配置格式展示效果名称和剩余秒数；效果结束、玩家离线或插件关闭时会清理或恢复侧边栏状态。</p>
    <p>插件提供 <code>/morepotioneffects</code> 主命令，别名为 <code>/mpe</code> 和 <code>/xicemorepotioneffects</code>。管理员可使用 <code>give</code>、<code>clear</code>、<code>check</code> 和 <code>reload</code> 管理自定义效果，时长参数支持纯秒数以及 <code>s</code>、<code>m</code>、<code>h</code> 后缀。</p>
    <h2>部署方式</h2>
    <p>插件基于 Paper API 运行，当前项目按 Paper 1.21.11 和 Java 21 构建。构建产物安装为 <code>/opt/xicemc/runtime/plugins/XiceMorePotionEffects.jar</code>。</p>
    <p>运行时配置文件位于 <code>/opt/xicemc/runtime/plugins/XiceMorePotionEffects/config.yml</code>。配置中包含 <code>access</code> 权限分配、侧边栏标题与行格式，以及命令提示文案。当前 <code>give</code> 动作可通过配置授权给指定玩家，完整管理权限仍由 <code>xicemorepotioneffects.admin</code> 控制。</p>
    <p>XiceClaim 会以软依赖方式检测该插件。两者同时安装时，领地图腾传送完成后会调用 XiceMorePotionEffects 施加 30 秒跃迁抑制；缺少该插件时，领地图腾传送只跳过这一额外冷却，不影响领地插件加载。</p>
  </article>
</section>
{{end}}

{{define "pluginXiceTextArranger"}}{{template "pageStart" .}}{{template "pluginXiceTextArrangerContent" .}}{{template "pageEnd" .}}{{end}}
{{define "pluginXiceTextArrangerContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <article class="markdown-doc">
    <p class="public-kicker">Text Interaction</p>
    <h1>XiceTextArranger 文本交互插件</h1>
    <div class="actions"><a class="button secondary" href="/plugins">返回插件列表</a></div>
    <p class="public-lead">XiceTextArranger 用于整理玩家在服务器内外看到的文本提示和验证流程，让白名单注册、黑名单拒绝和维护广播的文案集中管理。</p>
    <h2>功能概述</h2>
    <p>XiceTextArranger 在服务器上主要影响玩家看到的系统文本。未加入白名单的玩家连接服务器时，插件会把拒绝提示改写为更清楚的说明，并生成一次性验证码；插件本身负责生成、保存和展示验证码，不负责处理后续注册提交。</p>
    <p>插件还会处理正版验证失败提示、黑名单拒绝提示、玩家加入/离开消息，以及维护脚本通过 RCON 触发的系统广播。它不改变白名单、正版验证和封禁判断本身，只负责把玩家最终看到的文字整理成更准确的格式。</p>
    <h3>当前管理的文本</h3>
    <ul>
      <li>白名单拒绝提示和注册验证码。</li>
      <li>正版验证失败提示。</li>
      <li>黑名单拒绝登录提示。</li>
      <li>玩家加入与退出消息，可选择保留、删除、重写或追加。</li>
      <li><code>xicebroadcast</code> 系统广播，用于每日维护前的倒计时提醒。</li>
    </ul>
    <h2>部署方式</h2>
    <p>插件基于 Paper API 运行，当前项目按 Paper 1.21.11 和 Java 21 构建。构建产物安装为 <code>/opt/xicemc/runtime/plugins/XiceTextArranger.jar</code>。</p>
    <p>运行时配置文件位于 <code>/opt/xicemc/runtime/plugins/XiceTextArranger/config.yml</code>。配置中包含白名单拒绝提示、验证码生成规则、黑名单提示、正版验证失败匹配文本、进退服消息模式和广播模板。</p>
    <p>插件使用两个运行时数据文件：验证码文件 <code>/opt/xicemc/runtime/plugins/XiceTextArranger/verification-codes.tsv</code>，黑名单文件 <code>/opt/xicemc/runtime/plugins/XiceTextArranger/blacklist.tsv</code>。文件路径由 <code>config.yml</code> 中的 <code>verification-codes.path</code> 和 <code>blacklist.path</code> 配置。</p>
    <p><code>xicebroadcast</code> 命令权限为 <code>xicetextarranger.broadcast</code>，默认仅 OP 或控制台可执行；每日维护脚本通过 RCON 以控制台身份发送维护提醒。</p>
  </article>
</section>
{{end}}

{{define "publicOps"}}{{template "pageStart" .}}{{template "publicOpsContent" .}}{{template "pageEnd" .}}{{end}}
{{define "publicOpsContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <div class="section-heading"><p class="public-kicker">Operations</p><h2>运维记录</h2></div>
  <p>项目运行在个人云主机环境中，维护记录会关注系统服务、备份策略、访问控制、日志排查和资源配置。当前公开页面仅展示技术笔记，非公开后台入口用于个人项目维护。</p>
</section>
{{end}}

{{define "publicChangelog"}}{{template "pageStart" .}}{{template "publicChangelogContent" .}}{{template "pageEnd" .}}{{end}}
{{define "publicChangelogContent"}}
{{template "publicHeader" .}}
<section class="public-page">
  <div class="section-heading"><p class="public-kicker">Changelog</p><h2>更新日志</h2></div>
  <ol><li><strong>技术栈迁移：</strong>Web 端已从 Python 标准库服务迁移至 Go net/http。</li><li><strong>部署策略：</strong>systemd 继续沿用 xicemc-whitelist.service 服务名，实际运行 /opt/xicemc/bin/xicemc-web-go。</li></ol>
</section>
{{end}}

{{define "home"}}{{template "pageStart" .}}{{template "homeContent" .}}{{template "pageEnd" .}}{{end}}
{{define "homeContent"}}
<h1>首页</h1>
{{if .Message}}<p class="message">{{.Message}}</p>{{end}}
<section class="identity-card">
  <div><span class="identity-role {{roleClass .User.Role}}">{{.User.Role}}</span></div>
  <div>
    <h2>{{.User.Name}}</h2>
    <p>{{.User.UUID}}</p>
    <p>{{if .Profile.ProfileBio}}{{.Profile.ProfileBio}}{{else}}一名普通的Minecraft玩家{{end}}</p>
    <p>注册时间：{{formatTimeMillis .Profile.RegisteredAt}}</p>
  </div>
</section>
<div class="actions">
  <a class="button secondary" href="/password">修改密码</a>
  <button class="secondary" type="button" id="open-profile-dialog">编辑简介</button>
</div>
<section class="panel">
  <h2>领地列表</h2>
  <div class="article-grid">
    <div>
      <h3>我拥有的领地</h3>
      {{if .Claims.Owned}}{{range .Claims.Owned}}{{template "claimCard" .}}{{end}}{{else}}<p>暂无自己创建的领地。</p>{{end}}
    </div>
    <div>
      <h3>授权给我的领地</h3>
      {{if .Claims.Trusted}}{{range .Claims.Trusted}}{{template "claimCard" .}}{{end}}{{else}}<p>暂无被授权的领地。</p>{{end}}
    </div>
  </div>
</section>
<dialog id="profile-dialog">
  <div class="panel">
    <h2>编辑简介</h2>
    <form method="post" action="/profile">
      <label for="profile_bio">个人简介</label>
      <textarea id="profile_bio" name="profile_bio" maxlength="120">{{.Profile.ProfileBio}}</textarea>
      <p class="field-hint">最多 120 个字符。留空保存时会恢复默认简介。</p>
      <div class="actions">
        <button class="secondary" type="button" id="close-profile-dialog">取消</button>
        <button type="submit">保存简介</button>
      </div>
    </form>
  </div>
</dialog>
<script>
  const profileDialog = document.getElementById("profile-dialog");
  document.getElementById("open-profile-dialog").addEventListener("click", () => {
    if (typeof profileDialog.showModal === "function") profileDialog.showModal();
    else profileDialog.setAttribute("open", "");
  });
  document.getElementById("close-profile-dialog").addEventListener("click", () => profileDialog.close());
</script>
<section class="panel"><h2>Go 迁移状态</h2><p>当前 Go 版本已接管公开首页、登录会话、注册入口、个人首页、修改密码、编辑简介、领地列表和文档访问控制；复杂后台页面仍在迁移中。</p></section>
{{end}}

{{define "claimCard"}}
<article class="article-card">
  <h3>{{.Name}}</h3>
  <p>所有者：{{.OwnerName}}</p>
  <p>世界：{{.World}}</p>
  <p>坐标：{{.MinX}},{{.MinY}},{{.MinZ}} 到 {{.MaxX}},{{.MaxY}},{{.MaxZ}}</p>
  <p>大小：{{.SizeX}} x {{.SizeY}} x {{.SizeZ}}</p>
  {{if .Totem}}<p>领地图腾：{{.Totem.World}} {{.Totem.X}},{{.Totem.Y}},{{.Totem.Z}}</p>{{else}}<p>领地图腾：未绑定</p>{{end}}
</article>
{{end}}

{{define "password"}}{{template "pageStart" .}}{{template "passwordContent" .}}{{template "pageEnd" .}}{{end}}
{{define "passwordContent"}}
<h1>修改密码</h1>
<section class="panel">
  {{if .Message}}<p class="message">{{.Message}}</p>{{end}}
  <form method="post" action="/password">
    <label for="old_password">原密码</label>
    <input id="old_password" name="old_password" type="password" autocomplete="current-password" required minlength="6" maxlength="64" pattern="[A-Za-z0-9_]+">
    <label for="new_password">新密码</label>
    <input id="new_password" name="new_password" type="password" autocomplete="new-password" required minlength="6" maxlength="64" pattern="[A-Za-z0-9_]+">
    <label for="confirm_password">再次输入新密码</label>
    <input id="confirm_password" name="confirm_password" type="password" autocomplete="new-password" required minlength="6" maxlength="64" pattern="[A-Za-z0-9_]+">
    <div class="actions">
      <button type="submit">保存密码</button>
      <a class="button secondary" href="/home">返回首页</a>
    </div>
  </form>
</section>
{{end}}

{{define "status"}}{{template "pageStart" .}}{{template "statusContent" .}}{{template "pageEnd" .}}{{end}}
{{define "statusContent"}}
<h1>服务器状态</h1>
<section class="panel">
  <div class="status-grid">
    {{template "stateCard" .Status.ServerState}}
    {{template "metricCard" dict "Label" "在线玩家" "Metric" .Status.OnlinePlayers}}
    {{template "metricCard" dict "Label" "磁盘空间" "Metric" .Status.Disk}}
    {{template "metricCard" dict "Label" "内存占用" "Metric" .Status.Memory}}
  </div>
</section>
<section class="panel">
  <h2>备份记录</h2>
  <div class="table-wrap">
    <table>
      <thead><tr><th>备份时间</th><th>文件</th><th>大小</th></tr></thead>
      <tbody>
        {{if .Status.Backups}}
          {{range .Status.Backups}}<tr><td>{{.MTime}}</td><td>{{.Name}}</td><td>{{.Size}}</td></tr>{{end}}
        {{else}}
          <tr><td colspan="3" class="message">暂无备份文件</td></tr>
        {{end}}
      </tbody>
    </table>
  </div>
</section>
<section class="panel">
  <h2>日志 ERROR 情况</h2>
  <p class="message">最近 {{.Status.LogScanLines}} 行日志，匹配 ERROR / Exception / SEVERE 共 {{.Status.LogErrorCount}} 行。</p>
  <div class="mono log-lines">{{if .Status.LogErrors}}{{range .Status.LogErrors}}{{.}}
{{end}}{{else}}最近日志未发现 ERROR / Exception / SEVERE。{{end}}</div>
</section>
{{end}}

{{define "stateCard"}}
<div class="status-card">
  <div class="label">开服状态</div>
  <div class="status-value"><span class="status-pill {{.Class}}">{{.Label}}</span></div>
  <div class="status-subtext">{{.Detail}}</div>
</div>
{{end}}

{{define "metricCard"}}
<div class="status-card">
  <div class="label">{{.Label}}</div>
  <div class="status-value level-{{.Metric.Level}}">{{.Metric.Value}}</div>
  <div class="status-subtext">{{.Metric.Detail}}</div>
  <div class="meter"><div class="meter-fill level-{{.Metric.Level}}-bg" style="width:{{printf "%.1f" .Metric.Percent}}%"></div></div>
</div>
{{end}}

{{define "docs"}}{{template "pageStart" .}}{{template "docsContent" .}}{{template "pageEnd" .}}{{end}}
{{define "docsContent"}}
<div class="page-heading"><h1>服务器文档</h1>{{if or (eq .User.Role "管理员") (eq .User.Role "服主")}}<a class="button secondary" href="/docs/edit">编辑</a>{{end}}</div>
<section class="panel"><div class="pre">{{.Message}}</div></section>
{{end}}

`
