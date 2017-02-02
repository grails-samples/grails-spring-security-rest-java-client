package grailsclient;

import org.grails.springsecurityrest.client.Jwt;

public interface  JwtStorage {

    Jwt getJwt();

    void saveJwt(Jwt jwt);
}
