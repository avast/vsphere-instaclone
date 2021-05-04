package com.avast.teamcity.plugins.instaclone.web

import com.avast.teamcity.plugins.instaclone.ICCloudClientFactory
import com.avast.teamcity.plugins.instaclone.utils.BaseJsonController
import com.avast.teamcity.plugins.instaclone.web.service.CloudProfilesService
import com.avast.teamcity.plugins.instaclone.web.service.VCenterAccountService
import com.avast.teamcity.plugins.instaclone.web.service.profile.*
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.auth.SecurityContext
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class ICVCenterAccountController(
    private val pluginDescriptor: PluginDescriptor,
    private val cloudProfilesService: CloudProfilesService,
    private val vCenterAccountService: VCenterAccountService,
    projectManager: ProjectManager,
    webControllerManager: WebControllerManager,
    securityContext: SecurityContext
) : BaseJsonController(securityContext, projectManager) {

    private val logger = Logger.getInstance(ICVCenterAccountController::class.java.name)


    init {
        webControllerManager.registerController(
            "/app/${ICCloudClientFactory.CLOUD_CODE_WEB}/accounts/list",
            object : BaseController() {
                override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
                    return jsonRequestResponse(request, Void::class, response) {
                        checkManageCloudPermission()

                        val listAccounts = vCenterAccountService.listAccounts()

                        // hide username and passwords
                        val accounts = listAccounts.accounts.map { item -> VCenterAccountInfo(item.id, item.url) }
                        VCenterAccountListInfo(accounts)
                    }
                }
            }
        )
        webControllerManager.registerController(
            "/app/${ICCloudClientFactory.CLOUD_CODE_WEB}/accounts/update",
            object : BaseController() {
                override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
                    return if (isPost(request)) {
                        jsonRequestResponse(request, AccountsUpdateRequest::class, response) {
                            checkManageCloudPermission()

                            cloudProfilesService.updateProfileAccounts(it!!)

                            null
                        }
                    } else {
                        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Only POST method is allowed")
                        null
                    }
                }
            }
        )

    }

}