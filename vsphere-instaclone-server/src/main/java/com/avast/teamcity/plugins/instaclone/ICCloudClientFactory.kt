package com.avast.teamcity.plugins.instaclone

import com.vmware.vim25.VimPortType
import com.vmware.vim25.VimService
import jetbrains.buildServer.clouds.*
import jetbrains.buildServer.clouds.server.CloudEventAdapter
import jetbrains.buildServer.clouds.server.CloudEventDispatcher
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.util.*
import javax.xml.ws.BindingProvider

class ICCloudClientFactory(
        private val pluginDescriptor: PluginDescriptor,
        cloudRegistrar: CloudRegistrar,
        cloudEventDispatcher: CloudEventDispatcher,
        private val agentPoolManager: AgentPoolManager,
        private val buildAgentManager: BuildAgentManager) : CloudClientFactory {

    private val defaultImagesJson: String = try {
        IOUtils.toString(
                javaClass.getResourceAsStream("/samples/imageProfileConfig.json"),
                "UTF-8")
    } catch (e: IOException) {
        throw RuntimeException("Failed to get resource content", e)
    }

    private val vimService = VimService()

    init {
        cloudEventDispatcher.addListener(object : CloudEventAdapter() {
            override fun instanceAgentMatched(
                    profile: CloudProfile,
                    instance: CloudInstance,
                    agent: SBuildAgent) {
                if (instance is ICCloudInstance) {
                    instance.matchedAgentId = agent.id
                }
            }
        })

        cloudRegistrar.registerCloudFactory(this)
    }



    private fun getVimPort(sdkUrl: String?): VimPortType {
        val port = vimService.vimPort
        val requestContext = (port as BindingProvider).requestContext
        requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] = sdkUrl
        requestContext[BindingProvider.SESSION_MAINTAIN_PROPERTY] = true
        return port
    }

    override fun createNewClient(cloudState: CloudState,
                                 cloudClientParameters: CloudClientParameters): CloudClientEx {
        val profileUuid = cloudClientParameters.getParameter("vmwareInstacloneProfileUuid")!!
        val sdkUrl = cloudClientParameters.getParameter("vmwareInstacloneSdkUrl")
        val port = getVimPort(sdkUrl)
        val username = cloudClientParameters.getParameter("vmwareInstacloneUsername")!!
        val password = cloudClientParameters.getParameter("vmwareInstaclonePassword")!!
        val imageConfig = cloudClientParameters.getParameter("vmwareInstacloneImages")!!
        val vim = VimWrapper(port, username, password)
        return ICCloudClient(vim, buildAgentManager, agentPoolManager, profileUuid, imageConfig)
    }

    override fun getCloudCode(): String {
        return "vmic"
    }

    override fun getDisplayName(): String {
        return "VMware Instaclone"
    }

    override fun getEditProfileUrl(): String? {
        return pluginDescriptor.getPluginResourcesPath("vmware-instaclone-profile-settings.html")
    }

    override fun getInitialParameterValues(): Map<String, String> {
        val params = HashMap<String, String>()
        params["vmwareInstacloneImages"] = defaultImagesJson
        params["vmwareInstacloneProfileUuid"] = UUID.randomUUID().toString()
        return params
    }

    override fun getPropertiesProcessor(): PropertiesProcessor {
        return PropertiesProcessor { emptyList() }
    }

    override fun canBeAgentOfType(agentDescription: AgentDescription): Boolean {
        val config = agentDescription.configurationParameters
        return config.containsKey("vsphere-instaclone.instance.uuid")
    }

}