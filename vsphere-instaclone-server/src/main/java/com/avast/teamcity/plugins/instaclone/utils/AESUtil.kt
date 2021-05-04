package com.avast.teamcity.plugins.instaclone.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.InvalidKeyException
import java.security.Key
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.SecretKeySpec

object AESUtil {

    fun encrypt(data: ByteArray, secretKey: Key): ByteArray {
        return encryptOrDecrypt(Cipher.ENCRYPT_MODE, secretKey, data)
    }

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        return encryptOrDecrypt(Cipher.DECRYPT_MODE, getAESKey(key), data)
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
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(mode, key)
        val bOut = ByteArrayOutputStream()
        val cOut = CipherOutputStream(bOut, cipher)
        cOut.write(data)
        cOut.flush()
        cOut.close()
        return bOut.toByteArray()
    }

    @Throws(NoSuchAlgorithmException::class)
    fun generateAESKey(): Key {
        val kgen = KeyGenerator.getInstance(AES_ALGORITHM)
        kgen.init(256)
        return kgen.generateKey()
    }

    fun getAESKey(key: ByteArray): Key {
        return SecretKeySpec(key, AES_ALGORITHM)
    }


    private const val AES_ALGORITHM = "AES"

}