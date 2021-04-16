package com.avast.teamcity.plugins.instaclone.web

import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ICCloudProfileSettings(
        private val pluginDescriptor: PluginDescriptor,
        webControllerManager: WebControllerManager) : BaseController() {

    init {
        webControllerManager.registerController(
                pluginDescriptor.getPluginResourcesPath("vmware-instaclone-profile-settings.html"),
                this)
    }

    @Throws(Exception::class)
    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView {
        val path = pluginDescriptor.getPluginResourcesPath("cloud-profile-settings.jsp")
        return ModelAndView(path)
    }
}