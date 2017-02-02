package grailsclient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import grailsclient.model.Project;
import grailsclient.model.TDSApiError;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.grails.springsecurityrest.client.*;

import java.io.IOException;

import java.lang.reflect.Type;
import java.util.List;

class TDSApi {
    private static final String API_VERSION = "1.0";
    private static final String HTTP_HEADER_ACCEPT_VERSION = "Accept-Version";
    private static final String HTTP_HEADER_ACCEPT = "Accept";
    private static final String HTTP_HEADER_ACCEPT_VALUE = "application/json";
    private static final String HTTP_HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HTTP_HEADER_CONTENT_TYPE_JSON = "application/json";
    private static final String HTTP_HEADER_AUTHORIZATION = "Authorization";
    private static final String HTTP_HEADER_AUTHORIZATION_BEARER = "Bearer";

    private OkHttpClient client = new OkHttpClient();

    private JwtStorage jwtStorage;
    private String serverUrl;

    public TDSApi(String serverUrl, JwtStorage jwtStorage) {
        this.serverUrl = serverUrl;
        this.jwtStorage = jwtStorage;
    }

    public void authenticate(String username, String password) {
        GrailsSpringSecurityRestClient client = new GrailsSpringSecurityRestClient();
        AuthenticationRequest authenticationRequest = new AuthenticationRequest.Builder()
                .serverUrl(serverUrl)
                .username(username)
                .password(password)
                .build();
        JwtResponse rsp = client.authenticate(authenticationRequest);
        if (rsp instanceof JwtResponseOK && jwtStorage != null) {
            jwtStorage.saveJwt(((JwtResponseOK) rsp).getJwt());
        }
    }

    public void fetchProjects(FetchProjectsListener listener) {
        try {
            Response response = executeFetchProjects();
            fetchProjectsResponse(response, listener);

        } catch (IOException e) {
            e.printStackTrace();
            if (listener != null) {
                listener.projectsFetched(null, TDSApiError.NETWORKING_ERROR);
            }
        }
    }

    private Response executeFetchProjects() throws IOException {
        Request request = new Request.Builder()
                .header(HTTP_HEADER_ACCEPT_VERSION, API_VERSION)
                .header(HTTP_HEADER_ACCEPT, HTTP_HEADER_ACCEPT_VALUE)
                .header(HTTP_HEADER_AUTHORIZATION, authorizationHeaderValue())
                .url(serverUrl + "/api/projects")
                .get()
                .build();
        return client.newCall(request).execute();
    }

    private void refreshAccessToken() {
        String refreshToken = jwtStorage.getJwt().getRefreshToken();
        RefreshRequest refreshRequest = new RefreshRequest.Builder()
                .serverUrl(serverUrl)
                .refreshToken(refreshToken)
                .build();
        GrailsSpringSecurityRestClient client = new GrailsSpringSecurityRestClient();
        JwtResponse jwtResponse = client.refreshToken(refreshRequest);
        if (jwtResponse instanceof JwtResponseOK) {
            Jwt jwt = ((JwtResponseOK) jwtResponse).getJwt();
            jwtStorage.saveJwt(jwt);
        }
    }

    private String authorizationHeaderValue() {
        return HTTP_HEADER_AUTHORIZATION_BEARER
                + " "
                + jwtStorage.getJwt().getAccessToken();
    }

    private void fetchProjectsResponse(Response response, FetchProjectsListener listener) throws IOException {
        if (response.code() == 200) {
            processOKProjectsResponse(response, listener);
            return;
        }

        if (response.code() == 401) {
            refreshAccessToken();
            Response rsp = executeFetchProjects();
            if (rsp.code() == 200) {
                processOKProjectsResponse(response, listener);
                return;
            }
        }
        if (listener != null) {
            listener.projectsFetched(null, TDSApiError.NETWORKING_ERROR);
        }
    }

    private static void processOKProjectsResponse(Response response, FetchProjectsListener listener) {
        try {

            String jsonString = response.body().string();
            Type listType = new TypeToken<List<Project>>() {}.getType();
            Gson gson = new Gson();
            List<Project> projects = gson.fromJson(jsonString, listType);
            if (listener != null) {
                listener.projectsFetched(projects, TDSApiError.NONE);
            }

        } catch (IOException e) {
            e.printStackTrace();
            if (listener != null) {
                listener.projectsFetched(null, TDSApiError.JSON_PARSING_ERROR);
            }
        }
    }
}

interface FetchProjectsListener {
    void projectsFetched(List<Project> projects, TDSApiError error);
}