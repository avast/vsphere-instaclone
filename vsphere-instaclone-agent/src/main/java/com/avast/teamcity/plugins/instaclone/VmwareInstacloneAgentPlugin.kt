package com.avast.teamcity.plugins.instaclone

import jetbrains.buildServer.agent.*
import jetbrains.buildServer.util.EventDispatcher

class VmwareInstacloneAgentPlugin(
        lifeCycleEvents: EventDispatcher<AgentLifeCycleListener>) {

    init {
        lifeCycleEvents.addListener(object : AgentLifeCycleAdapter() {
            override fun agentInitialized(agent: BuildAgent) {
                RunningAgentPlugin(agent)
            }
        })
    }
}
