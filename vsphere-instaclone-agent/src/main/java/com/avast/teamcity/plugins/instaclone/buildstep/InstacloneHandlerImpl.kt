package com.avast.teamcity.plugins.instaclone.buildstep

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap

class InstacloneHandlerImpl(private val processMap: ConcurrentHashMap<String, InstaCloneBuildProcess>) : InstacloneHandler {
    private val logger = Logger.getInstance(InstacloneHandlerImpl::class.java.name)

    override fun updateStatus(uuid: String, status: String) {
        logger.info("Received update status message for $uuid and $status")
        processMap[uuid]?.updateStatus(status)
    }
}