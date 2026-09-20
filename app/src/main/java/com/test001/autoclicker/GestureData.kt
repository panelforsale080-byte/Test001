package com.test001.autoclicker

import org.json.JSONArray
import org.json.JSONObject

/** One captured touch sample. */
data class GesturePoint(val x: Float, val y: Float, val t: Long, val type: String)

/** A recorded gesture: ordered points + absolute start (anchor reference). */
data class Gesture(
    val name: String,
    val points: List<GesturePoint>,
    val startX: Float,
    val startY: Float
) {
    val duration: Long get() = points.lastOrNull()?.t ?: 0L

    fun toJson(): String {
        val arr = JSONArray()
        for (p in points) {
            arr.put(JSONObject().apply {
                put("x", p.x.toInt()); put("y", p.y.toInt())
                put("t", p.t); put("type", p.type)
            })
        }
        return JSONObject().apply {
            put("name", name); put("duration", duration)
            put("startX", startX.toInt()); put("startY", startY.toInt())
            put("points", arr)
        }.toString(2)
    }

    companion object {
        fun fromJson(text: String): Gesture? = try {
            val o = JSONObject(text)
            val arr = o.getJSONArray("points")
            val pts = ArrayList<GesturePoint>(arr.length())
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                pts.add(GesturePoint(
                    p.getDouble("x").toFloat(), p.getDouble("y").toFloat(),
                    p.getLong("t"), p.optString("type", "MOVE")))
            }
            Gesture(o.optString("name", "gesture"), pts,
                o.getDouble("startX").toFloat(), o.getDouble("startY").toFloat())
        } catch (t: Throwable) { null }
    }
}
