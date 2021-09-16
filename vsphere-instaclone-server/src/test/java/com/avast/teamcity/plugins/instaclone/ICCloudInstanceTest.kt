package com.avast.teamcity.plugins.instaclone

import jetbrains.buildServer.util.ThreadUtil.sleep
import kotlinx.coroutines.*
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.Executors

/**
 *
 * @author Vitasek L.
 */
class ICCloudInstanceTest {
    val supervisor = SupervisorJob()
    private val coroScopeClone = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher() + supervisor)

    @Test
    @Ignore
    fun testCourutine() {
        val async1 = coroScopeClone.launch {
            println("starting1")
            sleep(5000)
            throw RuntimeException("AAAAAAAAA")
        }
        async1.invokeOnCompletion { cause ->
            println("async1" + cause)
        }

        val async2 = coroScopeClone.launch {
            println("starting2")
            sleep(2000)
            println("async2 OK")
        }
        async2.invokeOnCompletion { cause ->
            println("async2 " + cause)
        }

        Thread.sleep(10000)
    }

    @Test
    @Ignore
    fun testCo() {

        try {
            testblock()
        } catch (e: Exception) {

        }
        try {
            testblock()
        } catch (e: Exception) {
        }

    }

    private fun testblock() {
        runBlocking {
            val job = coroScopeClone.launch {
                println(" i am here")
                RuntimeException("throwing error")
            }
            job.join()
        }
    }
}
