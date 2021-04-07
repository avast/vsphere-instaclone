package com.avast.teamcity.plugins.instaclone

import com.vmware.vim25.*
import jetbrains.buildServer.clouds.*
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.BuildAgentManager
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors

private val terminalStates = arrayOf(InstanceStatus.ERROR, InstanceStatus.ERROR_CANNOT_STOP, InstanceStatus.STOPPED)

class ICCloudClient(
        val vim: VimWrapper,
        val buildAgentManager: BuildAgentManager,
        agentPoolManager: AgentPoolManager,
        val uuid: String,
        imageConfig: String) : CloudClientEx {

    val coroScope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    private fun setupImage(image: ICCloudImage) {
        val children = vim.getProperty(image.instanceFolder, "childEntity") as ArrayOfManagedObjectReference?
            ?: return

        for (mof in children.managedObjectReference) {
            if (mof.type != "VirtualMachine")
                continue

            var instanceUuid: String? = null
            var isOurInstance = false
            val extraConfig = vim.getProperty(mof, "config.extraConfig") as ArrayOfOptionValue?
                ?: continue

            for (optionValue in extraConfig.optionValue) {
                if (optionValue.key == "guestinfo.teamcity-instance-uuid") {
                    instanceUuid = optionValue.value as String
                }
                if (optionValue.key == "guestinfo.teamcity-profile-uuid" && optionValue.value == uuid) {
                    isOurInstance = true
                }
            }
            if (isOurInstance && instanceUuid != null) {
                val instanceName = vim.getProperty(mof, "name") as String

                val instance = image.createRunningInstance(vim, instanceUuid, instanceName, mof)
                instances[instance.uuid] = instance
            }
        }
        images[image.id] = image
    }

    @Throws(QuotaException::class)
    override fun startNewInstance(cloudImage: CloudImage,
                                  cloudInstanceUserData: CloudInstanceUserData): CloudInstance {
        val image = cloudImage as ICCloudImage
        val instance = image.createFreshInstance(vim, cloudInstanceUserData)
        instances[instance.uuid] = instance
        return instance
    }

    override fun restartInstance(cloudInstance: CloudInstance) {
        throw RuntimeException("Restart is not supported yet")
    }

    override fun terminateInstance(cloudInstance: CloudInstance) {
        val instance = cloudInstance as ICCloudInstance
        instance.terminate()
    }

    @Throws(CloudException::class)
    override fun findImageById(s: String): CloudImage? {
        return images[s]
    }

    override fun findInstanceByAgent(agentDescription: AgentDescription): CloudInstance? {
        val config = agentDescription.configurationParameters
        val instanceUuid = config["vsphere-instaclone.instance.uuid"] ?: return null
        return instances[instanceUuid]
    }

    @Throws(CloudException::class)
    override fun getImages(): Collection<CloudImage?> {
        return images.values
    }

    override fun getErrorInfo(): CloudErrorInfo? {
        return null
    }

    override fun generateAgentName(agentDescription: AgentDescription): String? {
        return null
    }

    override fun canStartNewInstanceWithDetails(image: CloudImage): CanStartNewInstanceResult {
        if (image !is ICCloudImage) {
            return CanStartNewInstanceResult.no("The image is not associated with this profile")
        }

        while (image.instances.size >= image.maxInstances) {
            val instanceToDrop = image.instances.firstOrNull { it.status in terminalStates }
                    ?: return CanStartNewInstanceResult.no(CanStartNewInstanceResult.DEFAULT_NEGATIVE_REASON)

            image.removeInstance(instanceToDrop)
        }

        return CanStartNewInstanceResult.yes()
    }

    private val images: MutableMap<String, ICCloudImage> = HashMap()
    private val instances: MutableMap<String, ICCloudInstance> = HashMap()

    init {
        val images = JSONObject(imageConfig)
        for (imageName in images.keys()) {
            val image = images.getJSONObject(imageName)
            val imageTemplate = image.getString("template")
            val maxInstances = image.optInt("maxInstances", Int.MAX_VALUE)

            val sepIndex = imageTemplate.lastIndexOf('/')
            if (sepIndex == -1)
                throw RuntimeException("invalid template path: $imageTemplate")

            val imageInstanceFolder = image.optString("instanceFolder") ?: imageTemplate.substring(0, sepIndex)

            val vm = vim.authenticated {
                it.findByInventoryPath(vim.serviceContent.searchIndex, imageTemplate)
            }
            if (vm == null || vm.type != "VirtualMachine")
                throw RuntimeException("Not a VM: $imageTemplate")

            val folder = vim.authenticated {
                it.findByInventoryPath(vim.serviceContent.searchIndex, imageInstanceFolder)
            }
            if (folder == null || folder.type != "Folder")
                throw RuntimeException("Not a folder: $imageInstanceFolder")

            val networks = ArrayList<String>()
            when (val network = image.opt("network")) {
                is String -> networks.add(network)
                is JSONArray -> network.forEach { networks.add(it as String) }
                null -> {
                }
                else -> throw RuntimeException("Invalid network configuration")
            }

            val agentPool = when (val value = image.opt("agentPool")) {
                is String -> agentPoolManager.allAgentPools.firstOrNull { it.name == value }?.agentPoolId
                is Int -> value
                JSONObject.NULL, null -> null
                else -> throw RuntimeException("Invalid agentPool, must be either pool name or id")
            }

            val shutdownTimeout
                    = Duration.ofSeconds(image.optInt("shutdownTimeout", 30).toLong())

            val suspendOnly = image.optBoolean("suspendOnly", false)

            val imageObject = ICCloudImage(imageName, imageName, vm, folder, maxInstances,
                    networks, shutdownTimeout, suspendOnly, agentPool, this)
            setupImage(imageObject)
        }
    }
}