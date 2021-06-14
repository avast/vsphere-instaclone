package com.avast.teamcity.plugins.instaclone

import com.avast.teamcity.plugins.instaclone.web.service.VCenterAccountService
import jetbrains.buildServer.clouds.CloudRegistrar
import jetbrains.buildServer.clouds.server.CloudEventDispatcher
import jetbrains.buildServer.serverSide.BuildAgentManager
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class ICPlugin(
        private val pluginDescriptor: PluginDescriptor,
        private val cloudRegistrar: CloudRegistrar,
        private val cloudEventDispatcher: CloudEventDispatcher,
        private val agentPoolManager: AgentPoolManager,
        private val buildAgentManager: BuildAgentManager,
        private val vCenterAccountService: VCenterAccountService) : ApplicationContextAware {

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        val factory = ICCloudClientFactory(applicationContext.classLoader,
                pluginDescriptor, cloudEventDispatcher,
                agentPoolManager, buildAgentManager, vCenterAccountService)

        cloudRegistrar.registerCloudFactory(factory)
    }
}
