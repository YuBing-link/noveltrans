import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))


@pytest.fixture
def translate_app():
    """Return the FastAPI app without starting the server."""
    from translate_server import app
    return app


@pytest.fixture
def client(translate_app):
    """Provide a TestClient for the translation service."""
    from fastapi.testclient import TestClient
    return TestClient(translate_app)
