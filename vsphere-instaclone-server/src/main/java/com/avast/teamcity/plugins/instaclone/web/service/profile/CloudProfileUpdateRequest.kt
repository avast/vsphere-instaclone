package com.avast.teamcity.plugins.instaclone.web.service.profile

import com.avast.teamcity.plugins.instaclone.web.service.CloudProfilesService
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.concurrent.TimeUnit

/**
 *
 * @author Vitasek L.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CloudProfileUpdateRequest(
    val extProjectId: String,
    val profileId: String?,
    val profileName: String,
    val description: String = "",
    val enabled: Boolean?,
    val vCenterAccount: String,
    val terminateIdleTime: Long? = TimeUnit.MINUTES.toMillis(30),
    val customProfileParameters: MutableMap<String, String>
) {
    override fun toString(): String {
        val profileParams = CloudProfilesService.removeVCenterAccount(customProfileParameters)

        return "CloudProfileUpdateRequest(extProjectId='$extProjectId', profileId=$profileId, profileName='$profileName', description='$description', enabled=$enabled, terminateIdleTime=$terminateIdleTime, customProfileParameters=$profileParams), vCenterAccount=$vCenterAccount)"
    }
}