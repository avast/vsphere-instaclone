package com.avast.teamcity.plugins.instaclone

import com.intellij.openapi.diagnostic.Logger
import com.vmware.vim25.ManagedObjectReference
import jetbrains.buildServer.clouds.CloudErrorInfo
import jetbrains.buildServer.clouds.CloudImage
import jetbrains.buildServer.clouds.CloudInstance
import jetbrains.buildServer.clouds.CloudInstanceUserData
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class ICCloudImage(
    private val id: String,
    private val name: String,
    val instanceFolder: ManagedObjectReference,
    val resourcePool: ManagedObjectReference?,
    val datastore: ManagedObjectReference?,
    val maxInstances: Int,
    val networks: List<String>,
    val shutdownTimeout: Duration,
    private val agentPool: Int?,
    val profile: ICCloudClient,
    val imageTemplate: String
) : CloudImage {

    private var instanceCounter = 0
    // key = instance uuid
    private val instances = ConcurrentHashMap<String, ICCloudInstance>()
    private val logger = Logger.getInstance(ICCloudImage::class.java.name)

    fun allocateName(): String {
        return "$name-${instanceCounter++}"
    }

    fun createFreshInstance(vim: VimWrapper, userData: CloudInstanceUserData): ICCloudInstance {
        logger.info("Creating new fresh cloud instance for image: id = $id and name = $name")

        val instance = ICCloudInstance.createFresh(vim, this, userData)
        instances[instance.uuid] = instance
        return instance
    }

    fun createRunningInstance(vim: VimWrapper, uuid: String, name: String, vm: ManagedObjectReference): ICCloudInstance {
        val instance = ICCloudInstance.createRunning(vim, uuid, name, this, vm)
        instances[uuid] = instance
        return instance
    }

    override fun getErrorInfo(): CloudErrorInfo? {
        return null
    }

    override fun getId(): String {
        return id
    }

    override fun getName(): String {
        return name
    }

    override fun getInstances(): Collection<ICCloudInstance> {
        return instances.values
    }

    override fun findInstanceById(id: String): CloudInstance? {
        return instances[id]
    }

    fun removeInstance(instance: ICCloudInstance) {
        instances.remove(instance.uuid)
    }

    override fun getAgentPoolId(): Int? {
        return agentPool
    }
}