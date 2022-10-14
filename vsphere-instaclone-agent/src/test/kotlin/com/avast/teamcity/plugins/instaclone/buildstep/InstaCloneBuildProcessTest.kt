package com.avast.teamcity.plugins.instaclone.buildstep

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.NullBuildProgressLogger
import jetbrains.buildServer.agent.ServerCommandsHandlersRegistry
import jetbrains.buildServer.util.ThreadUtil.sleep
import org.junit.Ignore
import org.junit.Test

/**
 *
 * @author Vitasek L.
 */
class InstaCloneBuildProcessTest {
    private val logger = Logger.getInstance(InstaCloneBuildProcessTest::class.java.name)

    @Test
    @Ignore
    fun start() {
        val instaCloneBuildProcess = InstaCloneBuildProcess(object : AgentRunningBuildAdapter() {
            override fun getBuildLogger(): BuildProgressLogger {
                return object: NullBuildProgressLogger() {
                    override fun message(message: String?) {
                        logger.info("$message")
                    }

                    override fun exception(th: Throwable?) {
                        logger.error(th)
                    }
                }
            }
        }, object : BuildRunnerContextAdapter() {
            override fun getRunnerParameters(): MutableMap<String, String> {
                return mutableMapOf(Pair(InstaCloneBuildProcess.TEMPLATE_NAME_SUFFIX_PARAMETER_NAME, "suffix"))
            }
        }, HandlerBuildProcessManager(ServerCommandsHandlersRegistry { _, _ -> }))


        instaCloneBuildProcess.start()
        Thread {
            sleep(3000)
            instaCloneBuildProcess.updateStatus("OK")
        }.start()
        val waitForResult = instaCloneBuildProcess.waitFor()
        println("waitForResult = $waitForResult")
    }
}