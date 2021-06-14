package com.avast.teamcity.plugins.instaclone.web.service.profile

import com.avast.teamcity.plugins.instaclone.web.service.CloudProfilesService
import com.fasterxml.jackson.annotation.JsonIgnore
import jetbrains.buildServer.clouds.CloudClientParameters
import jetbrains.buildServer.clouds.CloudImageParameters
import jetbrains.buildServer.clouds.CloudProfile

/**
 * Wrapper/Delegation class to remove specific properties from the interface
 * @author Vitasek L.
 */
class CloudProfileResponse(private val cloudProfile : CloudProfile) : CloudProfile by cloudProfile {

    private val profilePropertiesClean = CloudProfilesService.removeVCenterAccount(cloudProfile.profileProperties)

    override fun getProfileProperties(): MutableMap<String, String> {
        return profilePropertiesClean
    }

    @JsonIgnore
    override fun getParameters(): CloudClientParameters {
        return cloudProfile.parameters
    }

    @JsonIgnore
    override fun getImages(): MutableCollection<CloudImageParameters> {
        return cloudProfile.images
    }
}