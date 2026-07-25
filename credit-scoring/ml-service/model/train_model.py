"""
FinScore AI — XGBoost Credit Scoring Model Training
====================================================
Generates 5000 synthetic records modeling alternate financial behavior
of rural Indian demographics. Trains an XGBoost Regressor to predict
credit scores (0-100) based on:
  - Monthly income from informal sources
  - UPI transaction frequency and average amount
  - Utility bill payment consistency
  - Mobile recharge amount
  - Agricultural yield (seasonal)
  - Occupation type
  
IMPORTANT: Gender is included in the dataset for demographic tracking
but is deliberately EXCLUDED from the scoring formula to prevent bias.
The model learns scoring patterns that are gender-neutral by design.

Author: FinScore AI Team
License: MIT
"""

import numpy as np
import pandas as pd
from xgboost import XGBRegressor
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.preprocessing import LabelEncoder
import joblib
import os
import warnings

warnings.filterwarnings('ignore')

# ─── Configuration ───────────────────────────────────────────────────────────
RANDOM_SEED = 42
NUM_RECORDS = 5000
MODEL_OUTPUT_PATH = os.path.join(os.path.dirname(__file__), 'credit_model.pkl')
ENCODERS_OUTPUT_PATH = os.path.join(os.path.dirname(__file__), 'label_encoders.pkl')

np.random.seed(RANDOM_SEED)

# ─── Categorical Options ────────────────────────────────────────────────────
# These must match the API dropdown options exactly
OCCUPATIONS = [
    'Farmer', 'Daily Wage Laborer', 'Street Vendor',
    'SHG Member', 'Migrant Worker', 'Small Business Owner',
    'Artisan/Craftsperson', 'Fisher', 'Other'
]

STATES = [
    'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar',
    'Chhattisgarh', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh',
    'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh',
    'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland',
    'Odisha', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu',
    'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand',
    'West Bengal', 'Andaman and Nicobar Islands', 'Chandigarh',
    'Dadra and Nagar Haveli and Daman and Diu', 'Delhi',
    'Jammu and Kashmir', 'Ladakh', 'Lakshadweep', 'Puducherry'
]

GENDERS = ['Male', 'Female', 'Other']

UTILITY_CONSISTENCY_OPTIONS = ['Always on time', 'Sometimes late', 'Frequently late']

# Occupation weights — reflect typical income stability of each occupation
# These influence score calculation but do NOT create discriminatory bias
OCCUPATION_SCORE_WEIGHTS = {
    'Farmer': 0.7,
    'Daily Wage Laborer': 0.5,
    'Street Vendor': 0.6,
    'SHG Member': 0.75,
    'Migrant Worker': 0.45,
    'Small Business Owner': 0.8,
    'Artisan/Craftsperson': 0.65,
    'Fisher': 0.6,
    'Other': 0.55
}


def generate_synthetic_data(n_records: int) -> pd.DataFrame:
    """
    Generate synthetic dataset of alternate financial behavior records.
    
    The data models realistic distributions for rural Indian demographics:
    - Income follows a right-skewed distribution (most earn 5K-15K/month)
    - UPI adoption varies (some have high frequency, many have low)
    - Agricultural yield is only non-zero for farmers
    - Utility payment consistency is categorical
    
    BIAS MITIGATION: Gender distribution is balanced, and gender does NOT
    influence the credit score target variable.
    """
    print("=" * 60)
    print("FinScore AI -- Synthetic Data Generation")
    print("=" * 60)
    
    # ─── Generate Features ───────────────────────────────────────────────
    
    # Gender — balanced distribution (no influence on score)
    gender = np.random.choice(GENDERS, n_records, p=[0.45, 0.45, 0.10])
    
    # Occupation — weighted toward most common rural occupations
    occupation_probs = [0.25, 0.15, 0.12, 0.12, 0.10, 0.08, 0.08, 0.05, 0.05]
    occupation = np.random.choice(OCCUPATIONS, n_records, p=occupation_probs)
    
    # State — higher probability for target states (UP, Bihar, Rajasthan, Odisha)
    state_probs = np.ones(len(STATES)) / len(STATES)
    target_state_indices = [
        STATES.index('Uttar Pradesh'),
        STATES.index('Bihar'),
        STATES.index('Rajasthan'),
        STATES.index('Odisha')
    ]
    for idx in target_state_indices:
        state_probs[idx] = 0.08  # Higher probability for target states
    state_probs = state_probs / state_probs.sum()  # Normalize
    state = np.random.choice(STATES, n_records, p=state_probs)
    
    # Monthly income (INR) — right-skewed, most earn 5K-15K
    monthly_income = np.clip(
        np.random.lognormal(mean=8.5, sigma=0.7, size=n_records),
        1000, 50000
    ).round(2)
    
    # UPI transaction frequency (0-100 per month)
    # Some people have very low adoption, others use it frequently
    upi_frequency = np.clip(
        np.random.exponential(scale=15, size=n_records),
        0, 100
    ).astype(int)
    
    # Average UPI transaction amount (INR 50-5000)
    avg_upi_amount = np.clip(
        np.random.lognormal(mean=5.5, sigma=0.8, size=n_records),
        50, 5000
    ).round(2)
    
    # Utility bill payment consistency
    utility_consistency = np.random.choice(
        UTILITY_CONSISTENCY_OPTIONS,
        n_records,
        p=[0.40, 0.35, 0.25]
    )
    
    # Mobile recharge amount (INR 50-500 monthly)
    recharge_amount = np.clip(
        np.random.normal(loc=200, scale=80, size=n_records),
        50, 500
    ).round(2)
    
    # Agricultural yield (kg/season) — only relevant for farmers
    agricultural_yield = np.zeros(n_records)
    farmer_mask = occupation == 'Farmer'
    agricultural_yield[farmer_mask] = np.clip(
        np.random.normal(loc=2000, scale=1000, size=farmer_mask.sum()),
        0, 5000
    ).round(2)
    
    # Loan requested (INR) — varies widely
    loan_requested = np.clip(
        np.random.lognormal(mean=10.5, sigma=0.8, size=n_records),
        5000, 500000
    ).round(2)
    
    # ─── Generate Target: Credit Score (0-100) ───────────────────────────
    # 
    # SCORING FORMULA — Weighted combination of financial behavior signals
    # NOTE: Gender is INTENTIONALLY EXCLUDED from this formula
    #
    # Components:
    #   1. Income component (25%): Higher income → higher score
    #   2. UPI activity component (25%): More UPI usage → higher score
    #   3. Utility payment component (20%): Consistent payment → higher score
    #   4. Recharge component (10%): Regular recharges → higher score
    #   5. Agricultural yield component (5%): Higher yield → higher score
    #   6. Occupation stability component (15%): Based on occupation weights
    
    # Normalize features to 0-1 range for scoring
    income_norm = (monthly_income - 1000) / (50000 - 1000)
    upi_freq_norm = upi_frequency / 100
    upi_amt_norm = (avg_upi_amount - 50) / (5000 - 50)
    recharge_norm = (recharge_amount - 50) / (500 - 50)
    yield_norm = agricultural_yield / 5000
    
    # Utility consistency encoding
    utility_score = np.where(
        utility_consistency == 'Always on time', 1.0,
        np.where(utility_consistency == 'Sometimes late', 0.5, 0.15)
    )
    
    # Occupation weight lookup
    occ_weight = np.array([OCCUPATION_SCORE_WEIGHTS[o] for o in occupation])
    
    # Combined score formula
    raw_score = (
        0.25 * income_norm +          # Income weight
        0.15 * upi_freq_norm +         # UPI frequency weight
        0.10 * upi_amt_norm +          # UPI amount weight
        0.20 * utility_score +         # Utility consistency weight
        0.10 * recharge_norm +         # Recharge pattern weight
        0.05 * yield_norm +            # Agricultural yield weight
        0.15 * occ_weight              # Occupation stability weight
    )
    
    # Scale to 0-100 and add realistic noise
    credit_score = np.clip(
        raw_score * 100 + np.random.normal(0, 5, n_records),
        0, 100
    ).astype(int)
    
    # ─── Assemble DataFrame ──────────────────────────────────────────────
    df = pd.DataFrame({
        'monthly_income': monthly_income,
        'upi_frequency': upi_frequency,
        'avg_upi_amount': avg_upi_amount,
        'utility_consistency': utility_consistency,
        'recharge_amount': recharge_amount,
        'agricultural_yield': agricultural_yield,
        'loan_requested': loan_requested,
        'occupation': occupation,
        'state': state,
        'gender': gender,
        'credit_score': credit_score
    })
    
    print(f"\n[Generated {n_records} synthetic records]")
    print(f"\n[Credit Score Distribution]")
    print(f"   Mean:   {df['credit_score'].mean():.1f}")
    print(f"   Median: {df['credit_score'].median():.1f}")
    print(f"   Std:    {df['credit_score'].std():.1f}")
    print(f"   Min:    {df['credit_score'].min()}")
    print(f"   Max:    {df['credit_score'].max()}")
    
    # ─── Bias Check: Gender should NOT influence scores ──────────────────
    print(f"\n[Gender Bias Check]")
    for g in GENDERS:
        g_scores = df[df['gender'] == g]['credit_score']
        print(f"   {g:8s}: mean={g_scores.mean():.1f}, "
              f"median={g_scores.median():.1f}, count={len(g_scores)}")
    
    return df


def encode_features(df: pd.DataFrame) -> tuple:
    """
    Encode categorical features for XGBoost training.
    
    Returns:
        Tuple of (encoded DataFrame, dict of LabelEncoders)
    """
    df_encoded = df.copy()
    encoders = {}
    
    # Encode categorical columns
    categorical_cols = ['utility_consistency', 'occupation', 'state', 'gender']
    
    for col in categorical_cols:
        le = LabelEncoder()
        df_encoded[col] = le.fit_transform(df_encoded[col])
        encoders[col] = le
        print(f"   Encoded '{col}': {len(le.classes_)} categories")
    
    return df_encoded, encoders


def train_model(df_encoded: pd.DataFrame) -> XGBRegressor:
    """
    Train XGBoost Regressor on the encoded dataset.
    
    Model hyperparameters are tuned for:
    - Moderate tree depth to prevent overfitting on synthetic data
    - Learning rate of 0.1 for stable convergence
    - 200 estimators for good generalization
    - Subsample and colsample for regularization
    """
    print("\n" + "=" * 60)
    print("XGBoost Model Training")
    print("=" * 60)
    
    # Feature columns (ALL features including gender for model input,
    # but gender has minimal learned weight due to no correlation in training data)
    feature_cols = [
        'monthly_income', 'upi_frequency', 'avg_upi_amount',
        'utility_consistency', 'recharge_amount', 'agricultural_yield',
        'loan_requested', 'occupation', 'state', 'gender'
    ]
    
    X = df_encoded[feature_cols]
    y = df_encoded['credit_score']
    
    # Train-test split (80/20)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=RANDOM_SEED
    )
    
    print(f"\n[Dataset Split]")
    print(f"   Training:  {len(X_train)} records")
    print(f"   Testing:   {len(X_test)} records")
    
    # Initialize XGBoost Regressor
    model = XGBRegressor(
        n_estimators=200,
        max_depth=6,
        learning_rate=0.1,
        subsample=0.8,
        colsample_bytree=0.8,
        min_child_weight=3,
        reg_alpha=0.1,        # L1 regularization
        reg_lambda=1.0,       # L2 regularization
        random_state=RANDOM_SEED,
        objective='reg:squarederror',
        eval_metric='mae'
    )
    
    # Train with early stopping
    model.fit(
        X_train, y_train,
        eval_set=[(X_test, y_test)],
        verbose=False
    )
    
    # ─── Evaluation ──────────────────────────────────────────────────────
    y_pred = model.predict(X_test)
    
    # Clip predictions to valid range
    y_pred = np.clip(y_pred, 0, 100)
    
    mae = mean_absolute_error(y_test, y_pred)
    rmse = np.sqrt(mean_squared_error(y_test, y_pred))
    r2 = r2_score(y_test, y_pred)
    
    print(f"\n[Model Performance]")
    print(f"   MAE  (Mean Absolute Error):  {mae:.2f}")
    print(f"   RMSE (Root Mean Sq Error):   {rmse:.2f}")
    print(f"   R2   (Coefficient of Det.):  {r2:.4f}")
    
    # ─── Feature Importance ──────────────────────────────────────────────
    feature_importance = dict(zip(feature_cols, model.feature_importances_))
    sorted_importance = sorted(feature_importance.items(), key=lambda x: x[1], reverse=True)
    
    print(f"\n[Feature Importance]")
    for feat, imp in sorted_importance:
        bar = "*" * int(imp * 50)
        # Flag if gender has high importance (shouldn't happen with our data)
        flag = " [BIAS CHECK]" if feat == 'gender' and imp > 0.1 else ""
        print(f"   {feat:25s}: {imp:.4f} {bar}{flag}")
    
    # ─── Gender Bias Validation ──────────────────────────────────────────
    gender_importance = feature_importance.get('gender', 0)
    print(f"\n[Gender Bias Assessment]")
    if gender_importance < 0.05:
        print(f"   PASS -- Gender importance ({gender_importance:.4f}) is negligible")
        print(f"   The model does not discriminate based on gender.")
    elif gender_importance < 0.1:
        print(f"   WARNING -- Gender importance ({gender_importance:.4f}) is low but present")
        print(f"   Consider retraining with gender excluded from features.")
    else:
        print(f"   FAIL -- Gender importance ({gender_importance:.4f}) is too high")
        print(f"   Gender is influencing predictions. Investigation required.")
    
    # ─── Risk Category Distribution ──────────────────────────────────────
    risk_categories = pd.cut(
        y_pred,
        bins=[0, 39, 69, 100],
        labels=['High Risk', 'Medium Risk', 'Low Risk']
    )
    
    print(f"\n[Predicted Risk Distribution]")
    for cat in ['Low Risk', 'Medium Risk', 'High Risk']:
        count = (risk_categories == cat).sum()
        pct = count / len(risk_categories) * 100
        print(f"   {cat:12s}: {count:4d} ({pct:.1f}%)")
    
    return model


def main():
    """Main training pipeline."""
    print("\n" + "=" * 60)
    print("  FinScore AI -- ML Model Training Pipeline")
    print("=" * 60 + "\n")
    
    # Step 1: Generate synthetic data
    df = generate_synthetic_data(NUM_RECORDS)
    
    # Step 2: Encode features
    print(f"\n[Encoding categorical features]")
    df_encoded, encoders = encode_features(df)
    
    # Step 3: Train model
    model = train_model(df_encoded)
    
    # Step 4: Save model and encoders
    print(f"\n[Saving artifacts]")
    joblib.dump(model, MODEL_OUTPUT_PATH)
    print(f"   Model saved to: {MODEL_OUTPUT_PATH}")
    
    joblib.dump(encoders, ENCODERS_OUTPUT_PATH)
    print(f"   Encoders saved to: {ENCODERS_OUTPUT_PATH}")
    
    # Step 5: Quick prediction test
    print(f"\n[Quick Prediction Test]")
    test_input = pd.DataFrame([{
        'monthly_income': 12000,
        'upi_frequency': 25,
        'avg_upi_amount': 300,
        'utility_consistency': encoders['utility_consistency'].transform(['Always on time'])[0],
        'recharge_amount': 200,
        'agricultural_yield': 1500,
        'loan_requested': 50000,
        'occupation': encoders['occupation'].transform(['Farmer'])[0],
        'state': encoders['state'].transform(['Uttar Pradesh'])[0],
        'gender': encoders['gender'].transform(['Female'])[0]
    }])
    
    test_score = int(np.clip(model.predict(test_input)[0], 0, 100))
    print(f"   Input: Farmer, Female, UP, Income Rs 12K, UPI freq 25")
    print(f"   Predicted Score: {test_score}/100")
    
    risk = 'Low Risk' if test_score >= 70 else ('Medium Risk' if test_score >= 40 else 'High Risk')
    print(f"   Risk Category: {risk}")
    
    print(f"\n{'=' * 60}")
    print(f"SUCCESS: Training Complete! Model ready for deployment.")
    print(f"{'=' * 60}\n")


if __name__ == '__main__':
    main()
