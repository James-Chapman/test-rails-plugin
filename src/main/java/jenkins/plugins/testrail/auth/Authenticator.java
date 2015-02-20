package jenkins.plugins.testrail.auth;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.PrintStream;

/**
 * @author Janario Oliveira
 */
public interface Authenticator {

    String getKeyName();

    void authenticate(DefaultHttpClient client, HttpRequestBase requestBase,
                      PrintStream logger) throws IOException, InterruptedException;
}
