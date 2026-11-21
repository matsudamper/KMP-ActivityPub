package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.kmp.activitypub.FakeRepositories

class DeliveryTargetLimitServiceTest {
    private val repositories = FakeRepositories()
    private val service = DeliveryTargetLimitService(
        settings = repositories.serverSettingsRepository,
        followers = repositories.followerRepository,
    )

    @Test
    fun limitIsTwentyUntilChanged() {
        assertEquals(20, service.maxPerHost())
    }

    @Test
    fun changedLimitIsReadBack() {
        assertEquals(UpdateDeliveryTargetLimitResult.Updated, service.updateMaxPerHost(35))

        assertEquals(35, service.maxPerHost())
    }

    @Test
    fun limitOutsideTheRangeIsNotSaved() {
        listOf(0, -1, DeliveryTargetLimitService.MAX_MAX_PER_HOST + 1).forEach { limit ->
            assertEquals(UpdateDeliveryTargetLimitResult.OutOfRange, service.updateMaxPerHost(limit))
        }

        assertEquals(20, service.maxPerHost())
        assertEquals(UpdateDeliveryTargetLimitResult.Updated, service.updateMaxPerHost(DeliveryTargetLimitService.MAX_MAX_PER_HOST))
        assertEquals(UpdateDeliveryTargetLimitResult.Updated, service.updateMaxPerHost(DeliveryTargetLimitService.MIN_MAX_PER_HOST))
    }
}
