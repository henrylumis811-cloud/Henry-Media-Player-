# RVH Music v66 — Lyric Timeline Hardening

- Playback position remains the single synchronization authority.
- Enhanced-LRC word timestamps now update the transparent Now Playing line without a second timing clock.
- Word end times are derived from the next word cue or next line cue when the source provides only starts.
- Line text preserves the source spacing between word cues rather than reconstructing it with forced spaces.
- Seeking and playback-speed changes naturally re-evaluate against the same current player position.
- The live surface remains one line only; no glass card, miniature lyrics page, or duplicate scrolling surface was added.
