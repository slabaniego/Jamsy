package ca.sheridancollege.jamsy.beans;

public class SpotifyAuthResponse {
    private String access_token;
    private String token_type;
    private String firebaseCustomToken;

   
    public SpotifyAuthResponse() {
    }

    public SpotifyAuthResponse(String access_token, String token_type, String firebaseCustomToken) {
        this.access_token = access_token;
        this.token_type = token_type;
        this.firebaseCustomToken = firebaseCustomToken;
    }

 
    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getToken_type() {
        return token_type;
    }

    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    public String getFirebaseCustomToken() {
        return firebaseCustomToken;
    }

    public void setFirebaseCustomToken(String firebaseCustomToken) {
        this.firebaseCustomToken = firebaseCustomToken;
    }
}