package com.koushikdutta.async.http;

import java.net.URI;

import junit.framework.Assert;

import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.libcore.RequestHeaders;

public class AsyncHttpRequest {
    public String getRequestLine() {
        String path = getUri().getPath();
        if (path.length() == 0)
            path = "/";
        return String.format("%s %s HTTP/1.1", mMethod, path);
    }

    protected final String getDefaultUserAgent() {
        String agent = System.getProperty("http.agent");
        return agent != null ? agent : ("Java" + System.getProperty("java.version"));
    }
    
    private String mMethod;
    public String getMethod() {
       return mMethod; 
    }

    public AsyncHttpRequest(URI uri, String method) {
        Assert.assertNotNull(uri);
        mMethod = method;
        mHeaders = new RequestHeaders(uri, mRawHeaders);
        mRawHeaders.setStatusLine(getRequestLine());
        mHeaders.setHost(uri.getHost());
        mHeaders.setUserAgent(getDefaultUserAgent());
        mHeaders.setAcceptEncoding("gzip");
    }
    
    public URI getUri() {
        return mHeaders.getUri();
    }
    
    private RawHeaders mRawHeaders = new RawHeaders();
    private RequestHeaders mHeaders;

    public String getRequestString() {
        return mRawHeaders.toHeaderString();
    }
    
    private boolean mFollowRedirect = true;
    public boolean getFollowRedirect() {
        return mFollowRedirect;
    }
    public void setFollowRedirect(boolean follow) {
        mFollowRedirect = follow;
    }
}
