package com.example.mbsiss;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static final String BASE_URL = "https://mbsisssvmsprojec.byethost24.com/mbsiss/";
    private static Retrofit instance;

    public static Retrofit getInstance() {
        if (instance == null) {

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // ── Cookie jar — persists the __test cookie between requests ──
            final Map<String, List<Cookie>> cookieStore = new HashMap<>();
            CookieJar cookieJar = new CookieJar() {
                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    List<Cookie> existing = cookieStore.containsKey(url.host())
                            ? cookieStore.get(url.host())
                            : new ArrayList<>();
                    // Merge — don't wipe cookies already stored for this host
                    for (Cookie newCookie : cookies) {
                        existing.removeIf(c -> c.name().equals(newCookie.name()));
                        existing.add(newCookie);
                    }
                    cookieStore.put(url.host(), existing);
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url.host());
                    return cookies != null ? cookies : new ArrayList<>();
                }
            };

            // ── Anti-bot interceptor — solves the AES cookie challenge ────
            Interceptor antiBotInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request original = chain.request();

                    // Add browser headers to every request
                    Request request = original.newBuilder()
                            .header("User-Agent",
                                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
                                            + "Chrome/124.0.0.0 Mobile Safari/537.36")
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("Referer", BASE_URL)
                            .build();

                    Response response = chain.proceed(request);
                    String contentType = response.header("Content-Type", "");

                    // ── Detect the anti-bot HTML challenge ────────────────
                    // If we get text/html back instead of application/json,
                    // check if it contains the AES challenge script
                    if (contentType != null && contentType.contains("text/html")) {
                        String body = response.peekBody(4096).string();

                        if (body.contains("slowAES.decrypt")) {
                            // Extract the ciphertext from the challenge HTML
                            String cipherHex = AesUtil.extractCipherHex(body);

                            if (cipherHex != null) {
                                // Solve the AES challenge to get the cookie value
                                String cookieValue = AesUtil.decryptChallenge(cipherHex);

                                if (cookieValue != null) {
                                    // Build and store the __test cookie manually
                                    HttpUrl url = request.url();
                                    Cookie testCookie = new Cookie.Builder()
                                            .name("__test")
                                            .value(cookieValue)
                                            .domain(url.host())
                                            .path("/")
                                            .build();

                                    List<Cookie> cookies = cookieStore.containsKey(url.host())
                                            ? cookieStore.get(url.host())
                                            : new ArrayList<>();
                                    cookies.removeIf(c -> c.name().equals("__test"));
                                    cookies.add(testCookie);
                                    cookieStore.put(url.host(), cookies);

                                    // Retry the original request — now with the cookie
                                    // Append ?i=1 as the redirect target expects
                                    HttpUrl retryUrl = request.url().newBuilder()
                                            .addQueryParameter("i", "1")
                                            .build();

                                    Request retryRequest = request.newBuilder()
                                            .url(retryUrl)
                                            .build();

                                    response.close();
                                    return chain.proceed(retryRequest);
                                }
                            }
                        }
                    }

                    return response;
                }
            };

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(antiBotInterceptor)   // solve challenge BEFORE logging
                    .addInterceptor(logging)
                    .cookieJar(cookieJar)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();

            com.google.gson.Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            instance = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return instance;
    }
}