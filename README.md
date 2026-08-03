# HabrRSS

Kotlin Multiplatform + Compose Multiplatform reader for Habr feeds.

The app is built around a local cache: network sources refresh the cache, and the
UI observes cached feed snapshots. This keeps article lists stable across screen
navigation, app restarts, and incremental archive loading.

## Targets

- Android
- Desktop JVM: Windows, Linux, macOS

## Current Features

- Habr latest feed.
- User-added Habr hub feeds.
- Habr hub latest articles from dedicated RSS endpoints.
- Habr API archive loading for hub pagination after the RSS latest page.
- Local deduplication and sorting by publication date.
- Room/file-backed cache with local read and bookmark state.
- Article reader with full content loading from Habr API and HTML fallback.
- Hub and tag chips, article cards, bookmarks, search, and reader settings.
- Persistent article card display mode.
- Scroll restoration when returning from an article to the feed.
- Scroll-to-top only when a refresh actually loads newer top articles.
- Dismissible error banner and retry flow.
- Runtime logging for feed, API, repository, navigation, and article operations.

## Habr Feed Strategy

Habr hub feeds use a hybrid source model:

- RSS is used first for the latest hub articles:
  `https://habr.com/ru/rss/hub/<slug>/all/?fl=ru&with_hubs=true&with_tags=true`
- Habr API is used for archive pages:
  `/kek/v2/articles/?hub=<slug>&sort=date&period=alltime&page=N&perPage=100`
- RSS/API results are filtered by the selected hub, deduplicated, cached, and
  sorted by `publishedAtEpoch` descending.
- Generated hub ids such as `hub-760110735` are treated as internal ids, not real
  Habr slugs. When needed, article HTML is used to recover real `/hubs/<slug>/`
  links.

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
.\gradlew.bat :composeApp:testDebugUnitTest :composeApp:jvmTest
```

## Notes

- The list UI is cache-backed, not a direct Paging UI.
- PagingSource is used as the network-page loading engine; loaded pages are
  written into the local cache.
- Hub RSS usually returns only the latest page, while the Habr API can provide
  many archive pages. The app therefore keeps RSS fast and uses API pagination
  for long history.
