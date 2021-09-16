package com.avast.teamcity.plugins.instaclone.buildstep

import jetbrains.buildServer.remote.RemoteHandler

/**
 *
 * @author Vitasek L.
 */
@RemoteHandler(handleName = "instacloneHandler")
interface InstacloneHandler {

    fun updateStatus(uuid: String, status: String)

    companion object {
        const val HANDLER = "instacloneHandler"
    }
}