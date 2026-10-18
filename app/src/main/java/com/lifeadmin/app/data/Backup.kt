package com.lifeadmin.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON backup of everything the app stores, so people can move phones or keep a copy. */
object Backup {
    private const val FORMAT = 2

    fun toJson(items: List<Item>, history: List<History>): String {
        val root = JSONObject()
        root.put("app", "LifeAdmin")
        root.put("format", FORMAT)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("items", JSONArray().apply {
            items.forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id)
                    put("title", i.title)
                    put("category", i.category.name)
                    put("dueEpochDay", i.dueEpochDay)
                    put("cycle", i.cycle.name)
                    put("autoRenew", i.autoRenew)
                    if (i.amount != null) put("amount", i.amount)
                    put("currency", i.currency)
                    put("notes", i.notes)
                    put("reminderDays", i.reminderDays)
                    put("createdAt", i.createdAt)
                    put("kind", i.kind.name)
                    if (i.dueMinute != null) put("dueMinute", i.dueMinute)
                    put("repeatEvery", i.repeatEvery)
                    put("repeatDays", i.repeatDays)
                    if (i.repeatLimit != null) put("repeatLimit", i.repeatLimit)
                    if (i.repeatUntilEpochDay != null) put("repeatUntilEpochDay", i.repeatUntilEpochDay)
                    put("alertOffsets", i.alertOffsets)
                    put("alertStyle", i.alertStyle.name)
                    put("nagEveryMin", i.nagEveryMin)
                    put("nagCount", i.nagCount)
                    put("done", i.done)
                    if (i.doneAt != null) put("doneAt", i.doneAt)
                    put("reference", i.reference)
                    put("paymentMethod", i.paymentMethod)
                    put("pinned", i.pinned)
                    put("priority", i.priority)
                    put("checklist", i.checklist)
                    put("repeatRule", i.repeatRule)
                })
            }
        })
        root.put("history", JSONArray().apply {
            history.forEach { h ->
                put(JSONObject().apply {
                    put("itemId", h.itemId)
                    put("itemTitle", h.itemTitle)
                    put("category", h.category.name)
                    put("dueEpochDay", h.dueEpochDay)
                    put("paidAt", h.paidAt)
                    if (h.amount != null) put("amount", h.amount)
                    put("currency", h.currency)
                    put("kind", h.kind)
                })
            }
        })
        return root.toString(2)
    }

    data class Parsed(val items: List<Item>, val history: List<History>)

    /** Throws IllegalArgumentException if the text isn't a LifeAdmin backup. */
    fun fromJson(text: String): Parsed {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file isn't a LifeAdmin backup.")
        }
        if (root.optString("app") != "LifeAdmin") {
            throw IllegalArgumentException("This file isn't a LifeAdmin backup.")
        }
        val itemsJson = root.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsJson.length()).map { idx ->
            val o = itemsJson.getJSONObject(idx)
            val category = enumOr(o.optString("category"), Category.OTHER)
            Item(
                id = o.optLong("id", 0),
                title = o.getString("title"),
                category = category,
                dueEpochDay = o.getLong("dueEpochDay"),
                cycle = enumOr(o.optString("cycle"), Cycle.NONE),
                autoRenew = o.optBoolean("autoRenew", false),
                amount = if (o.has("amount")) o.getDouble("amount") else null,
                currency = o.optString("currency", ""),
                notes = o.optString("notes", ""),
                reminderDays = o.optString("reminderDays", Item.DEFAULT_REMINDERS),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                kind = enumOr(o.optString("kind"), category.defaultKind),
                dueMinute = if (o.has("dueMinute")) o.getInt("dueMinute") else null,
                repeatEvery = o.optInt("repeatEvery", 1),
                repeatDays = o.optString("repeatDays", ""),
                repeatLimit = if (o.has("repeatLimit")) o.getInt("repeatLimit") else null,
                repeatUntilEpochDay = if (o.has("repeatUntilEpochDay")) o.getLong("repeatUntilEpochDay") else null,
                alertOffsets = o.optString("alertOffsets", ""),
                alertStyle = enumOr(o.optString("alertStyle"), AlertStyle.NOTIFICATION),
                nagEveryMin = o.optInt("nagEveryMin", 0),
                nagCount = o.optInt("nagCount", 0),
                done = o.optBoolean("done", false),
                doneAt = if (o.has("doneAt")) o.getLong("doneAt") else null,
                reference = o.optString("reference", ""),
                paymentMethod = o.optString("paymentMethod", ""),
                pinned = o.optBoolean("pinned", false),
                priority = o.optInt("priority", 0),
                checklist = o.optString("checklist", ""),
                repeatRule = o.optString("repeatRule", ""),
            )
        }
        val historyJson = root.optJSONArray("history") ?: JSONArray()
        val history = (0 until historyJson.length()).map { idx ->
            val o = historyJson.getJSONObject(idx)
            History(
                itemId = o.optLong("itemId", 0),
                itemTitle = o.optString("itemTitle", ""),
                category = enumOr(o.optString("category"), Category.OTHER),
                dueEpochDay = o.getLong("dueEpochDay"),
                paidAt = o.optLong("paidAt", System.currentTimeMillis()),
                amount = if (o.has("amount")) o.getDouble("amount") else null,
                currency = o.optString("currency", ""),
                kind = o.optString("kind", ""),
            )
        }
        return Parsed(items, history)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
