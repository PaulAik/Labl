package main

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

// ObjectStore abstracts blob storage.
type ObjectStore interface {
	Put(key string, data []byte) error
	Get(key string) ([]byte, error)
}

// newObjectStore reads STORAGE_BACKEND and returns the appropriate implementation.
// "s3" → s3ObjectStore (requires S3_BUCKET + AWS credentials)
// anything else / unset → fileObjectStore (uses STORAGE_DIR, default "data")
func newObjectStore() (ObjectStore, error) {
	backend := os.Getenv("STORAGE_BACKEND")
	if strings.ToLower(backend) == "s3" {
		bucket := os.Getenv("S3_BUCKET")
		if bucket == "" {
			return nil, fmt.Errorf("S3_BUCKET environment variable is required when STORAGE_BACKEND=s3")
		}
		cfg, err := config.LoadDefaultConfig(context.Background())
		if err != nil {
			return nil, fmt.Errorf("load AWS config: %w", err)
		}
		return &s3ObjectStore{client: s3.NewFromConfig(cfg), bucket: bucket}, nil
	}

	dir := os.Getenv("STORAGE_DIR")
	if dir == "" {
		dir = "data"
	}
	return &fileObjectStore{baseDir: dir}, nil
}

// ── S3 implementation ──────────────────────────────────────────────────────

type s3ObjectStore struct {
	client *s3.Client
	bucket string
}

func (s *s3ObjectStore) Put(key string, data []byte) error {
	_, err := s.client.PutObject(context.Background(), &s3.PutObjectInput{
		Bucket:        aws.String(s.bucket),
		Key:           aws.String(key),
		Body:          bytes.NewReader(data),
		ContentType:   aws.String("image/jpeg"),
		ContentLength: aws.Int64(int64(len(data))),
	})
	return err
}

func (s *s3ObjectStore) Get(key string) ([]byte, error) {
	out, err := s.client.GetObject(context.Background(), &s3.GetObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return nil, err
	}
	defer out.Body.Close()
	buf := new(bytes.Buffer)
	if _, err := buf.ReadFrom(out.Body); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// ── Local filesystem implementation ───────────────────────────────────────

type fileObjectStore struct {
	baseDir string
}

func (f *fileObjectStore) Put(key string, data []byte) error {
	path, err := f.safePath(key)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, data, 0o644)
}

func (f *fileObjectStore) Get(key string) ([]byte, error) {
	path, err := f.safePath(key)
	if err != nil {
		return nil, err
	}
	return os.ReadFile(path)
}

// safePath joins baseDir with key and guards against directory traversal.
func (f *fileObjectStore) safePath(key string) (string, error) {
	base, err := filepath.Abs(f.baseDir)
	if err != nil {
		return "", err
	}
	joined := filepath.Clean(filepath.Join(base, key))
	if !strings.HasPrefix(joined, base+string(os.PathSeparator)) {
		return "", fmt.Errorf("key %q escapes storage directory", key)
	}
	return joined, nil
}
