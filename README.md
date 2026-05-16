# Kotlin Project Analyzer

An automated static analysis tool designed to detect changed APIs and perform impact analysis between two Git branches in Kotlin projects. 

## 🚀 Overview

When developing new features, it is crucial to understand how code modifications affect your public APIs. This tool recursively compares two branches (e.g., `develop` and a feature branch), tracks code changes down to the Abstract Syntax Tree (AST) level, and outputs a list of APIs that have been modified or impacted by the changes.

* **High Accuracy:** ~90% accuracy in detecting impacted API endpoints, DTOs, and methods.
* **Recursive Analysis:** Traces deep dependencies to find out which high-level APIs are affected when a low-level method or DTO changes.

## 🛠️ How It Works

The analyzer accepts the local repository path and two specific commits/branches to compare:

```kotlin
val repoPath = "C:\\Users\\UlugbekYunusov\\Desktop\\a2s-reloaded"
val mainCommit = "develop"
val branchCommit = "feature/xxxx4"
```

1. **AST Parsing:** Parses Kotlin source code into an Abstract Syntax Tree.
2. **Diff Processing:** Analyzes the recursive differences between `mainCommit` and `branchCommit`.
3. **Impact Mapping:** Identifies modified DTOs, changed method signatures, and traces them back to the API endpoints they affect.

## 📋 Requirements

* JDK 11 or higher
* Kotlin 1.9+
* Local Git installation

## 🔒 License

**All rights reserved.** 

This repository is for **demonstration purposes only**. You are granted permission to view the source code via the GitHub interface. However, you **may not** copy, modify, distribute, publish, or use this source code, in whole or in part, for any commercial or non-commercial purpose without prior written permission from the copyright owner.

## 🛑 Project Status

**This specific repository is no longer actively maintained.** 

The project has been succeeded by a brand-new, completely redesigned version. The next-generation analyzer supports both **Java and Kotlin**, features a much more powerful analysis engine, and delivers significantly higher performance and deeper impact tracing.

---
*Developed by [@yuplanet](https://github.com).*
