package com.avast.teamcity.plugins.instaclone.web

import com.avast.teamcity.plugins.instaclone.web.service.VCenterAccountService
import com.avast.teamcity.plugins.instaclone.web.service.profile.VCenterAccount
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ICCloudProfileSettings(
    private val pluginDescriptor: PluginDescriptor,
    private val vCenterAccountService: VCenterAccountService,
    webControllerManager: WebControllerManager
) : BaseController() {

    init {
        webControllerManager.registerController(
            pluginDescriptor.getPluginResourcesPath("vmware-instaclone-profile-settings.html"),
            this
        )
    }

    @Throws(Exception::class)
    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView {
        val path = pluginDescriptor.getPluginResourcesPath("cloud-profile-settings.jsp")
        val modelAndView = ModelAndView(path)
        modelAndView.addObject("accountsException", "")
        try {
            modelAndView.model["vCenterAccounts"] = vCenterAccountService.listAccounts().accounts
        } catch (e: Exception) {
            modelAndView.addObject("accountsException", e.message)
            modelAndView.addObject("vCenterAccounts", emptyList<VCenterAccount>())
        }
        return modelAndView
    }
}