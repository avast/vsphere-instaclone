package com.avast.teamcity.plugins.instaclone

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.CommandLineExecutor
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.impl.BuildAgentEx
import org.json.JSONObject
import java.io.File
import java.time.Duration

class RunningAgentPlugin(val agent: BuildAgent) : AgentService {

    private val config = agent.configuration as BuildAgentConfigurationEx
    private val rpcToolPath = findRpcTool(config)

    init {
        initialize()
    }

    private fun initialize() {
        if (rpcToolPath == null) {
            LOG.info("rpctool wasn't found")
            return
        }

        val freezeScript: String = config.configurationParameters["vmware.freeze.script"].let {
            if (it.isNullOrEmpty()) {
                LOG.info("vmware.freeze.script is unset")
                return
            }
            it
        }

        val instanceConfigString = callRpcTool("info-get guestinfo.teamcity-instance-config").let {
            if (it.isNullOrEmpty()) {
                if (config.authorizationToken.isNotEmpty()) {
                    LOG.error("Can't freeze: delete authorization token from build.properties")
                    return
                }
                LOG.info(String.format("Going to execute the freeze script: %s", freezeScript))

                exec(freezeScript).waitFor()

                LOG.info("Freeze script completed")
                val newConfig = callRpcTool("info-get guestinfo.teamcity-instance-config")
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

        private val LOG = Logger.getInstance(RunningAgentPlugin::class.java.name)
        private const val RPC_TOOL_PARAMETER = "teamcity.vmware.rpctool.path"
        private val DEFAULT_PATHS = arrayOf(
                "C:\\Program Files\\VMware\\VMware Tools\\rpctool.exe",
                "/usr/sbin/vmware-rpctool",
                "/usr/bin/vmware-rpctool",
                "/sbin/rpctool",
                "/Library/Application Support/VMware Tools/vmware-tools-daemon")
    }

    override fun shutdown() {
        val shutdownScript = config.configurationParameters["vmware.shutdown.script"]
        Thread {
            LOG.info("Received shutdown signal due to instance being terminated")
            val agent = this.agent as BuildAgentEx
            agent.serverMonitor.shutdown()
            agent.unregisterFromBuildServer()
            if (shutdownScript != null) {
                LOG.info(String.format("Going to execute the shutdown script: %s", shutdownScript))
                try {
                    exec(shutdownScript).waitFor()
                } catch (_: Exception) {
                }
            }

            LOG.info("Signalling shutdown completion")
            callRpcTool("info-set guestinfo.teamcity-instance-shutdown true")
        }.start()
    }

    private fun callRpcTool(command: String): String? {
        val executor = CommandLineExecutor(GeneralCommandLine().apply {
            exePath = rpcToolPath
            addParameter(command)
        })

        val result = executor.runProcess()
        if (result.exitCode != 0) {
            return null
        }

        return result.stdout.trim()
    }

    private fun exec(command: String): Process {
        val cmdline = org.apache.commons.exec.CommandLine.parse(command)
        val process = ProcessBuilder(cmdline.executable, *cmdline.arguments)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        process.inputStream.close()
        return process
    }
}