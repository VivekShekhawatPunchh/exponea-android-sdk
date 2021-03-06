package com.exponea.sdk.models

import android.app.NotificationManager
import com.exponea.sdk.exceptions.InvalidConfigurationException

data class ExponeaConfiguration(
    /** Default project token. */
    var projectToken: String = "",
    /** Map event types and projects to be send to Exponea API. */
    var projectRouteMap: Map<EventType, List<ExponeaProject>> = mapOf(),
    /** Authorization http header. */
    var authorization: String? = null,
    /** Base url for http requests to Exponea API. */
    var baseURL: String = Constants.Repository.baseURL,
    /** Level of HTTP logging, default value is BODY. */
    var httpLoggingLevel: HttpLoggingLevel = HttpLoggingLevel.BODY,
    /** Content type value to make http requests. */
    var contentType: String = Constants.Repository.contentType,
    /** Maximum retries value to flush data to api. */
    var maxTries: Int = 10,
    /** Timeout session value considered for app usage. */
    var sessionTimeout: Double = Constants.Session.defaultTimeout,
    /** Defines time to live of campaign click event in seconds considered for app usage. */
    var campaignTTL: Double = Constants.Campaign.defaultCampaignTTL,
    /** Flag to control automatic session tracking */
    var automaticSessionTracking: Boolean = Constants.Session.defaultAutomaticTracking,
    /** Flag to control if the App will handle push notifications automatically. */
    var automaticPushNotification: Boolean = Constants.PushNotif.defaultAutomaticListening,
    /** Icon to be showed in push notifications. */
    var pushIcon: Int? = null,
    /** Accent color of push notification icon and buttons */
    var pushAccentColor: Int? = null,
    /** Channel name for push notifications. Only for API level 26+. */
    var pushChannelName: String = "Exponea",
    /** Channel description for push notifications. Only for API level 26+. */
    var pushChannelDescription: String = "Notifications",
    /** Channel ID for push notifications. Only for API level 26+. */
    var pushChannelId: String = "0",
    /** Notification importance for the notification channel. Only for API level 26+. */
    var pushNotificationImportance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    /** A list of properties to be added to all tracking events */
    var defaultProperties: HashMap<String, Any> = hashMapOf(),
    /** How ofter the token is tracked */
    var tokenTrackFrequency: TokenFrequency = TokenFrequency.ON_TOKEN_CHANGE
) {

    val mainExponeaProject
        get() = ExponeaProject(baseURL, projectToken, authorization)

    enum class HttpLoggingLevel {
        /** No logs. */
        NONE,
        /** Logs request and response lines. */
        BASIC,
        /** Logs request and response lines and their respective headers. */
        HEADERS,
        /** Logs request and response lines and their respective headers and bodies (if present). */
        BODY
    }

    enum class TokenFrequency {
        /** Tracked on the first launch or if the token changes */
        ON_TOKEN_CHANGE,
        /** Tracked every time the app is launched */
        EVERY_LAUNCH,
        /** Tracked once on days where the user opens the app */
        DAILY
    }

    fun validate() {
        if (authorization?.startsWith("Basic ") == true) {
            throw InvalidConfigurationException("""
                Basic authentication is not supported by mobile SDK for security reasons.
                Use Token authentication instead.
                For more details see https://docs.exponea.com/reference#section-public-key
                """.trimIndent()
            )
        } else if (authorization?.startsWith("Token ") == false) {
            throw InvalidConfigurationException("""
                Use 'Token <access token>' as authorization for SDK.
                For more details see https://docs.exponea.com/reference#section-public-key
                """.trimIndent()
            )
        }
    }
}
