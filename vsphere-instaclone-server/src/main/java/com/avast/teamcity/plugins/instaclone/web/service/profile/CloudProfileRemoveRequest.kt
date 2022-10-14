package com.avast.teamcity.plugins.instaclone.web.service.profile

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 *
 * @author Vitasek L.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CloudProfileRemoveRequest(
    val extProjectId: String,
    val profileId: String
) {

}