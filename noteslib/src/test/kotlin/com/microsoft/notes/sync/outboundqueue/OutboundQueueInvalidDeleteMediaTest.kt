package com.microsoft.notes.sync.outboundqueue

import com.microsoft.notes.sync.ApiRequestOperation.InvalidApiRequestOperation.InvalidDeleteMedia
import com.microsoft.notes.sync.FeedFRESetup
import org.junit.Assert
import org.junit.Test
import org.hamcrest.CoreMatchers.`is` as iz

class OutboundQueueInvalidDeleteMediaTest : FeedFRESetup() {
    private val operation = InvalidDeleteMedia("", "")

    @Test
    fun `should not be worked off`() {
        var called = false
        val outboundQueue = createQueue(
            MockOperationHandler(),
            eventHandler = { called = true },
            isDebugMode = true, context = mockContext
        )

        outboundQueue.push(operation, workImmediately = false)
        outboundQueue.work().get()
        outboundQueue.work().get()
        outboundQueue.work().get()

        Assert.assertFalse(called)
        Assert.assertThat(outboundQueue.count, iz(1))
    }
}
