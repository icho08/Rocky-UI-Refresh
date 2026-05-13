package dev.i726.rocky.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class AuthManager {

    private static final String AUTH_SERVER_URL = "https://your-auth-server.replit.app/auth";
    private static final int TIMEOUT_MS = 8000;

    private final String hwid;
    private AuthResult lastResult;

    public AuthManager() {
        this.hwid = HwidUtil.generate();
    }

    public AuthResult authenticate() {
        try {
            String urlStr = AUTH_SERVER_URL + "?hwid=" + hwid;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "Rocky/b1.1");

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                InputStream is = conn.getInputStream();
                JsonObject json = JsonParser.parseReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8)
                ).getAsJsonObject();

                boolean authorized = json.has("authorized") && json.get("authorized").getAsBoolean();
                String message = json.has("message") ? json.get("message").getAsString() : "";

                lastResult = authorized
                        ? AuthResult.success(hwid)
                        : AuthResult.failure("Not authorized: " + message);
            } else if (responseCode == 403) {
                lastResult = AuthResult.failure("Your HWID is not authorized. Purchase a license to use Rocky.");
            } else {
                lastResult = AuthResult.failure("Auth server returned unexpected response: " + responseCode);
            }

        } catch (java.net.UnknownHostException e) {
            lastResult = AuthResult.failure("Cannot reach auth server. Check your internet connection.");
        } catch (java.net.SocketTimeoutException e) {
            lastResult = AuthResult.failure("Auth server timed out. Try again later.");
        } catch (Exception e) {
            lastResult = AuthResult.failure("Authentication error: " + e.getMessage());
        }

        return lastResult;
    }

    public String getHwid() {
        return hwid;
    }

    public AuthResult getLastResult() {
        return lastResult;
    }

    public static final class AuthResult {
        private final boolean authorized;
        private final String message;
        private final String hwid;

        private AuthResult(boolean authorized, String message, String hwid) {
            this.authorized = authorized;
            this.message = message;
            this.hwid = hwid;
        }

        public static AuthResult success(String hwid) {
            return new AuthResult(true, "Authorized", hwid);
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, message, null);
        }

        public boolean isAuthorized() {
            return authorized;
        }

        public String getMessage() {
            return message;
        }

        public String getHwid() {
            return hwid;
        }
    }
}
