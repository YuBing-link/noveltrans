"""agentscope.msghub compatibility shim for agentscope 1.0+.

agentscope 1.0 renamed ``msghub()`` to ``MsgHub`` and made it async.
This module provides a synchronous ``msghub()`` function that wraps
the new async ``MsgHub`` so existing code continues to work.
"""

from __future__ import annotations

import asyncio
from contextlib import contextmanager
from typing import TYPE_CHECKING, Iterator

from agentscope.pipeline import MsgHub

if TYPE_CHECKING:
    from agentscope.agent import AgentBase
    from agentscope.message import Msg


class _SyncMsgHub:
    """Synchronous wrapper around async MsgHub."""

    def __init__(self, participants: list[AgentBase]) -> None:
        self._participants = participants
        self._hub: MsgHub | None = None

    def __enter__(self) -> _SyncMsgHub:
        # Use the currently running event loop (caller manages lifecycle)
        loop = asyncio.get_event_loop()
        self._hub = loop.run_until_complete(
            MsgHub(participants=self._participants).__aenter__()
        )
        return self

    def __exit__(self, *args: object) -> None:
        if self._hub is not None:
            loop = asyncio.get_event_loop()
            loop.run_until_complete(self._hub.__aexit__(*args))

    def broadcast(self, msg: Msg) -> None:
        """Broadcast a message to all participants."""
        loop = asyncio.get_event_loop()
        for agent in self._participants:
            loop.run_until_complete(agent.observe(msg))


@contextmanager
def msghub(
    participants: list[AgentBase],
) -> Iterator[_SyncMsgHub]:
    """Synchronous msghub context manager (agentscope 1.0 compat)."""
    hub = _SyncMsgHub(participants)
    with hub:
        yield hub
