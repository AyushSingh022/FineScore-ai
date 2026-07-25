"""
FinScore AI — RAG Pipeline for Financial Tips
==============================================
Uses LangChain + FAISS + sentence-transformers to provide
personalized RBI-backed financial inclusion tips based on
borrower occupation, score range, and state.

The pipeline:
1. Loads rbi_guidelines.txt (50+ sections of RBI content)
2. Splits into semantic chunks using RecursiveCharacterTextSplitter
3. Creates embeddings using all-MiniLM-L6-v2 (lightweight, fast)
4. Stores in FAISS vector store (persisted to disk for reuse)
5. Queries with occupation + score context for relevant tips

Author: FinScore AI Team
License: MIT
"""

import os
import logging
from typing import List, Dict

# LangChain imports
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.vectorstores import FAISS
from langchain_community.embeddings import HuggingFaceEmbeddings

# ─── Configuration ───────────────────────────────────────────────────────────
logger = logging.getLogger(__name__)

# Paths relative to this file
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
GUIDELINES_PATH = os.path.join(BASE_DIR, 'rbi_guidelines.txt')
FAISS_INDEX_PATH = os.path.join(BASE_DIR, 'faiss_index')

# Embedding model — lightweight and efficient, suitable for deployment
EMBEDDING_MODEL = 'all-MiniLM-L6-v2'

# Text splitting configuration
CHUNK_SIZE = 800       # Characters per chunk
CHUNK_OVERLAP = 100    # Overlap between chunks for context continuity
TOP_K_RESULTS = 3      # Number of tips to return

# Icons mapped to common tip categories for UI display
TIP_ICONS = {
    'savings': 'bi-piggy-bank',
    'digital': 'bi-phone',
    'upi': 'bi-qr-code',
    'loan': 'bi-bank',
    'insurance': 'bi-shield-check',
    'farming': 'bi-flower1',
    'shg': 'bi-people',
    'scheme': 'bi-award',
    'education': 'bi-book',
    'default': 'bi-lightbulb'
}


class RAGPipeline:
    """
    Retrieval-Augmented Generation pipeline for financial tips.
    
    Uses FAISS vector store for fast similarity search over
    RBI financial inclusion guidelines. Returns top-K relevant
    tips personalized to the borrower's occupation and score.
    """
    
    def __init__(self):
        """Initialize the RAG pipeline with embeddings and vector store."""
        self.embeddings = None
        self.vector_store = None
        self._initialize()
    
    def _initialize(self):
        """
        Load or create the FAISS vector store.
        
        First attempts to load a persisted index from disk.
        If not found, creates a new index from rbi_guidelines.txt.
        """
        logger.info("Initializing RAG Pipeline...")
        
        # Initialize embedding model
        # Using all-MiniLM-L6-v2 — 384-dimensional embeddings, 
        # fast inference, good quality for short text similarity
        self.embeddings = HuggingFaceEmbeddings(
            model_name=EMBEDDING_MODEL,
            model_kwargs={'device': 'cpu'},  # CPU-only for broad deployment
            encode_kwargs={'normalize_embeddings': True}
        )
        
        # Try to load existing FAISS index
        if os.path.exists(FAISS_INDEX_PATH):
            try:
                self.vector_store = FAISS.load_local(
                    FAISS_INDEX_PATH,
                    self.embeddings,
                    allow_dangerous_deserialization=True
                )
                logger.info(f"Loaded existing FAISS index from {FAISS_INDEX_PATH}")
                return
            except Exception as e:
                logger.warning(f"Failed to load FAISS index: {e}. Rebuilding...")
        
        # Build new index from guidelines
        self._build_index()
    
    def _build_index(self):
        """
        Build FAISS index from rbi_guidelines.txt.
        
        Splits the document into semantic chunks and creates
        vector embeddings for similarity search.
        """
        logger.info(f"Building FAISS index from {GUIDELINES_PATH}")
        
        # Load the guidelines document
        if not os.path.exists(GUIDELINES_PATH):
            logger.error(f"Guidelines file not found: {GUIDELINES_PATH}")
            raise FileNotFoundError(f"RBI guidelines file missing: {GUIDELINES_PATH}")
        
        with open(GUIDELINES_PATH, 'r', encoding='utf-8') as f:
            guidelines_text = f.read()
        
        logger.info(f"Loaded guidelines: {len(guidelines_text)} characters")
        
        # Split into chunks using RecursiveCharacterTextSplitter
        # This splitter tries to split on paragraph boundaries first,
        # then sentences, then words — preserving semantic coherence
        text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=CHUNK_SIZE,
            chunk_overlap=CHUNK_OVERLAP,
            length_function=len,
            separators=["\n\n", "\n", ". ", " ", ""]
        )
        
        chunks = text_splitter.split_text(guidelines_text)
        logger.info(f"Split into {len(chunks)} chunks")
        
        # Create FAISS vector store from chunks
        self.vector_store = FAISS.from_texts(
            texts=chunks,
            embedding=self.embeddings
        )
        
        # Persist to disk for future reuse
        self.vector_store.save_local(FAISS_INDEX_PATH)
        logger.info(f"FAISS index saved to {FAISS_INDEX_PATH}")
    
    def get_tips(
        self,
        score: int,
        occupation: str,
        state: str = ""
    ) -> List[Dict[str, str]]:
        """
        Get personalized financial tips based on borrower profile.
        
        Args:
            score: Credit score (0-100)
            occupation: Borrower's occupation
            state: Borrower's state (optional, for regional tips)
        
        Returns:
            List of 3 tip dictionaries, each with:
            - heading: Short title for the tip
            - detail: Detailed explanation/actionable advice
            - icon: Bootstrap icon class name
        """
        if self.vector_store is None:
            logger.error("Vector store not initialized")
            return self._fallback_tips(score, occupation)
        
        # Build context query combining occupation and score range
        # This ensures we retrieve tips relevant to the borrower's specific situation
        score_range = self._get_score_range_label(score)
        
        query = (
            f"{occupation} with {score_range} credit score. "
            f"Financial inclusion tips and government schemes for "
            f"{occupation} in {state if state else 'rural India'}. "
            f"How to improve creditworthiness and access formal loans."
        )
        
        try:
            # Perform similarity search
            results = self.vector_store.similarity_search(
                query,
                k=TOP_K_RESULTS + 2  # Fetch extra to filter duplicates
            )
            
            # Process results into structured tips
            tips = []
            seen_content = set()  # Deduplicate similar results
            
            for doc in results:
                content = doc.page_content.strip()
                
                # Skip if too similar to already included tip
                content_key = content[:100].lower()
                if content_key in seen_content:
                    continue
                seen_content.add(content_key)
                
                # Extract heading and detail from chunk
                heading, detail = self._format_tip(content, occupation, score)
                icon = self._select_icon(content)
                
                tips.append({
                    'heading': heading,
                    'detail': detail,
                    'icon': icon
                })
                
                if len(tips) >= TOP_K_RESULTS:
                    break
            
            # If we got fewer than 3 tips, pad with fallbacks
            while len(tips) < TOP_K_RESULTS:
                fallback = self._fallback_tips(score, occupation)
                tips.append(fallback[len(tips) % len(fallback)])
            
            return tips[:TOP_K_RESULTS]
            
        except Exception as e:
            logger.error(f"RAG query failed: {e}")
            return self._fallback_tips(score, occupation)
    
    def _get_score_range_label(self, score: int) -> str:
        """Map numeric score to descriptive range for query context."""
        if score >= 70:
            return "high (low risk)"
        elif score >= 40:
            return "medium (moderate risk)"
        else:
            return "low (high risk)"
    
    def _format_tip(self, content: str, occupation: str, score: int) -> tuple:
        """
        Extract a heading and formatted detail from raw chunk content.
        
        Tries to create a concise, actionable tip from the retrieved text.
        """
        # Try to extract a natural heading from the content
        lines = content.split('.')
        
        # Use first sentence as potential heading, truncate if too long
        heading = lines[0].strip()
        if len(heading) > 80:
            # Find a natural break point
            heading = heading[:77].rsplit(' ', 1)[0] + "..."
        
        # Clean up heading — remove section prefixes
        for prefix in ['SECTION', 'The ', 'For ', 'Under ']:
            if heading.startswith(prefix) and len(lines) > 1:
                heading = lines[1].strip() if lines[1].strip() else heading
                break
        
        if len(heading) > 80:
            heading = heading[:77].rsplit(' ', 1)[0] + "..."
        
        # Detail is remaining content, cleaned up
        detail_parts = lines[1:4] if len(lines) > 1 else [content]
        detail = '. '.join(part.strip() for part in detail_parts if part.strip())
        
        # Truncate detail to reasonable length
        if len(detail) > 300:
            detail = detail[:297].rsplit(' ', 1)[0] + "..."
        
        return heading, detail
    
    def _select_icon(self, content: str) -> str:
        """Select an appropriate icon based on tip content keywords."""
        content_lower = content.lower()
        
        icon_keywords = {
            'bi-piggy-bank': ['savings', 'save', 'deposit', 'pmjdy', 'jan dhan'],
            'bi-phone': ['mobile', 'digital', 'phone', 'app', 'recharge'],
            'bi-qr-code': ['upi', 'payment', 'transaction', 'digital payment'],
            'bi-bank': ['loan', 'credit', 'mudra', 'bank', 'nbfc', 'lending'],
            'bi-shield-check': ['insurance', 'pmfby', 'pmsby', 'pmjjby', 'protection'],
            'bi-flower1': ['farm', 'agriculture', 'crop', 'kcc', 'kisan', 'yield'],
            'bi-people': ['shg', 'self help', 'group', 'community', 'cooperative'],
            'bi-award': ['scheme', 'yojana', 'government', 'subsidy', 'benefit'],
            'bi-book': ['literacy', 'training', 'education', 'skill', 'learn']
        }
        
        for icon, keywords in icon_keywords.items():
            if any(kw in content_lower for kw in keywords):
                return icon
        
        return TIP_ICONS['default']
    
    def _fallback_tips(self, score: int, occupation: str) -> List[Dict[str, str]]:
        """
        Provide fallback tips when RAG retrieval fails.
        
        These are generic but still relevant tips based on score range.
        """
        if score >= 70:
            return [
                {
                    'heading': 'Maintain Your Excellent Financial Habits',
                    'detail': 'Continue making regular UPI transactions and paying utility bills on time. Your consistent financial behavior is your strongest asset for loan approval.',
                    'icon': 'bi-trophy'
                },
                {
                    'heading': 'Explore Government Schemes',
                    'detail': 'With your strong credit profile, you may qualify for MUDRA loans, KCC, or PM SVANidhi schemes at concessional interest rates.',
                    'icon': 'bi-award'
                },
                {
                    'heading': 'Build Emergency Savings',
                    'detail': 'Set aside a small amount each month in your PMJDY account. Even Rs 500/month builds a safety net and strengthens your financial profile.',
                    'icon': 'bi-piggy-bank'
                }
            ]
        elif score >= 40:
            return [
                {
                    'heading': 'Increase Your UPI Transaction Frequency',
                    'detail': 'Using UPI for daily purchases creates a verifiable digital financial trail. Even small transactions of Rs 50-200 demonstrate active financial behavior.',
                    'icon': 'bi-qr-code'
                },
                {
                    'heading': 'Pay Utility Bills on Time',
                    'detail': 'Consistent on-time utility payments are one of the strongest indicators of creditworthiness. Set reminders for payment due dates.',
                    'icon': 'bi-lightning'
                },
                {
                    'heading': 'Join a Self Help Group or Cooperative',
                    'detail': 'SHG membership provides social collateral and builds credit history through group savings and lending activities.',
                    'icon': 'bi-people'
                }
            ]
        else:
            return [
                {
                    'heading': 'Start Building Your Financial Trail',
                    'detail': 'Open a PMJDY account if you do not have one. Make small regular deposits and start using UPI for receiving payments.',
                    'icon': 'bi-bank'
                },
                {
                    'heading': 'Register for Government Schemes',
                    'detail': 'Enroll in PM-KISAN, e-SHRAM, or Ayushman Bharat. These registrations demonstrate identity verification and provide a baseline income stream.',
                    'icon': 'bi-award'
                },
                {
                    'heading': 'Prioritize Utility Bill Payments',
                    'detail': 'Start paying electricity and phone bills on time. Even 3-6 months of consistent payments significantly improves your financial profile.',
                    'icon': 'bi-lightning'
                }
            ]


# ─── Module-level singleton ─────────────────────────────────────────────────
# Initialized lazily on first import to avoid slow startup
_pipeline_instance = None


def get_pipeline() -> RAGPipeline:
    """Get or create the singleton RAG pipeline instance."""
    global _pipeline_instance
    if _pipeline_instance is None:
        _pipeline_instance = RAGPipeline()
    return _pipeline_instance


def query_tips(score: int, occupation: str, state: str = "") -> List[Dict[str, str]]:
    """
    Convenience function to query tips from the RAG pipeline.
    
    Args:
        score: Credit score (0-100)
        occupation: Borrower's occupation
        state: Borrower's state (optional)
    
    Returns:
        List of 3 tip dictionaries with heading, detail, and icon
    """
    pipeline = get_pipeline()
    return pipeline.get_tips(score, occupation, state)


if __name__ == '__main__':
    """Test the RAG pipeline with sample queries."""
    logging.basicConfig(level=logging.INFO)
    
    print("=" * 60)
    print("FinScore AI — RAG Pipeline Test")
    print("=" * 60)
    
    # Initialize pipeline (this will build the index if needed)
    pipeline = RAGPipeline()
    
    # Test queries for different profiles
    test_profiles = [
        {'score': 75, 'occupation': 'Farmer', 'state': 'Uttar Pradesh'},
        {'score': 45, 'occupation': 'Street Vendor', 'state': 'Bihar'},
        {'score': 25, 'occupation': 'Daily Wage Laborer', 'state': 'Odisha'},
        {'score': 60, 'occupation': 'SHG Member', 'state': 'Rajasthan'},
    ]
    
    for profile in test_profiles:
        print(f"\n{'─' * 60}")
        print(f"Profile: {profile['occupation']} | Score: {profile['score']} | State: {profile['state']}")
        print(f"{'─' * 60}")
        
        tips = pipeline.get_tips(**profile)
        
        for i, tip in enumerate(tips, 1):
            print(f"\n  Tip {i}: [{tip['icon']}] {tip['heading']}")
            print(f"  Detail: {tip['detail'][:150]}...")
    
    print(f"\n{'=' * 60}")
    print("✅ RAG Pipeline Test Complete!")
    print(f"{'=' * 60}")
