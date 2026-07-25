"""
FinScore AI — FastAPI ML Service
=================================
Exposes the XGBoost credit scoring model and RAG pipeline as 
REST API endpoints for the Spring Boot backend to consume.

Endpoints:
  POST /predict     — Predict credit score from alternate financial data
  POST /rag/tips    — Get personalized financial tips from RAG pipeline
  GET  /health      — Service health check

The service loads the trained model and label encoders on startup,
and initializes the RAG pipeline (FAISS index) for tip retrieval.

Author: FinScore AI Team
License: MIT
"""

import os
import sys
import logging
import numpy as np
import pandas as pd
import joblib
from typing import Optional, List, Dict
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ─── Logging Configuration ──────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("finscore-ml")

# ─── Paths ───────────────────────────────────────────────────────────────────
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, 'model', 'credit_model.pkl')
ENCODERS_PATH = os.path.join(BASE_DIR, 'model', 'label_encoders.pkl')

# ─── Global Model References ────────────────────────────────────────────────
model = None
encoders = None
rag_pipeline = None


# ─── Pydantic Models for Request/Response Validation ────────────────────────

class PredictRequest(BaseModel):
    """
    Input schema for credit score prediction.
    All fields correspond to alternate financial behavior indicators
    collected from the borrower via the assessment form.
    """
    monthly_income: float = Field(..., ge=0, description="Monthly income in INR")
    upi_frequency: int = Field(..., ge=0, description="UPI transactions per month")
    avg_upi_amount: float = Field(..., ge=0, description="Average UPI transaction amount in INR")
    utility_consistency: str = Field(..., description="Bill payment consistency: 'Always on time', 'Sometimes late', 'Frequently late'")
    recharge_amount: float = Field(..., ge=0, description="Monthly mobile recharge amount in INR")
    agricultural_yield: float = Field(default=0, ge=0, description="Agricultural yield in kg/season (optional)")
    loan_requested: float = Field(..., gt=0, description="Loan amount requested in INR")
    occupation: str = Field(..., description="Borrower occupation")
    state: str = Field(..., description="Borrower state")
    gender: str = Field(..., description="Borrower gender")


class PredictResponse(BaseModel):
    """
    Output schema for credit score prediction.
    Contains the score, risk assessment, and loan eligibility details.
    """
    score: int = Field(..., ge=0, le=100, description="Credit score 0-100")
    risk_category: str = Field(..., description="Risk category: Low Risk, Medium Risk, High Risk")
    loan_eligible: bool = Field(..., description="Whether the borrower is eligible for a loan")
    max_loan_amount: float = Field(..., description="Maximum recommended loan amount in INR")
    suggested_interest_rate: str = Field(..., description="Suggested interest rate range")
    confidence: float = Field(..., description="Model prediction confidence 0-1")


class RAGRequest(BaseModel):
    """Input schema for RAG-powered financial tips."""
    score: int = Field(..., ge=0, le=100, description="Credit score")
    occupation: str = Field(..., description="Borrower occupation")
    state: str = Field(default="", description="Borrower state (optional)")


class TipItem(BaseModel):
    """Single financial tip from RAG pipeline."""
    heading: str
    detail: str
    icon: str


class RAGResponse(BaseModel):
    """Output schema for RAG tips endpoint."""
    tips: List[TipItem]


class HealthResponse(BaseModel):
    """Health check response."""
    status: str
    model_loaded: bool
    rag_ready: bool


# ─── Application Lifecycle ──────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Application lifecycle manager.
    Loads the ML model and initializes RAG pipeline on startup.
    """
    global model, encoders, rag_pipeline
    
    # ── Startup ──
    logger.info("=" * 60)
    logger.info("FinScore AI ML Service Starting...")
    logger.info("=" * 60)
    
    # Load XGBoost model
    try:
        if os.path.exists(MODEL_PATH):
            model = joblib.load(MODEL_PATH)
            logger.info(f"✅ XGBoost model loaded from {MODEL_PATH}")
        else:
            logger.warning(f"⚠️ Model file not found at {MODEL_PATH}")
            logger.warning("   Run 'python model/train_model.py' first")
    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
    
    # Load label encoders
    try:
        if os.path.exists(ENCODERS_PATH):
            encoders = joblib.load(ENCODERS_PATH)
            logger.info(f"✅ Label encoders loaded from {ENCODERS_PATH}")
        else:
            logger.warning(f"⚠️ Encoders file not found at {ENCODERS_PATH}")
    except Exception as e:
        logger.error(f"❌ Failed to load encoders: {e}")
    
    # Initialize RAG pipeline
    try:
        # Add rag directory to path for imports
        sys.path.insert(0, os.path.join(BASE_DIR, 'rag'))
        from rag_pipeline import RAGPipeline
        rag_pipeline = RAGPipeline()
        logger.info("✅ RAG pipeline initialized")
    except Exception as e:
        logger.error(f"❌ Failed to initialize RAG pipeline: {e}")
        logger.warning("   RAG tips will use fallback responses")
    
    logger.info("=" * 60)
    logger.info("FinScore AI ML Service Ready!")
    logger.info("=" * 60)
    
    yield  # Application runs here
    
    # ── Shutdown ──
    logger.info("FinScore AI ML Service shutting down...")


# ─── FastAPI Application ────────────────────────────────────────────────────

app = FastAPI(
    title="FinScore AI — ML Service",
    description=(
        "Credit scoring ML service using XGBoost for alternate financial "
        "data assessment and RAG pipeline for personalized financial tips."
    ),
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware — allow Spring Boot backend to call this service
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, restrict to backend URL
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─── Helper Functions ───────────────────────────────────────────────────────

def encode_categorical(value: str, encoder_name: str) -> int:
    """
    Safely encode a categorical value using the trained LabelEncoder.
    
    If the value is unseen (not in training data), returns a default
    encoding to prevent prediction failures.
    """
    if encoders is None or encoder_name not in encoders:
        logger.warning(f"Encoder '{encoder_name}' not available, using default")
        return 0
    
    le = encoders[encoder_name]
    try:
        return int(le.transform([value])[0])
    except ValueError:
        # Value not seen during training — use first class as default
        logger.warning(
            f"Unknown value '{value}' for '{encoder_name}', "
            f"using default: '{le.classes_[0]}'"
        )
        return 0


def calculate_risk_category(score: int) -> str:
    """Map credit score to risk category."""
    if score >= 70:
        return "Low Risk"
    elif score >= 40:
        return "Medium Risk"
    else:
        return "High Risk"


def calculate_max_loan(score: int, monthly_income: float, loan_requested: float) -> float:
    """
    Calculate maximum recommended loan amount based on score and income.
    
    Formula:
    - Base: 6-24x monthly income depending on score
    - Capped at loan requested (can't recommend more than asked)
    - Minimum: Rs 5,000 for any eligible borrower
    """
    if score >= 70:
        # Low risk — can support up to 24x monthly income
        multiplier = 18 + (score - 70) * 0.2  # 18-24x
    elif score >= 40:
        # Medium risk — can support 6-18x monthly income
        multiplier = 6 + (score - 40) * 0.4  # 6-18x
    else:
        # High risk — limited to 3-6x monthly income
        multiplier = 3 + (score / 40) * 3  # 3-6x
    
    max_amount = monthly_income * multiplier
    
    # Cap at requested amount
    max_amount = min(max_amount, loan_requested)
    
    # Ensure minimum of Rs 5,000 for eligible borrowers
    if score >= 30:  # Minimum score for any eligibility
        max_amount = max(max_amount, 5000)
    
    return round(max_amount, 2)


def calculate_interest_rate(score: int) -> str:
    """
    Suggest interest rate range based on credit score.
    
    Rates are based on RBI guidelines for priority sector lending
    and typical microfinance institution rates.
    """
    if score >= 80:
        return "8-10% p.a."
    elif score >= 70:
        return "10-12% p.a."
    elif score >= 60:
        return "12-14% p.a."
    elif score >= 50:
        return "14-16% p.a."
    elif score >= 40:
        return "16-18% p.a."
    elif score >= 30:
        return "18-22% p.a."
    else:
        return "Not recommended"


def calculate_confidence(score: int) -> float:
    """
    Calculate prediction confidence based on score distance from boundaries.
    
    Higher confidence near the center of risk categories,
    lower confidence near boundaries (40, 70).
    """
    # Distance from nearest boundary
    boundaries = [0, 40, 70, 100]
    min_distance = min(abs(score - b) for b in boundaries)
    
    # Normalize to 0-1 range (max distance from boundary = 35)
    confidence = min(min_distance / 35.0, 1.0)
    
    # Scale to 0.65-0.95 range (never show 100% or very low confidence)
    confidence = 0.65 + confidence * 0.30
    
    return round(confidence, 4)


# ─── API Endpoints ──────────────────────────────────────────────────────────

@app.post("/predict", response_model=PredictResponse)
async def predict_credit_score(request: PredictRequest):
    """
    Predict credit score from alternate financial behavior data.
    
    Uses XGBoost model trained on synthetic data representing
    rural Indian financial patterns. Gender is included as input
    but has negligible influence on prediction (by design).
    
    Returns score (0-100), risk category, loan eligibility,
    maximum loan amount, suggested interest rate, and confidence.
    """
    # Validate model is loaded
    if model is None:
        raise HTTPException(
            status_code=503,
            detail="ML model not loaded. Run 'python model/train_model.py' first."
        )
    
    try:
        # Encode categorical features
        utility_encoded = encode_categorical(
            request.utility_consistency, 'utility_consistency'
        )
        occupation_encoded = encode_categorical(
            request.occupation, 'occupation'
        )
        state_encoded = encode_categorical(
            request.state, 'state'
        )
        gender_encoded = encode_categorical(
            request.gender, 'gender'
        )
        
        # Create feature DataFrame in the exact order used during training
        features = pd.DataFrame([{
            'monthly_income': request.monthly_income,
            'upi_frequency': request.upi_frequency,
            'avg_upi_amount': request.avg_upi_amount,
            'utility_consistency': utility_encoded,
            'recharge_amount': request.recharge_amount,
            'agricultural_yield': request.agricultural_yield,
            'loan_requested': request.loan_requested,
            'occupation': occupation_encoded,
            'state': state_encoded,
            'gender': gender_encoded
        }])
        
        # Predict score
        raw_score = model.predict(features)[0]
        score = int(np.clip(raw_score, 0, 100))
        
        # Calculate derived values
        risk_category = calculate_risk_category(score)
        loan_eligible = score >= 30  # Minimum score for any loan eligibility
        max_loan = calculate_max_loan(score, request.monthly_income, request.loan_requested)
        interest_rate = calculate_interest_rate(score)
        confidence = calculate_confidence(score)
        
        logger.info(
            f"Prediction: score={score}, risk={risk_category}, "
            f"eligible={loan_eligible}, max_loan=₹{max_loan:,.2f}"
        )
        
        return PredictResponse(
            score=score,
            risk_category=risk_category,
            loan_eligible=loan_eligible,
            max_loan_amount=max_loan if loan_eligible else 0,
            suggested_interest_rate=interest_rate,
            confidence=confidence
        )
        
    except Exception as e:
        logger.error(f"Prediction failed: {e}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Prediction error: {str(e)}"
        )


@app.post("/rag/tips", response_model=RAGResponse)
async def get_financial_tips(request: RAGRequest):
    """
    Get personalized financial tips from the RAG pipeline.
    
    Queries the FAISS vector store with borrower's occupation and
    score context to retrieve the most relevant RBI financial
    inclusion guidelines and schemes.
    
    Returns exactly 3 tips, each with heading, detail, and icon.
    """
    try:
        if rag_pipeline is not None:
            tips = rag_pipeline.get_tips(
                score=request.score,
                occupation=request.occupation,
                state=request.state
            )
        else:
            # RAG not available — use fallback tips
            logger.warning("RAG pipeline not available, using fallback tips")
            from rag.rag_pipeline import RAGPipeline
            temp = RAGPipeline()
            tips = temp._fallback_tips(request.score, request.occupation)
        
        logger.info(
            f"RAG tips generated for {request.occupation} "
            f"(score: {request.score}): {len(tips)} tips"
        )
        
        return RAGResponse(
            tips=[TipItem(**tip) for tip in tips]
        )
        
    except Exception as e:
        logger.error(f"RAG tips failed: {e}", exc_info=True)
        # Return fallback tips instead of error
        fallback_tips = [
            TipItem(
                heading="Maintain Regular Financial Activity",
                detail="Keep making regular UPI transactions and paying bills on time to build your financial profile.",
                icon="bi-graph-up"
            ),
            TipItem(
                heading="Explore Government Schemes",
                detail="Check eligibility for PMJDY, MUDRA, PM-KISAN, and other financial inclusion schemes.",
                icon="bi-award"
            ),
            TipItem(
                heading="Build Your Savings Habit",
                detail="Even small regular deposits in your bank account demonstrate financial discipline to lenders.",
                icon="bi-piggy-bank"
            )
        ]
        return RAGResponse(tips=fallback_tips)


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Service health check endpoint.
    
    Reports whether the ML model and RAG pipeline are loaded and ready.
    Used by Spring Boot backend to verify service availability.
    """
    return HealthResponse(
        status="healthy",
        model_loaded=model is not None,
        rag_ready=rag_pipeline is not None
    )


# ─── Development Server ─────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    
    port = int(os.environ.get("ML_PORT", 8000))
    
    logger.info(f"Starting FinScore AI ML Service on port {port}")
    
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=port,
        reload=True,  # Auto-reload during development
        log_level="info"
    )
