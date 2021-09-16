package com.avast.teamcity.plugins.instaclone

import com.avast.teamcity.plugins.instaclone.buildstep.InstacloneHandler
import jetbrains.buildServer.clouds.server.CloudAgentRelation
import jetbrains.buildServer.messages.BuildMessage1
import jetbrains.buildServer.messages.DefaultMessagesInfo
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTranslator
import jetbrains.buildServer.serverSide.BuildAgentEx
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.buildLog.MessageAttrs
import jetbrains.buildServer.serverSide.impl.RunningBuildState
import org.springframework.core.NestedExceptionUtils
import java.util.*

class CreateInstaCloneDetector(
    private val cloudAgentRelation: CloudAgentRelation,
    private val buildServer: SBuildServer
) : ServiceMessageTranslator {

    private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(CreateInstaCloneDetector::class.java.name)

    companion object {
        const val MESSAGE_NAME = "createVCenterInstaClone"
        const val TEMPLATE_NAME_SUFFIX_PARAMETER_NAME = "templateNameSuffix"
    }

    override fun translate(
        build: SRunningBuild, originalMessage: BuildMessage1,
        serviceMessage: ServiceMessage
    ): MutableList<BuildMessage1> {
        logger.info("Got ServiceMessage $MESSAGE_NAME for processing")
        val output = mutableListOf<BuildMessage1>()
        try {
            createClone(build, output, originalMessage, serviceMessage)
        } catch (e: Exception) {
            logger.error("Unexpected error - failed to create clone", e)
            output.add(originalMessage.copyWithError("Unexpected error - failed to create clone ${e.rootCauseMessage()}", e))
        }

        return output
    }

    private fun createClone(
        build: SRunningBuild,
        output: MutableList<BuildMessage1>,
        originalMessage: BuildMessage1,
        serviceMessage: ServiceMessage
    ) {

        cloudAgentRelation.findInstanceByAgent(build.agent)?.second?.takeIf { it is ICCloudInstance }?.let {
            val instance = it as ICCloudInstance
            logger.info("Creating VM clone for agent ${build.agent.name} and instance template name ${instance.image.imageTemplate}")
            val ident = "agent ${build.agent.name} and instance ${instance.image.imageTemplate}"

            output.add(originalMessage.copyWithTimeAndText("Creating VM clone in progress for $ident"))

            val templateName = serviceMessage.attributes[TEMPLATE_NAME_SUFFIX_PARAMETER_NAME]
                ?: throw RuntimeException("Template name parameter $TEMPLATE_NAME_SUFFIX_PARAMETER_NAME cannot be null")

            val createCloneJob = try {
                instance.createClone(templateName, build.buildLog)
            } catch (e: Exception) {
                logger.error(e)
                output.add(
                    originalMessage.copyWithError(
                        "Failed to VM clone for $ident - ${
                            NestedExceptionUtils.getMostSpecificCause(
                                e
                            ).message
                        }", e
                    )
                )
                reportStatus(e, build, serviceMessage, instance)
//                build.setInterrupted(
//                    RunningBuildState.INTERRUPTED_BY_SYSTEM,
//                    null,
//                    "Failed to VM clone for $ident - ${e.rootCauseMessage()}"
//                )
                return
            }
            createCloneJob?.let {
                output.add(originalMessage.copyWithText("Creating VM clone for $ident in progress...."))
            } ?: run {
                output.add(originalMessage.copyWithError("Failed to create VM clone for $ident"))
                reportStatus(RuntimeException("Failed to create VM clone for $ident"), build, serviceMessage, instance)
//                build.setInterrupted(
//                    RunningBuildState.INTERRUPTED_BY_SYSTEM,
//                    null,
//                    "Failed to create VM clone for $ident"
//                )
            }
            createCloneJob?.invokeOnCompletion { cause ->
                reportStatus(cause, build, serviceMessage, instance)
            }
            instance
        } ?: run {
            output.add(originalMessage.copyWithError("Cannot make instaclone - instance for creating clone not found - for agent ${build.agent.name}"))
        }
    }

    private fun reportStatus(
        cause: Throwable?,
        build: SRunningBuild,
        serviceMessage: ServiceMessage,
        instance: ICCloudInstance
    ) {
        if (cause != null) {
            logger.error("Failed Cloned Job", cause)
            build.buildLog.message(
                "Clone failed " + cause.rootCauseMessage(),
                Status.ERROR,
                MessageAttrs.attrs()
            )
        } else {
            build.buildLog.message("Clone success", Status.NORMAL, MessageAttrs.attrs())
        }
        val uuid = serviceMessage.attributes["uuid"]
        build.buildLog.message("Instaclone job completion - step uuid = $uuid", Status.NORMAL, MessageAttrs.attrs())

        logger.info("Instaclone job completion - step uuid = $uuid")
        try {
            sendUpdateStatusMessage(build, uuid, cause, instance)
        } catch (e: Exception) {
            logger.error(
                "CreateInstaCloneDetector - Failed to send update status message for agent ${build.agent.name} and instance template name ${instance.image.imageTemplate}, step uuid = $uuid",
                e
            )
            build.buildLog.message(
                "Error - CreateInstaCloneDetector - Failed to send update status message for agent ${build.agent.name} and instance template name ${instance.image.imageTemplate}, step uuid = $uuid - " + e.rootCauseMessage(),
                Status.ERROR,
                MessageAttrs.attrs()
            )
            try {
                build.setInterrupted(
                    RunningBuildState.INTERRUPTED_BY_SYSTEM,
                    null,
                    "Failed to create VM clone for agent ${build.agent.name} and instance template name ${instance.image.imageTemplate}, step uuid = $uuid"
                )
            } catch (e: Exception) {
                logger.error(
                    "CreateInstaCloneDetector - Failed to send build interrupted - for agent ${build.agent.name} and instance template name ${instance.image.imageTemplate}, step uuid = $uuid - " + e.rootCauseMessage(),
                    e
                )
                build.buildLog.message(
                    "CreateInstaCloneDetector - Failed to send build interrupted - for agent ${build.agent.name} and instance template name ${instance.image.imageTemplate}, step uuid = $uuid - " + e.rootCauseMessage(),
                    Status.ERROR,
                    MessageAttrs.attrs()
                )
            }

        }
    }

    private fun sendUpdateStatusMessage(
        build: SRunningBuild,
        uuid: String?,
        cause: Throwable?,
        instance: ICCloudInstance
    ) {
        val status = if (cause != null) "FAIL" else "OK"
        logger.info("CreateInstaCloneDetector - Sending update status message about instaclone process result for agent ${build.agent.name} and instance template name ${instance.image.imageTemplate} - status = $status")

        val handler: InstacloneHandler =
            (build.agent as BuildAgentEx).getRemoteInterface(InstacloneHandler::class.java)
        handler.updateStatus(uuid!!, status)
    }

    override fun getServiceMessageName(): String {
        return MESSAGE_NAME
    }


    private fun BuildMessage1.copyWithText(text: String): BuildMessage1 {
        return BuildMessage1(sourceId, typeId, Status.NORMAL, Date(), text)
    }

    private fun BuildMessage1.copyWithTimeAndText(text: String): BuildMessage1 {
        return BuildMessage1(sourceId, typeId, Status.NORMAL, timestamp, text)
    }

    private fun BuildMessage1.copyWithError(text: String, throwable: Throwable): BuildMessage1 {
        return DefaultMessagesInfo.createError(text, typeId, throwable)
    }

    private fun BuildMessage1.copyWithError(text: String): BuildMessage1 {
        return DefaultMessagesInfo.createBuildFailure(text)
    }

    private fun Throwable.rootCauseMessage(): String? {
        return NestedExceptionUtils.getMostSpecificCause(this).message
    }
}