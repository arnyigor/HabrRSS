# План доведения HabrRSS до поведения Habr-like reader

## Анализ текущего кода

Проект уже построен как KMP/Compose Multiplatform приложение с одним модулем `composeApp`. Архитектурный каркас правильный для RSS-first reader:

- `domain/source/FeedSource.kt` задает границу источника данных.
- `data/rss/HabrRssSource.kt` загружает RSS Хабра через Ktor и парсит XML через Ksoup.
- `data/article/HabrWebReaderSource.kt` и `HabrArticleContentExtractor.kt` дают fallback на HTML reader mode для полной статьи.
- `data/repository/TechReaderRepository.kt` сохраняет элементы в DAO, не теряет `isRead` и `isBookmarked` при refresh.
- `presentation/ReaderPresenter.kt` держит состояние ленты, фильтры, поиск, закладки и открытие статьи.
- `FeedScreen.kt` и `ArticleScreen.kt` уже разделены, поэтому Habr-like UI можно внедрять без ломки data/domain слоев.

Главные отличия от сайта Хабра до доработки:

- Лента была обычным Material-списком без потоковых вкладок `Статьи / Посты / Новости / Хабы / Авторы / Компании`.
- Карточка публикации не повторяла структуру Хабра: автор/время, заголовок, сложность/время чтения/источник, теги, рейтинг, комментарии.
- Экран статьи начинался с крупного hero-изображения, а в референсе Habr делает текстовый header и служебные блоки вокруг статьи.
- После статьи не было Habr-like блоков `Теги`, `Хабы`, `Автор`, `Комментарии`, `Публикации`.
- RSS-источник имел только `articles`, `best` и один hub, но не `posts` и `news`.

## Уже реализовано

- Добавлены типы потоков `FeedKind.Posts` и `FeedKind.News`.
- В `HabrRssSource` добавлены RSS-дескрипторы:
  - `https://habr.com/ru/rss/articles/?limit=100&with_hubs=true&with_tags=true`
  - `https://habr.com/ru/rss/posts/?limit=100&with_hubs=true&with_tags=true`
  - `https://habr.com/ru/rss/news/?limit=100&with_hubs=true&with_tags=true`
- Добавлена presentation-модель вкладок `HabrPublicationSection`.
- Лента получила Habr-like header с потоковыми вкладками и строкой `Все подряд`.
- Карточка публикации стала плоской: автор, время, заголовок, мета-строка, хабы/теги, рейтинг, закладка, комментарии.
- Экран статьи приближен к референсу: текстовый header без обязательного hero, source notice, footer с тегами/хабами, author card, comments stub и publications stub.
- Добавлены unit-тесты на вкладки, время чтения и fallback-статистику.

## Дальнейший план

### Этап 1. Данные RSS

- Проверить live-доступность `posts` и `news` RSS на реальных ответах Хабра.
- Добавить обработку RSS-ошибок по потокам: если один поток недоступен, остальные не должны ломаться.
- Вынести список RSS-дескрипторов в конфиг источника, чтобы пользователь мог добавлять свои потоки.
- Улучшить извлечение метаданных из RSS description: rating, comments, hubs, tags, preview image.

### Этап 2. Лента как на Хабре

- Разделить вкладки на реальные источники и отдельные экраны:
  - `Статьи`, `Посты`, `Новости` выбирают RSS-feed.
  - `Хабы`, `Авторы`, `Компании` открывают отдельные каталоги, если данные доступны.
- Добавить режимы сортировки `все подряд`, `лучшие`, `новые`, `сначала непрочитанные`.
- Сохранять scroll position отдельно для каждого feedId.
- Добавить skeleton/loading состояние вместо пустого экрана при первой загрузке.

### Этап 3. Статья

- Доработать HTML parser: `figure/figcaption`, spoilers, tables, nested lists.
- Извлекать автора, дату, хабы и теги из HTML полной статьи, если RSS дал неполные данные.
- Показывать блок ссылок из статьи нативно, если он явно есть в HTML.
- Добавить переход к оригинальным комментариям через кнопку `Открыть обсуждение`.

### Этап 4. Offline-first

- Заменить временный `InMemoryFeedDao` на реальную Room KMP или SQLDelight реализацию.
- Сохранять открытые статьи, read state, bookmarks, favorite tags и настройки.
- Добавить cache policy: `online-first`, `cache-first`, `offline-only`, `refresh-in-background`.

### Этап 5. Тестирование

- Unit: RSS parser, HTML normalizer, reading time, tabs, filters, markdown export.
- Repository: refresh сохраняет read/bookmark, fallback работает без сети.
- UI/state: открытие статьи, back, переключение feed tabs, поиск, пустые состояния.
- Live integration: отдельный opt-in тест для реальных RSS endpoint Хабра.

## Ограничения

- Приложение не должно становиться WebView-only клиентом.
- Не нужно использовать закрытый/нестабильный Habr API как основу MVP.
- Не нужно имитировать авторизацию, голосование или подписки Хабра.
- Все недоступные действия должны вести к оригиналу или быть явно неактивными.
