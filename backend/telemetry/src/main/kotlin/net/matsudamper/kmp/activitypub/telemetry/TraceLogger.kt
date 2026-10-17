package net.matsudamper.kmp.activitypub.telemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.slf4j.LoggerFactory

interface TraceLogger {
    fun noticeThrowable(
        e: Throwable,
        isError: Boolean,
    )

    fun setAttribute(
        key: String,
        value: String,
    )

    fun noticeInfo(message: Any)

    companion object {
        fun impl(): TraceLogger = OpenTracerRepository()
    }
}

internal class OpenTracerRepository : TraceLogger {
    private val span: Span get() = Span.current()

    override fun noticeThrowable(
        e: Throwable,
        isError: Boolean,
    ) {
        if (isError) {
            span.setStatus(StatusCode.ERROR)
            logger
                .atError()
                .setCause(e)
                .addKeyValue("export", false)
                .log(e.message ?: e::class.java.name)
        }
        span.recordException(e)
        span.setAttribute("exception.${System.nanoTime()}", e.message.orEmpty())
    }

    override fun noticeInfo(message: Any) {
        span.setAttribute("info", message.toString())
    }

    override fun setAttribute(
        key: String,
        value: String,
    ) {
        span.setAttribute(key, value)
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(OpenTracerRepository::class.java)
    }
}
