package com.avast.teamcity.plugins.instaclone

import com.vmware.vim25.*
import jetbrains.buildServer.clouds.*
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.SBuildAgent
import kotlinx.coroutines.*
import org.json.JSONObject
import java.lang.RuntimeException
import java.util.*

class ICCloudInstance(
        private val vim: VimWrapper,
        private val startTime: Date,
        val uuid: String,
        private val image: ICCloudImage) : CloudInstance {

    private var name: String = image.name

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
            put("guestinfo.teamcity-profile-uuid", image.profile.uuid)
            put("guestinfo.teamcity-instance-config", data.toString())
        }

        val devices = image.profile.vim.getProperty(image.template, "config.hardware.device") as ArrayOfVirtualDevice
        val ethernetDevices = devices.virtualDevice.filterIsInstance(VirtualEthernetCard::class.java)

        val cloneTask = vim.authenticated {
            port.instantCloneTask(image.template, VirtualMachineInstantCloneSpec().apply {
                this.name = name
                this.location = VirtualMachineRelocateSpec().apply {
                    folder = image.instanceFolder

                    for ((networkName, ethernetDevice) in image.networks.zip(ethernetDevices)) {
                        val netMor = vim.authenticated {
                            vim.port.findByInventoryPath(vim.serviceContent.searchIndex, networkName)
                        }

                        val netName = networkName.substringAfterLast('/')

                        ethernetDevice.backing = VirtualEthernetCardNetworkBackingInfo().apply {
                            this.network = netMor
                            deviceName = netName
                            isUseAutoDetect = true
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

    private suspend fun powerOff() {
        if (vm == null)
            return

        val powerOffTask = vim.authenticated {
            port.powerOffVMTask(vm)
        }
        try {
            waitForTask(powerOffTask)
        } catch (e: Exception) {
        }

        val destroyTask = vim.authenticated {
            port.destroyTask(vm)
        }
        waitForTask(destroyTask)
    }

    fun terminate() {
        if (powerOffJob != null)
            return

        status = InstanceStatus.SCHEDULED_TO_STOP

        val agentId = matchedAgentId
        if (agentId != null) {
            val agent = image.profile.buildAgentManager.findAgentById<SBuildAgent>(agentId, false)
            agent?.apply {
                setEnabled(false, null, "Agent is terminating")
                setAuthorized(false, null, "Agent is terminating")
            }
        }

        powerOffJob = GlobalScope.launch {
            try {
                powerOnJob?.cancelAndJoin()
            } catch (e: Exception) {
            }

            try {
                status = InstanceStatus.STOPPING
                powerOff()
                status = InstanceStatus.STOPPED
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
                powerOnJob = GlobalScope.launch {
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
            val startTime = vim.authenticated {
                vim.getProperty(vm, "config.extraConfig[\"guestinfo.teamcity-instance-startTime\"].value") as String
            }

            return ICCloudInstance(vim, Date(startTime.toLong()), uuid, image).apply {
                status = InstanceStatus.RUNNING
                this.name = name
                this.vm = vm
            }
        }
    }
}

class LocalizedMethodFaultException(val fault: LocalizedMethodFault): RuntimeException(fault.localizedMessage)
