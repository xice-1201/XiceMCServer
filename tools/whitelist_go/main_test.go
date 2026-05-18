package main

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"testing"
	"time"
)

func TestCanonicalUUID(t *testing.T) {
	got := canonicalUUID("1234567890abcdef1234567890ABCDEF")
	want := "12345678-90ab-cdef-1234-567890abcdef"
	if got != want {
		t.Fatalf("canonicalUUID() = %q, want %q", got, want)
	}
}

func TestSessionRoundTrip(t *testing.T) {
	dir := t.TempDir()
	cfg := config{
		SessionSecret: "test-secret",
		WhitelistPath: filepath.Join(dir, "whitelist.json"),
	}
	a := &app{cfg: cfg}
	entry := whitelistEntry{
		UUID: "12345678-90ab-cdef-1234-567890abcdef",
		Name: "ExamplePlayer",
	}
	if _, err := a.addWhitelistEntry(entry); err != nil {
		t.Fatalf("addWhitelistEntry() error = %v", err)
	}
	session, err := a.makeSession(entry)
	if err != nil {
		t.Fatalf("makeSession() error = %v", err)
	}
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.AddCookie(&http.Cookie{Name: sessionCookieName, Value: session})
	user := a.currentUser(req)
	if user == nil {
		t.Fatal("currentUser() = nil")
	}
	if user.Name != "ExamplePlayer" || user.UUID != entry.UUID {
		t.Fatalf("currentUser() = %#v", user)
	}
}

func TestVerificationCodeConsume(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "verification-codes.tsv")
	expiresAt := time.Now().Add(time.Minute).UnixMilli()
	content := "# key\tuuid\tplayer\tcode\texpiresAtMillis\n" +
		"exampleplayer\t12345678-90ab-cdef-1234-567890abcdef\tExamplePlayer\tABC123\t" +
		"9999999999999\n" +
		"other\t12345678-90ab-cdef-1234-567890abcdee\tOther\tDEF456\t" +
		"9999999999999\n"
	content = replaceLast(content, "9999999999999", int64String(expiresAt))
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	a := &app{cfg: config{VerifyCodesPath: path}}
	if _, ok := a.findVerificationCode("ExamplePlayer", "ABC123"); !ok {
		t.Fatal("expected verification code")
	}
	if err := a.consumeVerificationCode("ExamplePlayer", "ABC123"); err != nil {
		t.Fatalf("consumeVerificationCode() error = %v", err)
	}
	if _, ok := a.findVerificationCode("ExamplePlayer", "ABC123"); ok {
		t.Fatal("consumed code still exists")
	}
	if _, ok := a.findVerificationCode("Other", "DEF456"); !ok {
		t.Fatal("unrelated code was removed")
	}
}

func TestParseClaimsYAML(t *testing.T) {
	text := `
claims:
  home:
    name: Home
    owner-uuid: 12345678-90ab-cdef-1234-567890abcdef
    owner-name: ExamplePlayer
    world: main
    min-x: 1
    min-y: 2
    min-z: 3
    max-x: 5
    max-y: 6
    max-z: 7
    members:
      - 12345678-90ab-cdef-1234-567890abcdee
    member-names:
      12345678-90ab-cdef-1234-567890abcdee: Other
`
	claims := parseClaimsYAML(text)
	if len(claims) != 1 {
		t.Fatalf("len(claims) = %d, want 1", len(claims))
	}
	claim := claims[0]
	if claim.Name != "Home" || claim.OwnerName != "ExamplePlayer" || claim.World != "main" {
		t.Fatalf("unexpected claim: %#v", claim)
	}
	if claim.SizeX != 5 || claim.SizeY != 5 || claim.SizeZ != 5 {
		t.Fatalf("unexpected size: %#v", claim)
	}
	if len(claim.Members) != 1 || claim.MemberName["12345678-90ab-cdef-1234-567890abcdee"] != "Other" {
		t.Fatalf("unexpected members: %#v", claim)
	}
}

func TestParseOnlinePlayerSummary(t *testing.T) {
	metric := parseOnlinePlayerSummary("There are 3 of a max of 20 players online: ExamplePlayer, Other")
	if metric.Value != "3 / 20 (15.0%)" {
		t.Fatalf("metric.Value = %q", metric.Value)
	}
	if metric.Level != "low" {
		t.Fatalf("metric.Level = %q", metric.Level)
	}
}

func TestFormatBytes(t *testing.T) {
	cases := map[int64]string{
		12:         "12 B",
		1536:       "1.5 KB",
		1073741824: "1.0 GB",
	}
	for input, want := range cases {
		if got := formatBytes(input); got != want {
			t.Fatalf("formatBytes(%d) = %q, want %q", input, got, want)
		}
	}
}

func TestTemplatesParse(t *testing.T) {
	if mustTemplates() == nil {
		t.Fatal("mustTemplates() = nil")
	}
}

func TestAuthenticatedTemplatesExecute(t *testing.T) {
	templates := mustTemplates()
	user := &userSession{UUID: "12345678-90ab-cdef-1234-567890abcdef", Name: "ExamplePlayer", Role: ownerRole}
	data := pageData{
		Title:  "test",
		User:   user,
		Public: publicData{SiteBaseURL: "http://127.0.0.1"},
		Status: serverStatus{
			ServerState:   statusState{Label: "运行中", Class: "state-running", Detail: "xicemc.service"},
			OnlinePlayers: statusMetric{Value: "0 / 20 (0.0%)", Detail: "当前在线 / 最大人数", Level: "low"},
			Disk:          statusMetric{Value: "1.0 GB / 2.0 GB (50.0%)", Detail: "已用 / 总量", Percent: 50, Level: "medium"},
			Memory:        statusMetric{Value: "1.0 GB / 2.0 GB (50.0%)", Detail: "已用 / 总量", Percent: 50, Level: "medium"},
			LogScanLines:  500,
		},
		Permissions: permissionPageData{
			SelectedCommand: "creative",
			CommandLabel:    "/creative",
			Commands:        []commandOption{{ID: "creative", Label: "/creative", Selected: true}},
		},
		Audit: auditPageData{Query: auditQuery{}},
	}
	for _, name := range []string{"home", "status", "players", "permissions", "report", "reports", "blacklist", "audit", "docsEdit"} {
		if err := templates.ExecuteTemplate(&bytes.Buffer{}, name, data); err != nil {
			t.Fatalf("ExecuteTemplate(%s) error = %v", name, err)
		}
	}
}

func replaceLast(value, old string, newValue string) string {
	index := -1
	for i := 0; i+len(old) <= len(value); i++ {
		if value[i:i+len(old)] == old {
			index = i
		}
	}
	if index < 0 {
		return value
	}
	return value[:index] + newValue + value[index+len(old):]
}

func int64String(value int64) string {
	return strconv.FormatInt(value, 10)
}
