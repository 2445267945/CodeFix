# app/services/rag_service.py
import os
import chromadb
from chromadb.config import Settings
from chromadb.utils.embedding_functions.ollama_embedding_function import OllamaEmbeddingFunction
from pypdf import PdfReader
from App.config import config

# ===================== 1. 从配置读取参数 =====================
embedding_fn = OllamaEmbeddingFunction(
    url=config.vector_db.OLLAMA_URL,
    model_name=config.vector_db.EMBEDDING_MODEL
)

chroma_client = chromadb.PersistentClient(
    path=config.vector_db.CHROMA_PATH,
    settings=Settings(anonymized_telemetry=False)
)

collection = chroma_client.get_or_create_collection(
    name=config.vector_db.COLLECTION_NAME,
    embedding_function=embedding_fn
)


# ===================== 2. 加载 PDF 入库 =====================
def load_manual_to_vector_db(pdf_path: str):
    if not os.path.exists(pdf_path):
        print(f"文件不存在: {pdf_path}")
        return

    reader = PdfReader(pdf_path)
    documents = []
    metadatas = []
    ids = []

    for page_num, page in enumerate(reader.pages, start=1):
        text = page.extract_text()
        if not text or len(text.strip()) < 10:
            continue
        chunks = text.split('\n\n')
        for idx, chunk in enumerate(chunks):
            chunk = chunk.strip()
            if len(chunk) < 20:
                continue
            chunk_id = f"page_{page_num}_chunk_{idx}"
            ids.append(chunk_id)
            documents.append(chunk)
            metadatas.append({
                "source": "Alibaba_Java_Manual",
                "page": page_num,
                "chunk_index": idx
            })

    if not documents:
        print("未能提取到任何有效文本，请检查PDF是否可读。")
        return

    collection.add(ids=ids, documents=documents, metadatas=metadatas)
    print(f"成功将 {len(documents)} 个知识片段存入向量库！")


# ===================== 3. 查询函数 =====================
def search_manual(query: str, n_results: int = None) -> str:
    """
    查询向量数据库，返回规范内容
    """
    if n_results is None:
        n_results = config.vector_db.N_RESULTS

    results = collection.query(
        query_texts=[query],
        n_results=n_results
    )
    docs = results['documents'][0] if results['documents'] else []
    if not docs:
        return "未找到相关规范内容。"

    truncated = []
    for doc in docs:
        truncated.append(doc)

    return "\n---\n".join(truncated)
