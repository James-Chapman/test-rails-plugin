/*******************************************************************************
 * Copyright   : MIT License
 * Author      : James Chapman testrail-plugin@mtbfr.co.uk
 * Date        : 17/02/2015
 * Description : TestRail plugin based on http_request plugin by
 *               Janario Oliveira
 *******************************************************************************/
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
import jenkins.plugins.testrail.auth.Authenticator;
import jenkins.plugins.testrail.auth.BasicAuthentication;
import jenkins.plugins.testrail.util.HttpClientUtil;
import jenkins.plugins.testrail.util.TestRailJsonParser;
import net.sf.json.JSONObject;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HttpRequest Class
 */
public class HttpRequest extends Builder {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);

    private final String basePlanId;
    private final String getPlanUrl;
    private final String getTestsUrl;
    private final String addPlanUrl;
    private final String customHeader;
    private final String outputFile;
    private final String authentication;
    private final Boolean returnCodeBuildRelevant;
    private final Boolean consoleLogResponseBody;

    @DataBoundConstructor
    public HttpRequest(String basePlanId, String getPlanUrl, String getTestsUrl, String addPlanUrl,
                       String authentication, String customHeader, String outputFile, Boolean consoleLogResponseBody,
                       Boolean returnCodeBuildRelevant, Boolean passBuildParameters)
                       throws URISyntaxException {
        this.basePlanId = basePlanId;
        this.getPlanUrl = getPlanUrl;
        this.getTestsUrl = getTestsUrl;
        this.addPlanUrl = addPlanUrl;
        this.customHeader = customHeader;
        this.outputFile = outputFile;
        this.authentication = Util.fixEmpty(authentication);
        this.returnCodeBuildRelevant = returnCodeBuildRelevant;
        this.consoleLogResponseBody = consoleLogResponseBody;
    }

    public String getBasePlanId() {
        return basePlanId;
    }

    public String getGetPlanUrl() {
        return getPlanUrl;
    }

    public String getGetTestsUrl() {
        return getTestsUrl;
    }

    public String getAddPlanUrl() {
        return addPlanUrl;
    }

    public String getCustomHeader() {
        return customHeader;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public String getAuthentication() {
        return authentication;
    }

    public Boolean getReturnCodeBuildRelevant() {
        return returnCodeBuildRelevant;
    }

    public Boolean getConsoleLogResponseBody() {
        return consoleLogResponseBody;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final PrintStream logger = listener.getLogger();

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

        // Do the first HTTP GET to .../get_plan
        String getPlanQueryUrl = getPlanUrl + "/" + basePlanId;
        logger.println(String.format("get_plan API URL: %s", getPlanQueryUrl));
        final String httpRespGetPlan = clientUtil.executeGet(httpclient, null, customHeader, getPlanQueryUrl, logger, consoleLogResponseBody);
        if(httpRespGetPlan == null || httpRespGetPlan.isEmpty()) {
            return false;
        }

        // Iterate through the returned data and perform more HTTP GET queries
        Map<String, String> testsJson = new HashMap<String, String>();
        List<String> testIds = testRailJsonParser.decodeGetPlanJSON(httpRespGetPlan);
        for (String testId : testIds) {
            // Do HTTP query to .../get_test with test id
            String getTestsQueryUrl = getTestsUrl + "/" + testId;
            logger.println(String.format("get_tests API URL: %s", getTestsQueryUrl));
            final String httpRespGetTests = clientUtil.executeGet(httpclient, null, customHeader, getTestsQueryUrl, logger, consoleLogResponseBody);
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

        // Do the HTTP POST to .../new_plan
        String projectId = testRailJsonParser.getProjectId(httpRespGetPlan);
        String addPlanQueryUrl = addPlanUrl + "/" + projectId;
        logger.println(String.format("add_plan API URL: %s", addPlanQueryUrl));
        final int httpRespNewPlan = clientUtil.executePost(httpclient, authentication, customHeader, addPlanQueryUrl, logger, newTestPlan);
        if(httpRespNewPlan != 200) {
            success = false;
        }

        return success;
    }



    private String evaluate(String value, VariableResolver<String> vars, Map<String, String> env) {
        return Util.replaceMacro(Util.replaceMacro(value, vars), env);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private List<BasicAuthentication> basicAuthentications = new ArrayList<BasicAuthentication>();
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

        public List<BasicAuthentication> getBasicAuthentications() {
            return basicAuthentications;
        }

        public void setBasicAuthentications(
                List<BasicAuthentication> basicAuthentications) {
            this.basicAuthentications = basicAuthentications;
        }

        public List<Authenticator> getAuthentications() {
            List<Authenticator> list = new ArrayList<Authenticator>();
            list.addAll(basicAuthentications);
            return list;
        }

        public Authenticator getAuthentication(String keyName) {
            for (Authenticator authenticator : getAuthentications()) {
                if (authenticator.getKeyName().equals(keyName)) {
                    return authenticator;
                }
            }
            return null;
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

        public ListBoxModel doFillAuthenticationItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            for (BasicAuthentication basicAuthentication : basicAuthentications) {
                items.add(basicAuthentication.getKeyName());
            }

            return items;
        }

        public FormValidation doCheckUrl(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.ok();
            // return HttpRequestValidation.checkUrl(value);
        }

        public FormValidation doValidateKeyName(@QueryParameter String value) {
            List<Authenticator> list = getAuthentications();

            int count = 0;
            for (Authenticator basicAuthentication : list) {
                if (basicAuthentication.getKeyName().equals(value)) {
                    count++;
                }
            }

            if (count > 1) {
                return FormValidation.error("The Key Name must be unique");
            }

            return FormValidation.validateRequired(value);
        }

    }
}
