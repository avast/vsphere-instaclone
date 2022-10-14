package com.avast.teamcity.plugins.instaclone

import com.avast.teamcity.plugins.instaclone.web.service.VCenterAccountService
import com.vmware.vim25.*
import javax.xml.soap.DetailEntry
import javax.xml.ws.soap.SOAPFaultException

class VimWrapper(
    private val port: VimPortType,
    private val vCenterAccountId: String,
    private val accountService: VCenterAccountService,
    private val pluginClassLoader: ClassLoader
) {

    val serviceContent: ServiceContent = port.retrieveServiceContent(serviceInstance)

    private var sessionId: String = ""

    private fun doLogin(faultMessage: String) {
        pluginClassLoader.inContext {
            val account = accountService.getAccountById(vCenterAccountId)!!
            val sessionActive = sessionId != "" && try {
                port.sessionIsActive(serviceContent.sessionManager, sessionId, account.username)
            } catch (e: Exception) {
                false
            }

            if (sessionActive) {
                throw RuntimeException(faultMessage)
            }

            val userSession = port.login(serviceContent.sessionManager, account.username, account.password, null)
            sessionId = userSession.key
        }
    }

    fun <T> unauthenticated(block: (port: VimPortType) -> T): T {
        return pluginClassLoader.inContext {
            block(port)
        }
    }

    fun connectionLoginTest() {
        return try {
            val account = accountService.getAccountById(vCenterAccountId)!!
            port.login(serviceContent.sessionManager, account.username, account.password, null)
            val property = getProperty(serviceContent.rootFolder, "name")
        } finally {
            try {
                port.logout(serviceContent.sessionManager)
            } catch (e: Exception) {
                // ignore
            }
        }
    }



    fun <T> authenticated(block: (port: VimPortType) -> T): T {
        rep@ while (true) {
            try {
                return unauthenticated(block)
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

    fun getNameProperty(obj: ManagedObjectReference): String {
        return this.getProperty(obj, "name") as String
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
                if (content.propSet.size != 1) {
                    throw RuntimeException("Property $name is not in missing set, but its value is not defined")
                }
                return content.propSet[0].getVal()
            }

            for (missing in content.missingSet) {
                if (missing.fault.fault !is NotAuthenticated) {
                    if (missing.fault.fault is InvalidProperty) {
                        throw RuntimeException("Invalid property '$name' - error message: ${missing.fault.localizedMessage ?: missing.fault.fault.faultMessage}")
                    }
                    throw RuntimeException(missing.fault.localizedMessage)
                }
            }

            /// missing -> NotAuthenticated
            doLogin(content.missingSet[0].fault.localizedMessage ?: "Not Authenticated")
        }
    }

    fun getEntities(managedObjectReference: ManagedObjectReference, propertyName : String): Any {
        return getProperty(managedObjectReference, propertyName)
    }


    fun getFolderVirtualMachines(instanceFolder: ManagedObjectReference): List<ManagedObjectReference> {
        val children = getProperty(instanceFolder, "childEntity") as ArrayOfManagedObjectReference

        return children.managedObjectReference.filter { mof -> mof.type == VM_TYPE }
    }


    companion object {
        const val VM_TYPE = "VirtualMachine"
        val serviceInstance = ManagedObjectReference().apply {
            type = "ServiceInstance"
            value = "ServiceInstance"
        }
    }
}
