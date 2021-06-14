package com.avast.teamcity.plugins.instaclone.web.service.profile

import jetbrains.buildServer.clouds.*
import java.util.*

class CloudProfileDataImpl(
    cloudCode: String, name: String, description: String,
    terminateIdleTime: Long?, enabled: Boolean, customProfileProperties: Map<String, String>,
    cloudImages: Collection<CloudImageParameters>
) :
    CloudProfileData {
    private val myCloudCode: String = cloudCode
    private val myName: String = name
    private val myDescription: String = description
    private val myTerminateIdleTime: Long? = terminateIdleTime
    private val myEnabled: Boolean = enabled
    private val myCustomProfileProperties: Map<String, String> = customProfileProperties
    private val myCloudImages: Collection<CloudImageParameters> = cloudImages

    override fun getCloudCode(): String {
        return myCloudCode
    }

    override fun getProfileName(): String {
        return myName
    }

    override fun getDescription(): String {
        return myDescription
    }


    override fun getTerminateIdleTime(): Long? {
        return myTerminateIdleTime
    }

    override fun getImagesParameters(): Collection<CloudImageData> {

        return Collections.unmodifiableCollection(myCloudImages)
    }

    override fun isEnabled(): Boolean {
        return myEnabled
    }

    override fun getProfileProperties(): Map<String, String> {
        val retval: HashMap<String, String> = HashMap()
        retval.putAll(myCustomProfileProperties)
        retval["name"] = myName
        retval["cloud-code"] = myCloudCode
        retval["description"] = myDescription
        retval["enabled"] = myEnabled.toString()
        if (myTerminateIdleTime != null) {
            retval["terminate-idle-time"] = (myTerminateIdleTime / 60L / 1000L).toString()
        } else {
            retval["terminate-idle-time"] = ""
        }

//        if (retval == null) {
//            $$$reportNull$$$0(10);
//        }
        return retval
    }

    override fun equals(other: Any?): Boolean {
        return if (this === other) {
            true
        } else if (other != null && this.javaClass == other.javaClass) {
            val that = other as CloudProfileDataImpl
            myEnabled == that.myEnabled && myCloudCode == that.myCloudCode && myName == that.myName && myDescription == that.myDescription && myTerminateIdleTime == that.myTerminateIdleTime && myCustomProfileProperties == that.myCustomProfileProperties && myCloudImages == that.myCloudImages
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(
            myCloudCode, myName, myDescription, myTerminateIdleTime, myEnabled, myCustomProfileProperties, myCloudImages
        )
    }

}
