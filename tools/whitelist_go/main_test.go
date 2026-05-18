package main

import (
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
