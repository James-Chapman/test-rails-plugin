package jenkins.plugins.testrail.util;

import java.io.*;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import hudson.FilePath;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * @author Janario Oliveira
 */
public class HttpClientUtil {

    private FilePath outputFilePath;

    public String getOutputFile() {
        return outputFilePath.getName();
    }

    public void setOutputFile(FilePath filePath) {
        this.outputFilePath = filePath;
    }

    private HttpEntity makeEntity(List<NameValuePair> params) throws
            UnsupportedEncodingException {
        return new UrlEncodedFormEntity(params);
    }

    public String executeGet(DefaultHttpClient httpClient, String authentication, String customHeader, String getUrl,
                             PrintStream logger, boolean consolLogResponseBody) throws IOException, InterruptedException {

        String returnData = null;
        try {
            URI uri = new URI(getUrl);
            doSecurity(httpClient, uri);
            HttpGet request = new HttpGet(uri);
            request.addHeader("accept", "application/json");
            request.addHeader("content-type", "application/json");
            if(authentication != null && !authentication.isEmpty()) {
                String[] parts = authentication.split(":");
                request.addHeader(parts[0], parts[1]);
            }
            if(customHeader != null && !customHeader.isEmpty()) {
                String[] parts = customHeader.split(":");
                request.addHeader(parts[0], parts[1]);
            }
            HttpResponse httpResponse = httpClient.execute(request);
            logger.println("HTTP response: " + httpResponse.toString());
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                returnData = EntityUtils.toString(httpResponse.getEntity());
                if (consolLogResponseBody) {
                    logger.println("Response: \n" + returnData);
                }
                outputFilePath.write().write(returnData.getBytes());
                EntityUtils.consume(httpResponse.getEntity());
            }

        } catch (Exception ex) {
            logger.println("Caught exception... " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            //httpClient.getConnectionManager().shutdown();
            return returnData;
        }
    }


    public int executePost(DefaultHttpClient httpClient, String authentication, String customHeader, String postUrl,
                             PrintStream logger, String postContent) throws IOException, InterruptedException {

        int status = 0;
        try {
            URI uri = new URI(postUrl);
            doSecurity(httpClient, uri);
            HttpPost request = new HttpPost(uri);
            StringEntity params = new StringEntity(postContent);
            request.addHeader("content-type", "application/json");
            if(authentication != null && !authentication.isEmpty()) {
                String[] parts = authentication.split(":");
                request.addHeader(parts[0], parts[1]);
            }
            if(customHeader != null && !customHeader.isEmpty()) {
                String[] parts = customHeader.split(":");
                request.addHeader(parts[0], parts[1]);
            }
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);
            logger.println("HTTP response: " + response.toString());
            status = response.getStatusLine().getStatusCode();
        } catch (Exception ex) {
            logger.println("Caught exception.. ." + ex.getMessage());
            logger.println(ex.getStackTrace().toString());
        } finally {
            httpClient.getConnectionManager().shutdown();
        }

        return status;
    }

    private void doSecurity(DefaultHttpClient httpClient, URI uri) throws IOException {
        if (!uri.getScheme().equals("https")) {
            return;
        }

        try {
            final SSLSocketFactory ssf = new SSLSocketFactory(new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain,
                        String authType) throws CertificateException {
                    return true;
                }
            }, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            final SchemeRegistry schemeRegistry = httpClient.getConnectionManager().getSchemeRegistry();
            final int port = uri.getPort() < 0 ? 443 : uri.getPort();
            schemeRegistry.register(new Scheme(uri.getScheme(), port, ssf));
        } catch (Exception ex) {
            throw new IOException("Error unknow", ex);
        }
    }
}
