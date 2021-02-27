package com.example.osgi.testing

import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.SandboxRuntimeContext
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.messages.Severity
import net.corda.djvm.messages.Severity.WARNING
import net.corda.djvm.rewiring.ExternalCache
import net.corda.djvm.source.BootstrapClassLoader
import net.corda.djvm.source.UserPathSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.fail
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.concurrent.thread

@Suppress("unused")
abstract class TestBase {
    companion object {
        private val threadId = AtomicInteger(0)

        @JvmField
        val DETERMINISTIC_RT: Path = Paths.get(
            System.getProperty("deterministic-rt.path") ?: fail("deterministic-rt.path property not set"))

        @JvmField
        val TESTING_LIBRARIES: List<Path> = (System.getProperty("sandbox-libraries.path") ?: fail("sandbox-libraries.path property not set"))
            .split(File.pathSeparator).map { Paths.get(it) }.filter { Files.exists(it) }

        private lateinit var bootstrapSource: BootstrapClassLoader
        private lateinit var parentConfiguration: SandboxConfiguration

        @BeforeAll
        @JvmStatic
        fun setupClassLoader() {
            bootstrapSource = BootstrapClassLoader(DETERMINISTIC_RT)
            val rootConfiguration = AnalysisConfiguration.createRoot(
                userSource = UserPathSource(emptyList()),
                bootstrapSource = bootstrapSource
            )
            parentConfiguration = SandboxConfiguration.createFor(
                analysisConfiguration = rootConfiguration,
                profile = null
            )
        }

        @AfterAll
        @JvmStatic
        fun destroyRootContext() {
            bootstrapSource.close()
        }
    }

    fun sandbox(action: SandboxRuntimeContext.() -> Unit) {
        sandbox(Consumer(action))
    }

    fun sandbox(action: Consumer<SandboxRuntimeContext>) {
        sandbox(WARNING, emptySet(), null, action)
    }

    fun sandbox(externalCache: ExternalCache, action: SandboxRuntimeContext.() -> Unit) {
        sandbox(externalCache, Consumer(action))
    }

    fun sandbox(externalCache: ExternalCache, action: Consumer<SandboxRuntimeContext>) {
        sandbox(WARNING, emptySet(), externalCache, action)
    }

    fun sandbox(visibleAnnotations: Set<Class<out Annotation>>, action: SandboxRuntimeContext.() -> Unit) {
        sandbox(visibleAnnotations, Consumer(action))
    }

    fun sandbox(visibleAnnotations: Set<Class<out Annotation>>, action: Consumer<SandboxRuntimeContext>) {
        sandbox(WARNING, visibleAnnotations, null, action)
    }

    fun sandbox(
        minimumSeverityLevel: Severity,
        visibleAnnotations: Set<Class<out Annotation>>,
        externalCache: ExternalCache?,
        action: Consumer<SandboxRuntimeContext>
    ) {
        var thrownException: Throwable? = null
        thread(start = false, name = "DJVM-${javaClass.name}-${threadId.getAndIncrement()}") {
            UserPathSource(TESTING_LIBRARIES).use { userSource ->
                SandboxRuntimeContext(parentConfiguration.createChild(userSource, Consumer {
                    it.setMinimumSeverityLevel(minimumSeverityLevel)
                    it.setVisibleAnnotations(visibleAnnotations)
                    it.setExternalCache(externalCache)
                })).use(action)
            }
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, ex ->
                thrownException = ex
            }
            start()
            join()
        }
        throw thrownException ?: return
    }
}