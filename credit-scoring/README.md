# 🏦 FinScore AI — Alternate Credit Scoring for Rural India

> **AI-powered credit assessment for 190M+ unbanked Indians who lack traditional credit history**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![SDG 1](https://img.shields.io/badge/SDG%201-No%20Poverty-E5243B)](https://sdgs.un.org/goals/goal1)
[![SDG 10](https://img.shields.io/badge/SDG%2010-Reduced%20Inequalities-DD1367)](https://sdgs.un.org/goals/goal10)

---

## 📋 Problem Statement

Traditional credit scoring systems like CIBIL exclude **190 million+ rural Indians** (World Bank, 2022) who:
- Work in informal sectors (farming, daily wage labor, street vending)
- Have no bank statements, salary slips, or formal employment records
- Are forced to borrow from predatory moneylenders at **24-48% annual interest** (RBI Report, 2023)
- Include women-led Self Help Groups and tribal communities facing severe financial exclusion

**Most affected states:** Uttar Pradesh, Bihar, Rajasthan, Odisha

## 💡 Our Solution

FinScore AI uses **alternate financial behavior data** to generate fair credit scores:

| Traditional (CIBIL) | FinScore AI (Alternate) |
|---------------------|------------------------|
| Bank statements | UPI transaction frequency & amount |
| Salary slips | Monthly informal income |
| Credit card history | Utility bill payment consistency |
| Loan repayment records | Mobile recharge patterns |
| N/A | Agricultural yield (seasonal) |

### Key Features
- **XGBoost ML Model** trained on alternate financial indicators
- **Google Gemini AI** explains scores in simple Hindi/English
- **RAG Pipeline** provides personalized RBI-backed financial tips
- **Bias-tested** across gender, region, and income groups
- **Privacy-first** — no Aadhaar, PAN, or biometric data stored

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Browser (User)                          │
│               Thymeleaf + Bootstrap 5 UI                    │
│         (index.html / form.html / result.html)              │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP (Port 8080)
┌──────────────────────▼──────────────────────────────────────┐
│               Spring Boot 3.2 (Java 17)                     │
│  ┌────────────────┐ ┌─────────────────┐ ┌────────────────┐  │
│  │ PageController  │ │CreditController │ │DashboardCtrl   │  │
│  │ (Thymeleaf)    │ │ (REST API)      │ │(Admin + Stats) │  │
│  └────────┬───────┘ └───────┬─────────┘ └───────┬────────┘  │
│           │                 │                     │          │
│  ┌────────▼─────────────────▼─────────────────────▼────────┐ │
│  │              Service Layer                              │ │
│  │  CreditScoringService | GeminiService | RAGService      │ │
│  └────────┬──────────────────────────────┬─────────────────┘ │
│           │                              │                   │
│  ┌────────▼────────┐           ┌─────────▼───────────────┐   │
│  │   MySQL (JPA)   │           │  Spring Security        │   │
│  │ borrower_assess │           │  (Dashboard protected)  │   │
│  └─────────────────┘           └─────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │ REST (Port 8000)
┌──────────────────────▼──────────────────────────────────────┐
│               FastAPI (Python 3.10)                          │
│  ┌───────────────────┐  ┌─────────────────────────────────┐  │
│  │  POST /predict    │  │  POST /rag/tips                 │  │
│  │  XGBoost Model    │  │  LangChain + FAISS + MiniLM-L6  │  │
│  │  (credit_model.pkl│  │  (rbi_guidelines.txt chunks)    │  │
│  └───────────────────┘  └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK | 17+ |
| Python | 3.10+ |
| MySQL | 8.0+ |
| Maven | 3.8+ |
| Node.js | Not required |

---

## 🚀 Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/finscore-ai.git
cd finscore-ai/credit-scoring
```

### 2. Create MySQL Database
```sql
CREATE DATABASE finscoreai;
```

### 3. Set Environment Variables

Create a `.env` file or set system variables:

```bash
# Required
export GEMINI_API_KEY=your_gemini_api_key_here
export DB_URL=jdbc:mysql://localhost:3306/finscoreai
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
export ML_SERVICE_URL=http://localhost:8000

# Optional (defaults shown)
export PORT=8080
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=your_admin_password
```

### 4. Setup Python ML Service
```bash
cd ml-service

# Create virtual environment
python -m venv venv
source venv/bin/activate  # Linux/Mac
# OR
venv\Scripts\activate     # Windows

# Install dependencies
pip install -r requirements.txt

# Train the ML model (generates credit_model.pkl)
python model/train_model.py

# Start the service
uvicorn main:app --host 0.0.0.0 --port 8000
```

### 5. Setup Spring Boot Backend
```bash
cd backend

# Build and run
mvn spring-boot:run
```

### 6. Access the Application
- **Homepage:** http://localhost:8080
- **Credit Form:** http://localhost:8080/form
- **Dashboard:** http://localhost:8080/dashboard (login required)

---

## 📡 API Documentation

### Public Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/credit/score` | Submit borrower data for scoring |
| `POST` | `/api/credit/explain` | Re-generate explanation in different language |
| `GET` | `/api/credit/history` | Paginated assessment history |
| `GET` | `/api/states` | List of Indian states and UTs |
| `GET` | `/api/occupations` | Available occupation options |
| `GET` | `/api/consistency-options` | Bill payment consistency options |

### Protected Endpoints (Admin)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/dashboard/stats` | Dashboard summary statistics |
| `GET` | `/dashboard` | Admin dashboard page |

### Python ML Service

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/predict` | Get credit score prediction |
| `POST` | `/rag/tips` | Get personalized financial tips |
| `GET` | `/health` | Service health check |

---

## 🌍 SDG Alignment

### SDG 1: No Poverty
FinScore AI directly addresses poverty by enabling **fair credit access** for marginalized communities who are currently excluded from formal lending. By providing an alternate credit score, rural Indians can access institutional loans at **fair interest rates** instead of predatory moneylenders.

### SDG 10: Reduced Inequalities
The system is specifically designed to **reduce financial inequality** by:
- Scoring based on actual financial behavior, not formal employment
- Ensuring gender neutrality in ML model scoring
- Targeting the most excluded demographics: farmers, daily wage workers, SHG women
- Making the tool accessible on basic 2G/3G mobile browsers

---

## 🤖 Responsible AI Principles

1. **Promote Equity & Inclusion** — Primary design goal; serves 190M+ excluded population
2. **Enable Transparency & Explainability** — Gemini AI explains every score in plain language
3. **Design for Privacy** — Consent-first approach; no Aadhaar/PAN/biometric data stored
4. **Bias Mitigation** — Gender field does not influence score; bias monitoring on dashboard

---

## 🚢 Deployment on Render

### Using render.yaml (Recommended)
1. Connect your GitHub repository to Render
2. Render will auto-detect `render.yaml` and create both services
3. Set environment variables in Render dashboard
4. Deploy

### Manual Deployment
1. Create a **Web Service** for Spring Boot (Docker, port 8080)
2. Create a **Web Service** for FastAPI (Docker, port 8000)
3. Set `ML_SERVICE_URL` in Spring Boot to point to FastAPI service URL
4. Set all other environment variables

---

## 📸 Screenshots

> Screenshots will be added after deployment

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **RBI Financial Inclusion Reports** — Guidelines and statistics
- **World Bank Global Findex** — Unbanked population data
- **Google Gemini AI** — Score explanation engine
- **India AI Impact Festival 2026** — Competition platform

---

*Built with ❤️ for rural India*
