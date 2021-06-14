package com.avast.teamcity.plugins.instaclone.web.service;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.crypt.DecryptionFailedException;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.crypt.EncryptionFailedException;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class intended to simplify RSA encryption / decryption.
 */
public class RSACipher {
    public static final int MAX_ENCRYPTED_DATA_SIZE = 124;
    public static final int MAX_NON_ASCII_ENCRYPTED_DATA_SIZE = 58;
    private static final Logger LOG = Logger.getInstance(RSACipher.class.getSimpleName());
    private static final int KEYSIZE = 2048;
  private static final int AES_KEYSIZE = 256;
  private static Cipher ourCipher;

    static {
        try {
            prepareCipher();
        } catch (final Throwable e) {
            LOG.error("Failed to initialize jetbrains.buildServer.serverSide.crypt.RSACipher class: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private RSACipher() {
    }

    /**
     * Generates new public and private keys and stores them in static fields.
     */
    public synchronized static void generateKeys() {
        try {
            KeyPairGenerator keyGen;
            keyGen = KeyPairGenerator.getInstance("RSA");
            final SecureRandom random = new SecureRandom();
            random.setSeed(System.currentTimeMillis());
            keyGen.initialize(new RSAKeyGenParameterSpec(KEYSIZE, RSAKeyGenParameterSpec.F4), random);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized static void prepareCipher() throws NoSuchAlgorithmException, NoSuchPaddingException {
        try {
            ourCipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING");
        } catch (NoSuchAlgorithmException e) {
            //
        } catch (NoSuchPaddingException e) {
            //
        } finally {
            if (ourCipher == null) {
                try {
                    ourCipher = Cipher.getInstance("RSA", "SunJCE"); // try to use default Java RSA cipher which is known to work with our rsa.js
                } catch (NoSuchProviderException e) {
                    ourCipher = Cipher.getInstance("RSA");
                }
            }
        }
    }

    private static Cipher getCipher() {
        return ourCipher;
    }

    /**
     * Decrypts hex encoded JavaScript encrypted data.
     *
     * @param hexEncoded hex encoded encrypted byte array
     * @param aPrivate
     * @return decrypted string
     * @throws DecryptionFailedException if decryption failed
     */
    @Nullable
    public static String decryptWebRequestData(String hexEncoded, PrivateKey aPrivate) throws DecryptionFailedException {
        if (hexEncoded == null) return null;

        int partLen = 2 * KEYSIZE / 8;
        List<String> parts = new ArrayList<String>();

        for (int i = 0; i < hexEncoded.length(); i += partLen) {
            String part = hexEncoded.substring(i, Math.min(i + partLen, hexEncoded.length()));
            parts.add(part);
        }

        StringBuilder res = new StringBuilder();
        for (String part : parts) {
            byte[] data = decrypt(part, aPrivate);
            if (data != null && data.length == 0 && parts.size() == 1) return "";

            if (data == null || data.length == 0) {
                throw new DecryptionFailedException("Failed to decrypt data");
            }

            try {
                byte length = data[data.length - 1];
                String decrypted = new String(data, 0, data.length - 1, "ISO-8859-1");
                if (decrypted.length() == length) {
                    res.append(decrypted);
                    continue;
                }

                decrypted = new String(data, 0, data.length - 1, "UTF-8");
                if (decrypted.length() == length) {
                    res.append(decrypted);
                    continue;
                }

                decrypted = new String(data, 0, data.length - 1, "UTF-16");
                if (decrypted.length() == length) {
                    res.append(decrypted);
                    continue;
                }

                throw new DecryptionFailedException("Failed to decrypt data");
            } catch (UnsupportedEncodingException e) {
                throw new DecryptionFailedException(e);
            }
        }

        return res.toString();
    }

    @Nullable
    private static byte[] decrypt(final String hexEncoded, PrivateKey aPrivate) {
        if (hexEncoded == null) return null;

        try {
            return encryptOrDecrypt(Cipher.DECRYPT_MODE, aPrivate, EncryptUtil.fromHex(hexEncoded));
        } catch (Exception e) {
            throw new DecryptionFailedException(e);
        }
    }

    /**
     * Encrypts specified string, and returns hex representation of the encrypted data
     *
     * @param plain text to encrypt
     * @return hex representation of the encrypted data
     * @throws EncryptionFailedException if encryption failed
     */
//  @Nullable
//  public static String encryptData(String plain) throws EncryptionFailedException {
//    return encryptData(plain, getKeys().getPublic());
//  }

    /**
     * Encrypts specified string, and returns hex representation of the encrypted data.
     * It differs from {@link #String)} in that it stores length of the encrypted string
     * as a last byte into byte array before encryption
     *
     * @param plain   text to encrypt
     * @param aPublic
     * @return hex representation of the encrypted data
     * @throws EncryptionFailedException if encryption failed
     */
    public static String encryptDataForWeb(String plain, PublicKey aPublic) throws EncryptionFailedException {
        if (plain == null) return null;
        if (plain.isEmpty()) {
            return encryptBytes(new byte[0], aPublic);
        }

        int packSize = MAX_ENCRYPTED_DATA_SIZE;
        for (char c : plain.toCharArray()) {
            if (c >> 8 > 0) {
                packSize = MAX_NON_ASCII_ENCRYPTED_DATA_SIZE;
                break;
            }
        }

        List<String> parts = new ArrayList<String>();
        for (int i = 0; i < plain.length(); i += packSize) {
            int endIdx = Math.min(plain.length(), i + packSize);
            String part = plain.substring(i, endIdx);

            final byte[] dataBytes = part.getBytes(StandardCharsets.UTF_8);
            final int length = dataBytes.length;
            byte[] bytes = new byte[length + 1];
            System.arraycopy(dataBytes, 0, bytes, 0, length);
            bytes[length] = (byte) part.length();
            parts.add(encryptBytes(bytes, aPublic));
        }

        return StringUtil.join("", parts);
    }

    @Nullable
    private static String encryptBytes(byte[] bytes, PublicKey pubKey) throws EncryptionFailedException {
        if (bytes == null) return null;
        try {
            byte[] data = encryptOrDecrypt(Cipher.ENCRYPT_MODE, pubKey, bytes);
            return EncryptUtil.toHex(data);
        } catch (Exception e) {
            throw new EncryptionFailedException(e);
        }
    }

    private synchronized static byte[] encryptOrDecrypt(int mode, Key key, final byte[] data)
            throws IOException, InvalidKeyException {
        Cipher rsa = getCipher();
        rsa.init(mode, key);

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        OutputStream cOut = new CipherOutputStream(bOut, rsa);
        cOut.write(data);
        cOut.close();
        return bOut.toByteArray();
    }

    public Key generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(AES_KEYSIZE);
        return kgen.generateKey();
    }

}
