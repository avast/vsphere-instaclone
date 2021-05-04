package com.avast.teamcity.plugins.instaclone.web.service

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.*
import java.security.spec.RSAKeyGenParameterSpec

class GenerateKeys(keylength: Int) {
    private val keyGen: KeyPairGenerator
    private var pair: KeyPair? = null
    var privateKey: PrivateKey? = null
        private set
    var publicKey: PublicKey? = null
        private set

    fun createKeys() {
        val pair = keyGen.generateKeyPair()
        this.pair = pair
        privateKey = pair.private
        publicKey = pair.public
    }

    @Throws(IOException::class)
    fun writeToFile(path: String, key: ByteArray) {
        val f = File(path)
        f.parentFile.mkdirs()
        val fos = FileOutputStream(f)
        fos.write(key)
        fos.flush()
        fos.close()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val gk: GenerateKeys
            try {
                gk = GenerateKeys(2048)
                gk.createKeys()
                gk.writeToFile("KeyPair/publicKey", gk.publicKey!!.encoded)
                gk.writeToFile("KeyPair/privateKey", gk.privateKey!!.encoded)
            } catch (e: NoSuchAlgorithmException) {
                System.err.println(e.message)
            } catch (e: NoSuchProviderException) {
                System.err.println(e.message)
            } catch (e: IOException) {
                System.err.println(e.message)
            }
        }
    }

    init {
        keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(RSAKeyGenParameterSpec(keylength, RSAKeyGenParameterSpec.F4))
    }
}