"""Basic tests for translate_server.py — import and health endpoint."""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))


class TestImports:
    def test_import_translate_server(self):
        from translate_server import app
        assert app is not None

    def test_import_models(self):
        from translate_server import (
            TranslateRequest,
            TranslateResponse,
            TeamTranslateRequest,
            EntityExtractionRequest,
            EntityTranslationRequestModel,
            PlaceholderTranslateRequest,
        )

    def test_import_rate_limiter(self):
        from translate_server import check_rate_limit, _rate_limit_lock
        assert _rate_limit_lock is not None


class TestHealthEndpoint:
    def test_health_returns_200(self, client):
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert "status" in data
        assert "engines" in data
        assert "engine_health" in data

    def test_health_contains_engine_list(self, client):
        response = client.get("/health")
        data = response.json()
        assert "openai" in data["engines"]


class TestRateLimiter:
    def test_rate_limit_allows_normal_traffic(self):
        from translate_server import check_rate_limit, _request_timestamps
        _request_timestamps.clear()
        import asyncio
        assert asyncio.get_event_loop().run_until_complete(check_rate_limit()) is True

    def test_rate_limit_blocks_over_max(self):
        from translate_server import check_rate_limit, _request_timestamps, RATE_LIMIT_MAX
        _request_timestamps.clear()
        import asyncio
        loop = asyncio.get_event_loop()
        for _ in range(RATE_LIMIT_MAX):
            loop.run_until_complete(check_rate_limit())
        assert loop.run_until_complete(check_rate_limit()) is False
