package com.avast.teamcity.plugins.instaclone

import jetbrains.buildServer.remote.RemoteHandler

@RemoteHandler(handleName = "vmware-instaclone-agent-service")
interface AgentService {
    fun shutdown()
}
