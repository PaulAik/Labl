# Labl Backend

Go HTTP server that receives appliance/care-label images, stores them in S3, classifies the symbols via Claude Vision, and exposes a REST API (plus a web UI) for reviewing results.

## Prerequisites

### Go

Go 1.24 or newer.

```bash
# macOS
brew install go

# verify
go version
```

### AWS account & S3 bucket

The server stores every uploaded image and its per-symbol crops in an S3 bucket.

1. Create an S3 bucket (e.g. `labl-images`) in your preferred region.
2. Create an IAM user or role with the following policy (replace `labl-images` with your bucket name):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject"],
      "Resource": "arn:aws:s3:::labl-images/*"
    }
  ]
}
```

3. Generate an **Access Key ID** and **Secret Access Key** for that user.

### AWS credentials

The server uses the AWS SDK v2 default credential chain. Any of the following will work:

**Option A — environment variables (recommended for local dev)**

```bash
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=eu-west-1        # must match your bucket's region
```

**Option B — shared credentials file**

```ini
# ~/.aws/credentials
[default]
aws_access_key_id     = AKIA...
aws_secret_access_key = ...

# ~/.aws/config
[default]
region = eu-west-1
```

**Option C — IAM role (EC2 / ECS / Lambda)**

Attach a role with the S3 policy above; the SDK picks it up automatically via IMDS.

### Anthropic API key

The server calls `claude-haiku-3-5` to detect and describe symbols.

1. Sign in at [console.anthropic.com](https://console.anthropic.com).
2. Go to **API Keys** and create a new key.
3. Export it:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

## Environment variables

| Variable                | Required               | Default      | Description                                                        |
|-------------------------|------------------------|--------------|--------------------------------------------------------------------|
| `ANTHROPIC_API_KEY`     | yes                    | —            | Anthropic API key                                                  |
| `STORAGE_BACKEND`       | no                     | `file`       | `s3` to use AWS S3; anything else (or unset) uses the local filesystem |
| `STORAGE_DIR`           | no                     | `data`       | Base directory for local file storage (ignored when `STORAGE_BACKEND=s3`) |
| `S3_BUCKET`             | when `STORAGE_BACKEND=s3` | —         | Name of the S3 bucket for image storage                            |
| `AWS_ACCESS_KEY_ID`     | when `STORAGE_BACKEND=s3`* | —        | AWS access key (*not needed when using IAM role)                   |
| `AWS_SECRET_ACCESS_KEY` | when `STORAGE_BACKEND=s3`* | —        | AWS secret key                                                     |
| `AWS_REGION`            | when `STORAGE_BACKEND=s3` | —         | AWS region matching the bucket (e.g. `eu-west-1`)                  |
| `DB_PATH`               | no                     | `labels.json`| Path to the JSON label store                                       |
| `PORT`                  | no                     | `8080`       | HTTP listen port                                                   |

## Local development (no AWS needed)

`STORAGE_BACKEND` defaults to `file`, so you can run the server with just an Anthropic key:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
# Images are stored under ./data/training/...
go run .
```

The server starts on `http://localhost:8080`. Open that URL in a browser to use the upload UI.

## Running with S3

```bash
cd backend

export STORAGE_BACKEND=s3
export S3_BUCKET=labl-images
export ANTHROPIC_API_KEY=sk-ant-...
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=eu-west-1

go run .
```

## API endpoints

| Method | Path               | Description                                                |
|--------|--------------------|------------------------------------------------------------|
| `POST` | `/upload`          | Upload an image for classification (multipart form)        |
| `POST` | `/classify`        | Re-classify an existing S3 image by key                    |
| `GET`  | `/labels`          | List labels (`?status=approved\|rejected`, default pending)|
| `POST` | `/labels/validate` | Approve or reject a label `{"id":"…","approved":true}`     |
| `GET`  | `/labels/examples` | Few-shot examples `?category=washing&limit=5`              |
| `GET`  | `/images`          | Proxy an S3 image `?key=training/…/image.jpg`              |
| `GET`  | `/health`          | Health check                                               |
| `GET`  | `/`                | Web upload UI                                              |

### Upload form fields

| Field           | Type   | Required | Description                                   |
|-----------------|--------|----------|-----------------------------------------------|
| `image`         | file   | yes      | JPEG or PNG of the appliance/label             |
| `appliance_type`| string | no       | `washing`, `dryer`, `dishwasher`, `oven`, `iron`, `other` |
| `session_id`    | string | no       | Group multiple shots; generated if omitted    |
| `shot_label`    | string | no       | Label for this shot (e.g. `front`, `detail`)  |

## Project structure

```
backend/
├── main.go        # HTTP server, routes, upload/classify/proxy handlers
├── classify.go    # Claude Vision call + S3 crop upload
├── db.go          # In-memory label store backed by a JSON file
├── static/
│   └── index.html # Web upload UI
├── go.mod
└── go.sum
```

## Notes

- Classification runs asynchronously after upload; the `/upload` response returns immediately with the `session_id` and S3 key.
- Crop images are stored at `training/{appliance}/{session}/crops/{id}.jpg` inside the bucket.
- The label store (`labels.json`) is a flat JSON file. It is not designed for high write concurrency — use a proper database for production.
