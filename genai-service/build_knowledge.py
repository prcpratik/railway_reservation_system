# build_knowledge.py
import os
from langchain_community.document_loaders import PyPDFLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
# CHANGED: Import the free HuggingFace module instead of OpenAI
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_chroma import Chroma

def build_vector_database():
    print("-------------- Step 1: Document Processing --------------")
    
    pdf_path = r"sourcepdf\RailSathi.pdf"
    if not os.path.exists(pdf_path):
        raise FileNotFoundError(f"Could not locate PDF file at: {pdf_path}")
        
    loader = PyPDFLoader(pdf_path)
    raw_pages = loader.load()
    print(f"Successfully loaded PDF. Found {len(raw_pages)} raw pages.")

    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=1000,       
        chunk_overlap=200,     
        length_function=len
    )
    chunked_documents = text_splitter.split_documents(raw_pages)
    print(f"Split pages into {len(chunked_documents)} chunks.")

    print("\n-------------- Step 2: Database Creation --------------")
    
    CHROMA_DB_DIR = "./chroma-db"
    DB_COL_NAME = "RailSathi_collection"
    
    # CHANGED: Initializing local embedding generator. No API key required.
    print("Loading local HuggingFace embedding engine in system memory...")
    embeddings = HuggingFaceEmbeddings(model_name="all-MiniLM-L6-v2")

    print("Generating vector calculations locally and saving to Chroma...")
    vector_db = Chroma.from_documents(
        documents=chunked_documents,
        embedding=embeddings,  # Using the local engine
        persist_directory=CHROMA_DB_DIR,
        collection_name=DB_COL_NAME
    )
    
    print(f"Success! Local Vector database successfully created at: {CHROMA_DB_DIR}")

if __name__ == "__main__":
    build_vector_database()
