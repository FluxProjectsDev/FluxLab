package com.febricahyaa.fluxlab.integration

/** Pure block graph contract used by the Android resolver and deterministic tests. */
data class BlockTopologyNode(
    val name: String,
    val slaves: List<String> = emptyList(),
    val holders: List<String> = emptyList(),
    val subsystem: String? = null,
    val devicePath: String? = null,
)

data class ResolvedBlockTopology(
    val logicalDevice: String,
    val physicalDevice: String?,
    val chain: List<String>,
    val diagnostics: List<String>,
)

object StorageTopologyResolver {
    fun resolve(start: String, nodes: Map<String, BlockTopologyNode>): ResolvedBlockTopology {
        val chain = mutableListOf<String>()
        val diagnostics = mutableListOf<String>()
        val visiting = mutableSetOf<String>()
        fun visit(name: String) {
            if (!visiting.add(name)) {
                diagnostics += "Cycle prevented at " + name
                return
            }
            chain += name
            val node = nodes[name]
            if (node == null) {
                diagnostics += "No sysfs node for " + name
            } else {
                diagnostics += name + " slaves=" + node.slaves.joinToString(",").ifBlank { "none" }
                node.slaves.forEach(::visit)
            }
            visiting.remove(name)
        }
        visit(start)
        val physical = chain.asReversed().firstOrNull { nodes[it]?.slaves.orEmpty().isEmpty() }
        return ResolvedBlockTopology(start, physical, chain.distinct(), diagnostics.distinct())
    }
}
