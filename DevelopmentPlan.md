# Development Plan

Работаем этапами: после каждого этапа запускаем `./gradlew :composeApp:assembleDebug --no-daemon` и делаем отдельный commit.

## Этапы

- [x] Починить сборку, зависимости и Gradle memory.
- [x] Разнести серверные и локальные данные по таблицам, чтобы refresh не затирал локальное состояние.
- [x] Navigation 3: стабильные back stacks для top-level разделов, корректный back/open article.
- [x] MVI: формализовать intents/state/effects, убрать прямые вызовы ViewModel из UI где возможно.
- [x] ViewModels: разделить ответственность FeedViewModel и ArticleViewModel.
- [x] Single Source of Truth: UI подписан на БД Flow, сеть только пишет в БД.
- [ ] Paging 3: нормальная бесконечная подгрузка через pager, без ручных костылей в UI.
- [ ] UI/UX: простой сценарий тег/хаб -> загрузка при пустом кеше -> чтение списка.
- [ ] Stateful/stateless Compose + previews для основных экранов.
