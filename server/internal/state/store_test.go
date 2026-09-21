package state

import (
	"errors"
	"os"
	"sync"
	"sync/atomic"
	"testing"
)

func TestStorePersistenceAndIsolation(t *testing.T) {
	directory := t.TempDir()
	store, err := NewFileStateStore(directory)
	if err != nil {
		t.Fatal(err)
	}
	initial, err := store.Load("a")
	if err != nil || initial.Revision != 0 {
		t.Fatalf("initial = %+v, %v", initial, err)
	}
	next := Empty()
	next.Preferences.DominantHand = "LEFT"
	saved, err := store.CompareAndSwap("a", 0, next)
	if err != nil || saved.Revision != 1 {
		t.Fatalf("save = %+v, %v", saved, err)
	}
	restarted, _ := NewFileStateStore(directory)
	loaded, err := restarted.Load("a")
	if err != nil || loaded.State.Preferences.DominantHand != "LEFT" {
		t.Fatal("state did not survive restart", err)
	}
	other, err := restarted.Load("b")
	if err != nil || other.Revision != 0 || other.State.Preferences.DominantHand != "RIGHT" {
		t.Fatal("principals leaked", err)
	}
	if _, err := store.CompareAndSwap("a", 0, Empty()); !errors.Is(err, ErrConflict) {
		t.Fatal("stale write accepted")
	}
}

func TestConcurrentCASHasExactlyOneWinner(t *testing.T) {
	store, _ := NewFileStateStore(t.TempDir())
	var winners atomic.Int32
	var wait sync.WaitGroup
	for range 20 {
		wait.Add(1)
		go func() {
			defer wait.Done()
			_, err := store.CompareAndSwap("owner", 0, Empty())
			if err == nil {
				winners.Add(1)
			} else if !errors.Is(err, ErrConflict) {
				t.Errorf("CAS: %v", err)
			}
		}()
	}
	wait.Wait()
	if winners.Load() != 1 {
		t.Fatalf("winners = %d", winners.Load())
	}
}

func TestCorruptFileIsNeverOverwritten(t *testing.T) {
	store, _ := NewFileStateStore(t.TempDir())
	_, _ = store.CompareAndSwap("owner", 0, Empty())
	path := store.path("owner")
	if err := os.WriteFile(path, []byte("broken"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := store.Load("owner"); err == nil {
		t.Fatal("corrupt file loaded")
	}
	if _, err := store.CompareAndSwap("owner", 0, Empty()); err == nil {
		t.Fatal("corrupt file overwritten")
	}
	data, _ := os.ReadFile(path)
	if string(data) != "broken" {
		t.Fatal("file changed")
	}
}

func TestDecodeRejectsInvalidState(t *testing.T) {
	for _, input := range []string{
		`{"schemaVersion":3,"templates":[],"preferences":{"dominantHand":"RIGHT"}}`,
		`{"schemaVersion":1,"templates":[],"preferences":{"dominantHand":"RIGHT"},"events":[]}`,
		`{"schemaVersion":1,"templates":null,"preferences":{"dominantHand":"RIGHT"}}`,
		`{} {}`,
		`not json`,
	} {
		if _, err := Decode([]byte(input)); err == nil {
			t.Errorf("accepted %s", input)
		}
	}
}
