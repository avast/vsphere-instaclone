package com.avast.teamcity.plugins.instaclone.buildstep

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.*

class InstaCloneBuildStepRunner(private val handlerBuildProcessManager: HandlerBuildProcessManager) : AgentExtension,
    AgentBuildRunner, AgentBuildRunnerInfo {
    private val logger = Logger.getInstance(InstaCloneBuildProcess::class.java.name)

    override fun createBuildProcess(runningBuild: AgentRunningBuild, context: BuildRunnerContext): BuildProcess {
        return InstaCloneBuildProcess(runningBuild, context, handlerBuildProcessManager)
    }

    override fun getRunnerInfo(): AgentBuildRunnerInfo {
        return this
    }

    override fun getType(): String {
        return "VSphere Instaclone"
    }

    override fun canRun(agentConfiguration: BuildAgentConfiguration): Boolean {
        return true
    }

}