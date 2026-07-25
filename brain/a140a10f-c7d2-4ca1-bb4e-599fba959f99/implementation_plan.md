# FinScore AI — Full Implementation Plan

## Overview

Build a complete AI-powered alternate credit scoring web application for 190M+ unbanked rural Indians. The system uses XGBoost ML for scoring, Google Gemini for explanations, and a RAG pipeline for personalized financial tips.

**Architecture**: Spring Boot 3.2 (backend + Thymeleaf frontend) ↔ FastAPI (ML + RAG) ↔ MySQL (persistence)

```
┌─────────────────────────────────────────────────────────┐
│                    Browser (User)                       │
│              Thymeleaf + Bootstrap 5 UI                 │
└───────────────┬─────────────────────────────────────────┘
                │ HTTP
┌───────────────▼─────────────────────────────────────────┐
│              Spring Boot 3.2 (Java 17)                  │
│  ┌──────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │Controller│ │CreditScoring │ │  GeminiService       │ │
│  │  Layer   │→│  Service     │→│  (Gemini 1.5 Flash)  │ │
│  └──────────┘ └──────┬───────┘ └──────────────────────┘ │
│                      │ REST                              │
│              ┌───────▼───────┐  ┌────────────────────┐  │
│              │  MySQL (JPA)  │  │  Spring Security   │  │
│              └───────────────┘  └────────────────────┘  │
└───────────────┬─────────────────────────────────────────┘
                │ REST (POST /predict, POST /rag/tips)
┌───────────────▼─────────────────────────────────────────┐
│              FastAPI (Python 3.10)                       │
│  ┌──────────────┐  ┌────────────────────────────────┐   │
│  │ XGBoost Model│  │ RAG Pipeline                   │   │
│  │ (pkl)        │  │ LangChain + FAISS + MiniLM-L6  │   │
│  └──────────────┘  └────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## User Review Required

> [!IMPORTANT]
> **Database**: The plan uses MySQL as specified. Ensure MySQL 8+ is installed locally with a `finscoreai` database created before running.

> [!IMPORTANT]
> **API Keys**: You must set `GEMINI_API_KEY` environment variable with a valid Google Gemini API key. The app will use a graceful fallback message if the key is missing.

> [!WARNING]
> **Python Dependencies**: The RAG pipeline uses `sentence-transformers` and `faiss-cpu` which require ~2GB download on first run. Ensure stable internet for initial setup.

---

## Open Questions

> [!IMPORTANT]
> **Render Deployment**: The spec mentions Render deployment. Should I include a `render.yaml` blueprint for one-click deploy, or is manual deployment sufficient? I'll include `Dockerfile` + `render.yaml` for both services by default.

> [!NOTE]
> **ML Model**: The spec calls for XGBoost "classifier" but credit scores are continuous (0-100). I'll use XGBoost **Regressor** to predict a continuous score, then bucket into risk categories. This is more realistic than classification.

> [!NOTE]
> **PDF Download**: The spec mentions "Download result as PDF". I'll implement this client-side using `html2canvas` + `jsPDF` to avoid adding server-side PDF dependencies. This keeps deployment lightweight.

---

## Proposed Changes

The project will be built in **6 phases**, organized by dependency order.

---

### Phase 1: Project Skeleton & Configuration

#### [NEW] [credit-scoring/](file:///e:/AI HAcKaTHON/credit-scoring/)
Top-level project directory.

#### [NEW] [README.md](file:///e:/AI HAcKaTHON/credit-scoring/README.md)
- Complete project documentation with ASCII architecture diagram
- Prerequisites, setup instructions, environment variables guide
- API docs, SDG section, Responsible AI section, deployment guide

---

### Phase 2: Python ML Service (No Java dependencies)

#### [NEW] [requirements.txt](file:///e:/AI HAcKaTHON/credit-scoring/ml-service/requirements.txt)
- All Python dependencies as specified (FastAPI, XGBoost, LangChain, FAISS, sentence-transformers, etc.)

#### [NEW] [train_model.py](file:///e:/AI HAcKaTHON/credit-scoring/ml-service/model/train_model.py)
- Generate 5000 synthetic records with realistic Indian rural demographic distributions
- Features: monthly_income, upi_frequency, avg_upi_amount, utility_consistency, recharge_amount, agricultural_yield, occupation_encoded, gender_encoded
- **Gender bias mitigation**: gender is included as input but NOT used in target generation formula
- Target: continuous credit score 0-100 using weighted formula with Gaussian noise
- Train XGBoost Regressor, evaluate with MAE/RMSE/R², save as `credit_model.pkl`

#### [NEW] [rbi_guidelines.txt](file:///e:/AI HAcKaTHON/credit-scoring/ml-service/rag/rbi_guidelines.txt)
- 50+ paragraphs of realistic RBI financial inclusion content
- Covers: PMJDY, MUDRA, KCC, SHG-BLP, DAY-NRLM, microfinance regulations
- Organized by: farmers, SHGs, daily wage, street vendors, migrants

#### [NEW] [rag_pipeline.py](file:///e:/AI HAcKaTHON/credit-scoring/ml-service/rag/rag_pipeline.py)
- Load and chunk `rbi_guidelines.txt` using LangChain RecursiveCharacterTextSplitter
- Generate embeddings with `all-MiniLM-L6-v2` via sentence-transformers
- Store in FAISS vector store (persisted to disk)
- Query function: context = `"{occupation} with score {score_range}"` → returns top 3 chunks formatted as tips

#### [NEW] [main.py](file:///e:/AI HAcKaTHON/credit-scoring/ml-service/main.py)
- FastAPI app with CORS enabled
- `POST /predict`: Load model, encode categoricals, predict score, compute risk/eligibility/max_loan/interest_rate
- `POST /rag/tips`: Query FAISS for personalized tips
- `GET /health`: Health check endpoint
- Proper error handling and input validation via Pydantic models

---

### Phase 3: Spring Boot Backend

#### [NEW] [pom.xml](file:///e:/AI HAcKaTHON/credit-scoring/backend/pom.xml)
- Spring Boot 3.2 parent, Java 17
- Dependencies: web, data-jpa, thymeleaf, security, validation, mysql-connector, lombok, devtools

#### [NEW] [application.properties](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/resources/application.properties)
- All configs via `${ENV_VAR}` placeholders, zero hardcoded values

#### [NEW] [FinScoreAiApplication.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/FinScoreAiApplication.java)
- Main Spring Boot application entry point

#### [NEW] [BorrowerInput.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/model/BorrowerInput.java)
- JPA entity + validation annotations
- Maps to `borrower_assessments` table
- All fields per schema: name, gender, state, occupation, income, UPI metrics, utility, recharge, yield, loan, language, consent
- Result fields: score, risk, eligibility, max_loan, interest, explanations (EN/HI), RAG tips (JSON), confidence
- Timestamps, IP address

#### [NEW] [CreditResult.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/model/CreditResult.java)
- DTO for API responses (score, risk, eligibility, explanation, tips)

#### [NEW] [BorrowerRepository.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/repository/BorrowerRepository.java)
- JPA repository with custom queries for dashboard stats
- Methods: findByState, findByOccupation, countByRiskCategory, avgScore, etc.

#### [NEW] [CreditScoringService.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/service/CreditScoringService.java)
- Orchestrates the scoring flow: validate consent → call ML → call Gemini → call RAG → save to DB
- RestTemplate calls to Python service
- Proper error handling with fallbacks

#### [NEW] [GeminiService.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/service/GeminiService.java)
- Calls Gemini 1.5 Flash REST API
- Uses the empathetic prompt template from spec
- Supports Hindi/English toggle
- Graceful fallback on API errors

#### [NEW] [RAGService.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/service/RAGService.java)
- Calls Python `/rag/tips` endpoint
- Returns structured tips with headings, details, icons

#### [NEW] [CreditController.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/controller/CreditController.java)
- `POST /api/credit/score` — full scoring pipeline
- `POST /api/credit/explain` — language toggle re-explanation
- `GET /api/credit/history` — paginated history with filters
- `GET /api/states` — all Indian states/UTs
- `GET /api/occupations` — occupation dropdown data
- `GET /api/consistency-options` — bill payment consistency options

#### [NEW] [DashboardController.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/controller/DashboardController.java)
- `GET /api/dashboard/stats` — summary stats JSON
- Page controllers for Thymeleaf views: `/`, `/form`, `/result/{id}`, `/dashboard`

#### [NEW] [AppConfig.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/config/AppConfig.java)
- RestTemplate bean
- CORS config
- Jackson ObjectMapper config

#### [NEW] [SecurityConfig.java](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/java/com/finscore/config/SecurityConfig.java)
- Permit all on public pages (`/`, `/form`, `/result/**`, `/api/**` except dashboard)
- Require authentication on `/dashboard`
- HTTP Basic auth with env-sourced credentials
- CSRF config for Thymeleaf forms

---

### Phase 4: Thymeleaf Templates (Frontend)

#### [NEW] [index.html](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/resources/templates/index.html)
- **Navbar**: Deep blue, saffron logo, white links
- **Hero**: Full-width gradient, real stats (190M+ unbanked, 24-48% moneylender interest), saffron CTA
- **How It Works**: 3-step cards with icons (Input → AI Score → Get Loan)
- **SDG Badges**: SDG 1 and SDG 10 with descriptions
- **Responsible AI**: 4 principles with icons (Equity, Transparency, Privacy, Bias Mitigation)
- **Footer**: Disclaimer, privacy note
- All text via `th:text` model attributes
- Counter animations on stats, smooth scrolling

#### [NEW] [form.html](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/resources/templates/form.html)
- Consent checkbox (mandatory, blocks form)
- All fields per spec with floating labels
- Dropdowns populated via `fetch()` to `/api/states`, `/api/occupations`, `/api/consistency-options`
- Real-time progress bar tracking field completion
- Client-side validation (required fields, number ranges)
- Loading spinner overlay on submit
- Privacy note footer
- Fully responsive Bootstrap 5 grid

#### [NEW] [result.html](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/resources/templates/result.html)
- Animated SVG circular score meter (0→actual, color-coded)
- Risk category pill badge (green/orange/red)
- Loan eligibility card with amount, interest rate
- AI explanation card with Hindi↔English toggle button (fetches via `/api/credit/explain`)
- Loading skeleton during explanation fetch
- RAG tips card with 3 tips, icons, headings
- Responsible AI transparency note
- PDF download (html2canvas + jsPDF)
- "Assess Another Borrower" button

#### [NEW] [dashboard.html](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/resources/templates/dashboard.html)
- 4 summary stat cards with counter animation
- Chart.js charts (all data from `/api/credit/history`):
  - Score distribution histogram
  - State-wise average score bar chart
  - Occupation-wise comparison
  - Gender-wise comparison (bias monitoring)
  - Daily volume line chart
- Paginated assessments table
- CSV export button
- Blue+saffron chart color scheme

---

### Phase 5: Static Assets

#### [NEW] [styles.css](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/resources/static/css/styles.css)
- Complete custom design system
- CSS variables for the color palette
- Inter font from Google Fonts
- Custom navbar, hero, cards, score circle, badges, form inputs, buttons
- Animations: counter, score circle, skeleton loading, toast
- Dark gradients, glassmorphism effects, subtle shadows
- Full responsive breakpoints

#### [NEW] [app.js](file:///e:/AI HAcKaTHON/credit-scoring/backend/src/main/resources/static/js/app.js)
- Form progress bar tracking
- Client-side validation
- API fetch helpers
- Score circle animation (SVG stroke-dashoffset)
- Counter animation
- Toast notification system
- Dropdown population from APIs
- Loading skeleton toggle
- PDF download logic

---

### Phase 6: Deployment Configuration

#### [NEW] [Dockerfile (backend)](file:///e:/AI HAcKaTHON/credit-scoring/backend/Dockerfile)
- Multi-stage build: Maven build → JRE 17 runtime

#### [NEW] [Dockerfile (ml-service)](file:///e:/AI HAcKaTHON/credit-scoring/ml-service/Dockerfile)
- Python 3.10 slim, install requirements, train model on build

#### [NEW] [render.yaml](file:///e:/AI HAcKaTHON/credit-scoring/render.yaml)
- Blueprint for deploying both services on Render

---

## File Manifest (28 files total)

| # | File | Type | Phase |
|---|------|------|-------|
| 1 | `README.md` | Documentation | 1 |
| 2 | `ml-service/requirements.txt` | Config | 2 |
| 3 | `ml-service/model/train_model.py` | Python | 2 |
| 4 | `ml-service/rag/rbi_guidelines.txt` | Data | 2 |
| 5 | `ml-service/rag/rag_pipeline.py` | Python | 2 |
| 6 | `ml-service/main.py` | Python | 2 |
| 7 | `backend/pom.xml` | Config | 3 |
| 8 | `backend/src/.../application.properties` | Config | 3 |
| 9 | `backend/src/.../FinScoreAiApplication.java` | Java | 3 |
| 10 | `backend/src/.../model/BorrowerInput.java` | Java | 3 |
| 11 | `backend/src/.../model/CreditResult.java` | Java | 3 |
| 12 | `backend/src/.../repository/BorrowerRepository.java` | Java | 3 |
| 13 | `backend/src/.../service/CreditScoringService.java` | Java | 3 |
| 14 | `backend/src/.../service/GeminiService.java` | Java | 3 |
| 15 | `backend/src/.../service/RAGService.java` | Java | 3 |
| 16 | `backend/src/.../controller/CreditController.java` | Java | 3 |
| 17 | `backend/src/.../controller/DashboardController.java` | Java | 3 |
| 18 | `backend/src/.../controller/PageController.java` | Java | 3 |
| 19 | `backend/src/.../config/AppConfig.java` | Java | 3 |
| 20 | `backend/src/.../config/SecurityConfig.java` | Java | 3 |
| 21 | `templates/index.html` | Thymeleaf | 4 |
| 22 | `templates/form.html` | Thymeleaf | 4 |
| 23 | `templates/result.html` | Thymeleaf | 4 |
| 24 | `templates/dashboard.html` | Thymeleaf | 4 |
| 25 | `static/css/styles.css` | CSS | 5 |
| 26 | `static/js/app.js` | JS | 5 |
| 27 | `backend/Dockerfile` | Docker | 6 |
| 28 | `ml-service/Dockerfile` | Docker | 6 |
| 29 | `render.yaml` | Deploy | 6 |

---

## Verification Plan

### Automated Tests
- `python ml-service/model/train_model.py` — should generate model, print evaluation metrics
- `cd ml-service && uvicorn main:app` — ML service starts on port 8000
- `POST http://localhost:8000/predict` with sample input → returns score JSON
- `POST http://localhost:8000/rag/tips` → returns 3 tips
- `cd backend && mvn spring-boot:run` — Spring Boot starts on port 8080 (requires MySQL)
- All 4 pages render without errors
- Form submission → score result page with animated score

### Manual Verification
- Visual review of all 4 pages for design quality (colors, animations, responsiveness)
- Mobile responsiveness test at 375px, 768px, 1024px widths
- Hindi/English toggle on result page
- Dashboard charts populate from API data
- Consent checkbox blocks form submission when unchecked
- Gender bias check: same inputs with different gender → similar scores
