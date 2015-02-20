package jenkins.plugins.testrail.auth;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.plugins.testrail.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.PrintStream;

/**
 * @author Janario Oliveira
 */
public class BasicAuthentication extends AbstractDescribableImpl<BasicAuthentication>
        implements Authenticator {

    private final String keyName;
    private final String userName;
    private final String password;

    @DataBoundConstructor
    public BasicAuthentication(String keyName, String userName,
                               String password) {
        this.keyName = keyName;
        this.userName = userName;
        this.password = password;
    }

    public String getKeyName() {
        return keyName;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public void authenticate(DefaultHttpClient client, HttpRequestBase requestBase, PrintStream logger) {
        client.getCredentialsProvider().setCredentials(
                new AuthScope(requestBase.getURI().getHost(), requestBase.getURI().getPort()),
                new UsernamePasswordCredentials(userName, password));
    }

    @Extension
    public static class BasicAuthenticationDescriptor extends Descriptor<BasicAuthentication> {

        public FormValidation doCheckKeyName(@QueryParameter String value) {
            HttpRequest.DescriptorImpl descriptor = (HttpRequest.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(HttpRequest.class);
            return descriptor.doValidateKeyName(value);
        }

        public FormValidation doCheckUserName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPassword(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @Override
        public String getDisplayName() {
            return "Basic Authentication";
        }
    }
}
