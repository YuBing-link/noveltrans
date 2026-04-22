"""agentscope-based multi-agent translation system."""

from .config import init_agentscope, get_model
from .pipeline import translate_chapter
from .team.translator import TranslationTeam
from .team.merger import MergeAgent

__all__ = [
    "init_agentscope",
    "get_model",
    "translate_chapter",
    "TranslationTeam",
    "MergeAgent",
]
