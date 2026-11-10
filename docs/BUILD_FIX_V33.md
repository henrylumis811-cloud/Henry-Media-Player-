# RVH Music v33 — scroll-aware artwork updates

- Artwork requests are paused while attached RecyclerViews are dragging or settling.
- Cached artwork is also kept visually stable during motion; no thumbnail swaps are delivered mid-scroll.
- Decodes already in progress may finish and populate the cache, but their view callbacks are suppressed if scrolling is active when delivery occurs.
- When all tracked lists become idle, only the currently visible rows are rebound so their artwork is loaded first.
- Applied to the Library list (including Albums/Artists mode), playlist catalogue/details, and Add Songs picker.
- Playback, navigation, visual design, and audio behaviour are unchanged.
- Version: 3.17 (versionCode 20).

Device validation is still required to measure frame pacing and confirm the perceived reduction in artwork shimmer on a large library.
