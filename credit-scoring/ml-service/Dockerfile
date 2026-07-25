# ═══════════════════════════════════════════════════════════════
# FinScore AI — Python ML Service Dockerfile
# ═══════════════════════════════════════════════════════════════
# Builds the FastAPI ML service with XGBoost model training
# and RAG pipeline initialization baked into the image.
# ═══════════════════════════════════════════════════════════════

FROM python:3.10-slim

WORKDIR /app

# Install system dependencies for FAISS and sentence-transformers
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for dependency caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Train the ML model during build (generates credit_model.pkl)
RUN python model/train_model.py

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:${ML_PORT:-8000}/health || exit 1

# Run FastAPI with uvicorn
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
