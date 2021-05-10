package com.avast.teamcity.plugins.instaclone.web.service

import com.avast.teamcity.plugins.instaclone.ICCloudClientFactory
import com.avast.teamcity.plugins.instaclone.web.ApiException
import com.avast.teamcity.plugins.instaclone.web.service.profile.*
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudProfile
import jetbrains.buildServer.clouds.server.CloudManagerBase
import jetbrains.buildServer.serverSide.DuplicateIdException
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SProject
import java.util.stream.Collectors

/**
 *
 * @author Vitasek L.
 */
class CloudProfilesService(
    private val cloudManagerBase: CloudManagerBase,
    private val projectManager: ProjectManager,
    private val vCenterAccountService: VCenterAccountService
) {

    private val logger = Logger.getInstance(CloudProfilesService::class.java.name)
    private val profileLock = Any()

    data class ProfileItem(
        val profile: CloudProfile,
        val project: SProject,
        val accountId: String?,
        val sdkUrl: String,
        val templates: List<String>
    )

    data class ProfileInfo(
        val projectName: String,
        val profileDescription: String,
        val projectExtId: String,
        val profileId: String, val profileName: String,
        val profileEnabled: Boolean,
        val profileParameters: MutableMap<String, String>,
        val sdkUrl: String,
        val vCenterAccount: String?,
        val templates: List<String>,
        val imageConfigJson: JsonNode
    )


    fun getSimpleProfileInfos(cloudCode: String): List<ProfileInfo> {
        return findProfiles(cloudCode).map { profileItem ->
            val imageConfig = profileItem.profile.profileProperties[ICCloudClientFactory.PROP_IMAGES] ?: "{}"
            ProfileInfo(
                profileItem.project.name,
                profileItem.profile.description,
                profileItem.project.externalId,
                profileItem.profile.profileId,
                profileItem.profile.profileName,
                profileItem.profile.isEnabled,
                removeVCenterAccount(profileItem.profile.profileProperties),
                profileItem.sdkUrl,
                profileItem.accountId,
                profileItem.templates,
                ICCloudClientFactory.getImageConfigMapper(imageConfig).readTree(imageConfig)
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


    fun findProfiles(cloudCode: String = ICCloudClientFactory.CLOUD_CODE): List<ProfileItem> {
        val compareBy = compareBy<ProfileItem> { item -> item.project.name }
        //     val sortTableComparator = compareBy.thenBy { item -> item.profile.profileName }
        return cloudManagerBase.listAllProfiles().stream()
            .filter { profile -> profile.cloudCode == cloudCode }
            .map { profile ->
                val accountId = profile.profileProperties[ICCloudClientFactory.PROP_VCENTER_ACCOUNT]
                val accountById = vCenterAccountService.getAccountById(accountId)

                ProfileItem(
                    profile,
                    projectManager.findProjectById(profile.projectId)!!,
                    accountId,
                    accountById?.url ?: "",
                    getProfileTemplates(profile.profileProperties[ICCloudClientFactory.PROP_IMAGES] ?: "")
                )
            }.sorted(compareBy).collect(Collectors.toList())
    }

    fun updateProfileAccounts(accountsUpdateRequest: AccountsUpdateRequest) {
        val storeAccounts = vCenterAccountService.storeAccounts(accountsUpdateRequest)

        storeAccounts.accounts.forEach {
            updateProfileAccount(it)
        }
    }

    private fun updateProfileAccount(updateAccount: VCenterAccount) {
        val accountHash = updateAccount.hash()
        findProfiles()
            .filter { profile -> profile.accountId == updateAccount.id }
            .filter { profile -> shouldUpdateProfile(profile.profile.profileProperties, accountHash) }
            .forEach { profileInfo ->

                val cloudProfileUpdateRequest = CloudProfileUpdateRequest(
                    profileInfo.project.externalId,
                    profileInfo.profile.profileId,
                    profileInfo.profile.profileName,
                    profileInfo.profile.description,
                    profileInfo.profile.isEnabled,
                    updateAccount.id,
                    profileInfo.profile.terminateIdleTime,
                    updateAccountHash(profileInfo.profile.profileProperties, accountHash)
                )

                updateProfile(cloudProfileUpdateRequest, false) // don't delete cloudImageparameters
            }
    }

    private fun updateAccountHash(properties: Map<String, String>, newHash: String): MutableMap<String, String> {
        val map = HashMap(properties)
        map[ICCloudClientFactory.PROP_VCENTER_ACCOUNT_HASH] = newHash
        return map
    }

    private fun shouldUpdateProfile(properties: Map<String, String>, accountHash: String): Boolean {
        return accountHash != properties[ICCloudClientFactory.PROP_VCENTER_ACCOUNT_HASH]
    }


    fun createProfile(profileCreateRequest: CloudProfileCreateRequest): CloudProfile {
        logger.info("Create cloud profile request: $profileCreateRequest")

        val projectId = translateProjectId(profileCreateRequest.extProjectId)

        updatePropImagesParameterIfPresent(
            profileCreateRequest.customProfileParameters
        )

        vCenterAccountService.updateAccountProperties(
            profileCreateRequest.vCenterAccount,
            profileCreateRequest.customProfileParameters
        )

        val customProfileParameters = profileCreateRequest.customProfileParameters
        customProfileParameters[ICCloudClientFactory.PROP_PROFILE_UUID] = ICCloudClientFactory.initProfileUUID()

        return try {
            synchronized(profileLock) {
                val listProfilesByProject = cloudManagerBase.listProfilesByProject(projectId, true)
                val alreadyExisting =
                    listProfilesByProject.find { profile -> profile.profileName == profileCreateRequest.profileName }
                if (alreadyExisting != null) {
                    throw java.lang.RuntimeException("TC create profile already exists - duplicate - projectId = $projectId AND profileName=${profileCreateRequest.profileName}")
                }

                val cloudProfile = cloudManagerBase.createProfile(
                    projectId, CloudProfileDataImpl(
                        profileCreateRequest.cloudCode,
                        profileCreateRequest.profileName, profileCreateRequest.description,
                        profileCreateRequest.terminateIdleTime, profileCreateRequest.enabled,
                        customProfileParameters, emptyList()
                    )
                )
                cloudProfile
            }
        } catch (e: DuplicateIdException) {
            logger.warn(
                "TC create profile bug - duplicate - projectId = $projectId AND profileName=${profileCreateRequest.profileName}",
                e
            )
            throw e
        }
    }

    fun updateProfile(profileUpdateRequest: CloudProfileUpdateRequest, cleanImageParameters: Boolean): CloudProfile {
        logger.info("Update cloud profile request: $profileUpdateRequest")
        val projectId = translateProjectId(profileUpdateRequest.extProjectId)
        val profileId = profileUpdateRequest.profileId

        val profile = cloudManagerBase.findProfileById(projectId, profileId!!)
            ?: throw RuntimeException("Profile with projectId = ${profileUpdateRequest.extProjectId} and profileId = $profileId was not found")

        val profileProperties = HashMap(profile.profileProperties)

        resetTerminateConditions(profileProperties)
        profileProperties.putAll(profileUpdateRequest.customProfileParameters) // overwrite parameters

        vCenterAccountService.updateAccountProperties(profileUpdateRequest.vCenterAccount, profileProperties)

        updatePropImagesParameterIfPresent(profileProperties)

        val enabled = profileUpdateRequest.enabled ?: profile.isEnabled

        updateEnabledParameterIfPresent(enabled, profileProperties)

        val cloudProfileData = CloudProfileDataImpl(
            profile.cloudCode, profileUpdateRequest.profileName,
            profileUpdateRequest.description,
            profileUpdateRequest.terminateIdleTime ?: profile.terminateIdleTime,
            enabled,
            profileProperties,
            if (cleanImageParameters) emptyList() else profile.images
        )

        return synchronized(profileLock) {
            cloudManagerBase.updateProfile(projectId, profileId, cloudProfileData)
        }
    }

    private fun resetTerminateConditions(profileProperties: MutableMap<String, String>) {
        TERMINATE_CONDITIONS_PARAMS.forEach {
            profileProperties.remove(it)
        }
    }

    private fun updateEnabledParameterIfPresent(
        enabled: Boolean,
        profileProperties: java.util.HashMap<String, String>
    ) {
        if (profileProperties.containsKey("enabled")) {
            profileProperties["enabled"] = enabled.toString()
        }
    }

    private fun updatePropImagesParameterIfPresent(
        customProfileParameters: MutableMap<String, String>
    ) {
        if (customProfileParameters.containsKey(ICCloudClientFactory.PROP_IMAGES)) {
            val imagesJson = customProfileParameters[ICCloudClientFactory.PROP_IMAGES]
            imagesJson?.let { config ->
                try {
                    val mapper = ICCloudClientFactory.getImageConfigMapper(config)
                    val parsedIcImageConfig = mapper.readTree(config)
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

        return synchronized(profileLock) {
            cloudManagerBase.removeProfile(projectId, removeRequest.profileId)
        }
    }


    companion object {

        private const val TERMINATE_CONDITIONS_NEXT_HOUR = "next-hour"
        private const val TERMINATE_CONDITIONS_TOTAL_WORK_TIME = "total-work-time"
        private const val TERMINATE_CONDITIONS_TERMINATE_AFTER_BUILD = "terminate-after-build"
        private val TERMINATE_CONDITIONS_PARAMS = setOf(
            TERMINATE_CONDITIONS_NEXT_HOUR,
            TERMINATE_CONDITIONS_TOTAL_WORK_TIME,
            TERMINATE_CONDITIONS_TERMINATE_AFTER_BUILD
        )

        fun removeVCenterAccount(profileProperties: MutableMap<String, String>): MutableMap<String, String> {
            val newMap = HashMap(profileProperties)
            newMap.remove(ICCloudClientFactory.PROP_PASSWORD)
            newMap.remove(ICCloudClientFactory.PROP_USERNAME)
            newMap.remove(ICCloudClientFactory.PROP_VCENTER_ACCOUNT_HASH)

            return newMap
        }

    }

}