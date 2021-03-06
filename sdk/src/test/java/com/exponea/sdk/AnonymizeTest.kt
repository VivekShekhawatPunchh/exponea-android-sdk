package com.exponea.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.exponea.sdk.models.Constants
import com.exponea.sdk.models.DatabaseStorageObject
import com.exponea.sdk.models.ExponeaConfiguration
import com.exponea.sdk.models.ExponeaProject
import com.exponea.sdk.models.ExportedEventType
import com.exponea.sdk.models.FlushMode
import com.exponea.sdk.models.PropertiesList
import com.exponea.sdk.testutil.ExponeaSDKTest
import com.exponea.sdk.testutil.componentForTesting
import com.exponea.sdk.util.currentTimeSeconds
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class AnonymizeTest : ExponeaSDKTest() {
    companion object {
        private const val PUSH_KEY = "google_push_notification_id"
    }

    private fun checkEvent(
        event: DatabaseStorageObject<ExportedEventType>,
        expectedEventType: String?,
        expectedProject: ExponeaProject,
        expectedUserId: String,
        expectedProperties: HashMap<String, Any>? = null
    ) {
        assertEquals(expectedEventType, event.item.type)
        assertEquals(expectedProject, event.exponeaProject)
        assertEquals(hashMapOf("cookie" to expectedUserId) as HashMap<String, String?>, event.item.customerIds)
        if (expectedProperties != null) assertEquals(expectedProperties, event.item.properties)
    }

    @Test
    fun `should anonymize sdk and switch projects`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initialProject = ExponeaProject("https://base-url.com", "project_token", "Token auth")
        Exponea.flushMode = FlushMode.MANUAL
        Exponea.init(context, ExponeaConfiguration(
            baseURL = initialProject.baseUrl,
            projectToken = initialProject.projectToken,
            authorization = initialProject.authorization)
        )
        val testFirebaseToken = "push_token"
        val userId = Exponea.componentForTesting.customerIdsRepository.get().cookie

        Exponea.trackEvent(
                eventType = "test",
                properties = PropertiesList(hashMapOf("name" to "test")),
                timestamp = currentTimeSeconds()
        )
        Exponea.trackPushToken(testFirebaseToken)

        val newProject = ExponeaProject("https://other-base-url.com", "new_project_token", "Token other-auth")
        Exponea.anonymize(exponeaProject = newProject)
        Exponea.trackEvent(
            eventType = "test",
            properties = PropertiesList(hashMapOf("name" to "test")),
            timestamp = currentTimeSeconds()
        )

        val newUserId = Exponea.componentForTesting.customerIdsRepository.get().cookie

        val events = Exponea.componentForTesting.eventRepository.all()
        events.sortBy { it.item.timestamp }
        assertEquals(events.size, 8)
        checkEvent(events[0], Constants.EventTypes.installation, initialProject, userId!!, null)
        checkEvent(events[1], "test", initialProject, userId!!, hashMapOf("name" to "test"))
        checkEvent(events[2], Constants.EventTypes.push, initialProject, userId!!, hashMapOf(PUSH_KEY to "push_token"))
        // anonymize is called. We clear push token in old user and track initial events for new user
        checkEvent(events[3], Constants.EventTypes.push, initialProject, userId!!, hashMapOf(PUSH_KEY to " "))
        checkEvent(events[4], Constants.EventTypes.installation, newProject, newUserId!!, null)
        checkEvent(events[5], Constants.EventTypes.sessionStart, newProject, newUserId!!, null)
        checkEvent(events[6], Constants.EventTypes.push, newProject, newUserId!!, hashMapOf(PUSH_KEY to "push_token"))
        checkEvent(events[7], "test", newProject, newUserId!!, hashMapOf("name" to "test"))
    }
}
