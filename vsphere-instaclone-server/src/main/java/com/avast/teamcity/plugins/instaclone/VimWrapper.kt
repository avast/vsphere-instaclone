package com.avast.teamcity.plugins.instaclone

import com.vmware.vim25.*
import javax.xml.soap.DetailEntry
import javax.xml.ws.soap.SOAPFaultException

class VimWrapper(
        val port: VimPortType,
        private val username: String,
        private val password: String) {

    val serviceContent: ServiceContent = port.retrieveServiceContent(serviceInstance)

    init {
        port.login(serviceContent.sessionManager, username, password, null)
    }

    fun<T> authenticated(block: VimWrapper.() -> T): T {
        rep@ while (true) {
            try {
                return this.block()
            } catch (e: SOAPFaultException) {
                for (it in e.fault.detail.detailEntries) {
                    val entry = it as DetailEntry
                    if (entry.elementQName.localPart == "NotAuthenticatedFault") {
                        port.login(serviceContent.sessionManager, username, password, null)
                        continue@rep
                    }
                }
                throw e
            }
        }
    }

    fun getProperty(obj: ManagedObjectReference, name: String): Any? {
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

        val res = authenticated {
            port.retrievePropertiesEx(serviceContent.propertyCollector, listOf(filterSpec), RetrieveOptions())
        }

        for (robj in res.objects) {
            if (robj.propSet != null && robj.propSet.size != 0)
                return robj.propSet[0].getVal()
        }

        return null
    }

    companion object {
        val serviceInstance = ManagedObjectReference().apply {
            type = "ServiceInstance"
            value = "ServiceInstance"
        }
    }
}