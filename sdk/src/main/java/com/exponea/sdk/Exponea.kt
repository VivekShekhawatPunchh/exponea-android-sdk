package com.exponea.sdk

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.work.Configuration
import androidx.work.WorkManager
import com.exponea.sdk.exceptions.InvalidConfigurationException
import com.exponea.sdk.manager.SessionManagerImpl
import com.exponea.sdk.models.BannerResult
import com.exponea.sdk.models.CampaignClickInfo
import com.exponea.sdk.models.Consent
import com.exponea.sdk.models.Constants
import com.exponea.sdk.models.CustomerIds
import com.exponea.sdk.models.DeviceProperties
import com.exponea.sdk.models.EventType
import com.exponea.sdk.models.ExponeaConfiguration
import com.exponea.sdk.models.ExportedEventType
import com.exponea.sdk.models.FetchError
import com.exponea.sdk.models.FlushMode
import com.exponea.sdk.models.FlushMode.APP_CLOSE
import com.exponea.sdk.models.FlushMode.IMMEDIATE
import com.exponea.sdk.models.FlushMode.MANUAL
import com.exponea.sdk.models.FlushMode.PERIOD
import com.exponea.sdk.models.FlushPeriod
import com.exponea.sdk.models.NotificationAction
import com.exponea.sdk.models.NotificationData
import com.exponea.sdk.models.PropertiesList
import com.exponea.sdk.models.PurchasedItem
import com.exponea.sdk.models.Result
import com.exponea.sdk.repository.ExponeaConfigRepository
import com.exponea.sdk.util.Logger
import com.exponea.sdk.util.addAppStateCallbacks
import com.exponea.sdk.util.currentTimeSeconds
import com.exponea.sdk.util.isDeeplinkIntent
import com.exponea.sdk.util.toDate
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.RemoteMessage
import io.paperdb.Paper
import java.util.*
import java.util.concurrent.TimeUnit

@SuppressLint("StaticFieldLeak")
object Exponea {

    private lateinit var context: Context
    private lateinit var configuration: ExponeaConfiguration
    lateinit var component: ExponeaComponent

    /**
     * Defines which mode the library should flush out events
     */
    var flushMode: FlushMode = IMMEDIATE
        set(value) {
            field = value
            onFlushModeChanged()
        }

    /**
     * Defines the period at which the library should flush events
     */
    var flushPeriod: FlushPeriod = FlushPeriod(60, TimeUnit.MINUTES)
        set(value) {
            field = value
            onFlushPeriodChanged()
        }

    /**
     * Defines session timeout considered for app usage
     */
    var sessionTimeout: Double
        get() = configuration.sessionTimeout
        set(value) {
            configuration.sessionTimeout = value
        }

    /**
     * Defines if automatic session tracking is enabled
     */
    var isAutomaticSessionTracking: Boolean
        get() = configuration.automaticSessionTracking
        set(value) {
            configuration.automaticSessionTracking = value
            startSessionTracking(value)
        }

    /**
     * Check if our library has been properly initialized
     */
    var isInitialized: Boolean = false
        internal set

    /**
     * Check if the push notification listener is set to automatically
     */
    var isAutoPushNotification: Boolean
        get() = configuration.automaticPushNotification
        set(value) {
            configuration.automaticPushNotification = value
        }

    /**
     * Indicate the frequency which Firebase token needs to be updated
     */
    val tokenTrackFrequency: ExponeaConfiguration.TokenFrequency?
        get() = configuration.tokenTrackFrequency

    /**
     * Whenever a notification with extra values is received, this callback is called
     * with the values as map
     *
     * If a previous data was received and no listener was attached to the callback,
     * that data i'll be dispatched as soon as a listener is attached
     */
    var notificationDataCallback: ((data: Map<String, String>) -> Unit)? = null
        set(value) {
            if (!isInitialized) {
                Logger.w(this, "SDK not initialized")
                return
            }
            field = value
            val storeData = component.pushNotificationRepository.getExtraData()
            if (storeData != null) {
                field?.invoke(storeData)
                component.pushNotificationRepository.clearExtraData()
            }
        }

    /**
     * Set which level the debugger should output log messages
     */
    var loggerLevel: Logger.Level
        get() = Logger.level
        set(value) {
            Logger.level = value
        }
    /**
     * Defines time to live of campaign click event in seconds considered for app usage
     */

    var campaignTTL: Double
        get() = configuration.campaignTTL
        set(value) {
            configuration.campaignTTL = value
        }

    /**
     * Use this method using a file as configuration. The SDK searches for a file called
     * "exponea_configuration.json" that must be inside the "assets" folder of your application
     */
    @Throws(InvalidConfigurationException::class)
    fun initFromFile(context: Context) {

        Paper.init(context)
        component = ExponeaComponent(ExponeaConfiguration(), context)

        // Try to parse our file
        val configuration = Exponea.component.fileManager.getConfigurationFromDefaultFile(context)

        // If our file isn't null then try initiating normally
        if (configuration != null) {
            init(context, configuration)
        } else {
            throw InvalidConfigurationException()
        }
    }

    fun init(context: Context, configuration: ExponeaConfiguration) {
        if (isInitialized) {
            Logger.w(this, "Exponea SDK is alrady initialized!")
            return
        }
        Logger.i(this, "Init")

        if (Looper.myLooper() == null)
            Looper.prepare()

        Paper.init(context)

        this.context = context
        this.configuration = configuration
        isInitialized = true
        ExponeaConfigRepository.set(context, configuration)
        FirebaseApp.initializeApp(context)
        initializeSdk()
    }

    /**
     * Update the informed properties to a specific customer.
     * All properties will be stored into database until it will be
     * flushed (send it to api).
     */

    fun identifyCustomer(customerIds: CustomerIds, properties: PropertiesList) {
        component.customerIdsRepository.set(customerIds)
        track(
            properties = properties.properties,
            type = EventType.TRACK_CUSTOMER
        )
    }

    /**
     * Track customer event add new events to a specific customer.
     * All events will be stored into database until it will be
     * flushed (send it to api).
     */

    fun trackEvent(
        properties: PropertiesList,
        timestamp: Double? = currentTimeSeconds(),
        eventType: String?
    ) {

        track(
            properties = properties.properties,
            timestamp = timestamp,
            eventType = eventType,
            type = EventType.TRACK_EVENT
        )
    }

    /**
     * Manually push all events to Exponea
     */

    fun flushData() {
        if (component.flushManager.isRunning) {
            Logger.w(this, "Cannot flush, Job service is already in progress")
            return
        }

        component.flushManager.flushData()
    }

    /**
     * Fetches banners web representation
     * @param onSuccess - success callback, when data is ready
     * @param onFailure - failure callback, in case of errors
     */
    fun getPersonalizationWebLayer(
        onSuccess: (Result<ArrayList<BannerResult>>) -> Unit,
        onFailure: (Result<FetchError>) -> Unit
    ) {
        // TODO map banners id's
        val customerIds = Exponea.component.customerIdsRepository.get()
        Exponea.component.personalizationManager.getWebLayer(
            customerIds = customerIds,
            projectToken = Exponea.configuration.projectToken,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * Fetch the list of your existing consent categories.
     * @param onSuccess - success callback, when data is ready
     * @param onFailure - failure callback, in case of errors
     */
    fun getConsents(
            onSuccess: (Result<ArrayList<Consent>>) -> Unit,
            onFailure: (Result<FetchError>) -> Unit
    ) {
        Exponea.component.fetchManager.fetchConsents(
                projectToken = Exponea.configuration.projectToken,
                onSuccess = onSuccess,
                onFailure = onFailure
        )
    }

    /**
     * Manually tracks session start
     * @param timestamp - determines session start time ( in seconds )
     */
    fun trackSessionStart(timestamp: Double = currentTimeSeconds()) {
        if (isAutomaticSessionTracking) {
            Logger.w(
                Exponea.component.sessionManager,
                "Can't manually track session, since automatic tracking is on "
            )
            return
        }
        component.sessionManager.trackSessionStart(timestamp)
    }

    /**
     * Manually tracks session end
     * @param timestamp - determines session end time ( in seconds )
     */
    fun trackSessionEnd(timestamp: Double = currentTimeSeconds()) {

        if (isAutomaticSessionTracking) {
            Logger.w(
                Exponea.component.sessionManager,
                "Can't manually track session, since automatic tracking is on "
            )
            return
        }

        component.sessionManager.trackSessionEnd(timestamp)
    }

    /**
     * Manually track FCM Token to Exponea API.
     */

    fun trackPushToken(fcmToken: String) {
        component.firebaseTokenRepository.set(fcmToken, System.currentTimeMillis())
        val properties = PropertiesList(hashMapOf("google_push_notification_id" to fcmToken))
        track(
                eventType = Constants.EventTypes.push,
                properties = properties.properties,
                type = EventType.PUSH_TOKEN
        )
    }

    /**
     * Manually track delivered push notification to Exponea API.
     */

    fun trackDeliveredPush(
        data: NotificationData? = null,
        timestamp: Double? = currentTimeSeconds()
    ) {
        val properties = PropertiesList(
            hashMapOf(
                "action_type" to "mobile notification",
                "status" to "delivered",
                "os_name" to "Android",
                "platform" to "Android"
            )
        )
        Logger.d(this, "Push dev: ${timestamp.toString()}")
        Logger.d(this, "Push dev time ${Date()}")
        Logger.d(this, "Push dev time ${timestamp?.toDate()}")
        data?.let {
            properties["campaign_id"] = it.campaignId
            properties["campaign_name"] = it.campaignName
            properties["action_id"] = it.actionId
        }
        track(
            eventType = Constants.EventTypes.push,
            properties = properties.properties,
            type = EventType.PUSH_DELIVERED,
            timestamp = timestamp
        )
    }

    /**
     * Manually track clicked push notification to Exponea API.
     */

    fun trackClickedPush(
        data: NotificationData? = null,
        actionData: NotificationAction? = null,
        timestamp: Double? = currentTimeSeconds()
    ) {
        val properties = PropertiesList(
            hashMapOf(
                "action_type" to "mobile notification",
                "status" to "clicked",
                "platform" to "Android")
        )

        // Copy notification action data
        actionData?.let { properties.properties.putAll(it.toHashMap()) }

        data?.let {
            properties["campaign_id"] = data.campaignId
            properties["campaign_name"] = data.campaignName
            properties["action_id"] = data.actionId
        }
        track(
            eventType = Constants.EventTypes.push,
            properties = properties.properties,
            type = EventType.PUSH_OPENED,
            timestamp = timestamp
        )
    }

    /**
     * Opens a WebView showing the personalized page with the
     * banners for a specific customer.
     */

    fun showBanners(customerIds: CustomerIds) {
        Exponea.component.personalizationManager.showBanner(
            projectToken = Exponea.configuration.projectToken,
            customerIds = customerIds
        )
    }

    /**
     * Tracks payment manually
     * @param purchasedItem - represents payment details.
     * @param timestamp - Time in timestamp format where the event was created. ( in seconds )
     */

    fun trackPaymentEvent(
        timestamp: Double = currentTimeSeconds(),
        purchasedItem: PurchasedItem
    ) {

        track(
            eventType = Constants.EventTypes.payment,
            timestamp = timestamp,
            properties = purchasedItem.toHashMap(),
            type = EventType.PAYMENT
        )
    }

    /**
     * Handles the notification payload from FirebaseMessagingService
     * @param message the RemoteMessage payload received from Firebase
     * @param manager the system notification manager instance
     * @param showNotification indicates if the SDK should display the notification or just track it
     */
    fun handleRemoteMessage(
            message: RemoteMessage?,
            manager: NotificationManager,
            showNotification: Boolean = true
    ) {
        if (!isInitialized) {
            Logger.e(this, "Exponea SDK was not initialized properly!")
            return
        }
        component.fcmManager.handleRemoteMessage(message, manager, showNotification)
    }

    // Private Helpers

    /**
     * Initialize and start all services and automatic configurations.
     */

    private fun initializeSdk() {

        // Start Network Manager
        this.component = ExponeaComponent(this.configuration, context)

        // WorkManager
        initWorkManager()

        // Alarm Manager Starter
        startService()

        // Track Install Event
        trackInstallEvent()

        // Track In-App purchase
        trackInAppPurchase(configuration.skuList)

        // Track Firebase Token
        trackFirebaseToken()

        // Initialize session observer
        component.preferences
                .setBoolean(
                        SessionManagerImpl.PREF_SESSION_AUTO_TRACK,
                        configuration.automaticSessionTracking
                )
        startSessionTracking(configuration.automaticSessionTracking)

        context.addAppStateCallbacks(
            onOpen = {
                Logger.i(this, "App is opened")
                if (flushMode == APP_CLOSE) {
                    flushMode = PERIOD
                }
            },
            onClosed = {
                Logger.i(this, "App is closed")
                if (flushMode == PERIOD) {
                    flushMode = APP_CLOSE
                    // Flush data when app is closing for flush mode periodic.
                    Exponea.component.flushManager.flushData()
                }
            }
        )
    }

    /**
     * Initialize the WorkManager instance
     */
    private fun initWorkManager() {
        try {
            WorkManager.initialize(context, Configuration.Builder().build())
        } catch (e: Exception) {
            Logger.i(this, "WorkManager already init, skipping")
        }
    }

    /**
     * Start the service when the flush period was changed.
     */

    private fun onFlushPeriodChanged() {
        Logger.d(this, "onFlushPeriodChanged: $flushPeriod")
        startService()
    }

    /**
     * Start or stop the service when the flush mode was changed.
     */

    private fun onFlushModeChanged() {
        Logger.d(this, "onFlushModeChanged: $flushMode")
        when (flushMode) {
            PERIOD -> startService()
            APP_CLOSE -> stopService()
            MANUAL -> stopService()
            IMMEDIATE -> stopService()
        }
    }

    /**
     * Starts the service.
     */

    private fun startService() {
        Logger.d(this, "startService")

        if (flushMode == MANUAL || flushMode == IMMEDIATE) {
            Logger.w(this, "Flush mode manual set -> Skipping job service")
            return
        }
        component.serviceManager.start(context)
    }

    /**
     * Stops the service.
     */

    private fun stopService() {
        Logger.d(this, "stopService")
        component.serviceManager.stop(context)
    }

    /**
     * Initializes session listener
     * @param enableSessionTracking - determines sdk tracking session's state
     */

    private fun startSessionTracking(enableSessionTracking: Boolean) {
        if (enableSessionTracking) {
            component.sessionManager.startSessionListener()
        } else {
            component.sessionManager.stopSessionListener()
        }
    }

    /**
     * Initializes payments listener
     */

    private fun trackInAppPurchase(skuList: List<String>) {
        if (this.configuration.automaticPaymentTracking) {
            // Add the observers when the automatic session tracking is true.
            this.component.iapManager.configure(skuList)
            this.component.iapManager.startObservingPayments()
        } else {
            // Remove the observers when the automatic session tracking is false.
            this.component.iapManager.stopObservingPayments()
        }
    }

    /**
     * Send the firebase token
     */
    private fun trackFirebaseToken() {
        if (isAutoPushNotification) {
            this.component.pushManager.trackFcmToken(component.firebaseTokenRepository.get())
        }
    }

    /**
     * Send a tracking event to Exponea
     */

    internal fun track(
        eventType: String? = null,
        timestamp: Double? = currentTimeSeconds(),
        properties: HashMap<String, Any> = hashMapOf(),
        type: EventType
    ) {

        if (!isInitialized) {
            Logger.e(this, "Exponea SDK was not initialized properly!")
            return
        }

        val customerIds = component.customerIdsRepository.get()

        val trackedProperties: HashMap<String, Any> = hashMapOf()
        trackedProperties.putAll(configuration.defaultProperties)
        trackedProperties.putAll(properties)

        val event = ExportedEventType(
            type = eventType,
            timestamp = timestamp,
            customerIds = customerIds.toHashMap(),
            properties = trackedProperties
        )

        component.eventManager.addEventToQueue(event, type)
    }

    /**
     * Installation event is fired only once for the whole lifetime of the app on one
     * device when the app is launched for the first time.
     */

    internal fun trackInstallEvent(
        campaign: String? = null,
        campaignId: String? = null,
        link: String? = null
    ) {

        if (component.deviceInitiatedRepository.get()) {
            return
        }

        val device = DeviceProperties(
            campaign = campaign,
            campaignId = campaignId,
            link = link,
            deviceType = component.deviceManager.getDeviceType()
        )

        track(
            eventType = Constants.EventTypes.installation,
            properties = device.toHashMap(),
            type = EventType.INSTALL
        )

        component.deviceInitiatedRepository.set(true)
    }

    fun anonymize() {
        if (!isInitialized) {
            Logger.e(this, "Exponea SDK was not initialized properly!")
            return
        }

        val firebaseToken = component.firebaseTokenRepository.get()

        component.pushManager.trackFcmToken(" ")
        component.campaignRepository.clear()
        component.anonymizeManager.anonymize()
        component.sessionManager.trackSessionStart(currentTimeSeconds())
        component.pushManager.trackFcmToken(firebaseToken)

    }

    /**
     * Tries to handle Intent from Activity. If Intent contains data as defined for Deeplinks,
     * given Uri is parsed, info is send to Campaign server and TRUE is returned. Otherwise FALSE
     * is returned.
     */
    fun handleCampaignIntent(intent: Intent?, appContext: Context): Boolean {
        if (!isInitialized) {
            val config = ExponeaConfigRepository.get(appContext)
            if (config == null) {
                Logger.e(this, "Cannot track campaign intent, unable to automatically initialize Exponea SDK!")
                return false
            }
            Logger.d(this, "Newly initiated")
            init(appContext, config)
        }
        if (!intent.isDeeplinkIntent()) {
            return false
        }
        val event = CampaignClickInfo(intent!!.data!!)
        if (!event.isValid()) {
            Logger.w(this, "Intent doesn't contain a valid Campaign info in Uri: ${intent.data}")
            return false
        }
        component.campaignRepository.set(event)
        track(
            eventType = Constants.EventTypes.push,
            properties = hashMapOf(
                    "timestamp" to event.createdAt,
                    "platform" to "Android",
                    "url" to event.completeUrl
            ),
            type = EventType.CAMPAIGN_CLICK
        )
        return true
    }

}

