package edu.neu.info5.DemoApplication.service;

import edu.neu.info5.DemoApplication.dao.RedisDaoImpl;
import edu.neu.info5.DemoApplication.validator.SchemaValidator;
import edu.neu.info5.DemoApplication.service.ETagService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;




import redis.clients.jedis.Jedis;

import java.util.*;

import java.util.*;

@Service
public class PlanServiceImpl implements PlanService{

    private static String SPLITTER_COLON = ":";

    private static Logger logger = LoggerFactory.getLogger(PlanServiceImpl.class);

    @Autowired
    private RedisDaoImpl redisDao;
    @Autowired
    private EncryptionService encryptionService;

    public PlanServiceImpl() {
    }

    @Override
    public void createPlan(String key, String value) {
        logger.info("CREATING NEW DATA: [" + key + " : " + value + "]");
        redisDao.postValue(key, value);

    }

    @Override
    public boolean deletePlan(String key) {
        logger.info("DELETING DATA - KEY: " + key);
        return redisDao.deleteValue(key);
    }

    @Override
    public String readPlan(String key) {
        logger.info("READING DATA - KEY: " + key);
        return redisDao.getValue(key).toString();
    }


    //    Demo2


    @Override
    public boolean hasKey(String key) {
        return redisDao.hasKey(key);
    }

    @Override
    public String savePlan(String key, JSONObject object) {
        // save plan
        Map<String, Object> objectMap = nestStore(key, object);
        String nodeMapStr = new JSONObject(objectMap).toString();

        // set new etag
        String newEtag = encryptionService.encrypt(object.toString());
        redisDao.hSet(key, "eTag", newEtag);

        return newEtag;
    }

    @Override
    public void delete(String key) {
        populate(key, null, true);
    }

    @Override
    public void update(String key, JSONObject object) {
        traverseNode(object);
    }

    @Override
    public void update(JSONObject object) {
        traverseNode(object);
    }

    @Override
    public String getEtag(String key, String etag) {
        return redisDao.hGet(key, etag);
    }

    private Map<String, Object> nestStore(String key, JSONObject object) {

        traverseNode(object);

        Map<String, Object> output = new HashMap<>();
        populate(key, output, false);

        return output;
    }


    // store the nested json object
    public Map<String, Map<String, Object>> traverseNode(JSONObject jsonObject) {
        Map<String, Map<String, Object>> objMap = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();

        // get all attributes
        Iterator<String> keys = jsonObject.keySet().iterator();
        logger.info(jsonObject.toString() + "'s ALL ATTRIBUTES: " + jsonObject.keySet().toString());

        // traverse all attributes for store
        while (keys.hasNext()) {

            String objectKey = jsonObject.getString("objectType") + ":" + jsonObject.getString("objectId");

            String attName = keys.next();
            Object attValue = jsonObject.get(attName);

            // type - Object
            if (attValue instanceof JSONObject) {

                attValue = traverseNode((JSONObject) attValue);

                Map<String, Map<String, Object>> ObjValueMap = (HashMap<String, Map<String, Object>>) attValue;

                String transitiveKey = objectKey + ":" + attName;
                redisDao.setAdd(transitiveKey, ObjValueMap.entrySet().iterator().next().getKey());
            }
            else if (attValue instanceof org.json.JSONArray) {

                // type - Array
                attValue = getNodeList((org.json.JSONArray)attValue);

                List<HashMap<String, HashMap<String, Object>>> formatList = (List<HashMap<String, HashMap<String, Object>>>) attValue;
                formatList.forEach((listObject) -> {

                    listObject.entrySet().forEach((listEntry) -> {

                        String internalKey = objectKey + ":" + attName;

                        redisDao.setAdd(internalKey, listEntry.getKey());

                    });

                });
            } else {
                // type - Object
                redisDao.hSet(objectKey, attName, attValue.toString());

                valueMap.put(attName, attValue);
                objMap.put(objectKey, valueMap);
            }

        }

        return objMap;
    }


    private List<Object> getNodeList(JSONArray attValue) {

        List<Object> list = new ArrayList<>();

        if (attValue == null)   return list;

        attValue.forEach((e) -> {

            if (e instanceof JSONObject) {
                e = traverseNode((JSONObject )e);
            } else if (e instanceof JSONArray) {
                e = getNodeList((JSONArray)e);
            }

            list.add(e);

        });

        return list;
    }

    public Map<String, Object> getPlan(String key) {
        Map<String, Object> output = new HashMap<>();

        populate(key, output, false);

        return output;
    }

    // populate plan nested node
    public Map<String, Object> populate(String objectKey, Map<String, Object> map, boolean delete) {
        // get all attributes
        Set<String> keys = redisDao.keys(objectKey + "*");
        System.out.println("Keys: " + keys);
        keys.forEach((key) -> {
            if (key.length() > objectKey.length() && key.substring(objectKey.length()).indexOf(SPLITTER_COLON) == -1) {
                return;
            }
            // process key : value
            if (key.equals(objectKey)) {
                if (delete) {

                    redisDao.deleteKey(key);
                } else {
                    // store the string object: key-pair
                    Map<Object, Object> objMap = redisDao.hGetAll(key);
                    objMap.entrySet().forEach((att) -> {
                        String attKey = (String) att.getKey();
                        if (!attKey.equalsIgnoreCase("eTag")) {
                            String attValue = att.getValue().toString();
                            map.put(attKey, isNumberValue(attValue)? Integer.parseInt(attValue) : att.getValue());
                        }
                    });
                }
            } else {
                // nest nodes
                String subKey = key.substring((objectKey + SPLITTER_COLON).length());
                Set<String> objSet = redisDao.sMembers(key);
                if (objSet.size() > 1) {
                    // process nested object list
                    List<Object> objectList = new ArrayList<>();
                    objSet.forEach((member) -> {
                        if (delete) {
                            populate(member, null, true);
                        } else {
                            Map<String, Object> listMap = new HashMap<>();
                            objectList.add(populate(member, listMap, false));
                        }
                    });
                    if (delete) {
                        redisDao.deleteKey(key);
                    } else {
                        map.put(subKey, objectList);
                    }
                } else {
                    // process nested object
                    if (delete) {
                        System.out.println("Keys: ");
                        redisDao.deleteKeys(Arrays.asList(key, objSet.iterator().next()));
                    } else {
                        Map<Object, Object> values = redisDao.hGetAll(objSet.iterator().next());
                        Map<String, Object> objMap = new HashMap<>();
                        values.entrySet().forEach((value) -> {
                            String name = value.getKey().toString();
                            String val = value.getValue().toString();
                            objMap.put(name, isNumberValue(val)? Integer.parseInt(val) : value.getValue());
                        });
                        map.put(subKey, objMap);
                    }
                }
            }
        });
        return map;
    }
    private boolean isNumberValue(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            logger.info("Non-number attributes: " + e.getMessage());
            return false;
        }

    }
}
