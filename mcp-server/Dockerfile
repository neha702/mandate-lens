FROM python:3.12-slim

WORKDIR /app

RUN pip install --no-cache-dir uv

COPY pyproject.toml uv.lock README.md ./
RUN uv sync --locked --no-dev

COPY server.py ./

ENV PYTHONUNBUFFERED=1
EXPOSE 10000

CMD ["sh", "-c", "uv run --no-sync fastmcp run server.py --transport http --host 0.0.0.0 --port ${PORT:-10000}"]
