package org.example.data

data class DiffResult(
    val added: List<KotlinMethod>,
    val removed: List<KotlinMethod>,
    val changed: List<KotlinMethod>
)