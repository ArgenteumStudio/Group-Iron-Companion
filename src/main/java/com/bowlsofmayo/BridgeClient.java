package com.bowlsofmayo;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

final class BridgeClient
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient http;
    private final String base;
    private final String token;

    BridgeClient(OkHttpClient http, String base, String token)
    {
        HttpUrl url = HttpUrl.parse(base);
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty() ||
            url.query() != null || url.fragment() != null)
        {
            throw new IllegalArgumentException("Invalid bridge URL");
        }
        boolean local = url.host().equals("localhost") || url.host().equals("127.0.0.1") ||
            url.host().equals("::1");
        if (!url.isHttps() && !local)
        {
            throw new IllegalArgumentException("Remote bridges require HTTPS");
        }
        if (token == null || token.trim().length() < 32)
        {
            throw new IllegalArgumentException("Configure your member token (32+ characters)");
        }
        this.http = http.newBuilder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS).build();
        this.base = base.replaceAll("/+$", "");
        this.token = token.trim();
    }

    String get(String path) throws IOException
    {
        return execute(new Request.Builder().url(base + path).get());
    }

    byte[] getBytes(String path, int limit) throws IOException
    {
        try (Response response = http.newCall(new Request.Builder().url(base + path).get()
            .header("Authorization", "Bearer " + token).build()).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("Bridge HTTP " + response.code());
            }
            BufferedSource source = response.body().source();
            if (source.request(limit + 1L))
            {
                throw new IOException("Bridge response too large");
            }
            return source.getBuffer().readByteArray();
        }
    }

    void post(String path, String json) throws IOException
    {
        execute(new Request.Builder().url(base + path).post(RequestBody.create(JSON, json)));
    }

    private String execute(Request.Builder request) throws IOException
    {
        try (Response response = http.newCall(request.header("Authorization", "Bearer " + token).build()).execute())
        {
            if (!response.isSuccessful())
            {
                String reason = null;
                if (response.body() != null)
                {
                    try
                    {
                        BufferedSource source = response.body().source();
                        source.request(4096);
                        String text = source.getBuffer().clone().readUtf8(Math.min(4096, source.getBuffer().size()));
                        com.google.gson.JsonObject error = new com.google.gson.JsonParser().parse(text).getAsJsonObject();
                        if (error.has("error"))
                        {
                            reason = error.get("error").getAsString();
                        }
                    }
                    catch (RuntimeException | IOException ignored)
                    {
                        // fall back to the generic message
                    }
                }
                throw new IOException("Bridge HTTP " + response.code() + ": " +
                    (reason != null ? reason : "check URL, token and assigned RS name"));
            }
            if (response.body() == null)
            {
                throw new IOException("Empty bridge response");
            }
            return response.body().string();
        }
    }
}
