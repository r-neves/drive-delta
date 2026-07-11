package app.drivedelta.data.remote.firestore

/**
 * Safe coercion helpers for reading raw Firestore document maps.
 *
 * Firestore returns every number as either [Long] or [Double] regardless of how it was written, so
 * typed getters must coerce through [Number] rather than casting directly. These helpers keep the
 * DTO [fromMap] functions DRY and deterministic — no reflection or POJO mapping is used anywhere in
 * the remote layer.
 */

/** Reads [k] as a nullable [Long], coercing any numeric type. */
fun Map<String, Any?>.longOrNull(k: String): Long? = (this[k] as? Number)?.toLong()

/** Reads [k] as a nullable [Double], coercing any numeric type. */
fun Map<String, Any?>.doubleOrNull(k: String): Double? = (this[k] as? Number)?.toDouble()

/** Reads [k] as a nullable [Float], coercing any numeric type. */
fun Map<String, Any?>.floatOrNull(k: String): Float? = (this[k] as? Number)?.toFloat()

/** Reads [k] as a nullable [Int], coercing any numeric type. */
fun Map<String, Any?>.intOrNull(k: String): Int? = (this[k] as? Number)?.toInt()

/** Reads [k] as a nullable [String]. */
fun Map<String, Any?>.stringOrNull(k: String): String? = this[k] as? String

/** Reads [k] as a nullable [Boolean]. */
fun Map<String, Any?>.boolOrNull(k: String): Boolean? = this[k] as? Boolean

/** Reads [k] as a non-null [Long], defaulting to [default] when absent or the wrong type. */
fun Map<String, Any?>.long(k: String, default: Long = 0L): Long = longOrNull(k) ?: default

/** Reads [k] as a non-null [Double], defaulting to [default] when absent or the wrong type. */
fun Map<String, Any?>.double(k: String, default: Double = 0.0): Double = doubleOrNull(k) ?: default

/** Reads [k] as a non-null [Float], defaulting to [default] when absent or the wrong type. */
fun Map<String, Any?>.float(k: String, default: Float = 0f): Float = floatOrNull(k) ?: default

/** Reads [k] as a non-null [Int], defaulting to [default] when absent or the wrong type. */
fun Map<String, Any?>.int(k: String, default: Int = 0): Int = intOrNull(k) ?: default

/** Reads [k] as a non-null [String], defaulting to [default] when absent or the wrong type. */
fun Map<String, Any?>.string(k: String, default: String = ""): String = stringOrNull(k) ?: default

/** Reads [k] as a non-null [Boolean], defaulting to [default] when absent or the wrong type. */
fun Map<String, Any?>.bool(k: String, default: Boolean = false): Boolean = boolOrNull(k) ?: default
