package api

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"strconv"
	"strings"

	"daybricks/server/internal/state"
)

type principalKey struct{}
type Principal struct{ ID string }

func NewHandler(store state.StateStore, token, principalID string) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/state", func(w http.ResponseWriter, r *http.Request) {
		principal := r.Context().Value(principalKey{}).(Principal)
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		switch r.Method {
		case http.MethodGet:
			snapshot, err := store.Load(principal.ID)
			if err != nil {
				http.Error(w, "state unavailable", http.StatusInternalServerError)
				return
			}
			w.Header().Set("ETag", etag(snapshot.Revision))
			_ = json.NewEncoder(w).Encode(snapshot.State)
		case http.MethodPut:
			mediaType, _, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
			if err != nil || mediaType != "application/json" {
				http.Error(w, "application/json required", http.StatusUnsupportedMediaType)
				return
			}
			match := r.Header.Get("If-Match")
			if match == "" {
				http.Error(w, "If-Match required", http.StatusPreconditionRequired)
				return
			}
			expected, err := parseETag(match)
			if err != nil {
				http.Error(w, "invalid If-Match", http.StatusBadRequest)
				return
			}
			r.Body = http.MaxBytesReader(w, r.Body, state.MaxBytes)
			data, err := io.ReadAll(r.Body)
			if err != nil {
				http.Error(w, "request exceeds body limit", http.StatusRequestEntityTooLarge)
				return
			}
			next, err := state.Decode(data)
			if err != nil {
				http.Error(w, "invalid state", http.StatusBadRequest)
				return
			}
			snapshot, err := store.CompareAndSwap(principal.ID, expected, next)
			if errors.Is(err, state.ErrConflict) {
				http.Error(w, "revision conflict", http.StatusPreconditionFailed)
				return
			}
			if err != nil {
				http.Error(w, "state unavailable", http.StatusInternalServerError)
				return
			}
			w.Header().Set("ETag", etag(snapshot.Revision))
			w.WriteHeader(http.StatusNoContent)
		default:
			w.Header().Set("Allow", "GET, PUT")
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		}
	})
	expectedHash := sha256.Sum256([]byte(token))
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		provided, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		providedHash := sha256.Sum256([]byte(provided))
		if !ok || token == "" || subtle.ConstantTimeCompare(expectedHash[:], providedHash[:]) != 1 {
			w.Header().Set("WWW-Authenticate", "Bearer")
			w.Header().Set("Cache-Control", "no-store")
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		ctx := context.WithValue(r.Context(), principalKey{}, Principal{ID: principalID})
		mux.ServeHTTP(w, r.WithContext(ctx))
	})
}

func etag(revision uint64) string { return fmt.Sprintf(`"%d"`, revision) }
func parseETag(value string) (uint64, error) {
	if len(value) < 3 || value[0] != '"' || value[len(value)-1] != '"' {
		return 0, errors.New("invalid ETag")
	}
	number := value[1 : len(value)-1]
	revision, err := strconv.ParseUint(number, 10, 64)
	if err != nil || etag(revision) != value {
		return 0, errors.New("invalid ETag")
	}
	return revision, nil
}
