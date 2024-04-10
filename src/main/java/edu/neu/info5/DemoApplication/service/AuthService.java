package edu.neu.info5.DemoApplication.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthService {

    private static final JacksonFactory jacksonFactory = new JacksonFactory();

    private String GOOGLE_CLIENT_ID = "160308557197-v4hv16s8kk620or7bsr8bf80ueqhmdtm.apps.googleusercontent.com";

    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new ApacheHttpTransport(), jacksonFactory)
            .setAudience(Collections.singletonList(GOOGLE_CLIENT_ID)).build();

    public boolean authorize(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if(idToken != null) return true;

            return false;

        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}

