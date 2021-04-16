package com.avast.teamcity.plugins.instaclone.web.service

import com.avast.teamcity.plugins.instaclone.ICCloudClientFactory
import com.avast.teamcity.plugins.instaclone.web.ApiException
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileCreateRequest
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileDataImpl
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileRemoveRequest
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileUpdateRequest
import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudProfile
import jetbrains.buildServer.clouds.server.CloudManagerBase
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SProject
import java.util.*
import java.util.stream.Collectors

/**
 *
 * @author Vitasek L.
 */
class CloudProfilesService(
    private val cloudManagerBase: CloudManagerBase,
    private val projectManager: ProjectManager
) {

    private val mapper = jacksonObjectMapper()
    private val logger = Logger.getInstance(CloudProfilesService::class.java.name)

    data class ProfileItem(
        val profile: CloudProfile,
        val project: SProject,
        val sdkUrl: String,
        val templates: List<String>
    )

    data class ProfileInfo(
        val projectName: String, val projectExtId: String,
        val profileId: String, val profileName: String,
        val profileEnabled: Boolean,
        val sdkUrl: String,
        val templates: List<String>,
        @JsonRawValue
        val imageConfigJson: String
    )


    fun getSimpleProfileInfos(): List<ProfileInfo> {
        return findProfiles().map { profileItem ->
            ProfileInfo(
                profileItem.project.name,
                profileItem.project.externalId,
                profileItem.profile.profileId,
                profileItem.profile.profileName,
                profileItem.profile.isEnabled,
                profileItem.sdkUrl,
                profileItem.templates,
                profileItem.profile.profileProperties[ICCloudClientFactory.PROP_IMAGES] ?: "{}"
            )
        }
    }

    private fun getProfileTemplates(imageConfigJson: String): List<String> {
        if (imageConfigJson.isBlank()) {
            return emptyList()
        }
        return try {
            val icImageConfigMap = ICCloudClientFactory.parseIcImageConfig(imageConfigJson)
            icImageConfigMap.values.mapNotNull { mapItem -> mapItem.template }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun findProfiles(): List<ProfileItem> {
        val compareBy = compareBy<ProfileItem> { item -> item.project.name }
        //     val sortTableComparator = compareBy.thenBy { item -> item.profile.profileName }
        return cloudManagerBase.listAllProfiles().stream().filter { profile -> profile.cloudCode == ICCloudClientFactory.CLOUD_CODE }
            .map { profile ->
                ProfileItem(
                    profile,
                    projectManager.findProjectById(profile.projectId)!!,
                    profile.profileProperties[ICCloudClientFactory.PROP_SDKURL] ?: "",
                    getProfileTemplates(profile.profileProperties[ICCloudClientFactory.PROP_IMAGES] ?: "")
                )
            }.sorted(compareBy).collect(Collectors.toList())
    }

    fun createProfile(profileCreateRequest: CloudProfileCreateRequest): CloudProfile {
        logger.info("Create cloud profile request: $profileCreateRequest")

        val projectId = translateProjectId(profileCreateRequest.extProjectId)

        updatePropImagesParameterIfPresent(
            profileCreateRequest.customProfileParameters
        )

        return cloudManagerBase.createProfile(projectId, CloudProfileDataImpl(profileCreateRequest.cloudCode,
                                    profileCreateRequest.profileName, profileCreateRequest.description,
                                    profileCreateRequest.terminateIdleTime, profileCreateRequest.enabled,
                                    profileCreateRequest.customProfileParameters, emptyList()))
    }

    fun updateProfile(profileUpdateRequest: CloudProfileUpdateRequest, cleanImageParameters : Boolean): CloudProfile {
        logger.info("Update cloud profile request: $profileUpdateRequest")
        val projectId = translateProjectId(profileUpdateRequest.extProjectId)
        val profileId = profileUpdateRequest.profileId

        val profile = cloudManagerBase.findProfileById(projectId, profileId!!)
            ?: throw RuntimeException("Profile with projectId = ${profileUpdateRequest.extProjectId} and profileId = $profileId was not found")

        val profileProperties = HashMap(profile.profileProperties)

        profileProperties.putAll(profileUpdateRequest.customProfileParameters) // overwrite parameters

        updatePropImagesParameterIfPresent(profileProperties)


        val cloudProfileData = CloudProfileDataImpl(
            profile.cloudCode, profileUpdateRequest.profileName,
            profileUpdateRequest.description,
            profileUpdateRequest.terminateIdleTime ?: profile.terminateIdleTime,
            profileUpdateRequest.enabled,
            profileProperties,
            if (cleanImageParameters) emptyList() else profile.images
        )

        return cloudManagerBase.updateProfile(projectId, profileId, cloudProfileData)
    }

    private fun updatePropImagesParameterIfPresent(
        customProfileParameters: MutableMap<String, String>
    ) {
        if (customProfileParameters.containsKey(ICCloudClientFactory.PROP_IMAGES)) {
            val imagesJson = customProfileParameters[ICCloudClientFactory.PROP_IMAGES]
            if (imagesJson != null) {
                try {
                    val parsedIcImageConfig = mapper.readTree(imagesJson)
                    // let's reformat JSON to make it more readable for user in UI
                    val formattedValue = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedIcImageConfig)
                    customProfileParameters[ICCloudClientFactory.PROP_IMAGES] = formattedValue

                } catch (e: Exception) {
                    throw ApiException(
                        "Failed to parse ${ICCloudClientFactory.PROP_IMAGES} custom profile parameter",
                        e
                    )
                }
            }
        }
    }

    private fun translateProjectId(extProjectId: String): String {
        return projectManager.findProjectByExternalId(extProjectId)!!.projectId
    }

    fun removeProfile(removeRequest: CloudProfileRemoveRequest): Boolean {
        logger.info("Remove cloud profile request: $removeRequest")
        val projectId = translateProjectId(removeRequest.extProjectId)

        return cloudManagerBase.removeProfile(projectId, removeRequest.profileId)
    }

//    internal fun updateTestProfile(extProjectId: String, profileId: String): CloudProfile {
//        val customProfileProperties = mutableMapOf(
//            ICCloudClientFactory.PROP_IMAGES to """{"image-name2": {"template": "datacenter/vm/tc-agent-template2","instanceFolder": "datacenter/vm/tc-agents","maxInstances": 10}}""",
//            ICCloudClientFactory.PROP_USERNAME to "asd2",
//            ICCloudClientFactory.PROP_PASSWORD to "asd2",
//            ICCloudClientFactory.PROP_PROFILE_UUID to UUID.randomUUID().toString(),
//            ICCloudClientFactory.PROP_SDKURL to "http://vcsim:8989/sdk"
//        )
//
//        return updateProfile(
//            CloudProfileUpdateRequest(
//                extProjectId, profileId, "test-profile", "description2",
//                true, null, customProfileProperties
//            ), true
//        )
//    }
//
//    fun createTestProfile(extProjectId: String): CloudProfile {
//        val customProfileProperties = mutableMapOf(
//            ICCloudClientFactory.PROP_IMAGES to """{"image-name2": {"template": "datacenter/vm/tc-agent-template","instanceFolder": "datacenter/vm/tc-agents","maxInstances": 10}}""",
//            ICCloudClientFactory.PROP_USERNAME to "asd",
//            ICCloudClientFactory.PROP_PASSWORD to "asd",
//            ICCloudClientFactory.PROP_PROFILE_UUID to UUID.randomUUID().toString(),
//            ICCloudClientFactory.PROP_SDKURL to "http://vcsim:8989/sdk"
//        )
//        return this.createProfile(
//            extProjectId,
//            CloudProfileDataImpl(
//                ICCloudClientFactory.CLOUD_CODE,
//                "test-profile",
//                "test-profile-desc",
//                TimeUnit.MINUTES.toMillis(30),
//                true,
//                customProfileProperties,
//                emptyList()
//            )
//        )
//    }

}