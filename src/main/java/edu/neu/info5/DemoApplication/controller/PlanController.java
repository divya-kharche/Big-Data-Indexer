package edu.neu.info5.DemoApplication.controller;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.neu.info5.DemoApplication.DemoApplication;
import edu.neu.info5.DemoApplication.service.AuthService;
import edu.neu.info5.DemoApplication.service.EncryptionService;
import edu.neu.info5.DemoApplication.service.PlanService;
import edu.neu.info5.DemoApplication.validator.SchemaValidator;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.*;

@RestController
public class PlanController {

    private static final Logger logger = LoggerFactory.getLogger(PlanController.class);

    private static ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PlanService planService;
    @Autowired
    private EncryptionService encryptionService;
    @Autowired
    private AuthService authService;

    private RabbitTemplate rabbitTemplate;

    public PlanController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @GetMapping("/")
    public String hello() {
        return "Hello";
    }

    SchemaValidator planSchema = new SchemaValidator();

    @PostMapping("/{object}")
    public ResponseEntity<String> createPlan(@PathVariable String object,
                                             @RequestBody String  reqJson,
                                             @RequestHeader("Authorization") String idToken,
                                             HttpEntity<String> req) {



        logger.info("GOOGLE_CLIENT_TOKEN:" + idToken);

        //Authorization
        if(!authService.authorize(idToken.substring(7))) {
            logger.error("TOKEN AUTHORIZATION - google token expired");

            return new ResponseEntity<>(new JSONObject().put("message", "Invalid Token").toString(),HttpStatus.UNAUTHORIZED);
        }

        logger.info("REQUEST BODY:" + req.getBody());
        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        JSONObject newPlan = new JSONObject(reqJson);

        try {
            logger.info(reqJson);
//            planSchema.validateSchema(new JSONObject(reqJson));
            planSchema.validateSchema(newPlan);
        } catch (Exception e) {
            logger.info("VALIDATING ERROR: SCHEMA NOT MATCH - " + e.getMessage());

            return ResponseEntity.badRequest().body(e.getMessage());
        }

//        JSONObject jsonObject = new JSONObject(reqJson);
        String internalKey = newPlan.get("objectType") + ":" + newPlan.get("objectId");

        if(planService.hasKey((internalKey))) {
            return new ResponseEntity<>(new JSONObject().put("message", "Key Exists").toString(), HttpStatus.CONFLICT);
        }

        logger.info("CREATING NEW DATA: key - " + internalKey + ": json - " + newPlan.toString());
        planService.savePlan(internalKey, newPlan);

        // Send a message to queue for indexing
        Map<String, String> message = new HashMap<>();
        message.put("operation", "SAVE");
        message.put("body", reqJson);

        System.out.println("Sending message: " + message);
        rabbitTemplate.convertAndSend(DemoApplication.queueName, message);

        String res = "{ObjectId: " + newPlan.get("objectId") + ", ObjectType: " + newPlan.get("objectType") + "}";

        return ResponseEntity.status(HttpStatus.CREATED).header("ETag",encryptionService.encrypt(newPlan.toString())).body(new JSONObject(res).toString());

    }

    @DeleteMapping("/{object}/{id}")
    public ResponseEntity<String> deleteByKey(@PathVariable String id,
                                              @PathVariable String object,
                                              @RequestHeader("Authorization") String idToken) {


        logger.info("DELETING OBJECT: id - " + id);

        String intervalKey = object + ":" + id;

        // Authorization
//        logger.info("AUTHORIZATION: GOOGLE_ID_TOKEN: " + idToken);

        if (!authService.authorize(idToken.substring(7))) {
            logger.error("TOKEN AUTHORIZATION - google token expired");
            return new ResponseEntity<>(new JSONObject().put("message", "Invalid Token").toString(), HttpStatus.BAD_REQUEST);
        }

        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        if (!planService.hasKey(intervalKey)) {
            logger.info("OBJECT NOT FOUND - " + intervalKey);

            return new ResponseEntity<>(new JSONObject().put("message", "No Data Found").toString(), HttpStatus.NOT_FOUND);
        }

        // Send message to queue for deleting indices
        Map<String, Object> plan = planService.getPlan(intervalKey);
        Map<String, String> message = new HashMap<>();
        message.put("operation", "DELETE");
        message.put("body",  new JSONObject(plan).toString());

        System.out.println("Sending message: " + message);
        rabbitTemplate.convertAndSend(DemoApplication.queueName, message);

        planService.delete(intervalKey);

        logger.info("DELETED SUCCESSFULLY: " + object + ":" + intervalKey);


        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{object}/{id}")
    public ResponseEntity<String> readByKey(@PathVariable String id,
                                            @PathVariable String object,
                                            @RequestHeader(value = "Authorization") String idToken,
                                            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        logger.info("RETRIEVING REDIS DATA: id - " + object + ":" + id);

        String intervalKey = object + ":" + id;

        //Authorization
        logger.info("AUTHORIZATION: GOOGLE_ID_TOKEN: " + idToken);
        if (!authService.authorize(idToken.substring(7))) {
            logger.error("TOKEN AUTHORIZATION - google token expired");
            return new ResponseEntity<>(new JSONObject().put("message", "Invalid Token").toString(), HttpStatus.UNAUTHORIZED);
        }
        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        if (!planService.hasKey(intervalKey)) {
            logger.info("OBJECT NOT FOUND - " + intervalKey);

            return new ResponseEntity<>(new JSONObject().put("message", "No Data Found").toString(), HttpStatus.NOT_FOUND);
        }

//        Map<String, Object> foundValue = new HashMap<>();
//        foundValue = planService.getPlan(intervalKey);

        Map<String, Object> foundValue = planService.getPlan(intervalKey);

        // Check Etag
        String planETag = planService.getEtag(intervalKey, "eTag");

        if (ifNoneMatch != null && ifNoneMatch.equals(planETag)) {
            return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
        }

        logger.info("OBJECT FOUND - " + intervalKey);

        try {
            String value = objectMapper.writeValueAsString(foundValue);
            return ResponseEntity.status(HttpStatus.CREATED).header("ETag","802531cc2974f9c0c0c4b28b7dba8262").body(value);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

    }



    @PutMapping("/{object}/{id}")
    public ResponseEntity<String> updatePlan(@PathVariable String object,
                                             @PathVariable String id,
                                             @RequestHeader(value = "Authorization") String idToken,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                             @RequestBody String reqJson) {

        // Authorization
        if (!authService.authorize(idToken.substring(7))) {
            logger.error("TOKEN AUTHORIZATION - google token expired");

            return new ResponseEntity<>(new JSONObject().put("message", "Invalid Token").toString(), HttpStatus.BAD_REQUEST);
        }

        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        JSONObject newPlan = new JSONObject(reqJson);

        try {
            logger.info(reqJson);
            planSchema.validateSchema(newPlan);
        } catch(Exception e) {
            logger.info("VALIDATING ERROR: SCHEMA NOT MATCH - " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        String intervalKey = object + ":" + id;

        if (!planService.hasKey(intervalKey)) {
            logger.info("PUT PLAN: " + intervalKey + " does not exist");

            return new ResponseEntity<>(new JSONObject().put("message", "ObjectId does not exist").toString(), HttpStatus.NOT_FOUND);
        }

        // Check ETag
        String planEtag = planService.getEtag(intervalKey, "eTag");

        if(ifMatch == null) {
            return new ResponseEntity<>(new JSONObject().put("message", "eTag not provided").toString(), HttpStatus.BAD_REQUEST);
        }

        if(!ifMatch.equals(planEtag)) {
            return new ResponseEntity<>(new JSONObject().put("message", "Etag does not match").toString(), HttpStatus.PRECONDITION_FAILED);
        }

        // Send message to queue for deleting previous indices incase of put
        Map<String, Object> oldPlan = planService.getPlan(intervalKey);
        Map<String, String> message = new HashMap<>();
        message.put("operation", "DELETE");
        message.put("body", new JSONObject(oldPlan).toString());

        System.out.println("Sending message: " + message);
        rabbitTemplate.convertAndSend(DemoApplication.queueName, message);

        planService.delete(intervalKey);

        JSONObject putNewPlan = new JSONObject(reqJson);
        try {
            logger.info(reqJson);
            planSchema.validateSchema(putNewPlan);
        } catch(Exception e) {
            logger.info("VALIDATING ERROR: SCHEMA NOT MATCH - " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        planService.savePlan(intervalKey, putNewPlan);

        // Send message to queue for index update
        Map<String, Object> newPutPlan = planService.getPlan(intervalKey);
        message = new HashMap<>();
        message.put("operation", "SAVE");
        message.put("body", new JSONObject(newPutPlan).toString());

        System.out.println("Sending message: " + message);
        rabbitTemplate.convertAndSend(DemoApplication.queueName, message);

        logger.info("PUT PLAN: " + intervalKey + " updates successfully");

        return ResponseEntity.status(HttpStatus.OK).header("ETag",encryptionService.encrypt(putNewPlan.toString()))
                .body(new JSONObject().put("message", "Updated Successfully").toString());

    }

    @PatchMapping("/{object}/{id}")
    public ResponseEntity<String> patchPlan(@PathVariable String object,
                                            @PathVariable String id,
                                            @RequestHeader(value = "Authorization") String idToken,
                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @RequestBody(required = false) String reqJson) {

        logger.info("PATCHING PLAN: " + object + ":" + id);

        // Authorization
        if (!authService.authorize(idToken.substring(7))) {
            logger.error("TOKEN AUTHORIZATION - google token expired");
            return new ResponseEntity<>(new JSONObject().put("Message", "Invalid Token").toString(), HttpStatus.BAD_REQUEST);
        }

        logger.info("TOKEN AUTHORIZATION SUCCESSFUL");

        JSONObject newPlan = new JSONObject(reqJson);

        try {
            logger.info(reqJson);
            planSchema.validateSchema(newPlan);
        } catch(Exception e) {
            logger.info("VALIDATING ERROR: SCHEMA NOT MATCH - " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        String intervalKey = object + ":" + id;

        if (!planService.hasKey(intervalKey)) {
            logger.info("PATCH PLAN: " + intervalKey + " does not exist");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        // Check Etag
        String planEtag = planService.getEtag(intervalKey, "eTag");

        if(ifMatch == null) {
            return new ResponseEntity<>(new JSONObject().put("message", "eTag not provided").toString(), HttpStatus.BAD_REQUEST);
        }

        if(!ifMatch.equals(planEtag)) {
            return new ResponseEntity<>(new JSONObject().put("message", "Etag does not match").toString(), HttpStatus.PRECONDITION_FAILED);
        }

        JSONObject patchNewPlan = new JSONObject(reqJson);

        planService.update(intervalKey, patchNewPlan);

        // Send message to queue for index update
        Map<String, String> message = new HashMap<>();
        message.put("operation", "SAVE");
        message.put("body", reqJson);

        System.out.println("Sending message: " + message);
        rabbitTemplate.convertAndSend(DemoApplication.queueName, message);

        logger.info("PATCH PLAN : " + intervalKey + " updates successfully");
        return ResponseEntity.status(HttpStatus.OK).header("ETag",encryptionService.encrypt(patchNewPlan.toString()))
                .body(new JSONObject().put("message", "Updated Successfully").toString());
    }
}
