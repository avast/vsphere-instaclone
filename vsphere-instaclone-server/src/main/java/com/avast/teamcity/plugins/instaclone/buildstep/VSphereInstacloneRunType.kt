package com.avast.teamcity.plugins.instaclone.buildstep

import com.avast.teamcity.plugins.instaclone.ICCloudInstance
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.RunType
import jetbrains.buildServer.serverSide.RunTypeRegistry
import jetbrains.buildServer.web.openapi.PluginDescriptor
import java.time.Duration

class VSphereInstacloneRunType(descriptor: PluginDescriptor, registry: RunTypeRegistry) : RunType() {
    private val descriptor: PluginDescriptor

    override fun getType(): String {
        return VSPHERE_INSTACLONE_RUNTYPE
    }

    override fun getDisplayName(): String {
        return "VSphere Instaclone"
    }

    override fun getDescription(): String {
        return "Allows creating new instaclone VM image during running build - as build step"
    }

    override fun getRunnerPropertiesProcessor(): PropertiesProcessor {
        return PropertiesProcessor { map ->
            val result = mutableListOf<InvalidProperty>()

            map[TEMPLATE_NAME_SUFFIX_PARAMETER_NAME]?.let { value ->
                if (!value.matches("^[a-zA-Z-0-9_]*$".toRegex())) {
                    result.add(
                        InvalidProperty(
                            TEMPLATE_NAME_SUFFIX_PARAMETER_NAME,
                            "Value can contain only alphabet characters with '-' or '_'"
                        )
                    )
                }
            }
            map[TEMPLATE_TIMEOUT_PARAMETER_NAME]?.let { value ->
                if (!value.matches("^[0-9]{1,7}+$".toRegex())) {
                    result.add(
                        InvalidProperty(
                            TEMPLATE_TIMEOUT_PARAMETER_NAME,
                            "Invalid timeout value. Not a valid number"
                        )
                    )
                }
            } ?: run {
                result.add(
                    InvalidProperty(
                        TEMPLATE_TIMEOUT_PARAMETER_NAME,
                        "Value is not defined"
                    )
                )
            }

            map[CLONE_NUMBER_LIMIT_PARAMETER_NAME]?.let { value ->
                val isValidNumber = value.matches("^[0-9]{1,7}+$".toRegex())
                if (!isValidNumber) {
                    result.add(
                        InvalidProperty(
                            CLONE_NUMBER_LIMIT_PARAMETER_NAME,
                            "Invalid value. Not a valid number"
                        )
                    )
                }
                if (isValidNumber && value.toLong() < 1) {
                    result.add(
                        InvalidProperty(
                            CLONE_NUMBER_LIMIT_PARAMETER_NAME,
                            "Value must be >= 1"
                        )
                    )
                }
            } ?: run {
                result.add(
                    InvalidProperty(
                        CLONE_NUMBER_LIMIT_PARAMETER_NAME,
                        "Value is not defined"
                    )
                )
            }

            return@PropertiesProcessor result
        }
    }

    override fun getEditRunnerParamsJspFilePath(): String {
        return descriptor.getPluginResourcesPath("instacloneRunParams.jsp")
    }

    override fun getViewRunnerParamsJspFilePath(): String {
        return descriptor.getPluginResourcesPath("viewInstacloneParams.jsp")
    }

    override fun getDefaultRunnerProperties(): Map<String, String> {
        return mapOf(
            TEMPLATE_NAME_SUFFIX_PARAMETER_NAME to "",
            TEMPLATE_TIMEOUT_PARAMETER_NAME to Duration.ofMinutes(DEFAULT_TIMEOUT_MINUTES).toSeconds().toString(),
            CLONE_NUMBER_LIMIT_PARAMETER_NAME to ICCloudInstance.LEAVE_MULTIPLE_IMAGES_COUNT.toString()
        )
    }

    init {
        registry.registerRunType(this)
        this.descriptor = descriptor
    }

    companion object {
        const val TEMPLATE_NAME_SUFFIX_PARAMETER_NAME = "templateNameSuffix"
        const val TEMPLATE_TIMEOUT_PARAMETER_NAME = "templateTimeout"
        const val CLONE_NUMBER_LIMIT_PARAMETER_NAME = "cloneNumberLimit"

        const val VSPHERE_INSTACLONE_RUNTYPE = "VSphere Instaclone"
        const val DEFAULT_TIMEOUT_MINUTES = 3L
    }

}