package com.dmarc.cordovacall;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import android.os.Bundle;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.Manifest;
import android.telecom.Connection;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashMap;
import android.graphics.drawable.Icon;
import android.media.AudioManager;

import com.google.firebase.messaging.RemoteMessage;
import org.apache.cordova.firebase.FirebasePluginMessageReceiver;
import android.util.Log;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.app.Notification;
import android.text.TextUtils;
import android.content.ContentResolver;
import android.graphics.Color;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Random;

public class CordovaCall extends CordovaPlugin {

    private static String TAG = "CordovaCall";
    public static final int CALL_PHONE_REQ_CODE = 0;
    public static final int REAL_PHONE_CALL = 1;
    private int permissionCounter = 0;
    private String pendingAction;
    private TelecomManager tm;
    private PhoneAccountHandle handle;
    private PhoneAccount phoneAccount;
    private CallbackContext callbackContext;
    private String appName;
    private String from;
    private String to;
    private String realCallTo;
    private static HashMap<String, ArrayList<CallbackContext>> callbackContextMap = new HashMap<String, ArrayList<CallbackContext>>();
    private static CordovaInterface cordovaInterface;
    private static CordovaWebView cordovaWebView;
    private static Icon icon;
    private static CordovaCall instance;

    public static HashMap<String, ArrayList<CallbackContext>> getCallbackContexts() {
        return callbackContextMap;
    }

    public static CordovaInterface getCordova() {
        return cordovaInterface;
    }

    public static CordovaWebView getWebView() { 
        return cordovaWebView; 
    }

    public static Icon getIcon() {
        return icon;
    }

    public static CordovaCall getInstance() {
        return instance;
    }

    private CustomFCMReceiver customFCMReceiver;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        cordovaInterface = cordova;
        cordovaWebView = webView;
        customFCMReceiver = new CustomFCMReceiver();
        super.initialize(cordova, webView);
        appName = getApplicationName(this.cordova.getActivity().getApplicationContext());
        handle = new PhoneAccountHandle(new ComponentName(this.cordova.getActivity().getApplicationContext(),MyConnectionService.class),appName);
        tm = (TelecomManager)this.cordova.getActivity().getApplicationContext().getSystemService(this.cordova.getActivity().getApplicationContext().TELECOM_SERVICE);
        if(android.os.Build.VERSION.SDK_INT >= 26) {
          phoneAccount = new PhoneAccount.Builder(handle, appName)
                  .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                  .build();
          tm.registerPhoneAccount(phoneAccount);
        }
        if(android.os.Build.VERSION.SDK_INT >= 23) {
          phoneAccount = new PhoneAccount.Builder(handle, appName)
                   .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                   .build();
          tm.registerPhoneAccount(phoneAccount);
        }
        callbackContextMap.put("answer",new ArrayList<CallbackContext>());
        callbackContextMap.put("reject",new ArrayList<CallbackContext>());
        callbackContextMap.put("hangup",new ArrayList<CallbackContext>());
        callbackContextMap.put("sendCall",new ArrayList<CallbackContext>());
        callbackContextMap.put("receiveCall",new ArrayList<CallbackContext>());

        instance = this;
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        this.checkCallPermission();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        if (action.equals("receiveCall")) {
            Connection conn = MyConnectionService.getConnection();
            if(conn != null) {
                if(conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("You can't receive a call right now because you're already in a call");
                } else {
                    this.callbackContext.error("You can't receive a call right now");
                }
            } else {
                from = args.getString(0);
                permissionCounter = 2;
                pendingAction = "receiveCall";
                this.checkCallPermission();
            }
            return true;
        } else if (action.equals("sendCall")) {
            Connection conn = MyConnectionService.getConnection();
            if(conn != null) {
                if(conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("You can't make a call right now because you're already in a call");
                } else if(conn.getState() == Connection.STATE_DIALING) {
                    this.callbackContext.error("You can't make a call right now because you're already trying to make a call");
                } else {
                    this.callbackContext.error("You can't make a call right now");
                }
            } else {
                to = args.getString(0);
                permissionCounter = 2;
                pendingAction = "sendCall";
                this.checkCallPermission();
                /*cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        getCallPhonePermission();
                    }
                });*/
            }
            return true;
        } else if (action.equals("connectCall")) {
            Connection conn = MyConnectionService.getConnection();
            if(conn == null) {
                this.callbackContext.error("No call exists for you to connect");
            } else if(conn.getState() == Connection.STATE_ACTIVE) {
                this.callbackContext.error("Your call is already connected");
            } else {
                conn.setActive();
                Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), this.cordova.getActivity().getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                this.cordova.getActivity().getApplicationContext().startActivity(intent);
                this.callbackContext.success("Call connected successfully");
            }
            return true;
        } else if (action.equals("endCall")) {
            Connection conn = MyConnectionService.getConnection();
            if(conn == null) {
                this.callbackContext.error("No call exists for you to end");
            } else {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                conn.setDisconnected(cause);
                conn.destroy();
                MyConnectionService.deinitConnection();
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                for (final CallbackContext cbContext : callbackContexts) {
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "hangup event called successfully");
                            result.setKeepCallback(true);
                            cbContext.sendPluginResult(result);
                        }
                    });
                }
                this.callbackContext.success("Call ended successfully");
            }
            return true;
        } else if (action.equals("registerEvent")) {
            String eventType = args.getString(0);
            ArrayList<CallbackContext> callbackContextList = callbackContextMap.get(eventType);
            callbackContextList.add(this.callbackContext);
            return true;
        } else if (action.equals("setAppName")) {
            String appName = args.getString(0);
            handle = new PhoneAccountHandle(new ComponentName(this.cordova.getActivity().getApplicationContext(),MyConnectionService.class),appName);
            if(android.os.Build.VERSION.SDK_INT >= 26) {
              phoneAccount = new PhoneAccount.Builder(handle, appName)
                  .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                  .build();
              tm.registerPhoneAccount(phoneAccount);
            }
            if(android.os.Build.VERSION.SDK_INT >= 23) {
              phoneAccount = new PhoneAccount.Builder(handle, appName)
                   .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                   .build();
              tm.registerPhoneAccount(phoneAccount);
            }
            this.callbackContext.success("App Name Changed Successfully");
            return true;
        } else if (action.equals("setIcon")) {
            String iconName = args.getString(0);
            int iconId = this.cordova.getActivity().getApplicationContext().getResources().getIdentifier(iconName, "drawable", this.cordova.getActivity().getPackageName());
            if(iconId != 0) {
                icon = Icon.createWithResource(this.cordova.getActivity(), iconId);
                this.callbackContext.success("Icon Changed Successfully");
            } else {
                this.callbackContext.error("This icon does not exist. Make sure to add it to the res/drawable folder the right way.");
            }
            return true;
        } else if (action.equals("mute")) {
            this.mute();
            this.callbackContext.success("Muted Successfully");
            return true;
        } else if (action.equals("unmute")) {
            this.unmute();
            this.callbackContext.success("Unmuted Successfully");
            return true;
        } else if (action.equals("speakerOn")) {
            this.speakerOn();
            this.callbackContext.success("Speakerphone is on");
            return true;
        } else if (action.equals("speakerOff")) {
            this.speakerOff();
            this.callbackContext.success("Speakerphone is off");
            return true;
        } else if (action.equals("callNumber")) {
            realCallTo = args.getString(0);
            if(realCallTo != null) {
              cordova.getThreadPool().execute(new Runnable() {
                  public void run() {
                      callNumberPhonePermission();
                  }
              });
              this.callbackContext.success("Call Successful");
            } else {
              this.callbackContext.error("Call Failed. You need to enter a phone number.");
            }
            return true;
        }
        return false;
    }

    private void checkCallPermission() {
        if(permissionCounter >= 1) {
            PhoneAccount currentPhoneAccount = tm.getPhoneAccount(handle);
            if(currentPhoneAccount.isEnabled()) {
                if(pendingAction == "receiveCall") {
                    this.receiveCall();
                } else if(pendingAction == "sendCall") {
                    this.sendCall();
                }
            } else {
                if(permissionCounter == 2) {
                    Intent phoneIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                    phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    this.cordova.getActivity().getApplicationContext().startActivity(phoneIntent);
                } else {
                    this.callbackContext.error("You need to accept phone account permissions in order to send and receive calls");
                }
            }
        }
        permissionCounter--;
    }

    private void receiveCall() {
        Bundle callInfo = new Bundle();
        callInfo.putString("from",from);
        tm.addNewIncomingCall(handle, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Incoming call successful");
    }

    private void sendCall() {
        Uri uri = Uri.fromParts("tel", to, null);
        Bundle callInfoBundle = new Bundle();
        callInfoBundle.putString("to",to);
        Bundle callInfo = new Bundle();
        callInfo.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,callInfoBundle);
        callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        callInfo.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, true);
        tm.placeCall(uri, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Outgoing call successful");
    }

    private void mute() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(true);
    }

    private void unmute() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(false);
    }

    private void speakerOn() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
    }

    private void speakerOff() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(false);
    }

    public static String getApplicationName(Context context) {
      ApplicationInfo applicationInfo = context.getApplicationInfo();
      int stringId = applicationInfo.labelRes;
      return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    protected void getCallPhonePermission() {
        cordova.requestPermission(this, CALL_PHONE_REQ_CODE, Manifest.permission.CALL_PHONE);
    }

    protected void callNumberPhonePermission() {
        cordova.requestPermission(this, REAL_PHONE_CALL, Manifest.permission.CALL_PHONE);
    }

    private void callNumber() {
        try {
          Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", realCallTo, null));
          this.cordova.getActivity().getApplicationContext().startActivity(intent);
        } catch(Exception e) {
          this.callbackContext.error("Call Failed");
        }
        this.callbackContext.success("Call Successful");
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException
    {
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "CALL_PHONE Permission Denied"));
                return;
            }
        }
        switch(requestCode)
        {
            case CALL_PHONE_REQ_CODE:
                this.sendCall();
                break;
            case REAL_PHONE_CALL:
                this.callNumber();
                break;
        }
    }

    // private class CustomFCMReceiver extends FirebasePluginMessageReceiver {
    //     @Override
    //     public boolean onMessageReceived(RemoteMessage remoteMessage) {
    //         Log.d("CustomFCMReceiver", "onMessageReceived");
    //         boolean isHandled = true;

    //         try {
    //             // Map<String, String> data = remoteMessage.getData();
    //             Connection conn = MyConnectionService.getConnection();
    //             if(conn != null) {
    //                 isHandled = false;
    //             } else {
    //                 from = "測試一波";
    //                 permissionCounter = 2;
    //                 pendingAction = "receiveCall";
    //                 checkCallPermission();
    //             }
    //         }catch (Exception e){
    //             handleException("onMessageReceived", e);
    //         }

    //         return isHandled;
    //     }

    //     @Override
    //     public boolean sendMessage(Bundle bundle){
    //         Log.d("CustomFCMReceiver", "sendMessage");
    //         boolean isHandled = true;

    //         try {
    //             // Map<String, String> data = bundleToMap(bundle);
    //             Connection conn = MyConnectionService.getConnection();
    //             if(conn != null) {
    //                 isHandled = false;
    //             } else {
    //                 from = "測試一波";
    //                 permissionCounter = 2;
    //                 pendingAction = "receiveCall";
    //                 checkCallPermission();
    //             }
    //         }catch (Exception e){
    //             handleException("onMessageReceived", e);
    //         }

    //         return isHandled;
    //     }
        
    // }
    // protected static void handleException(String description, Exception exception) {
    //     handleError(description + ": " + exception.toString());
    // }
    // protected static void handleError(String errorMsg) {
    //     Log.e(TAG, errorMsg);
    // }


    public class FirebasePluginMessagingService extends FirebaseMessagingService {

        private static final String TAG = "FirebasePlugin";
    
        static final String defaultSmallIconName = "notification_icon";
        static final String defaultLargeIconName = "notification_icon_large";
    
    
        /**
         * Called if InstanceID token is updated. This may occur if the security of
         * the previous token had been compromised. Note that this is called when the InstanceID token
         * is initially generated so this is where you would retrieve the token.
         */
        @Override
        public void onNewToken(String refreshedToken) {
            try{
                super.onNewToken(refreshedToken);
                Log.d(TAG, "Refreshed token: " + refreshedToken);
                FirebasePlugin.sendToken(refreshedToken);
            }catch (Exception e){
                FirebasePlugin.handleExceptionWithoutContext(e);
            }
        }
    
    
        /**
         * Called when message is received.
         * Called IF message is a data message (i.e. NOT sent from Firebase console)
         * OR if message is a notification message (e.g. sent from Firebase console) AND app is in foreground.
         * Notification messages received while app is in background will not be processed by this method;
         * they are handled internally by the OS.
         *
         * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
         */
        @Override
        public void onMessageReceived(RemoteMessage remoteMessage) {
            try{
                // [START_EXCLUDE]
                // There are two types of messages data messages and notification messages. Data messages are handled
                // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
                // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
                // is in the foreground. When the app is in the background an automatically generated notification is displayed.
                // When the user taps on the notification they are returned to the app. Messages containing both notification
                // and data payloads are treated as notification messages. The Firebase console always sends notification
                // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
                // [END_EXCLUDE]
    
                // Pass the message to the receiver manager so any registered receivers can decide to handle it
                boolean wasHandled = FirebasePluginMessageReceiverManager.onMessageReceived(remoteMessage);
                if (wasHandled) {
                    Log.d(TAG, "Message was handled by a registered receiver");
    
                    // Don't process the message in this method.
                    return;
                }
    
                if(FirebasePlugin.applicationContext == null){
                    FirebasePlugin.applicationContext = this.getApplicationContext();
                }

                String messageType;
                String title = null;
                String body = null;
                String id = null;
                String sound = null;
                String vibrate = null;
                String light = null;
                String color = null;
                String icon = null;
                String channelId = null;
                String visibility = null;
                String priority = null;
                boolean foregroundNotification = false;
    
                Map<String, String> data = remoteMessage.getData();
    
                if (remoteMessage.getNotification() != null) {
                    // Notification message payload
                    Log.i(TAG, "Received message: notification");
                    messageType = "notification";
                    id = remoteMessage.getMessageId();
                    RemoteMessage.Notification notification = remoteMessage.getNotification();
                    title = notification.getTitle();
                    body = notification.getBody();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        channelId = notification.getChannelId();
                    }
                    sound = notification.getSound();
                    color = notification.getColor();
                    icon = notification.getIcon();
                }else{
                    Log.i(TAG, "Received message: data");
                    messageType = "data";
                }
    
                if (data != null) {
                    // Data message payload
                    if(data.containsKey("notification_foreground")){
                        foregroundNotification = true;
                    }
                    if(data.containsKey("notification_title")) title = data.get("notification_title");
                    if(data.containsKey("notification_body")) body = data.get("notification_body");
                    if(data.containsKey("notification_android_channel_id")) channelId = data.get("notification_android_channel_id");
                    if(data.containsKey("notification_android_id")) id = data.get("notification_android_id");
                    if(data.containsKey("notification_android_sound")) sound = data.get("notification_android_sound");
                    if(data.containsKey("notification_android_vibrate")) vibrate = data.get("notification_android_vibrate");
                    if(data.containsKey("notification_android_light")) light = data.get("notification_android_light"); //String containing hex ARGB color, miliseconds on, miliseconds off, example: '#FFFF00FF,1000,3000'
                    if(data.containsKey("notification_android_color")) color = data.get("notification_android_color");
                    if(data.containsKey("notification_android_icon")) icon = data.get("notification_android_icon");
                    if(data.containsKey("notification_android_visibility")) visibility = data.get("notification_android_visibility");
                    if(data.containsKey("notification_android_priority")) priority = data.get("notification_android_priority");
                }
    
                if (TextUtils.isEmpty(id)) {
                    Random rand = new Random();
                    int n = rand.nextInt(50) + 1;
                    id = Integer.toString(n);
                }
    
                Log.d(TAG, "From: " + remoteMessage.getFrom());
                Log.d(TAG, "Id: " + id);
                Log.d(TAG, "Title: " + title);
                Log.d(TAG, "Body: " + body);
                Log.d(TAG, "Sound: " + sound);
                Log.d(TAG, "Vibrate: " + vibrate);
                Log.d(TAG, "Light: " + light);
                Log.d(TAG, "Color: " + color);
                Log.d(TAG, "Icon: " + icon);
                Log.d(TAG, "Channel Id: " + channelId);
                Log.d(TAG, "Visibility: " + visibility);
                Log.d(TAG, "Priority: " + priority);
    
    
                if (!TextUtils.isEmpty(body) || !TextUtils.isEmpty(title) || (data != null && !data.isEmpty())) {
                    boolean showNotification = (FirebasePlugin.inBackground() || !FirebasePlugin.hasNotificationsCallback() || foregroundNotification) && (!TextUtils.isEmpty(body) || !TextUtils.isEmpty(title));
                    sendMessage(remoteMessage, data, messageType, id, title, body, showNotification, sound, vibrate, light, color, icon, channelId, priority, visibility);
                }
            }catch (Exception e){
                FirebasePlugin.handleExceptionWithoutContext(e);
            }
        }
    
        private void sendMessage(RemoteMessage remoteMessage, Map<String, String> data, String messageType, String id, String title, String body, boolean showNotification, String sound, String vibrate, String light, String color, String icon, String channelId, String priority, String visibility) {
            Log.d(TAG, "sendMessage(): messageType="+messageType+"; showNotification="+showNotification+"; id="+id+"; title="+title+"; body="+body+"; sound="+sound+"; vibrate="+vibrate+"; light="+light+"; color="+color+"; icon="+icon+"; channel="+channelId+"; data="+data.toString());
            Bundle bundle = new Bundle();
            for (String key : data.keySet()) {
                bundle.putString(key, data.get(key));
            }
            bundle.putString("messageType", messageType);
            this.putKVInBundle("id", id, bundle);
            this.putKVInBundle("title", title, bundle);
            this.putKVInBundle("body", body, bundle);
            this.putKVInBundle("sound", sound, bundle);
            this.putKVInBundle("vibrate", vibrate, bundle);
            this.putKVInBundle("light", light, bundle);
            this.putKVInBundle("color", color, bundle);
            this.putKVInBundle("icon", icon, bundle);
            this.putKVInBundle("channel_id", channelId, bundle);
            this.putKVInBundle("priority", priority, bundle);
            this.putKVInBundle("visibility", visibility, bundle);
            this.putKVInBundle("show_notification", String.valueOf(showNotification), bundle);
            this.putKVInBundle("from", remoteMessage.getFrom(), bundle);
            this.putKVInBundle("collapse_key", remoteMessage.getCollapseKey(), bundle);
            this.putKVInBundle("sent_time", String.valueOf(remoteMessage.getSentTime()), bundle);
            this.putKVInBundle("ttl", String.valueOf(remoteMessage.getTtl()), bundle);
    
            if (showNotification) {
                Intent intent = new Intent(this, OnNotificationOpenReceiver.class);
                intent.putExtras(bundle);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    
                // Channel
                if(channelId == null || !FirebasePlugin.channelExists(channelId)){
                    channelId = FirebasePlugin.defaultChannelId;
                }
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    Log.d(TAG, "Channel ID: "+channelId);
                }
    
                NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
                notificationBuilder
                        .setContentTitle("走你")
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);
    
                // On Android O+ the sound/lights/vibration are determined by the channel ID
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O){
                    // Sound
                    if (sound == null) {
                        Log.d(TAG, "Sound: none");
                    }else if (sound.equals("default")) {
                        notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                        Log.d(TAG, "Sound: default");
                    }else{
                        Uri soundPath = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/raw/" + sound);
                        Log.d(TAG, "Sound: custom=" + sound+"; path="+soundPath.toString());
                        notificationBuilder.setSound(soundPath);
                    }
    
                    // Light
                    if (light != null) {
                        try {
                            String[] lightsComponents = color.replaceAll("\\s", "").split(",");
                            if (lightsComponents.length == 3) {
                                int lightArgb = Color.parseColor(lightsComponents[0]);
                                int lightOnMs = Integer.parseInt(lightsComponents[1]);
                                int lightOffMs = Integer.parseInt(lightsComponents[2]);
                                notificationBuilder.setLights(lightArgb, lightOnMs, lightOffMs);
                                Log.d(TAG, "Lights: color="+lightsComponents[0]+"; on(ms)="+lightsComponents[2]+"; off(ms)="+lightsComponents[3]);
                            }
    
                        } catch (Exception e) {}
                    }
    
                    // Vibrate
                    if (vibrate != null){
                        try {
                            String[] sVibrations = vibrate.replaceAll("\\s", "").split(",");
                            long[] lVibrations = new long[sVibrations.length];
                            int i=0;
                            for(String sVibration: sVibrations){
                                lVibrations[i] = Integer.parseInt(sVibration.trim());
                                i++;
                            }
                            notificationBuilder.setVibrate(lVibrations);
                            Log.d(TAG, "Vibrate: "+vibrate);
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }
                }
    
    
                // Icon
                int defaultSmallIconResID = getResources().getIdentifier(defaultSmallIconName, "drawable", getPackageName());
                int customSmallIconResID = 0;
                if(icon != null){
                    customSmallIconResID = getResources().getIdentifier(icon, "drawable", getPackageName());
                }
    
                if (customSmallIconResID != 0) {
                    notificationBuilder.setSmallIcon(customSmallIconResID);
                    Log.d(TAG, "Small icon: custom="+icon);
                }else if (defaultSmallIconResID != 0) {
                    Log.d(TAG, "Small icon: default="+defaultSmallIconName);
                    notificationBuilder.setSmallIcon(defaultSmallIconResID);
                } else {
                    Log.d(TAG, "Small icon: application");
                    notificationBuilder.setSmallIcon(getApplicationInfo().icon);
                }
    
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    int defaultLargeIconResID = getResources().getIdentifier(defaultLargeIconName, "drawable", getPackageName());
                    int customLargeIconResID = 0;
                    if(icon != null){
                        customLargeIconResID = getResources().getIdentifier(icon+"_large", "drawable", getPackageName());
                    }
    
                    int largeIconResID;
                    if (customLargeIconResID != 0 || defaultLargeIconResID != 0) {
                        if (customLargeIconResID != 0) {
                            largeIconResID = customLargeIconResID;
                            Log.d(TAG, "Large icon: custom="+icon);
                        }else{
                            Log.d(TAG, "Large icon: default="+defaultLargeIconName);
                            largeIconResID = defaultLargeIconResID;
                        }
                        notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), largeIconResID));
                    }
                }
    
                // Color
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    int defaultColor = getResources().getColor(getResources().getIdentifier("accent", "color", getPackageName()), null);
                    if(color != null){
                        notificationBuilder.setColor(Color.parseColor(color));
                        Log.d(TAG, "Color: custom="+color);
                    }else{
                        Log.d(TAG, "Color: default");
                        notificationBuilder.setColor(defaultColor);
                    }
                }
    
                // Visibility
                int iVisibility = NotificationCompat.VISIBILITY_PUBLIC;
                if(visibility != null){
                    iVisibility = Integer.parseInt(visibility);
                }
                Log.d(TAG, "Visibility: " + iVisibility);
                notificationBuilder.setVisibility(iVisibility);
    
                // Priority
                int iPriority = NotificationCompat.PRIORITY_MAX;
                if(priority != null){
                    iPriority = Integer.parseInt(priority);
                }
                Log.d(TAG, "Priority: " + iPriority);
                notificationBuilder.setPriority(iPriority);
    
    
                // Build notification
                Notification notification = notificationBuilder.build();
    
                // Display notification
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                Log.d(TAG, "show notification: "+notification.toString());
                notificationManager.notify(id.hashCode(), notification);
            }
            // Send to plugin
            FirebasePlugin.sendMessage(bundle, this.getApplicationContext());
        }
    
        private void putKVInBundle(String k, String v, Bundle b){
            if(v != null && !b.containsKey(k)){
                b.putString(k, v);
            }
        }
    }

}
