# 🚀 FinScore AI — Render Deployment Guide

Everything is already pre-configured for Render deployment. Follow these steps exactly.

---

## Prerequisites

- [x] GitHub account
- [x] Render account (free at render.com)
- [x] Gemini API key (from Google AI Studio)
- [x] MySQL database (Render free tier OR external like PlanetScale/Aiven)

---

## Step 1 — Push Code to GitHub

Your project needs to be on GitHub first.

```powershell
cd "E:\AI HAcKaTHON\credit-scoring"

# Initialize git (if not already done)
git init
git add .
git commit -m "Initial commit - FinScore AI"

# Create a repo on GitHub, then push:
git remote add origin https://github.com/YOUR_USERNAME/finscore-ai.git
git branch -M main
git push -u origin main
```

> [!CAUTION]
> Your `.gitignore` already excludes `.env` files and `*.pkl` model files.
> **Never push your `.env` file** — it contains API keys and passwords.

---

## Step 2 — Get a Free MySQL Database

Render's free tier doesn't include a persistent database. Use one of these free options:

### Option A: Aiven (Recommended — free tier)
1. Go to [aiven.io](https://aiven.io) → Create free account
2. Create a **MySQL** service (free plan)
3. Copy the **Service URI** — it looks like:
   ```
   mysql://avnadmin:PASSWORD@HOST:PORT/defaultdb?ssl-mode=REQUIRED
   ```

### Option B: PlanetScale (free tier)
1. Go to [planetscale.com](https://planetscale.com)
2. Create a database → Get connection string

### Option C: Render PostgreSQL (free 90 days)
> [!NOTE]
> If using PostgreSQL, change `DB_DRIVER` to `org.postgresql.Driver`
> and `DB_DIALECT` to `org.hibernate.dialect.PostgreSQLDialect` in env vars.

---

## Step 3 — Deploy on Render (Blueprint Method — Easiest)

### 3a. Open Render Dashboard
Go to [dashboard.render.com](https://dashboard.render.com) → **New** → **Blueprint**

### 3b. Connect GitHub Repo
1. Click **"Connect a repository"**
2. Select your `finscore-ai` GitHub repo
3. Render will **auto-detect** your `render.yaml` file ✅

### 3c. Click Deploy
Render reads your `render.yaml` and creates **2 services** automatically:
- `finscore-ml-service` (Python/Docker)
- `finscore-backend` (Java/Docker)

---

## Step 4 — Set Environment Variables

After the blueprint creates the services, you MUST set these secret variables manually.

### For `finscore-ml-service`:
Go to **finscore-ml-service** → **Environment** tab → Add:

| Key | Value |
|-----|-------|
| `ML_PORT` | `8000` |

### For `finscore-backend`:
Go to **finscore-backend** → **Environment** tab → Add:

| Key | Value | Notes |
|-----|-------|-------|
| `GEMINI_API_KEY` | `AIza...` | From [aistudio.google.com](https://aistudio.google.com) |
| `DB_URL` | `jdbc:mysql://HOST:PORT/DBNAME?useSSL=true` | From your MySQL provider |
| `DB_USERNAME` | `avnadmin` | From your MySQL provider |
| `DB_PASSWORD` | `your_db_password` | From your MySQL provider |
| `DB_DRIVER` | `com.mysql.cj.jdbc.Driver` | Already in render.yaml |
| `DB_DIALECT` | `org.hibernate.dialect.MySQLDialect` | Already in render.yaml |
| `ADMIN_USERNAME` | `admin` | Dashboard login username |
| `ADMIN_PASSWORD` | `choose_a_strong_password` | Dashboard login password |
| `H2_CONSOLE` | `false` | Disable H2 in production |
| `ML_SERVICE_URL` | *(auto-set by Render from render.yaml)* | Points to ml-service URL |

---

## Step 5 — Verify ML Service URL Wiring

After both services are deployed, Render auto-injects `ML_SERVICE_URL` into the backend service (as defined in your `render.yaml`).

> [!IMPORTANT]
> If Render doesn't auto-wire it, manually copy the ML service URL:
> 1. Go to `finscore-ml-service` → copy the URL (e.g. `https://finscore-ml-service.onrender.com`)
> 2. Go to `finscore-backend` → Environment → set `ML_SERVICE_URL` to that URL

---

## Step 6 — Test After Deployment

Once both services show **"Live"** (green) status:

| Page | URL |
|------|-----|
| Home | `https://finscore-backend.onrender.com` |
| Login | `https://finscore-backend.onrender.com/login` |
| Register | `https://finscore-backend.onrender.com/register` |
| Assessment | `https://finscore-backend.onrender.com/form` |
| Dashboard | `https://finscore-backend.onrender.com/dashboard` |
| ML Health | `https://finscore-ml-service.onrender.com/health` |

---

## ⚠️ Known Issues on Render Free Tier

| Issue | Cause | Fix |
|-------|-------|-----|
| **Cold start delay (30-60s)** | Free services spin down after 15min inactivity | Normal — first request is slow |
| **ML model training on every deploy** | `Dockerfile` runs `train_model.py` at build time | Expected — takes ~2-3min per deploy |
| **H2 console exposed** | Must be disabled in production | Set `H2_CONSOLE=false` env var |
| **FAISS build time** | C++ compilation during pip install | Takes ~5-8min on first deploy |

---

## Alternative: Manual Service Creation (Without Blueprint)

If the Blueprint doesn't work, create services manually:

### ML Service:
1. Render → **New Web Service**
2. Connect GitHub repo
3. **Root directory:** `ml-service`
4. **Runtime:** Docker
5. **Dockerfile path:** `./Dockerfile`

### Backend:
1. Render → **New Web Service**
2. Connect GitHub repo
3. **Root directory:** `backend`
4. **Runtime:** Docker
5. **Dockerfile path:** `./Dockerfile`

---

## Quick Checklist Before Pushing to GitHub

```
[ ] .env files are NOT committed (check .gitignore)
[ ] *.pkl model files are NOT committed
[ ] render.yaml is at the ROOT of the repo (not inside backend/)
[ ] Both Dockerfiles exist (backend/Dockerfile, ml-service/Dockerfile)
[ ] GEMINI_API_KEY is ready (get from aistudio.google.com)
[ ] MySQL connection string is ready
```
