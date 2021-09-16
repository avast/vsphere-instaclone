package com.avast.teamcity.plugins.instaclone

import com.intellij.openapi.diagnostic.Logger
import com.vmware.vim25.*
import jetbrains.buildServer.clouds.CloudErrorInfo
import jetbrains.buildServer.clouds.CloudInstance
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.BuildAgentEx
import jetbrains.buildServer.serverSide.buildLog.BuildLog
import jetbrains.buildServer.serverSide.buildLog.MessageAttrs
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.Duration
import java.util.*
import java.util.regex.Pattern

class ICCloudInstance(
    private val vim: VimWrapper,
    private val startTime: Date,
    val uuid: String,
    private val image: ICCloudImage
) : CloudInstance {

    private var name: String = image.name
    private val profile = image.profile
    private val logger = Logger.getInstance(ICCloudInstance::class.java.name)

    override fun getInstanceId(): String {
        return uuid
    }

    override fun getName(): String {
        return name
    }

    override fun getImageId(): String {
        return image.id
    }

    override fun getStartedTime(): Date {
        return startTime
    }

    override fun getImage(): ICCloudImage {
        return image
    }

    override fun getNetworkIdentity(): String? {
        return null
    }

    override fun getStatus(): InstanceStatus {
        return status
    }

    override fun getErrorInfo(): CloudErrorInfo? {
        return errorInfo
    }

    override fun containsAgent(agent: AgentDescription): Boolean {
        return agent.configurationParameters["vsphere-instaclone.instance.uuid"] == uuid
    }

    private suspend fun waitForTask(task: ManagedObjectReference): Any? {
        while (true) {
            val info = vim.getProperty(task, "info") as TaskInfo
            when (info.state) {
                TaskInfoState.ERROR -> throw LocalizedMethodFaultException(info.error)
                TaskInfoState.SUCCESS -> return info.result
                else -> delay(1000)
            }
        }
    }

    private suspend fun powerOn(name: String, userData: CloudInstanceUserData): ManagedObjectReference {
        val extraConfig = mutableMapOf<String, String>().apply {
            val data = JSONObject().apply {
                val agentName = userData.agentName
                put("agentName", agentName.ifEmpty { name })
                put("authToken", userData.authToken)
                put("serverUrl", userData.serverAddress)
                put("configParams", JSONObject().apply {
                    put("vsphere-instaclone.instance.uuid", uuid)
                    for ((key, value) in userData.customAgentConfigurationParameters) {
                        put(key, value)
                    }
                })
            }

            put("guestinfo.teamcity-instance-startTime", startTime.time.toString())
            put("guestinfo.teamcity-instance-uuid", uuid)
            put("guestinfo.teamcity-profile-uuid", profile.uuid)
            put("guestinfo.teamcity-instance-config", data.toString())
            put("guestinfo.guest.hostname", name)
        }

        val imageTemplateMor = getImageTemplateMOR()

        val ethernetDevices = getEthernetDevices(imageTemplateMor)

        val cloneTask = vim.authenticated { port ->
            port.instantCloneTask(imageTemplateMor, VirtualMachineInstantCloneSpec().apply {
                this.name = name
                this.location = getLocationSpec(ethernetDevices)
                this.config.addAll(extraConfig.asOptionValueList())
            })
        }

        return waitForTask(cloneTask) as ManagedObjectReference
    }

    private fun getImageTemplateMOR(): ManagedObjectReference {
        if (!image.imageTemplate.endsWith(MULTIPLE_VALUE_SEPARATOR)) {
            val vm = vim.authenticated { vimPortType ->
                vimPortType.findByInventoryPath(vim.serviceContent.searchIndex, image.imageTemplate)
            }
            if (vm == null || vm.type != VimWrapper.VM_TYPE) {
                throw RuntimeException("Not a VM: ${image.imageTemplate}")
            }
            return vm
        } else {
            val foundVm = getMultipleVMFrozenImages(image.imageTemplate.substringAfterLast('/', ""), "").maxBy { it.cloneNumber }

            if (foundVm == null) {
                logger.info("No existing VM virtual machines found with template ${image.imageTemplate}")
                throw RuntimeException("Not found a VM for template ${image.imageTemplate}")
            }
            return foundVm.mor
        }
    }

    private fun getMultipleVMFrozenImages(coreTemplateName: String, suffixTemplateName: String): List<VM> {

        val virtualMachines = vim.getFolderVirtualMachines(image.instanceFolder)

        logger.info("Found virtual machines in instanceFolder : count - ${virtualMachines.count()}")

        val simpleTemplateName =
            coreTemplateName + suffixTemplateName + if (image.imageTemplate.endsWith(
                    MULTIPLE_VALUE_SEPARATOR
                )
            ) "" else MULTIPLE_VALUE_SEPARATOR
        logger.info("Searching for multiple images (ends with @) - simple name template value: $simpleTemplateName , template value ${image.imageTemplate}")

        val foundVm = virtualMachines.asSequence().map { vmMor ->
            VM(vmMor, vim.getNameProperty(vmMor), 0)
        }.onEach { vm ->
            logger.debug("Found VM in folder ${vm.name} for ${image.imageTemplate} (but not selected yet)")
        }.filter { vm ->
            logger.debug("Testing starts name ${vm.name} starts with $simpleTemplateName")
            vm.name.startsWith(simpleTemplateName)
        }.onEach { vm ->
            val matcher = INSTA_PROFILE_PATTERN.matcher(vm.name)
            logger.debug("Testing regex for name ${vm.name}")
            if (matcher.find()) {
                vm.cloneNumber = matcher.group("num").toLong()
            } else {
                logger.debug("Did not match regex pattern ${vm.name}")
            }
        }.filter { vm ->
            vm.cloneNumber > 0
        }.filter { foundVm ->
            val freezed = vim.getProperty(foundVm.mor, "runtime.instantCloneFrozen") as Boolean
            if (!freezed) {
                logger.debug("Ignoring ${foundVm.name} from multiple selection - it's not freezed")
            }
            freezed
        }.toList()

        logger.info(
            "All found multiple images for image template ${image.imageTemplate} - found VM - ${
                foundVm.joinToString()
            }"
        )

        return foundVm
    }

    private fun getEthernetDevices(imageTemplateMOR: ManagedObjectReference): List<VirtualEthernetCard> {
        val devices = profile.vim.getProperty(imageTemplateMOR, "config.hardware.device") as ArrayOfVirtualDevice

        return devices.virtualDevice.filterIsInstance(VirtualEthernetCard::class.java)
    }

    private fun getLocationSpec(ethernetDevices: List<VirtualEthernetCard>): VirtualMachineRelocateSpec {
        return VirtualMachineRelocateSpec().apply {
            folder = image.instanceFolder
            pool = image.resourcePool
            datastore = image.datastore

            // network to which the cloned machine's ethernet card should be connected
            for ((networkName, ethernetDevice) in image.networks.zip(ethernetDevices)) {

                val netMor = vim.authenticated {
                    it.findByInventoryPath(vim.serviceContent.searchIndex, networkName)
                }
                val netName = networkName.substringAfterLast('/')

                ethernetDevice.backing = when (netMor.type) {
                    "Network" -> VirtualEthernetCardNetworkBackingInfo().apply {
                        this.network = netMor
                        deviceName = netName
                        isUseAutoDetect = true
                    }

                    "DistributedVirtualPortgroup" -> VirtualEthernetCardDistributedVirtualPortBackingInfo().apply {
                        this.port = DistributedVirtualSwitchPortConnection().apply {
                            portgroupKey = vim.getProperty(netMor, "key") as String
                            val switchMor = vim.getProperty(
                                netMor,
                                "config.distributedVirtualSwitch"
                            ) as ManagedObjectReference
                            switchUuid = vim.getProperty(switchMor, "uuid") as String
                        }
                    }

                    else -> throw RuntimeException("Can't connect a ${netMor.type} to a network adapter")
                }

                deviceChange.add(VirtualDeviceConfigSpec().apply {
                    operation = VirtualDeviceConfigSpecOperation.EDIT
                    this.device = ethernetDevice
                })
            }
        }
    }

    private suspend fun powerOff(vmMor: ManagedObjectReference?) {
        if (vmMor == null)
            return

        val powerOffTask = vim.authenticated {
            it.powerOffVMTask(vmMor)
        }
        try {
            waitForTask(powerOffTask)
        } catch (e: Exception) {
            logger.info("Failed power off task, but it can be OK if it's already OFF", e)
        }

        val destroyTask = vim.authenticated {
            it.destroyTask(vmMor)
        }
        waitForTask(destroyTask)
    }

    private suspend fun rename(vmMor: ManagedObjectReference, oldName: String, newName: String) {
        logger.info("Renaming instaclone from $oldName to a new name $newName")
        val renameTask = vim.authenticated {
            it.reconfigVMTask(vmMor, VirtualMachineConfigSpec().apply {
                this.name = newName
            })
        }
        try {
            waitForTask(renameTask)
            logger.info("Renaming instaclone from $oldName to a new name $newName finished")
        } catch (e: Exception) {
            logger.error("WaitForTask Failed to do rename to $newName from $oldName")
        }
    }

    private fun getVmInstanceState(vm: ManagedObjectReference): String {
        return try {
            vim.getProperty(vm, "config.extraConfig[\"guestinfo.teamcity-instance-state\"].value") as String
        } catch (_: Exception) {
            ""
        }
    }

    fun terminate() {
        if (powerOffJob != null)
            return

        status = InstanceStatus.SCHEDULED_TO_STOP

        val agent = matchedAgentId?.let {
            profile.buildAgentManager.findAgentById<BuildAgentEx>(it, false)
        }
        logger.info("Cloud agent is terminating")
        agent?.setEnabled(false, null, "Cloud agent is terminating")

        powerOffJob = profile.coroScope.launch {
            logger.info("Launching powerOffJob")
            try {
                logger.info("PowerOffJob - Doing cancelAndJoin")
                powerOnJob?.cancelAndJoin()
            } catch (e: Exception) {
                logger.error("PowerOffJob - Failed to make cancelAndJoin", e)
            }

            status = InstanceStatus.STOPPING


            try {
                if (this@ICCloudInstance.vm != null) {
                    val vm = this@ICCloudInstance.vm!!
                    try {
                        val task = vim.authenticated {
                            it.reconfigVMTask(vm, VirtualMachineConfigSpec().apply {
                                extraConfig.add(OptionValue().apply {
                                    key = "guestinfo.teamcity-instance-control"
                                    value = "shutdown"
                                })
                            })
                        }
                        waitForTask(task)

                        var instanceState = getVmInstanceState(vm)
                        if (instanceState != "ready" && instanceState != "shutdown") {
                            try {
                                vim.authenticated { it.shutdownGuest(vm) }
                            } catch (_: Exception) {
                            }
                        }

                        val deadline = System.nanoTime() + image.shutdownTimeout.toNanos()
                        while (instanceState != "shutdown" && System.nanoTime() < deadline) {
                            val powerState = vim.getProperty(vm, "runtime.powerState") as VirtualMachinePowerState
                            if (powerState != VirtualMachinePowerState.POWERED_ON)
                                break

                            delay(1000)

                            instanceState = getVmInstanceState(vm)
                        }
                    } catch (_: Exception) {
                    }

                    powerOff(this@ICCloudInstance.vm)
                }
                status = InstanceStatus.STOPPED
                image.removeInstance(this@ICCloudInstance)
            } catch (e: Exception) {
                errorInfo = CloudErrorInfo(e.message ?: "", "", e)
                status = InstanceStatus.ERROR_CANNOT_STOP
            }
        }
    }

    override fun toString(): String {
        return "ICCloudInstance(uuid='$uuid', name='$name', status=$status)"
    }

    fun createClone(templateNameSuffix: String, buildLog: BuildLog): Job? {
        if (vm == null) {
            logger.warn("Cannot create instaClone, instance '$name' has not vm reference available")
            return null
        }
        if (status != InstanceStatus.RUNNING) {
            logger.warn("Cannot create instaClone, instance '$name' is not in the state RUNNING")
            return null
        }

//        return runBlocking {


        val job = profile.coroScopeClone.launch {
            try {
                doClone(templateNameSuffix, buildLog)
            } catch (e: Throwable) {
                logger.error("Failed to doClone", e)
                throw CancellationException("Failed to doClone", e)
            }
        }

        return job
//        }
    }

    private suspend fun doClone(templateNameSuffix: String, buildLog: BuildLog) {
        val templateNameSepSuffix =
            if (templateNameSuffix.isNotEmpty() && !templateNameSuffix.startsWith(TEMPLATENAME_SUFFIX_SEPARATOR)) {
                "$TEMPLATENAME_SUFFIX_SEPARATOR$templateNameSuffix"
            } else templateNameSuffix

        val instaCloneName =
            "${image.name}${templateNameSepSuffix}${MULTIPLE_VALUE_SEPARATOR}${System.currentTimeMillis()}"

        val tempInstaCloneName = "temp-$instaCloneName"
        logger.info("Creating instaClone task for cloudInstance $name/${image.imageTemplate} - VM ($tempInstaCloneName)")

        val cloneTask = vim.authenticated {
            if (status != InstanceStatus.RUNNING) {
                logger.error("Cannot create instaClone, instance '$name/${image.imageTemplate}' is not in the state RUNNING")
                throw RuntimeException("Cannot create instaClone, instance '$name/${image.imageTemplate}' is not in the state RUNNING")
            }
            try {
                logger.info("Making clone with name - $tempInstaCloneName")
                buildLog.info("Making clone with name - $tempInstaCloneName")

                // clean initial properties from previous values
                val extraConfig = mutableMapOf<String, String>().apply {
                    GUEST_INFO_CLEAR_PROPERTIES.forEach { propName -> put(propName, "") }
                }

                it.instantCloneTask(vm, VirtualMachineInstantCloneSpec().apply {
                    this.name = tempInstaCloneName
                    this.location = getLocationSpec(getEthernetDevices(vm!!))
                    this.config.addAll(extraConfig.asOptionValueList())
                })

            } catch (e: Exception) {
                logger.error("Failed to run instaClone Task ($tempInstaCloneName)", e)
                throw RuntimeException("Failed to run instaClone Task ($tempInstaCloneName)", e)
            }
        }

        try {
            val newVm = waitForTask(cloneTask) as ManagedObjectReference

            buildLog.info("Rebooting VM - $tempInstaCloneName")
            rebootVm(newVm, tempInstaCloneName)

            try {
                buildLog.info("Renaming VM - $tempInstaCloneName to $instaCloneName")
                rename(newVm, tempInstaCloneName, instaCloneName)
            } catch (e: Exception) {
                logger.error("Unexpected error during rename existing VM", e)
                throw RuntimeException("Failed to run rename ($tempInstaCloneName into $instaCloneName)", e)
            }

            val timeoutInSeconds = Duration.ofMillis(INITIAL_FREEZE_DELAY_MS)
                .plus(Duration.ofSeconds(MAX_FREEZE_DELAY_ADDITIONAL_TIMEOUT_SEC.toLong())).toSeconds()
            buildLog.info("Waiting for freeze VM - $instaCloneName - waiting max $timeoutInSeconds seconds")
            waitForFreeze(newVm, instaCloneName)

        } catch (e: Exception) {
            logger.error("Failed to run clone Task ($tempInstaCloneName)", e)
            throw RuntimeException("Failed to run clone Task ($tempInstaCloneName)", e)
        }

        try {
            buildLog.info("Searching and destroying old virtual machines")
            searchCleanAndDestroyVM(image.name, templateNameSepSuffix, buildLog)
        } catch (e: Exception) {
            logger.error("Unexpected error during Search&Destroy existing VM", e)
            throw e
        }

        logger.info("Creating instaclone VM (${instaCloneName}) for cloudInstance $name finished")
    }

    private suspend fun waitForFreeze(newVm: ManagedObjectReference, cloneName: String) {
        logger.info("Starting to wait for freeze VM - $cloneName")
        delay(INITIAL_FREEZE_DELAY_MS)
        var freezed = false
        for (i in 1..MAX_FREEZE_DELAY_ADDITIONAL_TIMEOUT_SEC) {
            val freezedVm = try {
                vim.getProperty(newVm, "runtime.instantCloneFrozen") as Boolean
            } catch (e: Exception) {
                throw RuntimeException("Couldn't get property runtime.instantCloneFrozen for VM $cloneName")
            }
            if (freezedVm) {
                freezed = true
                break
            } else {
                delay(1000) //wait
            }
        }
        if (!freezed) {
            throw RuntimeException("VM not freezed in timelimit - timeout waiting - $cloneName")
        } else {
            logger.info("Machine VM $cloneName is freezed succesfully and ready for use")
        }
    }

    private fun Map<String, String>.asOptionValueList(): List<OptionValue> {
        return this.map {
            OptionValue().apply {
                this.key = it.key
                this.value = it.value
            }
        }
    }

    private fun BuildLog.info(msg: String) {
        this.message(msg, Status.NORMAL, MessageAttrs.attrs())
        this.flush()
    }

    private suspend fun rebootVm(newVm: ManagedObjectReference, instaCloneName: String) {
        try {
            val rebootTask = vim.authenticated {
                try {
                    logger.info("Making reset of the new VM ($instaCloneName)")
                    it.resetVMTask(newVm)
                } catch (e: Exception) {
                    logger.error("Failed to reset VM ($instaCloneName)", e)
                    throw RuntimeException("Failed to reset VM ($instaCloneName)", e)
                }
            }

            logger.info("Waiting for reset result VM ($instaCloneName)")
            waitForTask(rebootTask)
            logger.info("Reset finished for VM ($instaCloneName)")

        } catch (e: Exception) {
            logger.error("Failed to process  resetTask for cloudInstance $name/${image.imageTemplate}", e)
            throw RuntimeException("Failed to process  resetTask for cloudInstance $name/${image.imageTemplate}", e)
        }
    }

    private suspend fun searchCleanAndDestroyVM(
        coreImageTemplateName: String,
        templateNameSuffix: String,
        buildLog: BuildLog
    ) {
        logger.info("Searching for existing VM virtual machines")
        val vms = getMultipleVMFrozenImages(coreImageTemplateName, templateNameSuffix).sortedByDescending { it.cloneNumber }
            .drop(LEAVE_MULTIPLE_IMAGES_COUNT)

        val ident = "$name/${image.imageTemplate}"
        vms.forEach { vm ->
            val instanceName = vm.name
            logger.info("Found existing VM virtual machine (${instanceName} - going to call destroy for cloudInstance $ident")
            try {
                val msg = "Destroying instaclone VM (${instanceName}) for cloudInstance $ident"
                logger.info(msg)
                buildLog.info(msg)
                powerOff(vm.mor)
            } catch (e: Exception) {
                logger.error(
                    "Failed to process powerOff&destroy Task for cloudInstance $ident and VM = $instanceName",
                    e
                )
                throw e
            }
        }
    }


    private var status: InstanceStatus = InstanceStatus.UNKNOWN
    private var errorInfo: CloudErrorInfo? = null
    private var powerOnJob: Job? = null
    private var powerOffJob: Job? = null

    var vm: ManagedObjectReference? = null

    var matchedAgentId: Int? = null

    companion object {
        private val GUEST_INFO_CLEAR_PROPERTIES = arrayOf(
            "guestinfo.teamcity-instance-startTime",
            "guestinfo.teamcity-instance-uuid",
            "guestinfo.teamcity-profile-uuid",
            "guestinfo.teamcity-instance-config",
            "guestinfo.guest.hostname"
        )
        private val DELAY_RESTART_DURATION = Duration.ofMinutes(1)
        private const val LEAVE_MULTIPLE_IMAGES_COUNT = 2
        private const val INITIAL_FREEZE_DELAY_MS = 20000L
        private const val MAX_FREEZE_DELAY_ADDITIONAL_TIMEOUT_SEC = 80

        const val MULTIPLE_VALUE_SEPARATOR = "@"
        const val TEMPLATENAME_SUFFIX_SEPARATOR = "-"
        private val INSTA_PROFILE_PATTERN = Pattern.compile(".+?$MULTIPLE_VALUE_SEPARATOR(?<num>\\d+)")

        @JvmStatic
        fun createFresh(vim: VimWrapper, image: ICCloudImage, userData: CloudInstanceUserData): ICCloudInstance {
            return ICCloudInstance(vim, Date(), UUID.randomUUID().toString(), image).apply {
                status = InstanceStatus.SCHEDULED_TO_START
                powerOnJob = profile.coroScope.launch {
                    try {
                        status = InstanceStatus.STARTING
                        while (vm == null) {
                            val allocatedName = image.allocateName()
                            try {
                                vm = powerOn(allocatedName, userData)
                                name = allocatedName
                            } catch (e: LocalizedMethodFaultException) {
                                if (e.fault.fault !is DuplicateName) {
                                    throw e
                                }
                            }
                        }
                        status = InstanceStatus.RUNNING
                    } catch (e: Exception) {
                        errorInfo = CloudErrorInfo(e.message ?: "", "", e)
                        status = InstanceStatus.ERROR
                    }
                }
            }
        }

        /**
         * Find existing running instance/machine to be shown in the TC UI (e.g. after TC restart)
         */
        fun createRunning(
            vim: VimWrapper, uuid: String, name: String, image: ICCloudImage, vm: ManagedObjectReference
        ): ICCloudInstance {
            val startTime =
                vim.getProperty(vm, "config.extraConfig[\"guestinfo.teamcity-instance-startTime\"].value") as String

            return ICCloudInstance(vim, Date(startTime.toLong()), uuid, image).apply {
                status = InstanceStatus.RUNNING
                this.name = name
                this.vm = vm
            }
        }
    }

}

data class VM(val mor: ManagedObjectReference, val name: String, var cloneNumber: Long) {
    override fun toString(): String = "VM:<$name>"
}

class LocalizedMethodFaultException(val fault: LocalizedMethodFault) : RuntimeException(fault.localizedMessage)

