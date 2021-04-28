package com.avast.teamcity.plugins.instaclone.web

import com.avast.teamcity.plugins.instaclone.ICCloudClientFactory
import com.avast.teamcity.plugins.instaclone.web.service.CloudProfilesService
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileCreateRequest
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileRemoveRequest
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileResponse
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileUpdateRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.serverSide.auth.AuthUtil
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.serverSide.auth.SecurityContext
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.http.MediaType
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KClass


class ICProfileController(
    private val pluginDescriptor: PluginDescriptor,
    private val cloudProfilesService: CloudProfilesService,
    webControllerManager: WebControllerManager,
    private val securityContext: SecurityContext
) {

    private var mapper = jacksonObjectMapper()
    private val logger = Logger.getInstance(ICProfileController::class.java.name)


    init {
        webControllerManager.registerController(
            "/app/${ICCloudClientFactory.CLOUD_CODE}/list",
            object : BaseController() {
                override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
                    return jsonRequestResponse(request, Void::class, response) {
                        checkManageCloudPermission()

                        val cloudCode = request.getParameter("cloudCode") ?: ICCloudClientFactory.CLOUD_CODE
                        cloudProfilesService.getSimpleProfileInfos(cloudCode)
                    }
                }
            }
        )
        webControllerManager.registerController(
            "/app/${ICCloudClientFactory.CLOUD_CODE}/update",
            object : BaseController() {
                override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
                    return if (isPost(request)) {
                        jsonRequestResponse(request, CloudProfileUpdateRequest::class, response) {
                            checkManageCloudPermission()
                            checkProjectEditPermission(it!!.extProjectId)

                            val profile = cloudProfilesService.updateProfile(
                                it,
                                (request.getParameter("clean") ?: "false").toBoolean()
                            )
                            CloudProfileResponse(profile)
                        }
                    } else {
                        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Only POST method is allowed")
                        null
                    }
                }
            }
        )

        webControllerManager.registerController(
            "/app/${ICCloudClientFactory.CLOUD_CODE}/create",
            object : BaseController() {
                override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
                    return if (isPost(request)) {
                        jsonRequestResponse(request, CloudProfileCreateRequest::class, response) {

                            checkManageCloudPermission()
                            checkProjectEditPermission(it!!.extProjectId)

                            val profile = cloudProfilesService.createProfile(it)
                            CloudProfileResponse(profile)
                        }
                    } else {
                        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Only POST method is allowed")
                        null
                    }
                }
            }
        )
        webControllerManager.registerController(
            "/app/${ICCloudClientFactory.CLOUD_CODE}/remove",
            object : BaseController() {
                override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
                    return if (isPost(request)) {
                        jsonRequestResponse(request, CloudProfileRemoveRequest::class, response) {
                            checkManageCloudPermission()
                            checkProjectEditPermission(it!!.extProjectId)

                            cloudProfilesService.removeProfile(it)
                            null
                        }
                    } else {
                        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Only POST method is allowed")
                        null
                    }
                }
            }
        )
        webControllerManager.registerController(
            "/${ICCloudClientFactory.CLOUD_CODE}.html",
            object : BaseController() {
                override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView {
                    checkManageCloudPermission()
                    val path = pluginDescriptor.getPluginResourcesPath("cloud-profiles.jsp")

                    val model = mutableMapOf("profiles" to cloudProfilesService.findProfiles())
                    return ModelAndView(path, model)
                }
            }
        )
//        webControllerManager.registerController(
//            "/app/${ICCloudClientFactory.CLOUD_CODE}/testupdate",
//            object : BaseController() {
//                override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView {
//
//                    return jsonResponse(response) {
//                        cloudProfilesService.updateTestProfile(request.getParameter("projectId") as String, request.getParameter("profileId") as String)
//                    }
//                }
//            }
//        )
    }

    private fun checkProjectEditPermission(projectId: String) {
        if (!AuthUtil.hasPermissionToManageProject(
                securityContext.authorityHolder,
                cloudProfilesService.translateProjectId(projectId)
            )
        ) {
            throw IllegalAccessException("User has not edit permissions to access project with id $projectId")
        }
    }

    private fun checkManageCloudPermission() {
        if (!AuthUtil.hasGlobalPermission(
                securityContext.authorityHolder,Permission.MANAGE_AGENT_CLOUDS
            )
        ) {
            throw IllegalAccessException("User has not permissions to edit cloud profiles")
        }
    }


    private fun <T : Any> jsonRequestResponse(
        request: HttpServletRequest,
        requestClass: KClass<T>,
        response: HttpServletResponse,
        resultValue: (T?) -> Any?
    ): ModelAndView? {
        response.contentType = MediaType.APPLICATION_JSON_UTF8_VALUE

        val out = response.writer
        try {
            val parsedBody = if (requestClass == Void::class) {
                null
            } else
                requestClass.let {
                    try {
                        mapper.readValue(request.reader, requestClass.java)
                    } catch (e: Exception) {
                        logger.error("Failed to parse request", e)
                        throw ApiException("Failed to parse request: ${e.message}", e)
                    }
                }

            val result = try {
                resultValue(parsedBody)
            } catch (e: Exception) {
                logger.error("Failed to process request", e)
                throw ApiException("Failed to process request: ${e.message}", e)
            }
            if (result != null) {
                mapper.writeValue(out, result)
            } else {
                out.write("{}")
            }
        } catch (accessEx : IllegalAccessException) {
            logger.error("Failed to process request - not enough permissions", accessEx)
            response.sendError(HttpServletResponse.SC_FORBIDDEN, accessEx.message)
        } catch (apiEx: ApiException) {
            logger.error("Failed to process request", apiEx)
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, apiEx.message)
        } catch (e: java.lang.Exception) {
            // e.printStackTrace()
            logger.error("Failed to process request", e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString())
        }
        out.flush()

        return null
    }
}