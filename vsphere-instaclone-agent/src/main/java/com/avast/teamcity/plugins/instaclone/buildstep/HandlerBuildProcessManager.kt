package com.avast.teamcity.plugins.instaclone.buildstep

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.ServerCommandsHandlersRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * @author Vitasek L.
 */
class HandlerBuildProcessManager(serverCommandsHandlersRegistry: ServerCommandsHandlersRegistry) {
    private val logger = Logger.getInstance(HandlerBuildProcessManager::class.java.name)

    private val processMap = ConcurrentHashMap<String, InstaCloneBuildProcess>()

    init {
        logger.info("Registering serverCommands Handler")
        try {
            serverCommandsHandlersRegistry.registerCommandsHandler(
                InstacloneHandler.HANDLER,
                InstacloneHandlerImpl(processMap)
            )
        } catch (e: Exception) {
            logger.error("Failed to register serverCommands handler", e)
        }
    }

    fun register(uuid: String, instaCloneBuildProcess: InstaCloneBuildProcess) {
        processMap[uuid] = instaCloneBuildProcess
    }

    fun removeBuildProcess(uuid: String) {
        processMap.remove(uuid)
    }
}
