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
	usernameRE = regexp.MustCompile(`^[A-Za-z0-9_]{3,16}$`)
	passwordRE = regexp.MustCompile(`^[A-Za-z0-9_]{6,64}$`)
)

type config struct {
	Host                    string
	Port                    string
	RconHost                string
	RconPort                string
	RconPassword            string
	RuntimeDir              string
	WhitelistPath           string
	VerifyCodesPath         string
	ClaimsPath              string
	WebIconPath             string
	WebFaviconPath          string
	ResourcePackPath        string
	ServerDocsPath          string
	SessionSecret           string
	PublicSiteBaseURL       string
	PublicSiteDomain        string
	ICPRecordNo             string
	ICPRecordURL            string
	PublicSecurityRecordNo  string
	PublicSecurityRecordURL string
	DBHost                  string
	DBPort                  string
	DBName                  string
	DBUser                  string
	DBPassword              string
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
}

type publicData struct {
	SiteBaseURL             string
	SiteDomain              string
	ICPRecordNo             string
	ICPRecordURL            string
	PublicSecurityRecordNo  string
	PublicSecurityRecordURL string
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
		Host:                    env("WHITELIST_WEB_HOST", "0.0.0.0"),
		Port:                    env("WHITELIST_WEB_PORT", "8080"),
		RconHost:                env("XICEMC_RCON_HOST", "127.0.0.1"),
		RconPort:                env("XICEMC_RCON_PORT", "25575"),
		RconPassword:            rconPassword,
		RuntimeDir:              runtimeDir,
		WhitelistPath:           env("XICEMC_WHITELIST_PATH", filepath.Join(runtimeDir, "whitelist.json")),
		VerifyCodesPath:         env("XICEMC_VERIFY_CODES_PATH", filepath.Join(runtimeDir, "plugins", "XiceTextArranger", "verification-codes.tsv")),
		ClaimsPath:              env("XICEMC_CLAIMS_PATH", filepath.Join(runtimeDir, "plugins", "XiceClaim", "claims.yml")),
		WebIconPath:             env("XICEMC_WEB_ICON_PATH", filepath.Join(repoRoot, "server", "assets", "xicemc-logo.png")),
		WebFaviconPath:          env("XICEMC_WEB_FAVICON_PATH", filepath.Join(repoRoot, "server", "assets", "favicon.ico")),
		ResourcePackPath:        env("XICEMC_RESOURCE_PACK_PATH", filepath.Join(repoRoot, "server", "resourcepacks", "xiceclaim.zip")),
		ServerDocsPath:          env("XICEMC_DOCS_HOME_PATH", filepath.Join(runtimeDir, "web", "server-docs.md")),
		SessionSecret:           env("WHITELIST_WEB_SESSION_SECRET", rconPassword),
		PublicSiteBaseURL:       strings.TrimRight(env("XICEMC_PUBLIC_SITE_BASE_URL", "http://150.158.93.80"), "/"),
		PublicSiteDomain:        env("XICEMC_PUBLIC_SITE_DOMAIN", "xicemc.site"),
		ICPRecordNo:             env("XICEMC_ICP_RECORD_NO", ""),
		ICPRecordURL:            env("XICEMC_ICP_RECORD_URL", "https://beian.miit.gov.cn/"),
		PublicSecurityRecordNo:  env("XICEMC_PUBLIC_SECURITY_RECORD_NO", ""),
		PublicSecurityRecordURL: env("XICEMC_PUBLIC_SECURITY_RECORD_URL", ""),
		DBHost:                  env("XICE_AUDIT_DB_HOST", "127.0.0.1"),
		DBPort:                  env("XICE_AUDIT_DB_PORT", "5432"),
		DBName:                  env("XICE_AUDIT_DB_NAME", "xicemc_audit"),
		DBUser:                  env("XICE_AUDIT_DB_USER", "xicemc_audit"),
		DBPassword:              env("XICE_AUDIT_DB_PASSWORD", ""),
	}
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
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
	mux.HandleFunc("GET /resourcepacks/xiceclaim.zip", a.fileHandler(a.cfg.ResourcePackPath, "application/zip"))
	mux.HandleFunc("GET /", a.handlePublicHome)
	mux.HandleFunc("GET /register", a.handleRegisterPage)
	mux.HandleFunc("POST /login", a.handleLogin)
	mux.HandleFunc("POST /register", a.handleRegister)
	mux.HandleFunc("POST /logout", a.handleLogout)
	mux.HandleFunc("GET /home", a.requireUser(a.handleHome))
	mux.HandleFunc("GET /password", a.requireUser(a.handlePasswordPage))
	mux.HandleFunc("POST /password", a.requireUser(a.handlePasswordUpdate))
	mux.HandleFunc("POST /profile", a.requireUser(a.handleProfileUpdate))
	mux.HandleFunc("GET /docs", a.requireUser(a.handleDocs))
	mux.HandleFunc("GET /status", a.requireUser(a.handleMigrationPlaceholder("服务器状态")))
	mux.HandleFunc("GET /players", a.requireUser(a.handleMigrationPlaceholder("玩家列表")))
	mux.HandleFunc("GET /audit", a.requireUser(a.handleMigrationPlaceholder("操作查询")))
	mux.HandleFunc("GET /report", a.requireUser(a.handleMigrationPlaceholder("举报")))
	mux.HandleFunc("GET /reports", a.requireManager(a.handleMigrationPlaceholder("举报受理")))
	mux.HandleFunc("GET /blacklist", a.requireUser(a.handleMigrationPlaceholder("黑名单列表")))
	mux.HandleFunc("GET /permissions", a.requireManager(a.handleMigrationPlaceholder("权限管理")))
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
	a.render(w, http.StatusOK, "public", pageData{Title: "个人技术开发随记", Public: a.publicData()})
}

func (a *app) handleRegisterPage(w http.ResponseWriter, r *http.Request) {
	a.render(w, http.StatusOK, "public", pageData{
		Title:        "个人技术开发随记",
		Public:       a.publicData(),
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

func (a *app) handleMigrationPlaceholder(title string) func(http.ResponseWriter, *http.Request, *userSession) {
	return func(w http.ResponseWriter, r *http.Request, user *userSession) {
		a.render(w, http.StatusOK, "placeholder", pageData{
			Title:   title,
			User:    user,
			Active:  strings.TrimPrefix(r.URL.Path, "/"),
			Public:  a.publicData(),
			Message: "该页面的 Go 迁移骨架已接入，完整后台交互将在后续迁移批次中补齐。线上切换前仍由 Python 版本承载完整功能。",
		})
	}
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
	data := pageData{Title: "个人技术开发随记", Public: a.publicData(), Error: message}
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
			default:
				setClaimScalar(current, key, yamlScalar(value))
			}
			continue
		}
		if indent >= 6 && currentMap == "member-names" {
			current.MemberName[canonicalUUID(key)] = yamlScalar(value)
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
	}
	return template.Must(template.New("root").Funcs(funcs).Parse(templatesHTML))
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
    .public-brand { color:var(--text); font-weight:800; }
    .public-nav { display:flex; gap:6px; justify-content:center; }
    .public-nav a { display:inline-flex; align-items:center; min-height:34px; padding:6px 10px; border-radius:6px; color:var(--muted); font-size:14px; }
    .public-auth { display:flex; gap:8px; justify-content:flex-end; position:relative; }
    .auth-popover { position:relative; }
    .auth-popover summary { list-style:none; display:inline-flex; align-items:center; min-height:36px; padding:7px 11px; border-radius:6px; background:#e7edf5; color:var(--text); font-size:14px; cursor:pointer; user-select:none; }
    .auth-popover summary::-webkit-details-marker { display:none; }
    .auth-popover[open] summary { background:var(--accent); color:#fff; }
    .auth-popover-panel { position:absolute; right:0; top:calc(100% + 10px); width:min(340px,calc(100vw - 32px)); padding:16px; border:1px solid var(--line); border-radius:8px; background:#fff; box-shadow:0 18px 50px rgba(20,32,51,.18); }
    .public-hero { display:grid; grid-template-columns:minmax(0,1fr) minmax(230px,300px); gap:36px; align-items:end; width:min(100% - 48px,1120px); min-height:58vh; margin:0 auto; padding:82px 0 54px; }
    .public-hero h1 { margin:0 0 18px; font-size:48px; line-height:1.08; }
    .public-hero p { max-width:720px; margin:0; font-size:18px; color:#405169; }
    .public-kicker { margin:0 0 10px; color:#2b6c9f; font-size:13px; font-weight:800; text-transform:uppercase; letter-spacing:0; }
    .public-directory { border-left:3px solid #d8e0ea; padding-left:18px; }
    .public-directory a { display:block; padding:7px 0; color:var(--muted); }
    .public-section { width:min(100% - 48px,1120px); margin:0 auto; padding:42px 0; border-top:1px solid var(--line); }
    .section-heading { margin-bottom:18px; }
    .article-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
    .article-card { min-width:0; min-height:178px; padding:18px; border:1px solid var(--line); border-radius:8px; background:#fff; }
    .note-list { display:grid; border-top:1px solid var(--line); }
    .note-list article { display:grid; grid-template-columns:120px minmax(0,1fr); gap:18px; padding:18px 0; border-bottom:1px solid var(--line); }
    .panel { border:1px solid var(--line); border-radius:8px; background:var(--panel); padding:20px; margin-bottom:18px; }
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
    .register-message,.error { color:var(--danger); font-weight:700; border:1px solid #f1b7b7; background:#fff1f1; border-radius:6px; padding:10px 12px; }
    .field-hint { margin:6px 0 0; color:var(--muted); font-size:13px; line-height:1.5; }
    .compliance-footer { display:flex; flex-wrap:wrap; gap:8px 16px; margin-top:26px; padding-top:16px; border-top:1px solid var(--line); color:var(--muted); font-size:13px; line-height:1.5; }
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
    .public-brand { color:var(--text); font-weight:800; }
    .public-nav { display:flex; gap:6px; justify-content:center; }
    .public-nav a { display:inline-flex; align-items:center; min-height:34px; padding:6px 10px; border-radius:6px; color:var(--muted); font-size:14px; }
    .public-auth { display:flex; gap:8px; justify-content:flex-end; position:relative; }
    .auth-popover { position:relative; }
    .auth-popover summary { list-style:none; display:inline-flex; align-items:center; min-height:36px; padding:7px 11px; border-radius:6px; background:#e7edf5; color:var(--text); font-size:14px; cursor:pointer; user-select:none; }
    .auth-popover summary::-webkit-details-marker { display:none; }
    .auth-popover[open] summary { background:var(--accent); color:#fff; }
    .auth-popover-panel { position:absolute; right:0; top:calc(100% + 10px); width:min(340px,calc(100vw - 32px)); padding:16px; border:1px solid var(--line); border-radius:8px; background:#fff; box-shadow:0 18px 50px rgba(20,32,51,.18); }
    .public-hero { display:grid; grid-template-columns:minmax(0,1fr) minmax(230px,300px); gap:36px; align-items:end; width:min(100% - 48px,1120px); min-height:58vh; margin:0 auto; padding:82px 0 54px; }
    .public-hero h1 { margin:0 0 18px; font-size:48px; line-height:1.08; }
    .public-hero p { max-width:720px; margin:0; font-size:18px; color:#405169; }
    .public-kicker { margin:0 0 10px; color:#2b6c9f; font-size:13px; font-weight:800; text-transform:uppercase; letter-spacing:0; }
    .public-directory { border-left:3px solid #d8e0ea; padding-left:18px; }
    .public-directory a { display:block; padding:7px 0; color:var(--muted); }
    .public-section { width:min(100% - 48px,1120px); margin:0 auto; padding:42px 0; border-top:1px solid var(--line); }
    .article-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }
    .article-card,.panel { border:1px solid var(--line); border-radius:8px; background:#fff; padding:18px; }
    .panel { padding:20px; margin-bottom:18px; }
    .note-list { display:grid; border-top:1px solid var(--line); }
    .note-list article { display:grid; grid-template-columns:120px minmax(0,1fr); gap:18px; padding:18px 0; border-bottom:1px solid var(--line); }
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
    .compliance-footer { display:flex; flex-wrap:wrap; gap:8px 16px; margin-top:26px; padding-top:16px; border-top:1px solid var(--line); color:var(--muted); font-size:13px; line-height:1.5; }
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
    <main>{{end}}

{{define "pageEnd"}}{{template "footer" .}}</main></div></body></html>{{end}}

{{define "public"}}{{template "pageStart" .}}{{template "publicContent" .}}{{template "pageEnd" .}}{{end}}
{{define "publicContent"}}
<header class="public-header">
  <a class="public-brand" href="/">个人技术开发随记</a>
  <nav class="public-nav" aria-label="首页目录">
    <a href="#overview">首页介绍</a><a href="#stack">技术实现</a><a href="#plugins">插件动态</a><a href="#log">更新日志</a>
  </nav>
  <div class="public-auth">
    <details class="auth-popover" {{if .LoginOpen}}open{{end}}>
      <summary>后台登录</summary>
      <div class="auth-popover-panel">
        {{if and .Error .LoginOpen}}<p class="error">{{.Error}}</p>{{end}}
        <form method="post" action="/login">
          <label for="login-username">项目 ID</label>
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
          <label for="register-username">项目 ID</label>
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
<section class="public-hero" id="overview">
  <div>
    <p class="public-kicker">Java / Linux / Web / Personal Project Notes</p>
    <h1>个人技术开发随记</h1>
    <p>这里记录我的个人技术学习、开发实验和项目维护过程，内容以 Java 插件开发、Linux 运维、Web 页面开发、自动化部署和项目更新日志为主。</p>
  </div>
  <aside class="public-directory" aria-label="内容目录">
    <h2>目录</h2>
    <a href="#stack">技术实现</a><a href="#plugins">插件动态</a><a href="#ops">运维记录</a><a href="#log">更新日志</a>
  </aside>
</section>
<section class="public-section" id="stack">
  <div class="section-heading"><p class="public-kicker">Implementation</p><h2>技术实现</h2></div>
  <div class="article-grid">
    <article class="article-card"><h3>Java 插件开发</h3><p>围绕权限、领地、审计和文本交互等模块整理实现思路，记录事件监听、配置持久化、命令控制和用户界面迭代。</p></article>
    <article class="article-card"><h3>Web 页面开发</h3><p>记录 Go 后端迁移、会话管理、权限页面、审计查询和后台页面拆分过程，关注小型项目的可维护性。</p></article>
    <article class="article-card"><h3>自动化部署</h3><p>整理从代码提交、构建、备份到服务重启的部署流程，保留每次调整背后的取舍与问题复盘。</p></article>
  </div>
</section>
<section class="public-section" id="plugins">
  <div class="section-heading"><p class="public-kicker">Plugin Notes</p><h2>插件动态</h2></div>
  <div class="note-list">
    <article><span>2026-05</span><div><h3>领地交互 UI 调整</h3><p>通过虚拟容器界面组织坐标选择、范围预览、权限状态和授权成员管理，减少命令依赖。</p></div></article>
    <article><span>2026-05</span><div><h3>Go Web 迁移启动</h3><p>先迁移公开首页、登录、注册和文档访问控制，为后续后台页面迁移打基础。</p></div></article>
  </div>
</section>
<section class="public-section" id="ops">
  <div class="section-heading"><p class="public-kicker">Operations</p><h2>运维记录</h2></div>
  <p>项目运行在个人云主机环境中，维护记录会关注系统服务、备份策略、访问控制、日志排查和资源配置。当前公开页面仅展示技术笔记，非公开后台入口用于个人项目维护。</p>
</section>
<section class="public-section" id="log">
  <div class="section-heading"><p class="public-kicker">Changelog</p><h2>更新日志</h2></div>
  <ol><li><strong>技术栈迁移：</strong>新增 Go Web 骨架，先覆盖公开首页和白名单注册入口。</li><li><strong>部署策略：</strong>Go 服务暂不替换线上 Python，待后台页面迁移完成后再切换。</li></ol>
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

{{define "docs"}}{{template "pageStart" .}}{{template "docsContent" .}}{{template "pageEnd" .}}{{end}}
{{define "docsContent"}}
<h1>服务器文档</h1>
<section class="panel"><div class="pre">{{.Message}}</div></section>
{{end}}

{{define "placeholder"}}{{template "pageStart" .}}{{template "placeholderContent" .}}{{template "pageEnd" .}}{{end}}
{{define "placeholderContent"}}
<h1>{{.Title}}</h1>
<section class="panel"><p>{{.Message}}</p></section>
{{end}}
`
