package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"daybricks/server/internal/state"
)

const testToken = "test-token-that-is-at-least-32-characters"

func request(t *testing.T, handler http.Handler, method, token, match, body string) *httptest.ResponseRecorder {
	t.Helper()
	r := httptest.NewRequest(method, "/api/v1/state", strings.NewReader(body))
	if token != "" {
		r.Header.Set("Authorization", "Bearer "+token)
	}
	if match != "" {
		r.Header.Set("If-Match", match)
	}
	r.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	return w
}

func TestGetPutConflictAndDelete(t *testing.T) {
	store, _ := state.NewFileStateStore(t.TempDir())
	handler := NewHandler(store, testToken, "owner")
	get := request(t, handler, "GET", testToken, "", "")
	if get.Code != 200 || get.Header().Get("ETag") != `"0"` {
		t.Fatalf("GET: %d %v", get.Code, get.Header())
	}
	value := state.Empty()
	value.Templates = []state.Template{{ID: "9d30e090-2a77-4ee6-bdb6-26077db1e428", Title: "Training", DefaultDurationMinutes: 16, Presets: []state.Preset{}}}
	data, _ := json.Marshal(value)
	put := request(t, handler, "PUT", testToken, `"0"`, string(data))
	if put.Code != 204 || put.Header().Get("ETag") != `"1"` {
		t.Fatalf("PUT: %d %s", put.Code, put.Body.String())
	}
	conflict := request(t, handler, "PUT", testToken, `"0"`, string(data))
	if conflict.Code != 412 {
		t.Fatal("expected conflict", conflict.Code)
	}
	get = request(t, handler, "GET", testToken, "", "")
	if !bytes.Contains(get.Body.Bytes(), []byte("Training")) {
		t.Fatal("template missing")
	}
	value.Templates = []state.Template{}
	data, _ = json.Marshal(value)
	if response := request(t, handler, "PUT", testToken, `"1"`, string(data)); response.Code != 204 {
		t.Fatal("delete failed", response.Code)
	}
	get = request(t, handler, "GET", testToken, "", "")
	if bytes.Contains(get.Body.Bytes(), []byte("Training")) {
		t.Fatal("delete did not persist")
	}
}

func TestAuthValidationAndPreconditions(t *testing.T) {
	store, _ := state.NewFileStateStore(t.TempDir())
	handler := NewHandler(store, testToken, "owner")
	data, _ := json.Marshal(state.Empty())
	cases := []struct {
		method, token, match, body string
		code                       int
	}{
		{"GET", "", "", "", 401}, {"GET", "wrong", "", "", 401},
		{"POST", testToken, "", "", 405},
		{"PUT", testToken, "", string(data), 428},
		{"PUT", testToken, "*", string(data), 400},
		{"PUT", testToken, `W/"0"`, string(data), 400},
		{"PUT", testToken, `"0"`, "broken", 400},
		{"PUT", testToken, `"0"`, strings.Repeat("x", state.MaxBytes+1), 413},
	}
	for _, tc := range cases {
		if got := request(t, handler, tc.method, tc.token, tc.match, tc.body).Code; got != tc.code {
			t.Errorf("%s: got %d want %d", tc.method, got, tc.code)
		}
	}
	loaded, _ := store.Load("owner")
	if loaded.Revision != 0 {
		t.Fatal("invalid requests mutated store")
	}
}
