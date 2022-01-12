package com.avast.teamcity.plugins.instaclone

import com.intellij.openapi.diagnostic.Logger
import com.vmware.vim25.ArrayOfOptionValue
import com.vmware.vim25.ManagedObjectReference
import jetbrains.buildServer.clouds.*
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.BuildAgentManager
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext


private val terminalStates = arrayOf(InstanceStatus.ERROR, InstanceStatus.ERROR_CANNOT_STOP, InstanceStatus.STOPPED)

class ICCloudClient(
    val vim: VimWrapper,
    val buildAgentManager: BuildAgentManager,
    agentPoolManager: AgentPoolManager,
    val uuid: String,
    imageConfigMap: Map<String, ICImageConfig>
) : CloudClientEx {

    val coroScope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    val coroScopeClone = CoroutineScope(Executors.newCachedThreadPool().asCoroutineDispatcher())

    private fun setupImage(image: ICCloudImage) {
        logger.info("Searching for existing running instances for image: id=${image.id} and template name = ${image.imageTemplate}")

        val virtualMachines = vim.getFolderVirtualMachines(image.instanceFolder)

        logger.info("Find and create running cloud instance for image: id=${image.id} and image template name = ${image.imageTemplate}")

        val existingInstances = virtualMachines.mapNotNull { vmMor ->

            val extraConfig = try {
                vim.getProperty(vmMor, "config.extraConfig") as ArrayOfOptionValue
            } catch (e: Exception) {
                logger.error("Failed to extract property `config.extraConfig` from the VM $vmMor", e)
                return@mapNotNull null
            }

            findAndCreateRunningInstance(vmMor, extraConfig, image)
        }

        existingInstances.forEach { instance -> instances[instance.uuid] = instance }

        images[image.id] = image

        logger.info("Found existing running instances for image: id=${image.id} and name = ${image.name} - $existingInstances")
    }

    private fun findAndCreateRunningInstance(
        vmMor: ManagedObjectReference,
        extraConfig: ArrayOfOptionValue,
        image: ICCloudImage
    ): ICCloudInstance? {
        val instanceUuid = extraConfig.optionValue.firstOrNull { it.key == "guestinfo.teamcity-instance-uuid" }
            ?.let { it.value as String? }

        return instanceUuid?.let { _ ->
            extraConfig.optionValue.firstOrNull { it.key == "guestinfo.teamcity-profile-uuid" && it.value == this.uuid }
        }?.let {
            val instanceName = vim.getNameProperty(vmMor)

            image.createRunningInstance(vim, instanceUuid, instanceName, vmMor)
        }
    }

    @Throws(QuotaException::class)
    override fun startNewInstance(
        cloudImage: CloudImage,
        cloudInstanceUserData: CloudInstanceUserData
    ): CloudInstance {
        logger.info("Start new cloud instance for image: id=${cloudImage.id} and name = ${cloudImage.name}")
        val image = cloudImage as ICCloudImage
        val instance = image.createFreshInstance(vim, cloudInstanceUserData)
        instances[instance.uuid] = instance
        return instance
    }

    override fun restartInstance(cloudInstance: CloudInstance) {
        throw RuntimeException("Restart is not supported yet")
    }

    override fun terminateInstance(cloudInstance: CloudInstance) {
        logger.info("Terminating cloud instance for image id=${cloudInstance.imageId} , cloud instance name = ${cloudInstance.name}, cloud instance id = ${cloudInstance.instanceId} ")
        val instance = cloudInstance as ICCloudInstance
        instance.terminate()
    }

    override fun dispose() {
        super.dispose()
        logger.info("Disposing cloud instance")
        try {
            logger.info("Shutting down coroutine contexts - coroScope")
            coroScope.coroutineContext.asExecutor().close()
        } catch (e: Exception) {
            logger.error(e)
        }
        try {
            logger.info("Shutting down coroutine contexts - coroScopeClone")
            coroScopeClone.coroutineContext.asExecutor().close()
        } catch (e: Exception) {
            logger.error(e)
        }
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

    private val images = mutableMapOf<String, ICCloudImage>()
    private val instances = mutableMapOf<String, ICCloudInstance>()

    private val logger = Logger.getInstance(ICCloudClient::class.java.name)

    init {
        logger.info("Creating new ICCloudClient with imageConfigJSON $imageConfigMap")

        imageConfigMap.forEach { (imageName, imageConfig) ->
            val imageTemplate = imageConfig.template ?: ""
            val maxInstances = imageConfig.maxInstances

            val sepIndex = imageTemplate.lastIndexOf('/')
            if (sepIndex == -1)
                throw RuntimeException("invalid template path: $imageTemplate")

            val imageInstanceFolder = imageConfig.instanceFolder ?: imageTemplate.substring(0, sepIndex)

            if (!imageTemplate.endsWith(ICCloudInstance.MULTIPLE_VALUE_SEPARATOR)) {
                val vm = vim.authenticated { vimPortType ->
                    vimPortType.findByInventoryPath(vim.serviceContent.searchIndex, imageTemplate)
                }
                if (vm == null || vm.type != VimWrapper.VM_TYPE)
                    throw RuntimeException("Not a VM: $imageTemplate")
            }

            val folder = vim.authenticated { vimPortType ->
                vimPortType.findByInventoryPath(vim.serviceContent.searchIndex, imageInstanceFolder)
            }

            if (folder == null || folder.type != "Folder")
                throw RuntimeException("Not a folder: $imageInstanceFolder")

            val resourcePool = imageConfig.resourcePool?.let { resourcePool ->
                vim.authenticated { vimPortType ->
                    vimPortType.findByInventoryPath(vim.serviceContent.searchIndex, resourcePool)
                }
            }

            if ((resourcePool == null || resourcePool.type != "ResourcePool") && imageConfig.resourcePool != null) {
                throw RuntimeException("ResourcePool not found: ${imageConfig.resourcePool}")
            }

            val datastore = imageConfig.resourcePool?.let { datastore ->
                vim.authenticated { vimPortType ->
                    vimPortType.findByInventoryPath(vim.serviceContent.searchIndex, datastore)
                }
            }

            if ((datastore == null || datastore.type != "Datastore") && imageConfig.datastore != null) {
                throw RuntimeException("DataStore not found: ${imageConfig.datastore}")
            }

            val agentPool = when (val value = imageConfig.agentPool) {
                is String -> agentPoolManager.allAgentPools.firstOrNull { it.name == value }?.agentPoolId
                is Number -> value.toInt()
                null -> null
                else -> throw RuntimeException("Invalid agentPool, must be either pool name or id")
            }

            val shutdownTimeout = Duration.ofSeconds(imageConfig.shutdownTimeout)

            val imageObject = ICCloudImage(
                imageName, imageName, folder, resourcePool, datastore, maxInstances,
                imageConfig.network, shutdownTimeout, agentPool, this, imageTemplate
            )
            setupImage(imageObject)
        }
    }

    private fun CoroutineContext.asExecutor(): ExecutorCoroutineDispatcher =
        (get(ContinuationInterceptor) as ExecutorCoroutineDispatcher)
}