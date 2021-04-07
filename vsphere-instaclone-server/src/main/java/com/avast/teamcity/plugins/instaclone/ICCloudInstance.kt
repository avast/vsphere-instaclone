package com.avast.teamcity.plugins.instaclone

import com.vmware.vim25.*
import jetbrains.buildServer.clouds.*
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.BuildAgentEx
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.*

class ICCloudInstance(
        private val vim: VimWrapper,
        private val startTime: Date,
        val uuid: String,
        private val image: ICCloudImage) : CloudInstance {

    private var name: String = image.name
    private val profile = image.profile

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
        val extraConfig = HashMap<String, String>().apply {
            val data = JSONObject().apply {
                val agentName = userData.agentName
                put("agentName", if (agentName.isEmpty()) name else agentName)
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
        }

        val devices = profile.vim.getProperty(image.template, "config.hardware.device") as ArrayOfVirtualDevice?
            ?: throw RuntimeException("Can't list VM hardware for the template for $name")
        val ethernetDevices = devices.virtualDevice.filterIsInstance(VirtualEthernetCard::class.java)

        val cloneTask = vim.authenticated { port ->
            port.instantCloneTask(image.template, VirtualMachineInstantCloneSpec().apply {
                this.name = name
                this.location = VirtualMachineRelocateSpec().apply {
                    folder = image.instanceFolder

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
                                    val switchMor = vim.getProperty(netMor, "config.distributedVirtualSwitch") as ManagedObjectReference
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

                for ((key, value) in extraConfig) {
                    val option = OptionValue()
                    option.key = key
                    option.value = value
                    this.config.add(option)
                }
            })
        }

        return waitForTask(cloneTask) as ManagedObjectReference
    }

    private suspend fun suspendVm() {
        if (vm == null)
            return

        try {
            val suspendTask = vim.authenticated {
                it.suspendVMTask(vm)
            }
            waitForTask(suspendTask)
        } catch (e: Exception) {
        }
    }

    private suspend fun powerOff() {
        if (vm == null)
            return

        val powerOffTask = vim.authenticated {
            it.powerOffVMTask(vm)
        }
        try {
            waitForTask(powerOffTask)
        } catch (e: Exception) {
        }

        val destroyTask = vim.authenticated {
            it.destroyTask(vm)
        }
        waitForTask(destroyTask)
    }

    private fun getVmInstanceState(vm: ManagedObjectReference): String {
        return vim.getProperty(vm, "config.extraConfig[\"guestinfo.teamcity-instance-state\"].value") as String?
            ?: ""
    }

    fun terminate() {
        if (powerOffJob != null)
            return

        status = InstanceStatus.SCHEDULED_TO_STOP

        val agent = matchedAgentId?.let {
            profile.buildAgentManager.findAgentById<BuildAgentEx>(it, false)
        }
        agent?.setEnabled(false, null, "Cloud agent is terminating")

        powerOffJob = profile.coroScope.launch {
            try {
                powerOnJob?.cancelAndJoin()
            } catch (e: Exception) {
            }

            status = InstanceStatus.STOPPING

            try {
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

                if (image.suspendOnly)
                    suspendVm()
                else
                    powerOff()
                status = InstanceStatus.STOPPED
                image.removeInstance(this@ICCloudInstance)
            } catch (e: Exception) {
                errorInfo = CloudErrorInfo(e.message?: "", "", e)
                status = InstanceStatus.ERROR_CANNOT_STOP
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
                        errorInfo = CloudErrorInfo(e.message?: "", "", e)
                        status = InstanceStatus.ERROR
                    }
                }
            }
        }

        fun createRunning(vim: VimWrapper, uuid: String, name: String, image: ICCloudImage, vm: ManagedObjectReference): ICCloudInstance {
            val startTimeStr =
                vim.getProperty(vm, "config.extraConfig[\"guestinfo.teamcity-instance-startTime\"].value") as String?
            val startTime = Date(startTimeStr?.toLong() ?: System.currentTimeMillis())

            return ICCloudInstance(vim, startTime, uuid, image).apply {
                status = InstanceStatus.RUNNING
                this.name = name
                this.vm = vm
            }
        }
    }
}

class LocalizedMethodFaultException(val fault: LocalizedMethodFault): RuntimeException(fault.localizedMessage)
