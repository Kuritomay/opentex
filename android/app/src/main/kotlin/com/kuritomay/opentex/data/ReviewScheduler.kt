package com.kuritomay.opentex.data

import kotlin.math.max

data class ReviewSchedule(
    val ease: Float,
    val intervalDays: Int,
    val dueAt: Long,
    val reps: Int,
)

object ReviewScheduler {
    private const val DAY_MS = 86_400_000L
    private const val MIN_EASE = 1.3f

    fun schedule(card: FlashcardEntity, rating: String, now: Long): ReviewSchedule {
        var ease = card.ease
        var reps = card.reps
        var interval = card.intervalDays
        when (rating) {
            "again" -> {
                reps = 0
                interval = 0
                ease = (ease - 0.2f).coerceAtLeast(MIN_EASE)
            }
            "hard" -> {
                reps += 1
                interval = if (interval <= 0) 1 else max(1, (interval * 1.2f).toInt())
                ease = (ease - 0.15f).coerceAtLeast(MIN_EASE)
            }
            "good" -> {
                reps += 1
                interval = when (reps) {
                    1 -> 1
                    2 -> 3
                    else -> max(1, (interval * ease).toInt())
                }
            }
            else -> {
                reps += 1
                ease += 0.15f
                interval = if (reps <= 1) 4 else max(2, (interval * ease * 1.3f).toInt())
            }
        }
        return ReviewSchedule(ease = ease, intervalDays = interval, dueAt = now + interval * DAY_MS, reps = reps)
    }

    fun isDue(card: FlashcardEntity, now: Long): Boolean = card.dueAt <= now

    fun dueLabel(card: FlashcardEntity, now: Long): String = when {
        card.reviewCount == 0 -> "Nueva"
        card.dueAt <= now -> "Vencida"
        else -> {
            val days = ((card.dueAt - now + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)
            if (days == 1) "Mañana" else "En $days días"
        }
    }

    fun nextLabel(card: FlashcardEntity, rating: String, now: Long): String {
        val next = schedule(card, rating, now)
        return when {
            next.intervalDays == 0 -> "ahora"
            next.intervalDays == 1 -> "mañana"
            else -> "en ${next.intervalDays} días"
        }
    }
}
