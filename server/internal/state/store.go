package state

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"sync"
)

var ErrConflict = errors.New("revision conflict")

type Snapshot struct {
	Revision uint64 `json:"revision"`
	State    State  `json:"state"`
}

type StateStore interface {
	Load(principalID string) (Snapshot, error)
	CompareAndSwap(principalID string, expected uint64, next State) (Snapshot, error)
}

// One server process owns a data directory. The mutex serializes CAS and disk reads.
// Authentication selects the principal; clients never supply a filesystem key.
type FileStateStore struct {
	directory string
	mu        sync.Mutex
}

func NewFileStateStore(directory string) (*FileStateStore, error) {
	if err := os.MkdirAll(directory, 0700); err != nil {
		return nil, err
	}
	return &FileStateStore{directory: directory}, nil
}

func (f *FileStateStore) path(principalID string) string {
	hash := sha256.Sum256([]byte(principalID))
	return filepath.Join(f.directory, hex.EncodeToString(hash[:]), "state.json")
}

func (f *FileStateStore) load(principalID string) (Snapshot, error) {
	data, err := os.ReadFile(f.path(principalID))
	if errors.Is(err, os.ErrNotExist) {
		return Snapshot{State: Empty()}, nil
	}
	if err != nil {
		return Snapshot{}, err
	}
	var snapshot Snapshot
	if err = json.Unmarshal(data, &snapshot); err != nil {
		return Snapshot{}, fmt.Errorf("corrupt state file: %w", err)
	}
	if err = snapshot.State.Validate(); err != nil {
		return Snapshot{}, err
	}
	return snapshot, nil
}

func (f *FileStateStore) Load(principalID string) (Snapshot, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.load(principalID)
}

func (f *FileStateStore) CompareAndSwap(principalID string, expected uint64, next State) (Snapshot, error) {
	if err := next.Validate(); err != nil {
		return Snapshot{}, err
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	previous, err := f.load(principalID)
	if err != nil {
		return Snapshot{}, err
	}
	if previous.Revision != expected {
		return Snapshot{}, ErrConflict
	}
	if expected == ^uint64(0) {
		return Snapshot{}, errors.New("revision exhausted")
	}
	nextSnapshot := Snapshot{Revision: expected + 1, State: next}
	data, err := json.Marshal(nextSnapshot)
	if err != nil {
		return Snapshot{}, err
	}
	path := f.path(principalID)
	if err = os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return Snapshot{}, err
	}
	temporary, err := os.CreateTemp(filepath.Dir(path), ".state-*")
	if err != nil {
		return Snapshot{}, err
	}
	defer os.Remove(temporary.Name())
	defer temporary.Close()
	if _, err = temporary.Write(data); err != nil {
		return Snapshot{}, err
	}
	if err = temporary.Sync(); err != nil {
		return Snapshot{}, err
	}
	if err = temporary.Close(); err != nil {
		return Snapshot{}, err
	}
	if err = os.Rename(temporary.Name(), path); err != nil {
		return Snapshot{}, err
	}
	if runtime.GOOS != "windows" {
		directory, openErr := os.Open(filepath.Dir(path))
		if openErr != nil {
			return Snapshot{}, openErr
		}
		defer directory.Close()
		if err = directory.Sync(); err != nil {
			return Snapshot{}, err
		}
	}
	return nextSnapshot, nil
}
