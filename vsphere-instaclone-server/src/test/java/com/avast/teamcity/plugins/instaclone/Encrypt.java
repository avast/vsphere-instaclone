package com.avast.teamcity.plugins.instaclone;

import com.avast.teamcity.plugins.instaclone.utils.AESUtil;
import com.avast.teamcity.plugins.instaclone.web.service.RSACipher;
import com.avast.teamcity.plugins.instaclone.utils.RSAUtil;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author Vitasek L.
 */
@Ignore
public class Encrypt {

    @Test
    public void testEncrypt2() throws IOException {

        final byte[] publicKeyBytes = Files.readAllBytes(Paths.get("c:\\develope\\vsphere-instaclone\\KeyPair\\publicKey"));
        final PublicKey pk = RSAUtil.INSTANCE.getPublicKey(publicKeyBytes);

        final byte[] privateKeyBytes = Files.readAllBytes(Paths.get("c:\\develope\\vsphere-instaclone\\KeyPair\\privateKey"));
        final PrivateKey privateKey = RSAUtil.INSTANCE.getPrivateKey(privateKeyBytes);

        final byte[] data = Files.readAllBytes(Paths.get("c:\\develope\\vsphere-instaclone\\KeyPair\\accounts.yaml"));


        final String enc = RSACipher.encryptDataForWeb(new String(data, StandardCharsets.UTF_8), pk);
        // final String enc = RSACipher.encryptDataForWeb("AAAAAAAAAAAAAAAAAAAA");

        Files.write(Paths.get("c:\\develope\\vsphere-instaclone\\KeyPair\\accounts.yaml.enc"), enc.getBytes(StandardCharsets.UTF_8));

        final String hexEncoded = new String(Files.readAllBytes(Paths.get("c:\\develope\\vsphere-instaclone\\KeyPair\\accounts.yaml.enc")), StandardCharsets.UTF_8);


        final String s = RSACipher.decryptWebRequestData(hexEncoded, privateKey);
        System.out.println("s = " + s);

    }

    private String decrypt(String accountsHexEncoded, String aesHexEncoded, PrivateKey privateKey) throws DecoderException {
        byte[] decodedAES = RSAUtil.INSTANCE.decrypt(Hex.decodeHex(aesHexEncoded.toCharArray()), privateKey);
        byte[] decryptedContent = AESUtil.INSTANCE.decrypt(Hex.decodeHex(accountsHexEncoded.toCharArray()), decodedAES);

        return new String(decryptedContent);
    }


    @Test
    public void testEncrypt3() throws IOException, NoSuchAlgorithmException, DecoderException {

        final byte[] publicKeyBytes = Files.readAllBytes(Paths.get("c:\\develope\\vsphere-instaclone\\KeyPair\\publicKey"));
        final PublicKey pk = RSAUtil.INSTANCE.getPublicKey(publicKeyBytes);

        final byte[] privateKeyBytes = Files.readAllBytes(Paths.get("c:\\develope\\vsphere-instaclone\\KeyPair\\privateKey"));
        final PrivateKey privateKey = RSAUtil.INSTANCE.getPrivateKey(privateKeyBytes);

        final byte[] data = Files.readAllBytes(Paths.get("c:\\develope\\vsphere-instaclone\\KeyPair\\accounts.yaml"));

        final Key aesKey = AESUtil.INSTANCE.generateAESKey();

        final char[] encryptedAccounts = Hex.encodeHex(AESUtil.INSTANCE.encrypt(data, aesKey));
        final char[] encryptedAES = Hex.encodeHex(RSAUtil.INSTANCE.encrypt(aesKey.getEncoded(), pk));

        final String decrypt = decrypt(new String(encryptedAccounts), new String(encryptedAES), privateKey);

        System.out.println("decrypt = " + decrypt);
    }
}
