"""
Servicio de OCR gratuito y autoalojado para AgriAudit Docs.

Motor: RapidOCR (puerto ONNX de PP-OCR, licencia Apache-2.0). No requiere clave de
API, no envía datos a terceros y funciona con documentos impresos, escaneados,
fotografías de notas y — con menor fiabilidad — texto manuscrito.

Entrada:  multipart/form-data con el campo 'file' (PDF, PNG, JPG, TIFF, WEBP, BMP).
Salida:   JSON con el texto completo y las líneas detectadas (texto, confianza, bbox).
"""
import io
import logging
import os
import time

import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("ocr")

MAX_BYTES = int(os.getenv("OCR_MAX_BYTES", str(25 * 1024 * 1024)))
PDF_MAX_PAGES = int(os.getenv("OCR_PDF_MAX_PAGES", "20"))
PDF_RENDER_SCALE = float(os.getenv("OCR_PDF_SCALE", "2.4"))  # ~170 dpi

app = FastAPI(title="AgriAudit OCR", version="1.0.0")
engine = RapidOCR()


def _image_to_lines(img: np.ndarray, page: int):
    result, _ = engine(img)
    lines = []
    if not result:
        return lines
    for box, text, score in result:
        xs = [p[0] for p in box]
        ys = [p[1] for p in box]
        lines.append(
            {
                "page": page,
                "text": text,
                "confidence": round(float(score), 4),
                "box": [round(min(xs), 1), round(min(ys), 1), round(max(xs), 1), round(max(ys), 1)],
            }
        )
    return lines


def _pdf_pages(data: bytes):
    import pypdfium2 as pdfium

    pdf = pdfium.PdfDocument(data)
    n = min(len(pdf), PDF_MAX_PAGES)
    for i in range(n):
        page = pdf[i]
        bitmap = page.render(scale=PDF_RENDER_SCALE)
        pil = bitmap.to_pil().convert("RGB")
        yield i + 1, np.asarray(pil)
    return


@app.get("/health")
def health():
    return {"status": "UP", "engine": "rapidocr-onnxruntime"}


@app.post("/ocr")
async def ocr(file: UploadFile = File(...)):
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Fichero vacío")
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="Fichero demasiado grande")

    name = (file.filename or "").lower()
    ctype = (file.content_type or "").lower()
    started = time.time()
    all_lines = []
    truncated = False

    try:
        if name.endswith(".pdf") or ctype == "application/pdf" or data[:4] == b"%PDF":
            pages_done = 0
            for page_no, arr in _pdf_pages(data):
                all_lines.extend(_image_to_lines(arr, page_no))
                pages_done = page_no
            import pypdfium2 as pdfium

            total = len(pdfium.PdfDocument(data))
            truncated = total > pages_done
            pages = pages_done
        else:
            pil = Image.open(io.BytesIO(data)).convert("RGB")
            all_lines.extend(_image_to_lines(np.asarray(pil), 1))
            pages = 1
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        log.exception("Fallo de OCR")
        raise HTTPException(status_code=422, detail=f"No se pudo procesar el fichero: {exc}") from exc

    text = "\n".join(l["text"] for l in all_lines).strip()
    confs = [l["confidence"] for l in all_lines]
    mean_conf = round(sum(confs) / len(confs), 4) if confs else 0.0

    return {
        "engine": "rapidocr-onnxruntime",
        "fileName": file.filename,
        "pages": pages,
        "truncated": truncated,
        "lineCount": len(all_lines),
        "meanConfidence": mean_conf,
        "elapsedMs": int((time.time() - started) * 1000),
        "text": text,
        "lines": all_lines,
    }
