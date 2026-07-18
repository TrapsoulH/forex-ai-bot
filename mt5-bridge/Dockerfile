# NOTE: MetaTrader5 Python package requires Windows.
# This Dockerfile is for reference/GCP Windows VM deployment.
# For local development, run `python src/main.py` directly on Windows.

FROM python:3.11-slim-windowsservercore

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY src/ ./src/

EXPOSE 8001
CMD ["python", "src/main.py"]
