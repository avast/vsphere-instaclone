package com.avast.teamcity.plugins.instaclone.utils

import com.avast.teamcity.plugins.instaclone.web.ApiException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.auth.AuthUtil
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.serverSide.auth.SecurityContext
import org.apache.commons.io.IOUtils
import org.springframework.http.MediaType
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KClass

/**
 *
 * @author Vitasek L.
 */
abstract class BaseJsonController(
    private val securityContext: SecurityContext,
    private val projectManager: ProjectManager
) {

    private var mapper = jacksonObjectMapper()
    private val logger = Logger.getInstance(BaseJsonController::class.java.name)

    fun checkProjectEditPermission(projectId: String) {
        if (!AuthUtil.hasProjectPermission(
                securityContext.authorityHolder,
                translateProjectId(projectId),
                Permission.MANAGE_AGENT_CLOUDS
            )
        ) {
            throw IllegalAccessException("User has not edit permissions to access project with id $projectId")
        }
    }

    fun checkManageCloudPermission() {
        if (!AuthUtil.hasProjectPermission(
                securityContext.authorityHolder, projectManager.rootProject.projectId, Permission.MANAGE_AGENT_CLOUDS
            )
        ) {
            throw IllegalAccessException("User has not permissions to edit cloud profiles")
        }
    }


    private fun translateProjectId(extProjectId: String): String {
        return projectManager.findProjectByExternalId(extProjectId)!!.projectId
    }

    fun <T : Any> jsonRequestResponse(
        request: HttpServletRequest,
        requestClass: KClass<T>,
        response: HttpServletResponse,
        resultValue: (T?) -> Any?
    ): ModelAndView? {
        response.contentType = MediaType.APPLICATION_JSON_UTF8_VALUE

        val out = response.writer
        try {
            val parsedBody : T? = when (requestClass) {
                Void::class -> null
                ByteArray::class -> IOUtils.toByteArray(request.reader) as T
                else -> {
                    requestClass.let {
                        try {
                            mapper.readValue(request.reader, requestClass.java)
                        } catch (e: Exception) {
                            logger.error("Failed to parse request", e)
                            throw ApiException("Failed to parse request: ${e.message}", e)
                        }
                    }
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
        } catch (accessEx: IllegalAccessException) {
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