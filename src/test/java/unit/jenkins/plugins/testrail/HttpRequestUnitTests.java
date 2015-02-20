

package jenkins.plugins.testrail;

import jenkins.plugins.testrail.util.HttpClientUtil;
import jenkins.plugins.testrail.util.TestRailJsonParser;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.junit.BeforeClass;
import org.junit.Ignore;

import static org.junit.Assert.*;
import org.junit.Test;
import groovy.mock.interceptor.MockFor;
import groovy.mock.interceptor.*;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;



public class HttpRequestUnitTests {

    private final PrintStream printStream = System.out;


    @Test
    public final void testPerform() {
        // TODO: A lot of mocking.
    }

}
