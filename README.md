# IP India Trademark Scraper — Spring Boot

Spring Boot conversion of the `lugality-ml` Python scraper.

## Tech Stack Mapping

| Python                | Java Spring Boot          |
|-----------------------|--------------------------|
| Playwright (Python)   | Playwright (Java)         |
| PyMuPDF (fitz)        | Apache PDFBox             |
| pydantic-settings     | Spring `@ConfigurationProperties` |
| aiohttp server        | Spring Web MVC            |
| asyncio workers       | `ExecutorService` threads |
| LangGraph workflow    | `WorkflowOrchestrator`    |
| imaplib               | Jakarta Mail (IMAP)       |

## Project Structure

```
src/main/java/com/lugality/scraper/
├── ScraperApplication.java          # Entry point
├── config/
│   ├── ScraperSettings.java         # All env-var config (≈ config.py)
│   └── AsyncConfig.java             # Thread pool config
├── controller/
│   └── ScraperController.java       # REST API (≈ server.py + main.py CLI)
├── model/
│   └── TrademarkData.java           # Data models (≈ models/trademark.py)
├── service/
│   ├── BrowserManager.java          # Playwright browser (≈ services/browser.py)
│   ├── CaptchaSolver.java           # CAPTCHA logic (≈ services/captcha_solver.py)
│   ├── ExtractionService.java       # Data extraction (≈ graph/nodes/extract.py)
│   ├── LocalStorageService.java     # File storage (≈ services/local_storage.py)
│   ├── LoginService.java            # Login flow (≈ graph/nodes/login.py)
│   ├── OtpReaderService.java        # IMAP OTP reader (≈ services/otp_reader.py)
│   ├── ParallelScraperService.java  # Parallel workers (≈ services/parallel_runner.py)
│   ├── PdfParserService.java        # PDF parsing (≈ services/pdf_parser.py)
│   └── SearchService.java           # Application search (≈ graph/nodes/search.py)
└── workflow/
    ├── ScraperState.java            # Workflow state (≈ graph/state.py)
    └── WorkflowOrchestrator.java    # Workflow engine (≈ graph/workflow.py)
```

## Setup

### 1. Environment Variables (.env)

```env
EMAIL_ADDRESS=your@email.com
EMAIL_APP_PASSWORD=your_app_password
IMAP_SERVER=imap.hostinger.com

# Optional: additional worker emails for parallel mode
EMAIL_ADDRESS_1=worker1@email.com
EMAIL_APP_PASSWORD_1=worker1_password
EMAIL_ADDRESS_2=worker2@email.com
EMAIL_APP_PASSWORD_2=worker2_password

# Storage
STORAGE_MODE=local
LOCAL_DATA_DIR=./output/data
LOCAL_PDF_DIR=./output/pdfs

# Browser
HEADLESS=true

# Parallel workers
NUM_WORKERS=3
```

### 2. Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/scraper-1.0.0.jar
```

### 3. Docker

```bash
docker build -t lugality-scraper .
docker run -p 8080:8080 \
  -e EMAIL_ADDRESS=your@email.com \
  -e EMAIL_APP_PASSWORD=password \
  -v $(pwd)/output:/app/output \
  lugality-scraper
```

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/scrape/single` | Scrape one application (sync) |
| POST | `/api/scrape/batch` | Scrape a list (async, returns job ID) |
| POST | `/api/scrape/upload` | Upload JSON/CSV/TXT + scrape |
| GET  | `/api/scrape/status/{jobId}` | Poll async job status |
| GET  | `/api/status` | Overall progress summary |
| GET  | `/api/export/csv` | Download all data as CSV |
| GET  | `/api/data/{appNumber}` | Get one application's data |
| GET  | `/api/data` | List all scraped applications |

### Example: Scrape Single Application

```bash
curl -X POST http://localhost:8080/api/scrape/single \
  -H "Content-Type: application/json" \
  -d '{"email":"your@email.com","applicationNumber":"5672748"}'
```

### Example: Batch Scrape

```bash
curl -X POST http://localhost:8080/api/scrape/batch \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your@email.com",
    "applications": ["5672748","5672749","5672750"],
    "workers": 3,
    "resume": false
  }'
```

### Example: Upload File

```bash
curl -X POST http://localhost:8080/api/scrape/upload \
  -F "file=@applications.json" \
  -F "email=your@email.com" \
  -F "workers=3"
```

## Output

All data saved to:
- `./output/data/{appNumber}.json` — scraped trademark data
- `./output/pdfs/{appNumber}/` — downloaded PDF documents
- `./output/data/checkpoint.json` — resume checkpoint
- `./output/data/progress_log.json` — run history
