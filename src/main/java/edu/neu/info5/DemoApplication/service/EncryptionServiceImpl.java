package edu.neu.info5.DemoApplication.service;

import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class EncryptionServiceImpl implements EncryptionService{
    @Override
    public String encrypt(String data){
        String md5Hash;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(data.getBytes(StandardCharsets.UTF_8));
            BigInteger no = new BigInteger(1, hashBytes);

            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;

        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error calculating MD5 hash: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
