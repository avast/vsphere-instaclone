package com.avast.teamcity.plugins.instaclone.web.service.profile

import com.avast.teamcity.plugins.instaclone.ICCloudClientFactory
import java.util.concurrent.TimeUnit

/**
 *
 * @author Vitasek L.
 */
data class CloudProfileCreateRequest(
    val cloudCode: String = ICCloudClientFactory.CLOUD_CODE,
    val extProjectId: String,
    val profileName: String,
    val description: String = "",
    val enabled: Boolean = true,
    val terminateIdleTime: Long? = TimeUnit.MINUTES.toMillis(30),
    val customProfileParameters: MutableMap<String, String>
)
{
    override fun toString(): String {
        val profileParams = HashMap(customProfileParameters)
        profileParams.remove(ICCloudClientFactory.PROP_PASSWORD)

        return "CloudProfileCreateRequest(cloudCode='$cloudCode', extProjectId='$extProjectId', profileName='$profileName', description='$description', enabled=$enabled, terminateIdleTime=$terminateIdleTime, customProfileParameters=$profileParams)"
    }
}