# main.py
import os
import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Literal

from langchain_groq import ChatGroq
from langchain_core.messages import HumanMessage, SystemMessage

# RAG Integration Libraries
# FIX: Replaced langchain_openai with langchain_huggingface
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_chroma import Chroma

# Load ambient configurations from your system environment / .env file
load_dotenv()

app = FastAPI(title="GenAI RAG Chatbot")

SPRING_BOOT_URL = os.getenv("SPRING_BOOT_URL", "http://localhost:8081")

# 1. Initialize Groq Inference Engine
llm = ChatGroq(
    model_name="llama-3.3-70b-versatile",
    temperature=0.1,  # Kept minimal to shut down hallucinations
    groq_api_key=os.getenv("GROQ_API_KEY")
)

# 2. FIX: Reference the exact same Local Hugging Face Embedding Configuration
embeddings = HuggingFaceEmbeddings(model_name="all-MiniLM-L6-v2")

# 3. Connect to the existing persistent vector index folder
CHROMA_DB_DIR = "./chroma-db"
DB_COL_NAME = "RailSathi_collection"

vector_db = Chroma(
    persist_directory=CHROMA_DB_DIR,
    embedding_function=embeddings,
    collection_name=DB_COL_NAME
)

class ChatReqModel(BaseModel):
    message: str

class ChatRespModel(BaseModel):
    status: Literal["success", "failed"]
    message: str

async def fetch_available_trains():
    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{SPRING_BOOT_URL}/api/trains", timeout=5.0)
            if response.status_code == 200:
                return response.json()
            return []
    except Exception as e:
        print(f"Error calling Spring Boot database: {e}")
        return []

@app.post("/chat", response_model=ChatRespModel)
async def chat(data: ChatReqModel):
    try:
        # STEP A: QUERY VECTOR DATABASE & HARVEST SIMILARITY RATINGS
        results_with_scores = await vector_db.asimilarity_search_with_relevance_scores(data.message, k=3)
        
        # Threshold Tuning: 0.0 (Accepts everything) to 1.0 (Requires near exact match)
        SIMILARITY_THRESHOLD = 0.30
        
        is_relevant_to_docs = False
        context_chunks = []
        
        if results_with_scores:
            for doc, score in results_with_scores:
                if score >= SIMILARITY_THRESHOLD:
                    is_relevant_to_docs = True
                    context_chunks.append(doc.page_content)
                    
        document_context = "\n---\n".join(context_chunks)

        # STEP B: FETCH SYSTEM RUNTIME DATABASE LOGS 
        trains_data = await fetch_available_trains()
        
        # Test if the user mentions any known properties of your active train objects
        user_msg_lower = data.message.lower()
        is_relevant_to_api = any(
            str(train.get("trainNumber", "")).lower() in user_msg_lower or 
            str(train.get("trainName", "")).lower() in user_msg_lower 
            for train in trains_data
        ) if isinstance(trains_data, list) else False

        # STEP C: THE INSTANT HARD BLOCK GUARDRAIL
        # If it doesn't cross the documentation similarity limit AND doesn't mention active trains, kill it.
        if not is_relevant_to_docs and not is_relevant_to_api:
            return ChatRespModel(
                status="failed",
                message="I am sorry, but your query falls outside the scope of our verified system guidelines."
            )

        # STEP D: FORMAT RIGIDLY ENFORCED SYSTEM CONTEXT
        system_instruction = (
            "You are a strict technical assistant operating exclusively inside localized application parameters.\n\n"
            "VERIFIED APPLICATION CAPABILITIES & GUIDELINES DOCUMENTATION:\n"
            f"{document_context if document_context else 'No document context found for this request.'}\n\n"
            "LIVE RAILWAY DATABASE CONTENT:\n"
            f"{trains_data}\n\n"
            "CRITICAL OPERATIONAL LAWS:\n"
            "1. Answer the user query comprehensively using ONLY the verified system documentation and live database context above.\n"
            "2. Under no circumstance may you use external general knowledge, coding instructions, or historical knowledge outside these limits.\n"
            "3. If the answer cannot be confidently deduced directly from the provided text context, respond with: "
            "'I am sorry, but that information is unavailable within our application scope.'"
            "4.Please make it conversional and easy to understand for a 10th grade student. Avoid using complex technical jargon try to not" \
            "use the document references in the answer "
        )

        messages = [
            SystemMessage(content=system_instruction),
            HumanMessage(content=data.message)
        ]
        
        # STEP E: FETCH SAFE AND RELEVANT GENERATION
        ai_response = await llm.ainvoke(messages)
        
        return ChatRespModel(
            status="success",
            message=ai_response.content
        )
        
    except Exception as e:
        print(f"Error processing RAG execution route: {e}")
        raise HTTPException(status_code=500, detail="Failed to process chat request")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8083, reload=True)
