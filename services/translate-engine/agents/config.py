"""Model configuration and agentscope initialization.

Loads settings from environment variables with sensible defaults.
"""

from __future__ import annotations

import os

from agentscope.model import OpenAIChatModel

# ---------------------------------------------------------------------------
# Environment-driven configuration
# ---------------------------------------------------------------------------
OPENAI_BASE_URL: str = os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1")
OPENAI_API_KEY: str = os.environ.get("OPENAI_API_KEY", "")
TRANSLATION_MODEL: str = os.environ.get("TRANSLATION_MODEL", "gpt-4o")
MODEL_TEMPERATURE: float = float(os.environ.get("MODEL_TEMPERATURE", "0.3"))

# ---------------------------------------------------------------------------
# Lazy-initialized model singleton
# ---------------------------------------------------------------------------
_model: OpenAIChatModel | None = None


def get_model() -> OpenAIChatModel:
    """Return (and lazily initialize) the shared OpenAIChatModel instance."""
    global _model
    if _model is None:
        if not OPENAI_API_KEY:
            raise ValueError(
                "OPENAI_API_KEY environment variable is not set. "
                "Please configure it before using the translation agents."
            )
        _model = OpenAIChatModel(
            model_name=TRANSLATION_MODEL,
            api_key=OPENAI_API_KEY,
            base_url=OPENAI_BASE_URL,
            temperature=MODEL_TEMPERATURE,
        )
    return _model


def init_agentscope() -> None:
    """Initialize agentscope framework.

    Currently a no-op placeholder for future agentscope-level initialization
    (e.g., logging, tracing, or studio registration). The model is lazily
    created on first access via :func:`get_model`.
    """
    # Force model initialization early to catch config errors at startup
    get_model()


def reset_model() -> None:
    """Reset the model singleton (useful for testing or reconfiguration)."""
    global _model
    _model = None
