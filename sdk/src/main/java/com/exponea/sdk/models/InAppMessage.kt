package com.exponea.sdk.models

import com.exponea.sdk.models.eventfilter.EventFilter
import com.exponea.sdk.models.eventfilter.EventFilterEvent
import com.exponea.sdk.util.Logger
import com.google.gson.annotations.SerializedName
import java.util.Date

internal data class InAppMessage(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("message_type")
    val rawMessageType: String,
    @SerializedName("frequency")
    val rawFrequency: String,
    @SerializedName("payload")
    val payload: InAppMessagePayload,
    @SerializedName("variant_id")
    val variantId: Int,
    @SerializedName("variant_name")
    val variantName: String,
    @SerializedName("trigger")
    val trigger: EventFilter?,
    @SerializedName("date_filter")
    val dateFilter: DateFilter,
    @SerializedName("load_priority")
    val priority: Int?
) {
    val frequency: InAppMessageFrequency?
        get() {
            return try {
                InAppMessageFrequency.valueOf(rawFrequency.toUpperCase())
            } catch (e: Throwable) {
                Logger.e(this, "Unknown in-app-message frequency $rawFrequency. $e")
                null
            }
        }

    val messageType: InAppMessageType
        get() {
            return try {
                InAppMessageType.valueOf(rawMessageType.toUpperCase())
            } catch (e: Throwable) {
                Logger.e(this, "Unknown in-app-message type $rawMessageType. $e")
                InAppMessageType.MODAL
            }
        }

    fun applyDateFilter(currentTimeSeconds: Long): Boolean {
        if (!dateFilter.enabled) {
            return true
        }
        if (dateFilter.fromDate != null && dateFilter.fromDate > currentTimeSeconds) {
            return false
        }
        if (dateFilter.toDate != null && dateFilter.toDate < currentTimeSeconds) {
            return false
        }
        return true
    }

    fun applyEventFilter(eventType: String, properties: Map<String, Any?>, timestamp: Double?): Boolean {
        return try {
            trigger?.passes(EventFilterEvent(eventType, properties, timestamp)) ?: false
        } catch (e: Throwable) {
            Logger.e(this, "Applying event filter failed. $e")
            false
        }
    }

    fun applyFrequencyFilter(displayState: InAppMessageDisplayState, sessionStartDate: Date): Boolean {
        return when (frequency) {
            InAppMessageFrequency.ALWAYS ->
                true
            InAppMessageFrequency.ONLY_ONCE ->
                displayState.displayed == null
            InAppMessageFrequency.ONCE_PER_VISIT ->
                displayState.displayed == null || displayState.displayed.before(sessionStartDate)
            InAppMessageFrequency.UNTIL_VISITOR_INTERACTS ->
                displayState.interacted == null
            else -> true
        }
    }
}

enum class InAppMessageFrequency(val value: String) {
    ALWAYS("always"),
    ONLY_ONCE("only_once"),
    ONCE_PER_VISIT("once_per_visit"),
    UNTIL_VISITOR_INTERACTS("until_visitor_interacts")
}
