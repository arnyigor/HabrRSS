# TechReader

Kotlin Multiplatform + Compose Multiplatform RSS-first reader prototype based on `Tech.md`.

Targets:

- Android
- Desktop JVM: Windows, Linux, macOS

The current state is an MVP architecture scaffold, not a finished network reader. It includes:

- `FeedSource` contract in `domain/source`
- Habr RSS, generic RSS, and future Habr API adapters in `data`
- shared domain models for feeds, articles, comments, cache policy, settings, bookmarks, and export requests
- in-memory repository and use cases
- Compose UI for feed, sources, bookmarks, search placeholder, settings, and article reader
- Markdown export core

The Habr API adapter is intentionally a stub. RSS is the MVP data source, and HTML reader mode is only a fallback.

## Build Android

```shell
.\gradlew.bat :composeApp:assembleDebug
```

## Run Desktop

```shell
.\gradlew.bat :composeApp:run
```

## Test

```shell
.\gradlew.bat :composeApp:allTests
```

## Next Implementation Steps

1. Add Ktor Client engines for Android and Desktop.
2. Add RSS XML parser and date normalizer.
3. Replace sample `HabrRssSource` data with real RSS loading.
4. Add SQLDelight or Room KMP cache.
5. Add Coil 3 image rendering/cache.
6. Implement platform-specific share, browser open, filesystem export, and optional PDF export.
