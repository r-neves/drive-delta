package app.drivedelta.domain.model

/**
 * One aligned road segment across two trips (F8). Durations are null when a trip doesn't include
 * that road. [deltaMs] is tripA − tripB (negative = A was faster); [bestEverMs] is the minimum for
 * this roadKey across all of the user's trips (the "purple sector").
 */
data class SegmentComparison(
    val roadKey: String,
    val roadName: String,
    val tripADurationMs: Long?,
    val tripBDurationMs: Long?,
    val bestEverMs: Long?,
    val deltaMs: Long?,
    val winner: ComparisonWinner,
)

enum class ComparisonWinner { A, B, TIE }
