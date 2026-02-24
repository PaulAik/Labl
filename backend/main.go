package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/google/uuid"
)

var (
	s3Client     *s3.Client
	bucketName   string
	claudeAPIKey string
)

func main() {
	bucketName = os.Getenv("S3_BUCKET")
	if bucketName == "" {
		log.Fatal("S3_BUCKET environment variable is required")
	}
	claudeAPIKey = os.Getenv("ANTHROPIC_API_KEY")
	if claudeAPIKey == "" {
		log.Fatal("ANTHROPIC_API_KEY environment variable is required")
	}

	cfg, err := config.LoadDefaultConfig(context.Background())
	if err != nil {
		log.Fatalf("load AWS config: %v", err)
	}
	s3Client = s3.NewFromConfig(cfg)

	dbPath := os.Getenv("DB_PATH")
	if dbPath == "" {
		dbPath = "labels.json"
	}
	if err := initStore(dbPath); err != nil {
		log.Fatalf("init store: %v", err)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/upload", withCORS(uploadHandler))
	mux.HandleFunc("/classify", withCORS(classifyHandler))
	mux.HandleFunc("/labels", withCORS(labelsHandler))
	mux.HandleFunc("/labels/validate", withCORS(validateHandler))
	mux.HandleFunc("/health", withCORS(healthHandler))

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Printf("Labl backend on :%s  store=%s  bucket=%s", port, dbPath, bucketName)
	log.Fatal(http.ListenAndServe(":"+port, mux))
}

func uploadHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if err := r.ParseMultipartForm(32 << 20); err != nil {
		http.Error(w, "bad request: "+err.Error(), http.StatusBadRequest)
		return
	}

	sessionID := sanitise(r.FormValue("session_id"))
	if sessionID == "" {
		sessionID = uuid.New().String()
	}
	applianceType := sanitise(r.FormValue("appliance_type"))
	if applianceType == "" {
		applianceType = "unknown"
	}
	shotLabel := sanitise(r.FormValue("shot_label"))
	if shotLabel == "" {
		shotLabel = "shot"
	}

	file, _, err := r.FormFile("image")
	if err != nil {
		http.Error(w, "missing image: "+err.Error(), http.StatusBadRequest)
		return
	}
	defer file.Close()

	imgBytes, err := io.ReadAll(file)
	if err != nil {
		http.Error(w, "read: "+err.Error(), http.StatusInternalServerError)
		return
	}

	s3Key := fmt.Sprintf("training/%s/%s/%d_%s.jpg",
		applianceType, sessionID, time.Now().UnixMilli(), shotLabel)

	if err := putS3(s3Key, imgBytes); err != nil {
		log.Printf("s3 put %s: %v", s3Key, err)
		http.Error(w, "upload failed", http.StatusInternalServerError)
		return
	}
	log.Printf("uploaded %s", s3Key)

	go classifyAndStore(sessionID, applianceType, s3Key, imgBytes)

	writeJSON(w, map[string]string{"session_id": sessionID, "key": s3Key})
}

func classifyHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		S3Key         string `json:"s3_key"`
		SessionID     string `json:"session_id"`
		ApplianceType string `json:"appliance_type"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad json: "+err.Error(), http.StatusBadRequest)
		return
	}
	imgBytes, err := getS3(req.S3Key)
	if err != nil {
		http.Error(w, "s3 get: "+err.Error(), http.StatusInternalServerError)
		return
	}
	go classifyAndStore(req.SessionID, req.ApplianceType, req.S3Key, imgBytes)
	writeJSON(w, map[string]string{"status": "queued"})
}

func labelsHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var val int
	switch r.URL.Query().Get("status") {
	case "approved":
		val = 1
	case "rejected":
		val = -1
	default:
		val = 0
	}
	writeJSON(w, store.query(val))
}

func validateHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		ID       string `json:"id"`
		Approved bool   `json:"approved"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad json: "+err.Error(), http.StatusBadRequest)
		return
	}
	val := -1
	if req.Approved {
		val = 1
	}
	n := store.setValidated(req.ID, val)
	writeJSON(w, map[string]any{"updated": n})
}

func healthHandler(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, map[string]string{"status": "ok"})
}

func withCORS(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next(w, r)
	}
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

func sanitise(s string) string {
	s = strings.ReplaceAll(s, "/", "_")
	s = strings.ReplaceAll(s, "..", "_")
	return s
}

func putS3(key string, data []byte) error {
	_, err := s3Client.PutObject(context.Background(), &s3.PutObjectInput{
		Bucket:        aws.String(bucketName),
		Key:           aws.String(key),
		Body:          bytes.NewReader(data),
		ContentType:   aws.String("image/jpeg"),
		ContentLength: aws.Int64(int64(len(data))),
	})
	return err
}

func getS3(key string) ([]byte, error) {
	out, err := s3Client.GetObject(context.Background(), &s3.GetObjectInput{
		Bucket: aws.String(bucketName),
		Key:    aws.String(key),
	})
	if err != nil {
		return nil, err
	}
	defer out.Body.Close()
	return io.ReadAll(out.Body)
}
