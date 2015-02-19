package jenkins.plugins.testrail;

import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.VariableResolver;
//import jenkins.plugins.testrail.auth.Authenticator;
//import jenkins.plugins.testrail.auth.BasicDigestAuthentication;
//import jenkins.plugins.testrail.auth.FormAuthentication;
import jenkins.plugins.testrail.util.HttpClientUtil;
import jenkins.plugins.testrail.util.RequestAction;
import jenkins.plugins.testrail.util.TestRailJsonParser;
import net.sf.json.JSONObject;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Janario Oliveira
 * @author James Chapman
 */
public class HttpRequest extends Builder {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);
    private final String getPlanUrl;
    private final String getTestsUrl;
    private final String newPlanPostUrl;
    private final HttpMode httpMode;
    private final MimeType contentType;
    private final MimeType acceptType;
    private final String customHeader;
    private final String outputFile;
//    private final String authentication;
    private final Boolean returnCodeBuildRelevant;
    private final Boolean consoleLogResponseBody;
    private final Boolean passBuildParameters;

    @DataBoundConstructor
    public HttpRequest(String getPlanUrl, String getTestsUrl, String newPlanPostUrl, String httpMode, MimeType contentType,
                       MimeType acceptType, String customHeader, String outputFile, Boolean returnCodeBuildRelevant,
                       Boolean consoleLogResponseBody, Boolean passBuildParameters)
                       throws URISyntaxException {
        this.getPlanUrl = getPlanUrl;
        this.getTestsUrl = getTestsUrl;
        this.newPlanPostUrl = newPlanPostUrl;
        this.contentType = contentType;
        this.acceptType = acceptType;
        this.customHeader = customHeader;
        this.outputFile = outputFile;
        this.httpMode = Util.fixEmpty(httpMode) == null ? null : HttpMode.valueOf(httpMode);
//        this.authentication = Util.fixEmpty(authentication);
        this.returnCodeBuildRelevant = returnCodeBuildRelevant;
        this.consoleLogResponseBody = consoleLogResponseBody;
        this.passBuildParameters = passBuildParameters;
    }

    public Boolean getConsoleLogResponseBody() {
        return consoleLogResponseBody;
    }

    public String getGetPlanUrl() {
        return getPlanUrl;
    }

    public String getGetTestsUrl() {
        return getTestsUrl;
    }

    public String getNewPlanPostUrl() {
        return newPlanPostUrl;
    }

    public HttpMode getHttpMode() {
        return httpMode;
    }

    public MimeType getContentType() {
        return contentType;
    }

    public MimeType getAcceptType() {
        return acceptType;
    }

    public String getCustomHeader() {
        return customHeader;
    }

    public String getOutputFile() {
        return outputFile;
    }

//    public String getAuthentication() {
//        return authentication;
//    }

    public Boolean getReturnCodeBuildRelevant() {
        return returnCodeBuildRelevant;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final PrintStream logger = listener.getLogger();

        final HttpMode mode = httpMode != null ? httpMode : getDescriptor().getDefaultHttpMode();
        logger.println("HttpMode: " + mode);
        logger.println("Request Headers:-");
        logger.println("Content-type: " + contentType);
        logger.println("Accept: " + acceptType);
        if(customHeader != null && !customHeader.isEmpty()) {
            logger.println(customHeader);
        }

        final SystemDefaultHttpClient httpclient = new SystemDefaultHttpClient();

        logger.println("Parameters: ");
        final EnvVars envVars = build.getEnvironment(listener);
        //final List<NameValuePair> params = createParameters(build, logger, envVars);
        final HttpClientUtil clientUtil = new HttpClientUtil();

        // If configured, set the file to write HTTP request data output to
        if(outputFile != null && !outputFile.isEmpty()) {
            FilePath outputFilePath = build.getWorkspace().child(outputFile);
            clientUtil.setOutputFile(outputFilePath);
        }

        // Boolean value that determines overall success of creating a new Test Rails Test Plan
	    boolean success = true;

        // Parse JSON from testrails API
        final TestRailJsonParser testRailJsonParser = new TestRailJsonParser();

        // Set up first HTTP GET
        String evaluatedGetPlanUrl = evaluate(getPlanUrl, build.getBuildVariableResolver(), envVars);
        logger.println(String.format("Get Plan URL: %s", evaluatedGetPlanUrl));
        final RequestAction requestActionGetPlan = new RequestAction(new URL(evaluatedGetPlanUrl), mode, null);
        final HttpRequestBase httpRequestBaseGetPlan = clientUtil.createRequestBase(requestActionGetPlan);
        httpRequestBaseGetPlan.setHeader("Accept", getMimeType(acceptType));
        httpRequestBaseGetPlan.setHeader("Content-type", getMimeType(contentType));
        if(customHeader != null && !customHeader.isEmpty()) {
            String[] parts = customHeader.split(":");
            httpRequestBaseGetPlan.setHeader(parts[0], parts[1]);
        }

        // Do the first HTTP GET to .../get_plan
        final String httpRespGetPlan = clientUtil.execute(httpclient, httpRequestBaseGetPlan, logger, consoleLogResponseBody);
        if(httpRespGetPlan == null || httpRespGetPlan.isEmpty()) {
            success = false;
        }

        // Iterate through the returned data and perform more HTTP GET queries
        Map<String, String> testsJson = new HashMap<String, String>();
        List<String> testIds = testRailJsonParser.decodeGetPlanJSON(httpRespGetPlan);
        for (String testId : testIds) {
            // Do HTTP query to .../get_test with test id
            String queryUrl = getTestsUrl + "/" + testId;
            String evaluatedGetTestsUrl = evaluate(queryUrl, build.getBuildVariableResolver(), envVars);
            logger.println(String.format("Get Tests URL: %s", evaluatedGetTestsUrl));
            final RequestAction requestActionGetTests = new RequestAction(new URL(evaluatedGetTestsUrl), mode, null);
            final HttpRequestBase httpRequestBaseGetTests = clientUtil.createRequestBase(requestActionGetTests);
            httpRequestBaseGetTests.setHeader("Accept", getMimeType(acceptType));
            httpRequestBaseGetTests.setHeader("Content-type", getMimeType(contentType));
            if(customHeader != null && !customHeader.isEmpty()) {
                String[] parts = customHeader.split(":");
                httpRequestBaseGetTests.setHeader(parts[0], parts[1]);
            }
            // Do another HTTP GET to .../get_tests
            final String httpRespGetTests = clientUtil.execute(httpclient, httpRequestBaseGetTests, logger, consoleLogResponseBody);
            if(httpRespGetTests == null || httpRespGetTests.isEmpty()) {
                success = false;
            }
            else {
                logger.println("TEST ID: " + testId);
                logger.println("JSON: " + httpRespGetTests);
                testsJson.put(testId, httpRespGetTests);
            }
        }

        String newTestPlan = null;
        try {
            logger.println("Creating new test plan...");
            newTestPlan = testRailJsonParser.createNewPlan(httpRespGetPlan, testsJson);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        logger.println("\n\n\n NEW TEST PLAN \n" + newTestPlan);

        // Set up the final HTTP POST
        String evaluatedNewPlanPostUrl = evaluate(newPlanPostUrl, build.getBuildVariableResolver(), envVars);
        logger.println(String.format("New Plan POST URL: %s", evaluatedNewPlanPostUrl));
        final RequestAction requestActionNewPlan = new RequestAction(new URL(evaluatedNewPlanPostUrl), mode, null);
        final HttpRequestBase httpRequestBaseNewPlan = clientUtil.createRequestBase(requestActionNewPlan);
        httpRequestBaseNewPlan.setHeader("Accept", getMimeType(acceptType));
        httpRequestBaseNewPlan.setHeader("Content-type", getMimeType(contentType));
        if(customHeader != null && !customHeader.isEmpty()) {
            String[] parts = customHeader.split(":");
            httpRequestBaseNewPlan.setHeader(parts[0], parts[1]);
        }

        // Do the HTTP POST to .../new_plan
        final String httpRespNewPlan = clientUtil.execute(httpclient, httpRequestBaseNewPlan, logger, consoleLogResponseBody);
        if(httpRespNewPlan == null || httpRespNewPlan.isEmpty()) {
            success = false;
        }

        // use global configuration as default if it is unset for this job
//        boolean returnCodeRelevant = returnCodeBuildRelevant != null
//                ? returnCodeBuildRelevant : getDescriptor().isDefaultReturnCodeBuildRelevant();
//
//        LOGGER.debug("---> config local: {}", returnCodeBuildRelevant);
//        LOGGER.debug("---> global: {}", getDescriptor().isDefaultReturnCodeBuildRelevant());
//        LOGGER.debug("---> returnCodeRelevant: {}", returnCodeRelevant);

        return success;
    }

    private String getMimeType(MimeType mimeType) {
        if (mimeType == MimeType.TEXT_HTML) {
            return "text/html";
        } else if (mimeType == MimeType.APPLICATION_JSON) {
            return "application/json";
        } else if (mimeType == MimeType.APPLICATION_TAR) {
            return "application/x-tar";
        } else if (mimeType == MimeType.APPLICATION_ZIP) {
            return "application/zip";
        } else if (mimeType == MimeType.APPLICATION_OCTETSTREAM) {
            return "application/octet-stream";
        } else {
            return "text/html";
        }
    }

//    private List<NameValuePair> createParameters(
//            AbstractBuild<?, ?> build, PrintStream logger,
//            EnvVars envVars) {
//        final VariableResolver<String> vars = build.getBuildVariableResolver();
//
//        List<NameValuePair> nameValuePairList = new ArrayList<NameValuePair>();
//        for (Map.Entry<String, String> entry : build.getBuildVariables().entrySet()) {
//            String value = evaluate(entry.getValue(), vars, envVars);
//            logger.println("  " + entry.getKey() + " = " + value);
//
//            nameValuePairList.add(new NameValuePair(entry.getKey(), value));
//        }
//
//        return nameValuePairList;
//    }

    private String evaluate(String value, VariableResolver<String> vars, Map<String, String> env) {
        return Util.replaceMacro(Util.replaceMacro(value, vars), env);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private HttpMode defaultHttpMode = HttpMode.POST;
//        private List<BasicDigestAuthentication> basicDigestAuthentications = new ArrayList<BasicDigestAuthentication>();
//        private List<FormAuthentication> formAuthentications = new ArrayList<FormAuthentication>();
        private boolean defaultReturnCodeBuildRelevant = true;
    	private boolean defaultLogResponseBody = true;

        public DescriptorImpl() {
            load();
        }

	    public boolean isDefaultLogResponseBody() {
		    return defaultLogResponseBody;
	    }

	    public void setDefaultLogResponseBody(boolean defaultLogResponseBody) {
		    this.defaultLogResponseBody = defaultLogResponseBody;
	    }
	
        public HttpMode getDefaultHttpMode() {
            return defaultHttpMode;
        }

        public void setDefaultHttpMode(HttpMode defaultHttpMode) {
            this.defaultHttpMode = defaultHttpMode;
        }

//        public List<BasicDigestAuthentication> getBasicDigestAuthentications() {
//            return basicDigestAuthentications;
//        }

//        public void setBasicDigestAuthentications(
//                List<BasicDigestAuthentication> basicDigestAuthentications) {
//            this.basicDigestAuthentications = basicDigestAuthentications;
//        }

//        public List<FormAuthentication> getFormAuthentications() {
//            return formAuthentications;
//        }

//        public void setFormAuthentications(
//                List<FormAuthentication> formAuthentications) {
//            this.formAuthentications = formAuthentications;
//        }

//        public List<Authenticator> getAuthentications() {
//            List<Authenticator> list = new ArrayList<Authenticator>();
//            list.addAll(basicDigestAuthentications);
//            list.addAll(formAuthentications);
//            return list;
//        }

//        public Authenticator getAuthentication(String keyName) {
//            for (Authenticator authenticator : getAuthentications()) {
//                if (authenticator.getKeyName().equals(keyName)) {
//                    return authenticator;
//                }
//            }
//            return null;
//        }

        public boolean isDefaultReturnCodeBuildRelevant() {
            return defaultReturnCodeBuildRelevant;
        }

        public void setDefaultReturnCodeBuildRelevant(boolean defaultReturnCodeBuildRelevant) {
            this.defaultReturnCodeBuildRelevant = defaultReturnCodeBuildRelevant;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Generate TestRail Test Plan";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws
                FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

        public ListBoxModel doFillDefaultHttpModeItems() {
            return HttpMode.getFillItems();
        }

        public ListBoxModel doFillHttpModeItems() {
            ListBoxModel items = HttpMode.getFillItems();
            items.add(0, new ListBoxModel.Option("Default", ""));

            return items;
        }

        public ListBoxModel doFillDefaultContentTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillContentTypeItems() {
            ListBoxModel items = MimeType.getContentTypeFillItems();
            items.add(0, new ListBoxModel.Option("Default", ""));

            return items;
        }

        public ListBoxModel doFillDefaultAcceptTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillAcceptTypeItems() {
            ListBoxModel items = MimeType.getContentTypeFillItems();
            items.add(0, new ListBoxModel.Option("Default", ""));

            return items;
        }

//        public ListBoxModel doFillAuthenticationItems() {
//            ListBoxModel items = new ListBoxModel();
//            items.add("");
//            for (BasicDigestAuthentication basicDigestAuthentication : basicDigestAuthentications) {
//                items.add(basicDigestAuthentication.getKeyName());
//            }
//            for (FormAuthentication formAuthentication : formAuthentications) {
//                items.add(formAuthentication.getKeyName());
//            }
//
//            return items;
//        }

        public FormValidation doCheckUrl(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.ok();
            // return HttpRequestValidation.checkUrl(value);
        }

//        public FormValidation doValidateKeyName(@QueryParameter String value) {
//            List<Authenticator> list = getAuthentications();
//
//            int count = 0;
//            for (Authenticator basicAuthentication : list) {
//                if (basicAuthentication.getKeyName().equals(value)) {
//                    count++;
//                }
//            }
//
//            if (count > 1) {
//                return FormValidation.error("The Key Name must be unique");
//            }
//
//            return FormValidation.validateRequired(value);
//        }

    }
}
