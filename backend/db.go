package main

import (
	"encoding/json"
	"log"
	"os"
	"sync"
	"time"
)

// SymbolLabel is one classified symbol awaiting or having gone through validation.
type SymbolLabel struct {
	ID            string  `json:"id"`
	SessionID     string  `json:"session_id"`
	ApplianceType string  `json:"appliance_type"`
	S3Key         string  `json:"s3_key"`
	CropS3Key     *string `json:"crop_s3_key,omitempty"`
	Name          string  `json:"name"`
	Description   string  `json:"description"`
	Category      string  `json:"category"`
	Confidence    string  `json:"confidence"`
	// 0 = pending, 1 = approved, -1 = rejected
	Validated int   `json:"validated"`
	CreatedAt int64 `json:"created_at"`
}

// labelStore is an in-memory label database backed by a JSON file.
type labelStore struct {
	mu     sync.RWMutex
	path   string
	labels []SymbolLabel
}

var store *labelStore

func initStore(path string) error {
	s := &labelStore{path: path}
	data, err := os.ReadFile(path)
	if err != nil && !os.IsNotExist(err) {
		return err
	}
	if len(data) > 0 {
		if jsonErr := json.Unmarshal(data, &s.labels); jsonErr != nil {
			log.Printf("warn: could not parse %s, starting fresh: %v", path, jsonErr)
		}
	}
	store = s
	return nil
}

func (s *labelStore) insert(l SymbolLabel) {
	l.CreatedAt = time.Now().Unix()
	s.mu.Lock()
	s.labels = append(s.labels, l)
	s.mu.Unlock()
	s.flush()
}

func (s *labelStore) query(validated int) []SymbolLabel {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var out []SymbolLabel
	for _, l := range s.labels {
		if l.Validated == validated {
			out = append(out, l)
		}
	}
	if out == nil {
		out = []SymbolLabel{}
	}
	return out
}

func (s *labelStore) setValidated(id string, val int) int {
	s.mu.Lock()
	n := 0
	for i := range s.labels {
		if s.labels[i].ID == id {
			s.labels[i].Validated = val
			n++
		}
	}
	s.mu.Unlock()
	if n > 0 {
		s.flush()
	}
	return n
}

func (s *labelStore) flush() {
	s.mu.RLock()
	data, err := json.MarshalIndent(s.labels, "", "  ")
	s.mu.RUnlock()
	if err != nil {
		log.Printf("flush marshal: %v", err)
		return
	}
	if writeErr := os.WriteFile(s.path, data, 0644); writeErr != nil {
		log.Printf("flush write: %v", writeErr)
	}
}
