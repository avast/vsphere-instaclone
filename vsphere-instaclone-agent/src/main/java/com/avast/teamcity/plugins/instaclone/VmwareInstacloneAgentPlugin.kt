package com.avast.teamcity.plugins.instaclone

import com.intellij.openapi.diagnostic.Logger
import jdk.internal.agent.resources.agent
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.util.EventDispatcher

class VmwareInstacloneAgentPlugin(
        lifeCycleEvents: EventDispatcher<AgentLifeCycleListener>) {
    private val logger = Logger.getInstance(VmwareInstacloneAgentPlugin::class.java.name)

    init {
        lifeCycleEvents.addListener(object : AgentLifeCycleAdapter() {
            override fun pluginsLoaded() {
                logger.info("VmwareInstacloneAgentPlugin - plugins loaded " + agent.id)
            }

            override fun beforeAgentConfigurationLoaded(agent: BuildAgent) {
                super.beforeAgentConfigurationLoaded(agent)
                logger.info("VmwareInstacloneAgentPlugin - beforeAgentConfigurationLoaded " + agent.id)
            }

            override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
                super.afterAgentConfigurationLoaded(agent)
                logger.info("VmwareInstacloneAgentPlugin - afterAgentConfigurationLoaded " + agent.id)
            }

            override fun agentInitialized(agent: BuildAgent) {
                logger.info("VmwareInstacloneAgentPlugin - agentInitialized event, starting RunningAgentPlugin with freeze script")
                RunningAgentPlugin(agent)
            }
        })
    }
}
