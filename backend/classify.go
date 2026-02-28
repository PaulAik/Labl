package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"image"
	"image/draw"
	"image/jpeg"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/google/uuid"
)

// claudeSymbol is what Claude returns for each detected symbol.
type claudeSymbol struct {
	Name        string  `json:"name"`
	Description string  `json:"description"`
	Category    string  `json:"category"`
	Confidence  string  `json:"confidence"`
	CropX       float64 `json:"crop_x"`
	CropY       float64 `json:"crop_y"`
	CropW       float64 `json:"crop_w"`
	CropH       float64 `json:"crop_h"`
}

// classifyAndStore sends imgBytes to Claude, crops each detected symbol, and
// stores everything in SQLite + S3. Runs in a goroutine.
func classifyAndStore(sessionID, applianceType, origS3Key string, imgBytes []byte) {
	symbols, err := callClaudeClassify(imgBytes, applianceType)
	if err != nil {
		log.Printf("classify error for %s: %v", origS3Key, err)
		return
	}
	log.Printf("found %d symbol(s) in %s", len(symbols), origS3Key)

	for _, sym := range symbols {
		id := uuid.New().String()

		cropKey := uploadCrop(imgBytes, sym, origS3Key, id)

		var cropPtr *string
		if cropKey != "" {
			cropPtr = &cropKey
		}

		store.insert(SymbolLabel{
			ID:            id,
			SessionID:     sessionID,
			ApplianceType: applianceType,
			S3Key:         origS3Key,
			CropS3Key:     cropPtr,
			Name:          sym.Name,
			Description:   sym.Description,
			Category:   sym.Category,
			Confidence: sym.Confidence,
		})
	}
}

// callClaudeClassify sends an image to Claude and parses the returned symbols.
// applianceType is optional; when non-empty and not "unknown" it is included in
// the prompt so Claude can apply domain-specific knowledge.
func callClaudeClassify(imgBytes []byte, applianceType string) ([]claudeSymbol, error) {
	b64 := base64.StdEncoding.EncodeToString(imgBytes)

	context := ""
	if applianceType != "" && applianceType != "unknown" {
		context = fmt.Sprintf("\nThis image is from a %s appliance — use this context to improve accuracy.\n", applianceType)
	}

	prompt := `You are analysing an appliance control panel or care-label image.` + context + `

Identify every distinct symbol or icon visible and return ONLY the following JSON — no markdown, no prose:
{
  "symbols": [
    {
      "name": "concise symbol name",
      "description": "plain-English explanation of what the symbol means and what the user should do",
      "category": "washing | drying | ironing | bleaching | dishwasher | oven | other",
      "confidence": "high | medium | low",
      "crop_x": <left edge fraction>,
      "crop_y": <top edge fraction>,
      "crop_w": <width fraction>,
      "crop_h": <height fraction>
    }
  ]
}

Bounding box rules — read carefully:
- The origin (0, 0) is the TOP-LEFT corner of the image; x increases rightward, y increases downward.
- crop_x: fraction of image width from the left edge to the LEFT side of the symbol.
- crop_y: fraction of image height from the top edge to the TOP side of the symbol.
- crop_w: width of the bounding box as a fraction of image width.
- crop_h: height of the bounding box as a fraction of image height.
- All values are in [0.0, 1.0]. crop_x + crop_w ≤ 1.0 and crop_y + crop_h ≤ 1.0.
- Make boxes GENEROUS — include ~10 % padding around each symbol so nothing is clipped.
- Example: a symbol in the upper-left quarter might be crop_x=0.02, crop_y=0.03, crop_w=0.28, crop_h=0.22.

Return ONLY the JSON object.`

	reqBody := map[string]any{
		"model":      "claude-sonnet-4-6",
		"max_tokens": 2048,
		"messages": []map[string]any{
			{
				"role": "user",
				"content": []map[string]any{
					{
						"type": "image",
						"source": map[string]any{
							"type":       "base64",
							"media_type": "image/jpeg",
							"data":       b64,
						},
					},
					{"type": "text", "text": prompt},
				},
			},
		},
	}

	bodyBytes, _ := json.Marshal(reqBody)
	httpReq, err := http.NewRequest(http.MethodPost,
		"https://api.anthropic.com/v1/messages", bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, err
	}
	httpReq.Header.Set("x-api-key", claudeAPIKey)
	httpReq.Header.Set("anthropic-version", "2023-06-01")
	httpReq.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 90 * time.Second}
	resp, err := client.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("http: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		var apiErr struct {
			Error struct {
				Message string `json:"message"`
			} `json:"error"`
		}
		_ = json.NewDecoder(resp.Body).Decode(&apiErr)
		return nil, fmt.Errorf("claude API %d: %s", resp.StatusCode, apiErr.Error.Message)
	}

	var apiResp struct {
		Content []struct {
			Type string `json:"type"`
			Text string `json:"text"`
		} `json:"content"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&apiResp); err != nil {
		return nil, fmt.Errorf("decode: %w", err)
	}

	log.Printf("Claude response: %+v", apiResp)

	for _, block := range apiResp.Content {
		if block.Type != "text" {
			continue
		}
		text := block.Text
		// Strip markdown code fences and find the JSON object
		if i := strings.Index(text, "{"); i >= 0 {
			text = text[i:]
		}
		if i := strings.LastIndex(text, "}"); i >= 0 {
			text = text[:i+1]
		}
		var result struct {
			Symbols []claudeSymbol `json:"symbols"`
		}
		if err := json.Unmarshal([]byte(text), &result); err != nil {
			return nil, fmt.Errorf("parse json: %w", err)
		}
		return result.Symbols, nil
	}
	return nil, fmt.Errorf("no text block in Claude response")
}

// uploadCrop crops the bounding box from imgBytes and uploads it to S3.
// Returns the crop S3 key, or "" if cropping fails.
func uploadCrop(imgBytes []byte, sym claudeSymbol, origKey, labelID string) string {
	img, _, err := image.Decode(bytes.NewReader(imgBytes))
	if err != nil {
		log.Printf("crop decode error %s: %v", labelID, err)
		return ""
	}

	b := img.Bounds()
	W := float64(b.Dx())
	H := float64(b.Dy())

	// Expand the model's bounding box by 15 % on every side to compensate for
	// any coordinate imprecision. Clamped so we never exceed the image bounds.
	const pad = 0.15
	padX := sym.CropW * pad
	padY := sym.CropH * pad
	x0 := clampInt(int((sym.CropX-padX)*W), 0, b.Dx())
	y0 := clampInt(int((sym.CropY-padY)*H), 0, b.Dy())
	x1 := clampInt(int((sym.CropX+sym.CropW+padX)*W), 0, b.Dx())
	y1 := clampInt(int((sym.CropY+sym.CropH+padY)*H), 0, b.Dy())

	if x1-x0 < 4 || y1-y0 < 4 {
		log.Printf("crop region too small for %s (%dx%d)", labelID, x1-x0, y1-y0)
		return ""
	}

	cropRect := image.Rect(x0, y0, x1, y1)
	cropped := image.NewRGBA(image.Rect(0, 0, cropRect.Dx(), cropRect.Dy()))
	draw.Draw(cropped, cropped.Bounds(), img, cropRect.Min, draw.Src)

	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, cropped, &jpeg.Options{Quality: 88}); err != nil {
		log.Printf("crop encode error %s: %v", labelID, err)
		return ""
	}

	// Build crop key: same prefix as original, under a crops/ sub-directory
	dir := origKey[:strings.LastIndex(origKey, "/")+1]
	cropKey := dir + "crops/" + labelID + ".jpg"

	if err := objects.Put(cropKey, buf.Bytes()); err != nil {
		log.Printf("crop store put error %s: %v", labelID, err)
		return ""
	}
	return cropKey
}

func clampInt(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}
