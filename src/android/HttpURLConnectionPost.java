package com.dmarc.cordovacall;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.OutputStream;

public class HttpURLConnectionPost extends AsyncTask<String, Void, String> {
	private final static String TAG = "HTTPURLCONNECTION test";

	@Override
	protected String doInBackground(String... datas) {
		return POST(datas[0], datas[1]);
	}

	@Override
	protected void onPostExecute(String result) {
		Log.d(TAG,"onPostExecute");
	}
	private String POST(String APIUrl, String sessionId) {
		String result = "";
		HttpURLConnection connection;
		try {
			URL url = new URL(APIUrl);
			connection = (HttpURLConnection)url.openConnection();
			connection.setRequestMethod("POST");
			// connection.setRequestProperty("authentication", MainActivity.Authentication);
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
			
			JSONObject json = new JSONObject();
			json.put("session_id", sessionId);

			OutputStream os = connection.getOutputStream();
            DataOutputStream writer = new DataOutputStream(os);
            String jsonString = json.toString();
            writer.writeBytes(jsonString);
            writer.flush();
            writer.close();
			
			InputStream inputStream = connection.getInputStream();
			int status = connection.getResponseCode();
			Log.d(TAG, String.valueOf(status));
			if(inputStream != null){
				InputStreamReader reader = new InputStreamReader(inputStream,"UTF-8");
				BufferedReader in = new BufferedReader(reader);
				
				String line="";
				while ((line = in.readLine()) != null) {
					result += (line+"\n");
				}
			} else{
				result = "Did not work!";
			}
			return result;
		} catch (Exception e) {
			Log.d("ATask InputStream", e.getLocalizedMessage());
			e.printStackTrace();
			return result;
		}
	}
}