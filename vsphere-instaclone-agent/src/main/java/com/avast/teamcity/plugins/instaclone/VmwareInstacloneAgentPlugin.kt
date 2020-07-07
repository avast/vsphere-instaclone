package com.avast.teamcity.plugins.instaclone

import jetbrains.buildServer.agent.*
import jetbrains.buildServer.util.EventDispatcher

class VmwareInstacloneAgentPlugin(
        lifeCycleEvents: EventDispatcher<AgentLifeCycleListener>,
        serverCommandsHandlersRegistry: ServerCommandsHandlersRegistry) {

    init {
        lifeCycleEvents.addListener(object : AgentLifeCycleAdapter() {
            override fun agentInitialized(agent: BuildAgent) {
                val runningPlugin = RunningAgentPlugin(agent)
                serverCommandsHandlersRegistry.registerCommandsHandler(
                        "vmware-instaclone-agent-service",
                        runningPlugin)
            }
        })
    }
}
