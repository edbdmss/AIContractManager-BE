FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --create-home app
COPY --chown=app:app main.py .
COPY --chown=app:app extraction ./extraction
COPY --chown=app:app masking ./masking
COPY --chown=app:app rag ./rag
COPY --chown=app:app data/legal ./data/legal
RUN chown app:app /app
USER app
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
