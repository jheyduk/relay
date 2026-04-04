package dev.heyduk.relay.util

/**
 * FZF-style fuzzy matching. Returns a score > 0 if all characters in [query]
 * appear in [target] in order (case-insensitive). Higher scores for:
 * - Consecutive character matches
 * - Start-of-word matches (after '/', '-', '_', '.')
 * Returns 0 if no match.
 */
fun fuzzyMatch(query: String, target: String): Int {
    // Empty query matches everything with a baseline score
    if (query.isEmpty()) return 1

    val lowerQuery = query.lowercase()
    val lowerTarget = target.lowercase()

    var score = 0
    var queryIndex = 0
    var previousMatchIndex = -2 // Track consecutive matches

    for (targetIndex in lowerTarget.indices) {
        if (queryIndex >= lowerQuery.length) break

        if (lowerTarget[targetIndex] == lowerQuery[queryIndex]) {
            // Base match score
            score += 1

            // Consecutive match bonus
            if (targetIndex == previousMatchIndex + 1) {
                score += 2
            }

            // Start-of-word bonus: first char or preceded by separator
            if (targetIndex == 0 || lowerTarget[targetIndex - 1] in listOf('/', '-', '_', '.')) {
                score += 3
            }

            previousMatchIndex = targetIndex
            queryIndex++
        }
    }

    // All query chars must be found
    return if (queryIndex == lowerQuery.length) score else 0
}
