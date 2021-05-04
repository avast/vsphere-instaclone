package com.avast.teamcity.plugins.instaclone.web.service

import com.avast.teamcity.plugins.instaclone.ICCloudClientFactory
import com.avast.teamcity.plugins.instaclone.utils.AESUtil
import com.avast.teamcity.plugins.instaclone.utils.RSAUtil
import com.avast.teamcity.plugins.instaclone.web.ApiException
import com.avast.teamcity.plugins.instaclone.web.service.profile.AccountsUpdateRequest
import com.avast.teamcity.plugins.instaclone.web.service.profile.VCenterAccount
import com.avast.teamcity.plugins.instaclone.web.service.profile.VCenterAccountList
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.SystemProperties
import jetbrains.buildServer.clouds.server.CloudManagerBase
import jetbrains.buildServer.serverSide.CustomDataStorage
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.apache.commons.codec.binary.Base64
import java.nio.file.Files
import java.nio.file.Paths
import java.security.PrivateKey

/**
 *
 * @author Vitasek L.
 */

class VCenterAccountService(
    private val cloudManagerBase: CloudManagerBase,
    private val projectManager: ProjectManager,
    private val pluginDescriptor: PluginDescriptor
) {

    private var mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    private val logger = Logger.getInstance(VCenterAccountService::class.java.name)

    private val accountLock = Any()

    private var accountList: VCenterAccountList = VCenterAccountList()
    private var accountListInit = false

    private fun loadPrivateKey(): PrivateKey {
        val pkPath = getPrivateKeyPath()
        logger.info("VCenterAccountService - Path for Account Private key is $pkPath")

        if (pkPath != null) {
            val file = Paths.get(pkPath)
            val toFile = file.toFile()
            if (toFile.isFile && toFile.exists()) {
                try {
                    return RSAUtil.getPrivateKey(Files.readAllBytes(file))
                } catch (e: Exception) {
                    logger.error("VCenterAccountService - couldn't load Accounts Private Key $file", e)
                    throw RuntimeException(
                        "VCenterAccountService - couldn't load Accounts Private Key $file: ${e.message}",
                        e
                    )
                }
            }
        }

        logger.error("VCenterAccountService account private key is not defined")
        throw RuntimeException("VCenterAccountService account private key is not defined")
    }

    private fun getPrivateKeyPath(): String? {
        var path = pluginDescriptor.getParameterValue("accountsPkPath")
        if (path == null) {
            path = TeamCityProperties.getPropertyOrNull("teamcity.vsphereinstaclone.accountsPkPath")
            if (path == null) {
                path =
                    Paths.get(SystemProperties.getUserHome(), "vsphereinstaclone", "accountsPk").toFile().absolutePath
            }
        }
        return path

    }

    fun getAccountIdByUrl(sdkUrl: String): String? {
        return listAccounts().accounts.firstOrNull { account -> account.url == sdkUrl }?.id
    }

    fun getAccountByUrl(sdkUrl: String): VCenterAccount? {
        return listAccounts().accounts.firstOrNull { account -> account.url == sdkUrl }
    }

    fun getAccountById(id: String?): VCenterAccount? {
        if (id == null) {
            return null
        }
        return listAccounts().accounts.firstOrNull { account -> account.id == id }
    }


    fun storeAccounts(request: AccountsUpdateRequest): VCenterAccountList {
        synchronized(accountLock) {
            val vCenterAccounts = decrypt(request.accounts, request.aes256)
            val customDataStorage = getDataStorage()
            customDataStorage.putValue(DATASTORAGE_INFO, request.accounts)
            customDataStorage.putValue(ACCOUNTS_AES, request.aes256)
            customDataStorage.flush()
            accountList = vCenterAccounts

            accountListInit = true
            return accountList
        }
    }


    fun listAccounts(): VCenterAccountList {
        synchronized(accountLock) {
            if (!accountListInit) {
                accountList = reloadAccounts()
                accountListInit = true
            }
            return accountList
        }
    }

    private fun reloadAccounts(): VCenterAccountList {
        synchronized(accountLock) {
            val accounts = getDataStorage().getValue(DATASTORAGE_INFO) ?: return VCenterAccountList()

            val aesBase64Encoded = getDataStorage().getValue(ACCOUNTS_AES)

            accountList = decrypt(accounts, aesBase64Encoded!!)

            return accountList
        }
    }

    private fun decrypt(accountsBase64Encoded: String, aesBase64Encoded: String): VCenterAccountList {
        val decipheredAES = RSAUtil.decrypt(Base64.decodeBase64(aesBase64Encoded), loadPrivateKey())
        if (decipheredAES.isEmpty()) {
            throw ApiException("Couldn't decrypt AES key - wrong account private key?")
        }
        val decryptedContent = AESUtil.decrypt(Base64.decodeBase64(accountsBase64Encoded), decipheredAES)
        return mapper.readValue(decryptedContent)
    }

    private fun getDataStorage(): CustomDataStorage {
        return projectManager.rootProject.getCustomDataStorage("vCenterAccounts")
    }

    fun updateAccountProperties(vCenterAccount: String, profileProperties: MutableMap<String, String>) {
        val account =
            getAccountById(vCenterAccount) ?: throw ApiException("Cannot find vCenterAccount by ID $vCenterAccount")

        profileProperties[ICCloudClientFactory.PROP_VCENTER_ACCOUNT] = vCenterAccount
        profileProperties[ICCloudClientFactory.PROP_VCENTER_ACCOUNT_HASH] = account.hash()

    }

    companion object {
        const val DATASTORAGE_INFO: String = "accounts"
        const val ACCOUNTS_AES: String = "accounts_aes"
    }

}