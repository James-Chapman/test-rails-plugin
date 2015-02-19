/*******************************************************************************
 * Copyright   : MIT License
 * Author      : James Chapman testrail-plugin@mtbfr.co.uk
 * Date        : 17/02/2015
 * Description : Parse TestRail JSON
 *******************************************************************************/

package jenkins.plugins.testrail.util;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * TODO: add Logger for jenkins.log logging
 */
public class TestRailJsonParser {

    private String newTestPlanJsonString;
    private JSONObject newTestPlanJsonObject;
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRailJsonParser.class);

    public TestRailJsonParser() {
        this.newTestPlanJsonString = "";
        this.newTestPlanJsonObject = new JSONObject();
    }

    /**
     *
     * @param json
     * @return
     */
    public List<String> decodeGetPlanJSON(String json) {
        List<String> returnList = new ArrayList<String>();
        System.out.println("Parsing json");
        try {
            JSONObject rootJsonObject = (JSONObject)new JSONParser().parse(json);
            JSONArray entriesJsonArray = (JSONArray)rootJsonObject.get("entries");
            for (Object entryObject : entriesJsonArray) {
                JSONObject entryJsonObject = (JSONObject)entryObject;
                JSONArray runsJsonArray = (JSONArray)entryJsonObject.get("runs");
                for (Object runObject : runsJsonArray) {
                    JSONObject runJsonObject = (JSONObject)runObject;
                    returnList.add(runJsonObject.get("id").toString());
                }
            }
        } catch (ParseException pe) {
            System.out.println("Exception caught: " + pe.getPosition());
            System.out.println(pe);
        }

        return returnList;
    }


    /**
     *
     * @param oldPlanJson
     * @param oldPlanTestsJson
     * @return
     */
    public String createNewPlan(String oldPlanJson, Map<String, String> oldPlanTestsJson) throws ParseException {
        LOGGER.debug("createNewPlan() called.");
        LOGGER.debug(oldPlanJson);
        final JSONObject oldRootJsonObject = (JSONObject)new JSONParser().parse(oldPlanJson);

        LOGGER.debug("Grabbing first JSON entries");
        final String testName = oldRootJsonObject.get("name").toString();
        final String testDescription = oldRootJsonObject.get("description").toString();
        final String testMilestoneId = oldRootJsonObject.get("milestone_id").toString();
        LOGGER.debug("testName: " + testName);
        LOGGER.debug("testDescription: " + testDescription);
        LOGGER.debug("testMilestoneId: " + testMilestoneId);
        this.newTestPlanJsonObject.put("name", testName);
        this.newTestPlanJsonObject.put("description", testDescription);
        this.newTestPlanJsonObject.put("milestone_id", testMilestoneId);

        // Create new "entries" array and populate it
        LOGGER.debug("Parsing entries...");
        JSONArray newEntriesArray = new JSONArray();
        JSONArray oldEntriesArray = (JSONArray)oldRootJsonObject.get("entries");
        for (Object oldEntryObject : oldEntriesArray) {
            LOGGER.debug("entry: " + oldEntryObject.toString());
            JSONObject oldEntryJsonObject = (JSONObject)oldEntryObject;
            JSONObject newEntryJsonObject = new JSONObject();

            newEntryJsonObject.put("suite_id", oldEntryJsonObject.get("suite_id").toString());
            newEntryJsonObject.put("include_all", false);

            JSONArray newCaseIdsArray = new JSONArray();
            JSONArray newConfigIdsArray = new JSONArray();

            LOGGER.debug("Parsing runs...");
            JSONArray oldRunsJsonArray = (JSONArray)oldEntryJsonObject.get("runs");
            JSONArray newRunsJsonArray = new JSONArray();
            for (Object oldRunObj : oldRunsJsonArray) {
                JSONObject oldRunJsonObj = (JSONObject) oldRunObj;
                JSONObject newRunJsonObj = new JSONObject();
                JSONArray newRunCaseIdsArray = new JSONArray();
                JSONArray newRunConfigIdsArray = new JSONArray();
                //newCaseIdsArray.add(((JSONObject) run_obj).get("id"));
                String testId = oldRunJsonObj.get("id").toString();
                JSONArray testJsonArray = (JSONArray)new JSONParser().parse(oldPlanTestsJson.get(testId));
                for (Object testObject : testJsonArray) {
                    JSONObject testJsonObject = (JSONObject)testObject;
                    LOGGER.debug("case_id: " + testJsonObject.get("case_id"));
                    newCaseIdsArray.add(testJsonObject.get("case_id"));
                    newRunCaseIdsArray.add(testJsonObject.get("case_id"));
                }
                JSONArray oldConfigIdsJsonArray = (JSONArray)oldRunJsonObj.get("config_ids");
                LOGGER.debug("oldConfigIdsJsonArray: " + oldConfigIdsJsonArray.toJSONString());
                for (Object configIdObject : oldConfigIdsJsonArray) {
                    final String configIdString = configIdObject.toString();
                    LOGGER.debug("config_id: " + configIdString);
                    newConfigIdsArray.add(configIdString);
                    newRunConfigIdsArray.add(configIdString);
                }
                LOGGER.debug("putting data into run...");
                newRunJsonObj.put("include_all", false);
                newRunJsonObj.put("assignedto_id", null);
                newRunJsonObj.put("case_ids", newRunCaseIdsArray);
                newRunJsonObj.put("config_ids", newRunConfigIdsArray);

                LOGGER.debug("putting run into runs...");
                newRunsJsonArray.add(newRunJsonObj);
            }
            LOGGER.debug("putting data into entry...");
            newEntryJsonObject.put("case_ids", newCaseIdsArray);
            newEntryJsonObject.put("config_ids", newConfigIdsArray);
            newEntryJsonObject.put("runs", newRunsJsonArray);

            newEntriesArray.add(newEntryJsonObject);

        }
        LOGGER.debug("putting entries into root...");
        // Insert new "entries" array
        this.newTestPlanJsonObject.put("entries", newEntriesArray);

        this.newTestPlanJsonString = this.newTestPlanJsonObject.toJSONString();

        return this.newTestPlanJsonString;
    }

}
