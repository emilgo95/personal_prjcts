FROM python:3.12-slim

# libGL/glib para opencv-python-headless y renderizado de PDF
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

# Descarga anticipada de los modelos ONNX (evita la latencia en la primera petición)
RUN python -c "from rapidocr_onnxruntime import RapidOCR; RapidOCR()"

EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --retries=10 \
    CMD wget -q -O - http://localhost:8000/health | grep -q UP || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
