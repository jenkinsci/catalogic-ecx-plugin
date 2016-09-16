package com.catalogic.ecx.sdk;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.net.URLEncoder;


public class ECXSdk {

    private static final Logger logger = LoggerFactory.getLogger(ECXSdk.class);


    private String user;
    private String pwd;
    private String url;
    private boolean production;
    private String sessionid;
    private LinkedHashMap<String, String> jobList;
    private LinkedHashSet<String> jobMessages = new LinkedHashSet<>();


    private final static String ACCEPTHEADER = "application/json";
    private final static String CONTENTTYPE = ACCEPTHEADER;
    private final static String ECXAUTHORIZATION_HEADER = "x-endeavour-sessionid";

    public ECXSdk(String user, String password, String url, boolean production) {
        this.user = user;
        this.pwd = password;
        this.url = url + "/api";
        this.production = production;
    }

    public void connect() {

        setSessionId();
    }

    private void setSessionId() {

        doPostForSessionId("/endeavour/session", "SESSIONID");
    }

    public void setJobList()  {
    String q ="[{\"property\":\"name\",\"direction\":\"ASC\"}]";

  try { 
	  doGet("/endeavour/job?sort="+ URLEncoder.encode(q,"UTF-8"), "JOBS");
    }catch(IOException e){
	  throw new RuntimeException(e);
  }
    }

    public void runJob(String jobId) {
        jobMessages.clear();

        setStatus("Launching job with jobid " + jobId);

        doPost("/endeavour/job/" + jobId + "?action=start", "JOB");

    }

    public void monitorJob(String jobId) {

        doGet("/endeavour/job/" + jobId, "JOBMONITOR");

    }

    public void getJobResult(String jobId) {

        doGet("/endeavour/job/" + jobId, "JOBRESULT");

    }

    private void doPost(String endpoint, String jsonPropertyOfInterest) {

        HttpPost httpPost = new HttpPost(url + endpoint);
        httpPost.addHeader(HttpHeaders.ACCEPT, ACCEPTHEADER);
        httpPost.addHeader(HttpHeaders.CONTENT_TYPE, CONTENTTYPE);
        httpPost.addHeader(ECXAUTHORIZATION_HEADER, getSessionId());

        processRESTRequest(httpPost, jsonPropertyOfInterest);

    }

    private void doPostForSessionId(String endpoint, String jsonPropertyOfInterest) {

        HttpPost httpPost = new HttpPost(url + endpoint);
        httpPost.addHeader(HttpHeaders.ACCEPT, ACCEPTHEADER);
        httpPost.addHeader(HttpHeaders.CONTENT_TYPE, CONTENTTYPE);

        String auth = user + ":" + pwd;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("ISO-8859-1")));
        String authHeader = "Basic " + new String(encodedAuth, Charset.forName("UTF-8"));
        httpPost.addHeader(HttpHeaders.AUTHORIZATION, authHeader);

        processRESTRequest(httpPost, jsonPropertyOfInterest);

    }

    private void doGet(String endpoint, String property) {

        HttpGet httpGet = new HttpGet(url + endpoint);
        httpGet.addHeader(HttpHeaders.ACCEPT, ACCEPTHEADER);
        httpGet.addHeader(HttpHeaders.CONTENT_TYPE, CONTENTTYPE);
        httpGet.addHeader(ECXAUTHORIZATION_HEADER, getSessionId());

        processRESTRequest(httpGet, property);

    }
     
    private void processRESTRequest(HttpUriRequest request, String jsonPropertyOfInterest) {
        try {
            _processRESTRequest(request, jsonPropertyOfInterest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void _processRESTRequest(HttpUriRequest request, String jsonPropertyOfInterest) throws Exception {
    	CloseableHttpClient client;
    	
    	if(production)
    	{
    		client = HttpClients.createDefault();	
    	}
    	else {
       	SSLContextBuilder builder = new SSLContextBuilder();
    	builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		client = HttpClients.custom().setSSLSocketFactory(sslsf).build();
    	}
		try{
			
			 try (CloseableHttpResponse response = client.execute(request)){
				 
				 validateStatusLine(response, jsonPropertyOfInterest);
		         processRESTResponse(response, jsonPropertyOfInterest);
			 }
		}
		finally{
			client.close();
		}
    }
           
    private void processRESTResponse(CloseableHttpResponse httpResponse, String jsonPropertyOfInterest) {


        HttpEntity entity = httpResponse.getEntity();

        if (entity != null) {

            try (InputStream inputStream = entity.getContent()) {

                String result = convertStreamToString(inputStream);

                parseJSONStreamForPropertyOfInterest(jsonPropertyOfInterest, result);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void validateStatusLine(CloseableHttpResponse response, String jsonPropertyOfInterest) {

        int expectedResponse = -1;
        switch (jsonPropertyOfInterest) {

            case "JOBRESULT":
            case "SESSIONID":
            case "JOBS":
            case "JOB":
            case "JOBMONITOR":
                expectedResponse = HttpStatus.SC_OK;
                break;
            default:
        }

        if (response.getStatusLine().getStatusCode() != expectedResponse) {
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatusLine().getStatusCode());
        }
    }

    private String convertStreamToString(InputStream is) {


        StringBuilder sb = new StringBuilder();
        String line = null;

         try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    private void parseJSONStreamForPropertyOfInterest(String jsonPropertyOfInterest, String jsonRestResponse) {

        JSONParser parser = new JSONParser();
        JSONObject data;

        try {
            data = (JSONObject) parser.parse(jsonRestResponse);

            switch (jsonPropertyOfInterest) {
                case "SESSIONID":
                    this.sessionid = (String) data.get("sessionid");
                    break;
                case "JOBS": {
                    jobList = new LinkedHashMap<>();
                    JSONArray jobArray = (JSONArray) data.get("jobs");

                    for (Object object : jobArray) {

                        JSONObject aJson = (JSONObject) object;
                        String jobName = (String) aJson.get("name");
                        String jobId = (String) aJson.get("id");

                        jobList.put(jobId, jobName.toLowerCase() + " [" + jobId + "] ");
                    }
                    break;
                }
                case "JOB": {
                    setStatus((String) data.get("status"));
                    break;
                }
                case "JOBMONITOR": {
                    setStatus((String) data.get("status"));
                    break;
                }
                case "JOBRESULT": {
                    setStatus((String) data.get("lastSessionStatus"));
                    break;
                }

                default:
            }

        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void setStatus(String status) {

        this.jobMessages.add(status);

    }

    public Iterator<String> getStatus() {
        return jobMessages.iterator();
    }

    private String getSessionId() {
        return sessionid;
    }

    public Map<String, String> getJobList() {
        return jobList;
    }
}
