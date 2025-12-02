from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class AnalysisRequest(BaseModel):
    text: str

@app.post("/analyze")
def analyze_text(request: AnalysisRequest):
    print(f"🔍 [Python] 분석 요청: {request.text}")
    return {
        "status": "SUCCESS",
        "result": "파이썬 분석 완료: " + request.text[:10] + "..."
    }