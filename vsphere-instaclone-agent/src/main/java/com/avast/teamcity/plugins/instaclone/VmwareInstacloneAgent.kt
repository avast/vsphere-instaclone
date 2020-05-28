package com.avast.teamcity.plugins.instaclone

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.CommandLineExecutor
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.util.EventDispatcher
import org.json.JSONObject
import java.io.File

class VmwareInstacloneAgent(lifeCycleEvents: EventDispatcher<AgentLifeCycleListener>) {

    init {
        lifeCycleEvents.addListener(object : AgentLifeCycleAdapter() {
            override fun agentInitialized(agent: BuildAgent) {
                initializeVmInstance(agent)
            }
        })
    }

    private fun initializeVmInstance(agent: BuildAgent) {
        val config = agent.configuration as BuildAgentConfigurationEx
        val rpcToolPath = findRpcTool(config)
        if (rpcToolPath == null) {
            LOG.info("rpctool wasn't found")
            return
        }

        val freezeScript = config.configurationParameters["vmware.freeze.script"].let {
            if (it.isNullOrEmpty()) {
                LOG.info("vmware.freeze.script is unset")
                return
            }
            it
        }

        fun getInstanceConfig(): String? {
            val executor = CommandLineExecutor(GeneralCommandLine().apply {
                exePath = rpcToolPath
                addParameter("info-get guestinfo.teamcity-instance-config")
            })

            val result = executor.runProcess()
            if (result.exitCode != 0) {
                return null
            }

            return result.stdout.trim()
        }

        val instanceConfigString = getInstanceConfig().let {
            if (it.isNullOrEmpty()) {
                if (config.authorizationToken.isNotEmpty()) {
                    LOG.error("Can't freeze: delete authorization token from build.properties")
                    return
                }
                LOG.info(String.format("Going to execute the freeze script: %s", freezeScript))

                Runtime.getRuntime().exec(freezeScript).waitFor()

                LOG.info("Freeze script completed")
                val newConfig = getInstanceConfig()
                if (newConfig.isNullOrEmpty()) {
                    LOG.error("Missing guestinfo.teamcity-instance-config in an unfrozen machine")
                    return
                }

                newConfig
            } else {
                it
            }
        }

        val instanceConfig = JSONObject(instanceConfigString)
        val configParams = instanceConfig.getJSONObject("configParams")
        for (key in configParams.keySet()) {
            config.addConfigurationParameter(key, configParams.getString(key))
        }

        val agentName = instanceConfig.optString("agentName")
        if (agentName != null) {
            config.name = agentName
        }
    }

    companion object {
        private fun findRpcTool(config: BuildAgentConfiguration): String? {
            var path = config.configurationParameters[RPC_TOOL_PARAMETER]
            if (path == null) {
                for (defaultPath in DEFAULT_PATHS) {
                    val file = File(defaultPath)
                    if (file.isFile) {
                        path = defaultPath
                        break
                    }
                }
            }
            return path
        }

        private val LOG = Logger.getInstance(VmwareInstacloneAgent::class.java.name)
        private const val RPC_TOOL_PARAMETER = "teamcity.vmware.rpctool.path"
        private val DEFAULT_PATHS = arrayOf(
                "C:\\Program Files\\VMware\\VMware Tools\\rpctool.exe",
                "/usr/sbin/vmware-rpctool",
                "/usr/bin/vmware-rpctool",
                "/sbin/rpctool",
                "/Library/Application Support/VMware Tools/vmware-tools-daemon")
    }
}