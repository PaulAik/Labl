package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"image"
	"image/jpeg"
	_ "image/gif"
	_ "image/png"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
)

var (
	objects      ObjectStore
	claudeAPIKey string
)

func main() {
	claudeAPIKey = os.Getenv("ANTHROPIC_API_KEY")
	if claudeAPIKey == "" {
		log.Fatal("ANTHROPIC_API_KEY environment variable is required")
	}

	var err error
	objects, err = newObjectStore()
	if err != nil {
		log.Fatalf("init object store: %v", err)
	}

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
	mux.HandleFunc("/labels/update", withCORS(updateLabelHandler))
	mux.HandleFunc("/labels/similar", withCORS(similarLabelsHandler))
	mux.HandleFunc("/labels/examples", withCORS(examplesHandler))
	mux.HandleFunc("/labels/feedback", withCORS(feedbackHandler))
	mux.HandleFunc("/classify/preview", withCORS(classifyPreviewHandler))
	mux.HandleFunc("/labels/save", withCORS(labelsSaveHandler))
	mux.HandleFunc("/images", withCORS(imageProxyHandler))
	mux.HandleFunc("/health", withCORS(healthHandler))
	mux.Handle("/", http.FileServer(http.Dir("static")))

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Printf("Labl backend on :%s  store=%s", port, dbPath)
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

	raw, err := io.ReadAll(file)
	if err != nil {
		http.Error(w, "read: "+err.Error(), http.StatusInternalServerError)
		return
	}
	imgBytes, err := toJPEG(raw)
	if err != nil {
		http.Error(w, "image conversion failed: "+err.Error(), http.StatusBadRequest)
		return
	}
	imgBytes, err = resizeIfNeeded(imgBytes)
	if err != nil {
		log.Printf("resize warning: %v", err)
		imgBytes = raw // fall back to original if resize fails
	}

	s3Key := fmt.Sprintf("training/%s/%s/%d_%s.jpg",
		applianceType, sessionID, time.Now().UnixMilli(), shotLabel)

	if err := objects.Put(s3Key, imgBytes); err != nil {
		log.Printf("store put %s: %v", s3Key, err)
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
	imgBytes, err := objects.Get(req.S3Key)
	if err != nil {
		http.Error(w, "store get: "+err.Error(), http.StatusInternalServerError)
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

// similarLabelsHandler returns up to `limit` non-rejected labels with crop
// images that match the given category (and optionally appliance_type).
//
// GET /labels/similar?category=washing&appliance_type=dryer&limit=8
func similarLabelsHandler(w http.ResponseWriter, r *http.Request) {
	category      := r.URL.Query().Get("category")
	applianceType := r.URL.Query().Get("appliance_type")
	limit := 8
	if l := r.URL.Query().Get("limit"); l != "" {
		if n, err := strconv.Atoi(l); err == nil && n > 0 {
			limit = n
		}
	}
	results := store.querySimilar(category, applianceType, limit)
	writeJSON(w, results)
}

func updateLabelHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		ID          string `json:"id"`
		Name        string `json:"name"`
		Description string `json:"description"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad json: "+err.Error(), http.StatusBadRequest)
		return
	}
	n := store.updateFields(req.ID, req.Name, req.Description)
	writeJSON(w, map[string]any{"updated": n})
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

// ── /images?key={s3_key} ───────────────────────────────────────────────────
// Proxies a JPEG from S3 so the Android app can display crops without
// needing AWS credentials on the device.

func imageProxyHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	key := r.URL.Query().Get("key")
	if key == "" {
		http.Error(w, "key query param required", http.StatusBadRequest)
		return
	}
	data, err := objects.Get(key)
	if err != nil {
		log.Printf("image proxy get %s: %v", key, err)
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("Cache-Control", "max-age=86400")
	w.Write(data)
}

// ── /labels/examples?category={cat}&limit={n} ─────────────────────────────
// Returns up to N approved labels for the given category, formatted as
// few-shot examples ready to embed in a Claude prompt.

func examplesHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	category := strings.ToLower(r.URL.Query().Get("category"))
	limitStr := r.URL.Query().Get("limit")
	limit := 5
	if n := 0; limitStr != "" {
		fmt.Sscanf(limitStr, "%d", &n)
		if n > 0 && n <= 20 {
			limit = n
		}
	}

	approved := store.query(1) // validated == 1
	var examples []SymbolLabel
	for _, l := range approved {
		if category == "" || strings.EqualFold(l.Category, category) {
			examples = append(examples, l)
			if len(examples) >= limit {
				break
			}
		}
	}
	if examples == nil {
		examples = []SymbolLabel{}
	}
	writeJSON(w, examples)
}

// feedbackHandler accepts a live AR classification result and stores it as
// an immediately-validated label (approved=true → validated=1, false → -1).
// POST /labels/feedback  { name, category, description, confidence, approved }
// classifyPreviewHandler classifies an uploaded image synchronously and returns
// the detected bounding boxes without storing any SymbolLabel records.
// The image itself is stored so that /labels/save can retrieve it later.
//
// POST /classify/preview  (multipart: image, appliance_type?)
// → { session_id, s3_key, appliance_type, symbols: [{name,description,category,confidence,crop_x,crop_y,crop_w,crop_h}] }
func classifyPreviewHandler(w http.ResponseWriter, r *http.Request) {
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

	file, _, err := r.FormFile("image")
	if err != nil {
		http.Error(w, "missing image: "+err.Error(), http.StatusBadRequest)
		return
	}
	defer file.Close()

	raw, err := io.ReadAll(file)
	if err != nil {
		http.Error(w, "read: "+err.Error(), http.StatusInternalServerError)
		return
	}
	imgBytes, err := toJPEG(raw)
	if err != nil {
		http.Error(w, "image conversion failed: "+err.Error(), http.StatusBadRequest)
		return
	}
	imgBytes, err = resizeIfNeeded(imgBytes)
	if err != nil {
		log.Printf("resize warning: %v", err)
		imgBytes = raw
	}

	s3Key := fmt.Sprintf("training/%s/%s/%d_preview.jpg",
		applianceType, sessionID, time.Now().UnixMilli())
	if err := objects.Put(s3Key, imgBytes); err != nil {
		http.Error(w, "upload failed", http.StatusInternalServerError)
		return
	}

	symbols, err := callClaudeClassify(imgBytes, applianceType)
	if err != nil {
		log.Printf("classify preview error for %s: %v", s3Key, err)
		symbols = []claudeSymbol{}
	}

	type symOut struct {
		Name        string  `json:"name"`
		Description string  `json:"description"`
		Category    string  `json:"category"`
		Confidence  string  `json:"confidence"`
		CropX       float64 `json:"crop_x"`
		CropY       float64 `json:"crop_y"`
		CropW       float64 `json:"crop_w"`
		CropH       float64 `json:"crop_h"`
	}
	out := make([]symOut, len(symbols))
	for i, s := range symbols {
		out[i] = symOut{s.Name, s.Description, s.Category, s.Confidence, s.CropX, s.CropY, s.CropW, s.CropH}
	}
	writeJSON(w, map[string]any{
		"session_id":     sessionID,
		"s3_key":         s3Key,
		"appliance_type": applianceType,
		"symbols":        out,
	})
}

// labelsSaveHandler accepts user-adjusted bounding boxes, crops each symbol
// from the stored image, and writes SymbolLabel records (validated=0).
//
// POST /labels/save  (JSON body)
// → { count, ids }
func labelsSaveHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		SessionID     string `json:"session_id"`
		ApplianceType string `json:"appliance_type"`
		S3Key         string `json:"s3_key"`
		Symbols       []struct {
			Name        string  `json:"name"`
			Description string  `json:"description"`
			Category    string  `json:"category"`
			Confidence  string  `json:"confidence"`
			CropX       float64 `json:"crop_x"`
			CropY       float64 `json:"crop_y"`
			CropW       float64 `json:"crop_w"`
			CropH       float64 `json:"crop_h"`
		} `json:"symbols"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad json: "+err.Error(), http.StatusBadRequest)
		return
	}
	imgBytes, err := objects.Get(req.S3Key)
	if err != nil {
		http.Error(w, "image not found: "+err.Error(), http.StatusNotFound)
		return
	}
	ids := make([]string, 0, len(req.Symbols))
	for _, sym := range req.Symbols {
		id := uuid.New().String()
		cs := claudeSymbol{
			Name: sym.Name, Description: sym.Description,
			Category: sym.Category, Confidence: sym.Confidence,
			CropX: sym.CropX, CropY: sym.CropY, CropW: sym.CropW, CropH: sym.CropH,
		}
		cropKey := uploadCrop(imgBytes, cs, req.S3Key, id)
		var cropPtr *string
		if cropKey != "" {
			cropPtr = &cropKey
		}
		store.insert(SymbolLabel{
			ID: id, SessionID: req.SessionID, ApplianceType: req.ApplianceType,
			S3Key: req.S3Key, CropS3Key: cropPtr,
			Name: sym.Name, Description: sym.Description,
			Category: sym.Category, Confidence: sym.Confidence,
		})
		ids = append(ids, id)
	}
	writeJSON(w, map[string]any{"count": len(ids), "ids": ids})
}

func feedbackHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		Name        string `json:"name"`
		Category    string `json:"category"`
		Description string `json:"description"`
		Confidence  string `json:"confidence"`
		Approved    bool   `json:"approved"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad json: "+err.Error(), http.StatusBadRequest)
		return
	}
	validated := -1
	if req.Approved {
		validated = 1
	}
	id := uuid.New().String()
	store.insertValidated(SymbolLabel{
		ID:            id,
		SessionID:     "live-ar",
		ApplianceType: req.Category,
		S3Key:         "",
		Name:          req.Name,
		Description:   req.Description,
		Category:      req.Category,
		Confidence:    req.Confidence,
		Validated:     validated,
	})
	writeJSON(w, map[string]string{"id": id})
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

// toJPEG decodes any supported image format and re-encodes it as JPEG.
// Crucially, every image — including those already in JPEG format — is decoded
// and re-encoded. This strips EXIF orientation metadata and ensures the stored
// pixel data matches exactly what image.Decode will return when the crop step
// runs. Without this, Claude (which respects EXIF rotation) and Go's decoder
// (which ignores it) can operate on different coordinate spaces, causing crops
// to land in the wrong location.
func toJPEG(data []byte) ([]byte, error) {
	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode image: %w", err)
	}
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, img, &jpeg.Options{Quality: 92}); err != nil {
		return nil, fmt.Errorf("encode jpeg: %w", err)
	}
	return buf.Bytes(), nil
}

// resizeIfNeeded scales down data (a JPEG) so it stays under the 5 MB limit
// imposed by the Claude Vision API. Images already within the budget are
// returned unchanged. Dimensions are capped at maxResizeDim on the longest
// side; if that alone isn't enough, JPEG quality is progressively lowered.
const (
	maxImageBytes = 4_500_000 // 4.5 MB — comfortable margin below the 5 MB limit
	maxResizeDim  = 2048      // longest-side cap before quality tweaking kicks in
)

func resizeIfNeeded(data []byte) ([]byte, error) {
	if len(data) <= maxImageBytes {
		return data, nil
	}

	src, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("resize decode: %w", err)
	}

	b := src.Bounds()
	w, h := b.Dx(), b.Dy()

	// Scale so the longest side ≤ maxResizeDim.
	scale := 1.0
	if w >= h && w > maxResizeDim {
		scale = float64(maxResizeDim) / float64(w)
	} else if h > w && h > maxResizeDim {
		scale = float64(maxResizeDim) / float64(h)
	}

	dw := int(float64(w) * scale)
	dh := int(float64(h) * scale)

	// Nearest-neighbour downscale — sufficient quality for AI symbol recognition.
	dst := image.NewRGBA(image.Rect(0, 0, dw, dh))
	for y := 0; y < dh; y++ {
		for x := 0; x < dw; x++ {
			dst.Set(x, y, src.At(b.Min.X+x*w/dw, b.Min.Y+y*h/dh))
		}
	}

	// Try progressively lower quality until it fits.
	for _, q := range []int{85, 70, 55} {
		var buf bytes.Buffer
		if err := jpeg.Encode(&buf, dst, &jpeg.Options{Quality: q}); err != nil {
			return nil, fmt.Errorf("resize encode q%d: %w", q, err)
		}
		if buf.Len() <= maxImageBytes {
			log.Printf("resized image %.1f MB → %.1f MB (q%d, %dx%d)",
				float64(len(data))/1e6, float64(buf.Len())/1e6, q, dw, dh)
			return buf.Bytes(), nil
		}
	}

	// Fallback: return the last encode even if slightly over the soft cap.
	var buf bytes.Buffer
	jpeg.Encode(&buf, dst, &jpeg.Options{Quality: 55})
	return buf.Bytes(), nil
}
