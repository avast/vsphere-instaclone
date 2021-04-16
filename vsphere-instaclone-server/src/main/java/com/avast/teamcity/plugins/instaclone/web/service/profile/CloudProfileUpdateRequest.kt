package com.avast.teamcity.plugins.instaclone.web.service.profile

import com.avast.teamcity.plugins.instaclone.ICCloudClientFactory
import java.util.concurrent.TimeUnit

/**
 *
 * @author Vitasek L.
 */
data class CloudProfileUpdateRequest(
    val extProjectId: String,
    val profileId: String?,
    val profileName: String,
    val description: String = "",
    val enabled: Boolean = true,
    val terminateIdleTime: Long? = TimeUnit.MINUTES.toMillis(30),
    val customProfileParameters: MutableMap<String, String>
) {
    override fun toString(): String {
        val profileParams = HashMap(customProfileParameters)
        profileParams.remove(ICCloudClientFactory.PROP_PASSWORD)

        return "CloudProfileUpdateRequest(extProjectId='$extProjectId', profileId=$profileId, profileName='$profileName', description='$description', enabled=$enabled, terminateIdleTime=$terminateIdleTime, customProfileParameters=$profileParams)"
    }
}