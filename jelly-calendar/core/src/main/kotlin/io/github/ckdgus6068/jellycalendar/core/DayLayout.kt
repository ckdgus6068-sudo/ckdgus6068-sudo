package io.github.ckdgus6068.jellycalendar.core

/** Where a jelly sits when several of them overlap on the same day. */
data class LanePlacement(val lane: Int, val lanes: Int)

object DayLayout {

    class Item(val id: String, val start: Int, val end: Int)

    /**
     * Classic calendar layout: overlapping jellies share the column side by side.
     * [minVisualMinutes] keeps very short jellies from visually overlapping their neighbours.
     */
    fun place(items: List<Item>, minVisualMinutes: Int = 0): Map<String, LanePlacement> {
        val sorted = items
            .map { Item(it.id, it.start, maxOf(it.end, it.start + minVisualMinutes)) }
            .sortedWith(compareBy<Item>({ it.start }, { -(it.end - it.start) }))
        val result = HashMap<String, LanePlacement>(sorted.size)
        val cluster = ArrayList<Pair<Item, Int>>()
        val laneEnds = ArrayList<Int>()
        var clusterEnd = Int.MIN_VALUE

        fun flush() {
            val lanes = laneEnds.size.coerceAtLeast(1)
            for ((item, lane) in cluster) result[item.id] = LanePlacement(lane, lanes)
            cluster.clear()
            laneEnds.clear()
        }

        for (item in sorted) {
            if (item.start >= clusterEnd && cluster.isNotEmpty()) flush()
            var lane = laneEnds.indexOfFirst { it <= item.start }
            if (lane < 0) {
                lane = laneEnds.size
                laneEnds += item.end
            } else {
                laneEnds[lane] = item.end
            }
            cluster += item to lane
            clusterEnd = if (cluster.size == 1) item.end else maxOf(clusterEnd, item.end)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }
}
