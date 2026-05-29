package com.codex.whatsappguard.domain

/**
 * Query helper for visible accessibility node snapshots.
 *
 * @param nodes      Nodes collected from the selected WhatsApp window.
 * @param normalizer Text normalizer used for label matching.
 */
class NodeQuery(
    val nodes: List<NodeSnapshot>,
    private val normalizer: TextNormalizer = TextNormalizer()
) {
    /** Finds the largest node whose resource id ends with [idSuffix]. */
    fun findLargestById(idSuffix: String): NodeSnapshot? =
        nodes.asSequence().filter { hasId(it, idSuffix) }.maxByOrNull { it.rect.area }

    /** Returns true when any node matches the requested resource id suffix. */
    fun hasId(idSuffix: String): Boolean = nodes.any { hasId(it, idSuffix) }

    fun hasId(node: NodeSnapshot, idSuffix: String): Boolean =
        node.viewId.endsWith(":id/$idSuffix") || node.viewId.endsWith("/$idSuffix")

    /** Finds a compact top-right node matching one of [labels]. */
    fun findTopRightNode(labels: List<String>, metrics: ScreenMetrics): NodeSnapshot? =
        nodes.asSequence()
            .filter { matchesAny(it, labels) }
            .filter { it.rect.centerX > metrics.bounds.right * 0.58f }
            .filter { it.rect.centerY < metrics.bounds.top + metrics.dp(210) }
            .filter { it.rect.width <= metrics.dp(120) && it.rect.height <= metrics.dp(120) }
            .maxByOrNull { it.rect.centerX }

    /** Finds a compact top-left node matching one of [labels]. */
    fun findTopLeftNode(labels: List<String>, metrics: ScreenMetrics): NodeSnapshot? =
        nodes.asSequence()
            .filter { matchesAny(it, labels) }
            .filter { it.rect.centerX < metrics.bounds.right * 0.35f }
            .filter { it.rect.centerY < metrics.bounds.top + metrics.dp(210) }
            .filter { it.rect.width <= metrics.dp(120) && it.rect.height <= metrics.dp(120) }
            .minByOrNull { it.rect.centerX }

    /** Returns top nodes that match any label. */
    fun topNodesMatching(labels: List<String>, metrics: ScreenMetrics): List<NodeSnapshot> =
        nodes.filter { matchesAny(it, labels) && it.rect.centerY < metrics.bounds.top + metrics.dp(210) }

    fun matchesAny(node: NodeSnapshot, labels: List<String>): Boolean {
        val hay = normalizer.normalize("${node.text} ${node.description} ${node.viewId} ${node.className}")
        return labels.any { hay.contains(normalizer.normalize(it)) }
    }

    fun textOrDescriptionMatchesAny(node: NodeSnapshot, labels: List<String>): Boolean {
        val hay = normalizer.normalize("${node.text} ${node.description}")
        return labels.any { hay.contains(normalizer.normalize(it)) }
    }

    fun matchesExactOrPrefixed(node: NodeSnapshot, labels: List<String>): Boolean {
        val values = listOf(node.text, node.description)
            .map { normalizer.normalize(it) }.filter { it.isNotBlank() }
        val normalized = labels.map { normalizer.normalize(it) }
        return values.any { v -> normalized.any { l -> v == l || v.startsWith("$l,") } }
    }

    fun normalizedText(node: NodeSnapshot): String = normalizer.normalize(node.text)
}
