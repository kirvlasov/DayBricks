package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"daybricks/server/internal/api"
	"daybricks/server/internal/state"
)

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func main() {
	token := strings.TrimSpace(os.Getenv("DAYBRICKS_TOKEN"))
	if len(token) < 32 {
		log.Fatal("DAYBRICKS_TOKEN must contain at least 32 characters")
	}
	store, err := state.NewFileStateStore(env("DAYBRICKS_DATA_DIR", "./data"))
	if err != nil {
		log.Fatal("could not open data directory")
	}
	server := &http.Server{
		Addr:              env("DAYBRICKS_LISTEN", "127.0.0.1:8080"),
		Handler:           api.NewHandler(store, token, env("DAYBRICKS_PRINCIPAL", "owner")),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 * 1024,
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = server.Shutdown(shutdown)
	}()
	log.Printf("DayBricks sync listening on %s", server.Addr)
	if err = server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal("HTTP server stopped unexpectedly")
	}
}
