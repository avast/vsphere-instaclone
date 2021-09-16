package com.avast.teamcity.plugins.instaclone.buildstep

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.RunBuildException
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.BuildProcess
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.messages.Status
import java.time.Duration
import java.util.*
import java.util.concurrent.*


class InstaCloneBuildProcess(
    private val agent: AgentRunningBuild,
    private val context: BuildRunnerContext,
    private val handlerBuildProcessManager: HandlerBuildProcessManager
) : BuildProcess, Callable<BuildFinishedStatus> {

    private var buildStatus: Future<BuildFinishedStatus>? = null
    private val confirmDoneStatus: CompletableFuture<BuildFinishedStatus> = CompletableFuture()

    private val executor = Executors.newSingleThreadExecutor()

    private val logger = Logger.getInstance(InstaCloneBuildProcess::class.java.name)

    private val uuid = UUID.randomUUID().toString()

    override fun start() {
        handlerBuildProcessManager.register(uuid, this)
        buildStatus = try {
            executor.submit<BuildFinishedStatus>(this)
        } catch (e: RejectedExecutionException) {
            logger.error("Failed to start", e)
            throw RunBuildException(e)
        }
    }

    override fun isInterrupted(): Boolean {
        return buildStatus!!.isCancelled && isFinished
    }

    override fun isFinished(): Boolean {
        return buildStatus!!.isDone
    }

    override fun interrupt() {
        logger.info("Interrupting...")
        handlerBuildProcessManager.removeBuildProcess(uuid)

        if (!confirmDoneStatus.isDone) {
            confirmDoneStatus.cancel(true)
        }
        buildStatus!!.cancel(true)
    }

    override fun waitFor(): BuildFinishedStatus {
        return try {
            val status = buildStatus!!.get(
                Duration.ofSeconds(getTimeout()).plus(Duration.ofMinutes(1)).toSeconds(),
                TimeUnit.SECONDS
            )
            logger.info("Build process was finished")
            status
        } catch (e: TimeoutException) {
            logger.error("Timeout on Instaclone buildprocess", e)
            throw RunBuildException("Timeout on Instaclone buildprocess", e)
        } catch (e: InterruptedException) {
            logger.error("InterruptedException on Instaclone buildprocess", e)
            throw RunBuildException(e)
        } catch (e: ExecutionException) {
            logger.error("ExecutionException on Instaclone buildprocess", e)
            throw RunBuildException(e)
        } catch (e: CancellationException) {
            logger.error("Build process was interrupted: ", e)
            BuildFinishedStatus.INTERRUPTED
        } finally {
            handlerBuildProcessManager.removeBuildProcess(uuid)
            executor.shutdown()
        }
    }

    private fun getTimeout(): Long {
        val timeout = context.runnerParameters[TEMPLATE_TIMEOUT_PARAMETER_NAME]?.toLong()
        if (timeout == null || timeout <= 0) {
            return Duration.ofMinutes(
                DEFAULT_MINUTES_TIMEOUT
            ).toSeconds()
        }
        return timeout
    }

    override fun call(): BuildFinishedStatus {
        try {
            val templateNameSuffix = context.runnerParameters[TEMPLATE_NAME_SUFFIX_PARAMETER_NAME]

            logger.info("TemplateName suffix value $templateNameSuffix")
            agent.buildLogger.message("Running InstacloneBuildProcess... with $TEMPLATE_NAME_SUFFIX_PARAMETER_NAME=$templateNameSuffix")
            agent.buildLogger.flush()

            val serviceMessage =
                "##teamcity[$MESSAGE_NAME $TEMPLATE_NAME_SUFFIX_PARAMETER_NAME='$templateNameSuffix' uuid='$uuid']"
            agent.buildLogger.message(serviceMessage)
            agent.buildLogger.flush()
//            agent.buildLogger.message("Going to sleep for few minutes, after that -> success + continue")
            agent.buildLogger.message(
                "InstaCloneBuildProcess - TC agent side - asked for new instaclone via ServiceMessage ${
                    serviceMessage.substring(
                        2
                    )
                }"
            )
            agent.buildLogger.message("InstaCloneBuildProcess - TC agent side - going to wait for response from TC Server")
            agent.buildLogger.flush()

            val result = confirmDoneStatus.get(getTimeout(), TimeUnit.SECONDS)
            agent.buildLogger.message("InstaCloneBuildProcess - TC agent side - Received info result with success - result = $result")
            agent.buildLogger.flush()

            return result
        } catch (e: Throwable) {
            logger.error(
                "InstaCloneBuildProcess - TC agent side - Failed to wait for confirm done status - " + e.message,
                e
            )
            agent.buildLogger.message(
                "InstaCloneBuildProcess - TC agent side - Failed to wait for confirm done status:" + e.message,
                Status.ERROR
            )
            agent.buildLogger.exception(e)
            agent.buildLogger.flush()
            logger.info("InstaCloneBuildProcess - buildFinishStatus failed - after build logger message info")
            return BuildFinishedStatus.FINISHED_FAILED
        }
    }

    fun updateStatus(status: String) {
        logger.info("Received notified status $status")
        if (status == "OK") {
            confirmDoneStatus.complete(BuildFinishedStatus.FINISHED_SUCCESS)
        } else {
            confirmDoneStatus.complete(BuildFinishedStatus.FINISHED_FAILED)
        }
    }

    companion object {
        const val MESSAGE_NAME = "createVCenterInstaClone"
        const val TEMPLATE_NAME_SUFFIX_PARAMETER_NAME = "templateNameSuffix"
        const val TEMPLATE_TIMEOUT_PARAMETER_NAME = "templateTimeout"
        const val DEFAULT_MINUTES_TIMEOUT = 3L
    }

}