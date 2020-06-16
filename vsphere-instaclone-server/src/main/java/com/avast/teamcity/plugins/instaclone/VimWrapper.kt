package com.avast.teamcity.plugins.instaclone

import com.vmware.vim25.*
import java.lang.Exception
import javax.xml.soap.DetailEntry
import javax.xml.ws.soap.SOAPFaultException

class VimWrapper(
        val port: VimPortType,
        private val username: String,
        private val password: String) {

    val serviceContent: ServiceContent = port.retrieveServiceContent(serviceInstance)

    private var sessionId: String = ""

    private fun doLogin(faultMessage: String) {
        val sessionActive = sessionId != "" && try {
            port.sessionIsActive(serviceContent.sessionManager, sessionId, username)
        } catch(e: Exception) {
            false
        }

        if (sessionActive) {
            throw RuntimeException(faultMessage)
        }

        sessionId = port.login(serviceContent.sessionManager, username, password, null).key
    }

    fun<T> authenticated(block: VimWrapper.() -> T): T {
        rep@ while (true) {
            try {
                return this.block()
            } catch (e: SOAPFaultException) {
                for (it in e.fault.detail.detailEntries) {
                    val entry = it as DetailEntry
                    if (entry.elementQName.localPart == "NotAuthenticatedFault") {
                        doLogin(e.localizedMessage)
                        continue@rep
                    }
                }
                throw e
            }
        }
    }

    fun getProperty(obj: ManagedObjectReference, name: String): Any {
        val filterSpec = PropertyFilterSpec()
        val objectSpec = ObjectSpec().also {
            it.obj = obj
        }
        objectSpec.obj = obj
        filterSpec.objectSet.add(objectSpec)

        filterSpec.propSet.add(PropertySpec().apply {
            type = obj.type
            pathSet.add(name)
        })

        while (true) {
            val retrieveResult = authenticated {
                port.retrievePropertiesEx(serviceContent.propertyCollector, listOf(filterSpec), RetrieveOptions())
            }
            assert(retrieveResult.objects.size == 1)

            val content = retrieveResult.objects[0]
            if (content.missingSet.isEmpty()) {
                assert(content.propSet.size == 1)
                return content.propSet[0].getVal()
            }

            for (missing in content.missingSet) {
                if (missing.fault.fault !is NotAuthenticated) {
                    throw RuntimeException(missing.fault.localizedMessage)
                }
            }

            doLogin(content.missingSet[0].fault.localizedMessage)
        }
    }

    companion object {
        val serviceInstance = ManagedObjectReference().apply {
            type = "ServiceInstance"
            value = "ServiceInstance"
        }
    }
}