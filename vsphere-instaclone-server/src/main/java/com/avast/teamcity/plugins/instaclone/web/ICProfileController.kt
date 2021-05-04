package com.avast.teamcity.plugins.instaclone.web

import com.avast.teamcity.plugins.instaclone.ICCloudClientFactory
import com.avast.teamcity.plugins.instaclone.utils.BaseJsonController
import com.avast.teamcity.plugins.instaclone.web.service.CloudProfilesService
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileCreateRequest
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileRemoveRequest
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileResponse
import com.avast.teamcity.plugins.instaclone.web.service.profile.CloudProfileUpdateRequest
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.auth.SecurityContext
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class ICProfileController(
    private val pluginDescriptor: PluginDescriptor,
    private val cloudProfilesService: CloudProfilesService,
    projectManager: ProjectManager,
    webControllerManager: WebControllerManager,
    securityContext: SecurityContext
) : BaseJsonController(securityContext, projectManager) {

    private val logger = Logger.getInstance(ICProfileController::class.java.name)


    init {
        webControllerManager.registerController(
            "/app/${ICCloudClientFactory.CLOUD_CODE_WEB}/list",
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
            "/app/${ICCloudClientFactory.CLOUD_CODE_WEB}/update",
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
            "/app/${ICCloudClientFactory.CLOUD_CODE_WEB}/create",
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
            "/app/${ICCloudClientFactory.CLOUD_CODE_WEB}/remove",
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
            "/${ICCloudClientFactory.CLOUD_CODE_WEB}.html",
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
//            "/app/${ICCloudClientFactory.CLOUD_CODE_WEB}/testupdate",
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

}