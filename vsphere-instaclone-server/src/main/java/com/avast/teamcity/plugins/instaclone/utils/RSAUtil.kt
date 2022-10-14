package com.avast.teamcity.plugins.instaclone.utils

import com.intellij.openapi.diagnostic.Logger
import org.apache.commons.codec.binary.Base64
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.NoSuchPaddingException


object RSAUtil {
    private val logger = Logger.getInstance(
        RSAUtil::class.java.name
    )

    fun getPublicKey(encodedKey: ByteArray): PublicKey {
        try {
            val keySpec = X509EncodedKeySpec(encodedKey)

            return KeyFactory.getInstance("RSA").generatePublic(keySpec)
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Error getting public key", e)
            throw RuntimeException("Failed to get public key", e)
        } catch (e: InvalidKeySpecException) {
            logger.error("Error getting public key", e)
            throw RuntimeException("Failed to get public key", e)
        }
    }


    fun getPrivateKeyPem(file: File): PrivateKey {
        FileReader(file).use { keyReader ->
            val pemParser = PEMParser(keyReader)

            val readObject = pemParser.readObject()
            val privateKeyInfo =
                if (readObject is PEMKeyPair) {
                    PrivateKeyInfo.getInstance(readObject.privateKeyInfo)
                } else {
                    PrivateKeyInfo.getInstance(readObject)
                }

            return JcaPEMKeyConverter().getPrivateKey(privateKeyInfo)
        }
    }


    fun getPrivateKeyPem(encodeKey: ByteArray): PrivateKey {
        return getPrivateKey(decodePem(encodeKey))
    }

    fun getPublicKeyPem(encodeKey: ByteArray): PublicKey {
        return getPublicKey(decodePem(encodeKey))
    }

    private fun decodePem(encodeKey: ByteArray): ByteArray {
        val content = String(encodeKey, StandardCharsets.US_ASCII)
        val keyPem: String = content
            .replace(Regex("-----BEGIN.*?KEY-----"), "")
            .replace("\r\n", "")
            .replace("\n", "")
            .replace(Regex("-----END.*?KEY-----"), "")
        return Base64.decodeBase64(keyPem)
    }

    fun getPrivateKey(encodeKey: ByteArray): PrivateKey {
        val keySpec = PKCS8EncodedKeySpec(encodeKey)
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        } catch (e: InvalidKeySpecException) {
            logger.error("Error getting private key", e)
            throw RuntimeException("Failed to get private key", e)
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Error getting private key", e)
            throw RuntimeException("Failed to get private key", e)
        }
    }

    fun encrypt(data: ByteArray, publicKey: PublicKey): ByteArray {
        return encryptOrDecrypt(Cipher.ENCRYPT_MODE, publicKey, data)
    }

    fun decrypt(data: ByteArray, privateKey: PrivateKey): ByteArray {
        return encryptOrDecrypt(Cipher.DECRYPT_MODE, privateKey, data)
    }

    @Synchronized
    @Throws(
        IOException::class,
        NoSuchProviderException::class,
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class
    )

    private fun encryptOrDecrypt(mode: Int, key: Key, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING")
        cipher.init(mode, key)
        val bOut = ByteArrayOutputStream()
        val cOut = CipherOutputStream(bOut, cipher)
        cOut.write(data)
        cOut.flush()
        cOut.close()
        return bOut.toByteArray()
    }

}