# HabrRSS

HabrRSS is a Kotlin Multiplatform technical reading workspace for people who track engineering articles, hub updates, discussions, and long-running research threads across Android and Desktop.

It is more than a simple Habr reader: the app combines an offline-first feed cache, article extraction, hub and tag navigation, bookmark state, search, and desktop/mobile parity into one focused tool for daily technical reading.

## Why It Exists

Technical feeds are noisy and easy to lose. HabrRSS keeps the reading flow stable:

- fresh articles are loaded into a local cache before the UI changes;
- feed position is preserved when moving between the list and an article;
- new top articles intentionally move the list to the top;
- older archive pages can be appended without disrupting the current reading context;
- bookmarks, read state, filters, and display preferences stay local and fast.

## Platforms

- Android app for phones and tablets.
- Desktop JVM app for Windows, Linux, and macOS through Compose Multiplatform.

## Core Capabilities

- Latest Habr publications, posts, news, hubs, tags, and custom hub feeds.
- Hybrid feed strategy: RSS for fast latest updates, Habr API archive pagination for deeper history.
- Full article reading through Habr API with HTML fallback.
- Local deduplication, publication-date sorting, read state, and bookmarks.
- Hub and tag chips for focused browsing.
- Search, unread-only mode, card density options, theme settings, and typography controls.
- Stable scroll restoration when returning from articles.
- Scroll-to-top only when refresh brings newer top articles.
- Runtime logging across feed loading, repository cache, navigation, and article extraction.

## Architecture

The app is built as a Kotlin Multiplatform Compose project with shared UI and presentation logic. Data loading writes to a local cache, and screens observe cache-backed snapshots instead of rendering directly from transient network responses. This makes feed updates predictable across navigation, restarts, pagination, and offline use.
