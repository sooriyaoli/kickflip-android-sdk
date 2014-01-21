package io.kickflip.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientCredentialsTokenRequest;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import java.io.IOException;

/**
 * Created by davidbrodsky on 1/14/14.
 */
public abstract class OAuthClient {
    private static final String TAG = "OAuthClient";

    // For SharedPreferences storage
    private final String ACCESS_TOKEN_KEY = "AT";

    private HttpRequestFactory mRequestFactory;         // RequestFactory cached for life of mOAuthAccessToken
    private String mOAuthAccessToken;
    private OAuthConfig mConfig;                        // Immutable OAuth Configuration
    private SharedPreferences mStorage;
    private Context mContext;                           // Application Context

    public OAuthClient(Context context, OAuthConfig config) {
        mConfig = config;
        mContext = context;
        mStorage = context.getSharedPreferences(mConfig.getCredentialStoreName(), Context.MODE_PRIVATE);
    }

    public Context getContext() {
        return mContext;
    }

    public OAuthConfig getConfig() {
        return mConfig;
    }

    public SharedPreferences getStorage(){ return mStorage; }

    /**
     * Force clear and re-acquire an OAuth Acess Token
     */
    protected void refreshAccessToken(){
        refreshAccessToken(null);
    }

    /**
     * Force clear and re-acquire an OAuth Acess Token
     * cb is always called on a background thread
     */
    protected void refreshAccessToken(final OAuthCallback cb){
        clearAccessToken();
        acquireAccessToken(cb);
    }

    /**
     * Asynchronously attempt to jsonRequest an OAuth Access Token
     */
    protected void acquireAccessToken() {
        acquireAccessToken(null);
    }

    /**
     * Asynchronously attempt to jsonRequest an OAuth Access Token
     * @param cb called when AccessToken is acquired. Always called
     *           on a background thread suitable for networking.
     */
    protected void acquireAccessToken(final OAuthCallback cb) {
        if (mStorage.contains(ACCESS_TOKEN_KEY)) {
            Log.d(TAG, "Access token cached");
            if(cb != null){
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        cb.ready(httpRequestFactoryFromAccessToken(mStorage.getString(ACCESS_TOKEN_KEY, null)));
                    }
                }).start();
            }

            return;
        }

        new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... params) {
                TokenResponse response = null;
                try {
                    response = new ClientCredentialsTokenRequest(new NetHttpTransport(), new JacksonFactory(), new GenericUrl(mConfig.getAccessTokenRequestUrl()))
                            .setGrantType("client_credentials")
                            .setClientAuthentication(new BasicAuthentication(mConfig.getClientId(), mConfig.getClientSecret()))
                            .execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (response != null) {
                    Log.i(TAG, "Got Access Token " + response.getAccessToken());
                    storeAccessToken(response.getAccessToken());
                    if(cb != null)
                        cb.ready(httpRequestFactoryFromAccessToken(mStorage.getString(ACCESS_TOKEN_KEY, null)));
                } else
                    Log.w(TAG, "Failed to get Access Token");
                return null;
            }
        }.execute();
    }

    private HttpRequestFactory httpRequestFactoryFromAccessToken(String accessToken){
        if(accessToken == null){
            throw new NullPointerException("httpRequestFactoryFromAccessToken got null Access Token");
        }
        if(mRequestFactory == null || !accessToken.equals(mOAuthAccessToken)){
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod()).setAccessToken(accessToken);
            NetHttpTransport mHttpTransport = new NetHttpTransport.Builder().build();
            mRequestFactory = mHttpTransport.createRequestFactory(credential);
            mOAuthAccessToken = accessToken;
        }
        return mRequestFactory;
    }

    protected boolean isAccessTokenAcquired() {
        return mStorage.contains(ACCESS_TOKEN_KEY);
    }

    protected void storeAccessToken(String accessToken){
        getContext().getSharedPreferences(mConfig.getCredentialStoreName(), mContext.MODE_PRIVATE).edit()
            .putString(ACCESS_TOKEN_KEY, accessToken)
            .apply();
    }

    protected void clearAccessToken(){
        getContext().getSharedPreferences(mConfig.getCredentialStoreName(), mContext.MODE_PRIVATE).edit()
            .remove(ACCESS_TOKEN_KEY)
            .apply();
    }

    protected boolean isSuccessResponse(HttpResponse response){
        return response.getStatusCode() == 200;
    }

}
