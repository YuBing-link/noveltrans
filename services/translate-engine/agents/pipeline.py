"""Pipeline orchestration: receives text, calls the translation team, returns result.

This is the main entry point for external callers (e.g., translate_server.py).
"""

from __future__ import annotations

import logging

from .config import init_agentscope
from .team.translator import TranslationTeam
from .team.merger import MergeAgent

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Default chunk size for splitting long chapters (characters)
# ---------------------------------------------------------------------------
DEFAULT_CHUNK_SIZE = 4000


def _split_text(text: str, chunk_size: int = DEFAULT_CHUNK_SIZE) -> list[str]:
    """Split long text into chunks at paragraph boundaries.

    Respects both double newlines (paragraph breaks) and single newlines
    (dialogue/line breaks). Single newlines are preserved within chunks.
    """
    # First split by double newlines to get major paragraphs
    paragraphs = text.split("\n\n")
    chunks: list[str] = []
    current: list[str] = []
    current_len = 0

    for para in paragraphs:
        # Don't strip here — preserve internal single newlines for dialogue
        if not para.strip():
            continue
        if current_len + len(para) > chunk_size and current:
            # Join with double newline between major paragraphs
            chunks.append("\n\n".join(current))
            current = []
            current_len = 0
        current.append(para)
        current_len += len(para) + 2  # +2 for the "\n\n" separator

    if current:
        chunks.append("\n\n".join(current))

    # Fallback: if a single paragraph exceeds chunk_size, hard-split it
    final_chunks: list[str] = []
    for chunk in chunks:
        if len(chunk) <= chunk_size:
            final_chunks.append(chunk)
        else:
            for i in range(0, len(chunk), chunk_size):
                final_chunks.append(chunk[i : i + chunk_size])

    return final_chunks


def translate_chapter(
    text: str,
    novel_type: str = "daily",
    glossary_terms: list[dict[str, str]] | None = None,
    source_lang: str = "Japanese",
    target_lang: str = "Chinese",
    chunk_size: int = DEFAULT_CHUNK_SIZE,
    placeholders: dict[str, str] | None = None,
) -> str:
    """Translate a full chapter using the multi-agent team.

    Flow:
        1. Initialize agentscope (idempotent).
        2. Split long text into manageable chunks.
        3. For each chunk, run the TranslationTeam (translator + terminologist + polisher).
        4. Run MergeAgent to smooth transitions between chunks.
        5. Return the final polished translation.

    Args:
        text: The source chapter text to translate.
        novel_type: Novel genre/style hint. One of "battle", "mystery", or "daily"
                    (or any string containing those keywords).
        glossary_terms: List of dicts with at least 'source' and 'target' keys.
        source_lang: Source language name (default: "Japanese").
        target_lang: Target language name (default: "Chinese").
        chunk_size: Maximum characters per chunk (default: 4000).

    Returns:
        The fully translated and polished chapter text.
    """
    if not text or not text.strip():
        return ""

    logger.info(
        "translate_chapter: novel_type=%s, source=%s, target=%s, "
        "terms=%d, placeholders=%d, text_len=%d",
        novel_type,
        source_lang,
        target_lang,
        len(glossary_terms or []),
        len(placeholders or {}),
        len(text),
    )

    # Ensure agentscope is initialized
    init_agentscope()

    # Step 1: Split into chunks
    chunks = _split_text(text, chunk_size)
    logger.info("translate_chapter: split into %d chunk(s)", len(chunks))

    # Step 2: Translate each chunk via the team (reuse one team instance)
    team = TranslationTeam.create(
        source_lang=source_lang,
        target_lang=target_lang,
        novel_type=novel_type,
        glossary_terms=glossary_terms,
        placeholders=placeholders,
    )
    translated_chunks: list[str] = []
    for i, chunk in enumerate(chunks):
        logger.info("translate_chapter: processing chunk %d/%d", i + 1, len(chunks))
        result = team.translate(chunk)
        translated_chunks.append(result)

    # Step 3: Merge and polish
    if len(translated_chunks) > 1:
        merger = MergeAgent(placeholders=placeholders)
        final_text = merger.merge(translated_chunks)
    else:
        final_text = translated_chunks[0] if translated_chunks else ""

    logger.info("translate_chapter: done (output %d chars)", len(final_text))
    return final_text
