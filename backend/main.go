package main

import (
	"context"
	"encoding/json"
	"fmt"
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
	s3Client   *s3.Client
	bucketName string
)

func main() {
	bucketName = os.Getenv("S3_BUCKET")
	if bucketName == "" {
		log.Fatal("S3_BUCKET environment variable is required")
	}

	cfg, err := config.LoadDefaultConfig(context.Background())
	if err != nil {
		log.Fatalf("unable to load AWS config: %v", err)
	}
	s3Client = s3.NewFromConfig(cfg)

	mux := http.NewServeMux()
	mux.HandleFunc("/upload", withCORS(uploadHandler))
	mux.HandleFunc("/health", withCORS(healthHandler))

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Printf("Labl training server listening on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, mux))
}

// uploadHandler receives a multipart POST with an image and metadata, uploads
// to S3 at training/{appliance_type}/{session_id}/{timestamp}_{shot_label}.jpg
func uploadHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// 32 MB max per image
	if err := r.ParseMultipartForm(32 << 20); err != nil {
		http.Error(w, "failed to parse form: "+err.Error(), http.StatusBadRequest)
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
		http.Error(w, "failed to read image: "+err.Error(), http.StatusBadRequest)
		return
	}
	defer file.Close()

	key := fmt.Sprintf("training/%s/%s/%d_%s.jpg",
		applianceType, sessionID, time.Now().UnixMilli(), shotLabel)

	_, err = s3Client.PutObject(context.Background(), &s3.PutObjectInput{
		Bucket:      aws.String(bucketName),
		Key:         aws.String(key),
		Body:        file,
		ContentType: aws.String("image/jpeg"),
	})
	if err != nil {
		log.Printf("S3 upload failed for key %s: %v", key, err)
		http.Error(w, "upload failed", http.StatusInternalServerError)
		return
	}

	log.Printf("Uploaded %s", key)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"session_id": sessionID,
		"key":        key,
	})
}

func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// withCORS adds permissive CORS headers and handles preflight requests.
func withCORS(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next(w, r)
	}
}

// sanitise strips path separators to prevent directory traversal in S3 keys.
func sanitise(s string) string {
	s = strings.ReplaceAll(s, "/", "_")
	s = strings.ReplaceAll(s, "..", "_")
	return s
}
