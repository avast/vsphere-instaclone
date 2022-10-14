package com.avast.teamcity.plugins.instaclone.buildstep

import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildParametersMap
import jetbrains.buildServer.agent.BuildRunnerContext
import jetbrains.buildServer.agent.VirtualContext
import jetbrains.buildServer.parameters.ValueResolver
import java.io.File

/**
 *
 * @author Vitasek L.
 */
abstract class BuildRunnerContextAdapter : BuildRunnerContext {
    override fun getId(): String {
        TODO("Not yet implemented")
    }

    override fun getBuild(): AgentRunningBuild {
        TODO("Not yet implemented")
    }

    override fun getWorkingDirectory(): File {
        TODO("Not yet implemented")
    }

    override fun getRunType(): String {
        TODO("Not yet implemented")
    }

    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getBuildParameters(): BuildParametersMap {
        TODO("Not yet implemented")
    }

    override fun getConfigParameters(): MutableMap<String, String> {
        TODO("Not yet implemented")
    }

    override fun getRunnerParameters(): MutableMap<String, String> {
        TODO("Not yet implemented")
    }

    override fun addSystemProperty(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun addEnvironmentVariable(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun addConfigParameter(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun addRunnerParameter(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun getParametersResolver(): ValueResolver {
        TODO("Not yet implemented")
    }

    override fun getToolPath(toolName: String): String {
        TODO("Not yet implemented")
    }

    override fun parametersHaveReferencesTo(keys: MutableCollection<String>): Boolean {
        TODO("Not yet implemented")
    }

    override fun isVirtualContext(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getVirtualContext(): VirtualContext {
        TODO("Not yet implemented")
    }
}