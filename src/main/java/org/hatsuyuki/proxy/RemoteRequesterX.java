package org.hatsuyuki.proxy;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.*;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Connection;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Hatsuyuki on 2017/02/17.
 */
public class RemoteRequesterX extends Requester {
    private String host;
    private int port;
    private String username;
    private String password;

    public RemoteRequesterX(String host, int port) {
        super(null);
        this.host = host;
        this.port = port;
    }

    public RemoteRequesterX(String host, int port, String username, String password) {
        super(null);
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }


    @Override
    public Response forward(Request request) throws IOException {
        HttpRequestBase httpRequest;

        URI uri;
        try {
            uri = new URI(request.url);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }

        if (request.method == Connection.Method.POST) {
            //-- url
            HttpPost httpPost = new HttpPost(uri);
            //-- data
            List<BasicNameValuePair> params = request.data.stream().map(keyVal -> new BasicNameValuePair(keyVal.key, keyVal.value)).collect(Collectors.toList());
            httpPost.setEntity(new UrlEncodedFormEntity(params));
            httpRequest = httpPost;
        } else if (request.method == Connection.Method.GET) {
            //-- url
            httpRequest = new HttpGet(uri);
        } else {
            throw new IOException("Do not support for this method conversion: " + request.method);
        }

        //-- header
        for (Map.Entry<String, String> header: request.headers.entrySet()) {
            httpRequest.addHeader(header.getKey(), header.getValue());
        }

        //-- cookie
        CookieStore cookieStore = new BasicCookieStore();
        for (Map.Entry<String, String> entry: request.cookies.entrySet()) {
            BasicClientCookie cookie = new BasicClientCookie(entry.getKey(), entry.getValue());
            cookie.setDomain(uri.getHost());
            cookie.setPath("/");
            cookieStore.addCookie(cookie);
        }
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        //-- follow redirect
        HttpClientBuilder builder = HttpClientBuilder.create()
                .setRedirectStrategy(request.followRedirects ? new LaxRedirectStrategy() : new DefaultRedirectStrategy());

        //-- proxy
        builder.setProxy(new HttpHost(this.host, this.port));
        if (this.username != null && this.password != null) {
            Credentials credentials = new UsernamePasswordCredentials(this.username, this.password);
            AuthScope authScope = new AuthScope(this.host, this.port);
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(authScope, credentials);
            builder.setDefaultCredentialsProvider(credsProvider);
        }

        CloseableHttpClient httpClient = builder.build();
        CloseableHttpResponse httpResponse = httpClient.execute(httpRequest, context);

        try {
            HttpEntity entity = httpResponse.getEntity();

            //-- response
            Response response = new Response();
            response.statusCode = httpResponse.getStatusLine().getStatusCode();
            response.body = IOUtils.toString(entity.getContent(), "UTF-8");
            response.headers = request.headers;
            Map<String, String> cookies = new HashMap<>();
            for (Cookie cookie : context.getCookieStore().getCookies()) {
                cookies.put(cookie.getName(), cookie.getValue());
            }
            response.cookies = cookies;

            return response;
        } catch (IOException e) {
            throw new IOException(String.format("request=[%s] proxy=[%s:%d] error=[%s]", request.url(), this.host, this.port, e.getMessage()), e);
        } finally {
            httpResponse.close();
        }
    }
}
