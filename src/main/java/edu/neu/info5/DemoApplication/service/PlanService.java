package edu.neu.info5.DemoApplication.service;

import org.json.JSONObject;

import java.util.Map;

public interface PlanService {

    public void createPlan(String key, String value);

    public boolean deletePlan(String key);

    public String readPlan(String key);

    boolean hasKey(String key);

    String savePlan(String key, JSONObject object);

    void delete(String key);

    void update(String key, JSONObject object);

    void update(JSONObject object);

    Map<String, Object> getPlan(String key);

    String getEtag(String key, String etag);


}
