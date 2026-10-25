from fastapi import FastAPI, UploadFile, File, HTTPException

from extraction.pdf_processor import pdf_to_images

app = FastAPI(title="AI Contract Analysis API")


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/analyze")
async def analyze_contract(file: UploadFile = File(...)):
    if file.content_type != "application/pdf":
        raise HTTPException(
            status_code=400,
            detail="PDF 파일만 업로드할 수 있습니다."
        )

    pdf_bytes = await file.read()

    try:
        images = pdf_to_images(pdf_bytes)
    except Exception:
        raise HTTPException(
            status_code=400,
            detail="PDF 파일을 읽을 수 없습니다."
        )

    return {
        "filename": file.filename,
        "page_count": len(images),
        "image_sizes": [
            {"width": image.width, "height": image.height}
            for image in images
        ]
    }