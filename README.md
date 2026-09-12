# Kotlin Project Analyzer

An automated static analysis tool designed to detect changed APIs and perform impact analysis between two Git branches in Kotlin projects.

---

### 🌐 Language / Язык
* [English Version (#-english-version)](#-english-version)
* [Русская версия (#-русская-версия)](#-русская-версия)

---

## 🇬🇧 English Version

An automated static analysis tool designed to detect changed APIs and perform impact analysis between two Git branches in Kotlin projects.

### 🚀 Overview
When developing new features, it is crucial to understand how code modifications affect your public APIs. This tool loads a Kotlin project, reconstructs its **PSI (Program Structure Interface)** tree, and scans reference dependencies. By identifying altered methods in a feature branch, it recursively traces the entire call hierarchy to output a comprehensive list of high-level APIs impacted by the changes.

* **PSI-Based Analysis:** Reconstructs the JetBrains Program Structure Interface (PSI) to understand the code's true semantics, completely superseding text-based diffs.
* **Call Chain Tracking:** Scans method references and traces call chains upwards to see how deep changes propagate to public endpoints.
* **High Accuracy:** ~90% accuracy in detecting impacted API endpoints, DTOs, and methods.

### 🛠️ How It Works
The analyzer accepts a local repository path and compares your main/base branch against a feature branch:

```kotlin
val repoPath = "C:\\projects\\my-kotlin-app"
val mainCommit = "develop"
val branchCommit = "feature/api-redesign"
```

1. **Project Loading & PSI Reconstruction:** Loads the target project and fully recreates the PSI structure for both branches.
2. **Method Reference Scanning:** Scans code relationships and identifies internal methods that were modified or introduced in the feature branch.
3. **Call Hierarchy Tracing:** Analyzes the call chains of those modified methods, mapping them all the way up to the public API endpoints they affect.

### 📋 Requirements
* JDK 11 or higher
* Kotlin 1.9+
* Local Git installation

### 🔒 License
**All rights reserved.**

This repository is for **demonstration purposes only**. You are granted permission to view the source code via the GitHub interface. However, you **may not** copy, modify, distribute, publish, or use this source code, in whole or in part, for any commercial or non-commercial purpose without prior written permission from the copyright owner.

### 🛑 Project Status & Next Generation
**Active development on this specific Kotlin-only repository has been discontinued.**

I am currently developing a **next-generation, highly advanced version** of the analyzer. The upcoming release is a complete architectural redesign that introduces:
* Full cross-language support for both **Java and Kotlin** within the same project.
* A significantly more powerful and optimized analysis engine.
* Deeper impact tracing and substantially higher performance on large-scale enterprise repositories.

---

## 🇷🇺 Русская Версия

Автоматизированный инструмент статического анализа, предназначенный для обнаружения изменений в API и оценки их влияния (impact analysis) между двумя Git-ветками в проектах на Kotlin.

### 🚀 Обзор проекта
При разработке новых фич критически важно понимать, как изменения в коде аффектят публичные API. Этот инструмент загружает Kotlin-проект, воссоздает его структуру **PSI (Program Structure Interface)** и сканирует внутренние связи. Обнаружив изменившиеся методы в feature-ветке, он рекурсивно прослеживает всю цепочку вызовов (call hierarchy) вверх и выводит итоговый список затронутых высокоуровневых API.

* **Анализ на основе PSI:** Воссоздает дерево Program Structure Interface (PSI) от JetBrains для понимания семантики кода, полностью заменяя обычный текстовый `git diff`.
* **Отслеживание цепочек вызовов:** Сканирует ссылки на методы и прослеживает связи вверх по иерархии вызовов, выявляя влияние низкоуровневых изменений на публичные контракты.
* **Высокая точность:** Точность обнаружения затронутых API-эндпоинтов, DTO и методов составляет порядка ~90%.

### 🛠️ Как это работает
Анализатор принимает путь к локальному репозиторию и сравнивает базовую ветку с вашей feature-веткой:

```kotlin
val repoPath = "C:\\projects\\my-kotlin-app"
val mainCommit = "develop"
val branchCommit = "feature/api-redesign"
```

1. **Загрузка проекта и воссоздание PSI:** Загружает целевой проект и полностью реконструирует PSI-структуру для обеих веток.
2. **Сканирование связей и методов:** Сканирует внутренние зависимости кода и находит методы, которые изменились или появились в вашей фича-ветке.
3. **Прослеживание цепочки вызовов:** Анализирует иерархию вызовов для этих методов, рекурсивно поднимается по ней вверх и выводит список финальных API, на которые повлияла фича.

### 📋 Требования
* JDK 11 или выше
* Kotlin 1.9+
* Установленный локально Git

### 🔒 Лицензия
**All rights reserved (Все права защищены).**

Этот репозиторий опубликован **исключительно в демонстрационных целях**. Вам предоставляется разрешение на просмотр исходного кода через интерфейс GitHub. Однако вы **не имеете права** копировать, модифицировать, распространять, публиковать или использовать этот исходный код (частично или полностью) в любых коммерческих или некоммерческих целях без предварительного письменного разрешения правообладателя.

### 🛑 Статус проекта и Новое поколение
**Активная разработка этой конкретной (Kotlin-only) версии репозитория прекращена.**

В данный момент я с нуля разрабатываю **следующее, продвинутое поколение** этого анализатора. Новая версия представляет собой полный перенос на обновленную архитектуру и включает в себя:
* Полноценную кросс-языковую поддержку как **Java, так и Kotlin** в рамках одного проекта.
* Значительно более мощный, глубокий и оптимизированный движок анализа кода.
* Существенное повышение производительности при обработке крупных enterprise-репозиториев.

---
*Developed by [@yuplanet](https://github.com).*
