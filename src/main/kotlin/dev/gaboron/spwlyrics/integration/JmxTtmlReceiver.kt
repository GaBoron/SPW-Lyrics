package dev.gaboron.spwlyrics.integration

import dev.gaboron.spwlyrics.application.NativeTtmlReceiver
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import javax.management.MBeanServer
import javax.management.MBeanServerDelegate
import javax.management.MBeanServerNotification
import javax.management.NotificationListener
import javax.management.ObjectName

/** Protocol 1 uses JDK types only, across the two plugins' isolated PF4J class loaders. */
internal class JmxTtmlReceiver(
    private val server: MBeanServer = ManagementFactory.getPlatformMBeanServer(),
) : NativeTtmlReceiver {
    private val generation = AtomicLong()
    private val listener = NotificationListener { notification, _ ->
        if (notification is MBeanServerNotification && notification.mBeanName == NAME) generation.incrementAndGet()
    }
    private var listening = false

    override fun state(): Any? {
        if (!listening) {
            server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, null, null)
            listening = true
        }
        return if (server.isRegistered(NAME) && server.getAttribute(NAME, "ProtocolVersion") == 1 &&
            server.getAttribute(NAME, "Active") == true) generation.get() else null
    }

    override fun submit(query: TrackQuery, rawTtml: String, offsetMs: Long): Boolean =
        query.path.isNotBlank() && state() != null && server.invoke(
            NAME, "submitTtml", arrayOf<Any>(query.trackId, query.path, rawTtml, offsetMs),
            arrayOf("java.lang.String", "java.lang.String", "java.lang.String", "long"),
        ) == true

    override fun clear(query: TrackQuery) {
        if (query.path.isNotBlank() && state() != null) server.invoke(
            NAME, "clearTtml", arrayOf(query.trackId, query.path),
            arrayOf("java.lang.String", "java.lang.String"),
        )
    }

    override fun close() {
        if (listening) server.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener)
        listening = false
    }

    private companion object {
        val NAME = ObjectName("io.github.gaboron.amllspw:type=TtmlReceiver,version=1")
    }
}
