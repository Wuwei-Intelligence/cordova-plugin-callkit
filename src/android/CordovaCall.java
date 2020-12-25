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

import org.json.JSONObject;

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
    private static final String KEY_RESULT_PERMISSION = "hasPermission";

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

    private static Boolean isSendCall = false;

    public static Boolean getIsSendCall() {
        return isSendCall;
    }

    public static void setIsSendCall(boolean val) {
        isSendCall = val;
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        cordovaInterface = cordova;
        cordovaWebView = webView;
        super.initialize(cordova, webView);
        appName = getApplicationName(this.cordova.getActivity().getApplicationContext());
        try {
            handle = new PhoneAccountHandle(new ComponentName(this.cordova.getActivity().getApplicationContext(),MyConnectionService.class),appName);
            tm = (TelecomManager) this.cordova.getActivity().getApplicationContext()
                .getSystemService(this.cordova.getActivity().getApplicationContext().TELECOM_SERVICE);
            //
            tm.unregisterPhoneAccount(handle);
        } catch (Exception e) {

        }
        //
        // if (android.os.Build.VERSION.SDK_INT >= 26) {
        //     phoneAccount = new PhoneAccount.Builder(handle, appName)
        //             .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED).build();
        //     tm.registerPhoneAccount(phoneAccount);
        // }
        // if (android.os.Build.VERSION.SDK_INT >= 23) {
        //     phoneAccount = new PhoneAccount.Builder(handle, appName)
        //             .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER).build();
        //     tm.registerPhoneAccount(phoneAccount);
        // }
        callbackContextMap.put("answer", new ArrayList<CallbackContext>());
        callbackContextMap.put("reject", new ArrayList<CallbackContext>());
        callbackContextMap.put("hangup", new ArrayList<CallbackContext>());
        callbackContextMap.put("sendCall", new ArrayList<CallbackContext>());
        callbackContextMap.put("receiveCall", new ArrayList<CallbackContext>());

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
            from = args.getString(0);
            permissionCounter = 2;
            pendingAction = "receiveCall";
            this.checkCallPermission();
            return true;
        } else if (action.equals("sendCall")) {
            setIsSendCall(true);
            this.callbackContext.success("Outgoing call successful");
            return true;
        } else if (action.equals("connectCall")) {
            this.callbackContext.success("Call connected successfully");
            return true;
        } else if (action.equals("endCall")) {
            if (isSendCall) setIsSendCall(false);
            this.callbackContext.success("Call ended successfully");
            return true;
        } else if (action.equals("registerEvent")) {
            String eventType = args.getString(0);
            ArrayList<CallbackContext> callbackContextList = callbackContextMap.get(eventType);
            callbackContextList.add(this.callbackContext);
            return true;
        } else if (action.equals("setAppName")) {
            String appName = args.getString(0);
            // handle = new PhoneAccountHandle(new ComponentName(this.cordova.getActivity().getApplicationContext(),MyConnectionService.class),appName);
            // if (android.os.Build.VERSION.SDK_INT >= 26) {
            //     phoneAccount = new PhoneAccount.Builder(handle, appName)
            //             .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED).build();
            //     tm.registerPhoneAccount(phoneAccount);
            // }
            // if (android.os.Build.VERSION.SDK_INT >= 23) {
            //     phoneAccount = new PhoneAccount.Builder(handle, appName)
            //             .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER).build();
            //     tm.registerPhoneAccount(phoneAccount);
            // }
            this.callbackContext.success("App Name Changed Successfully");
            return true;
        } else if (action.equals("setIcon")) {
            String iconName = args.getString(0);
            int iconId = this.cordova.getActivity().getApplicationContext().getResources().getIdentifier(iconName,
                "drawable", this.cordova.getActivity().getPackageName());
            if (iconId != 0) {
                icon = Icon.createWithResource(this.cordova.getActivity(), iconId);
                this.callbackContext.success("Icon Changed Successfully");
            } else {
                this.callbackContext.error(
                    "This icon does not exist. Make sure to add it to the res/drawable folder the right way.");
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
            if (realCallTo != null) {
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
        } else if (action.equals("isEnabledPhoneAccount")) {
            try {
                // PhoneAccount currentPhoneAccount = tm.getPhoneAccount(handle);
                 JSONObject returnObj = new JSONObject();
                // if (currentPhoneAccount.isEnabled()) {
                //     addProperty(returnObj, KEY_RESULT_PERMISSION, true);
                // } else {
                //     addProperty(returnObj, KEY_RESULT_PERMISSION, false);
                // }
                this.callbackContext.success(returnObj);
            } catch (Exception e) {
                this.callbackContext.error("check error");
            }
        } else if (action.equals("openSettingPhoneAccount")) {
            try {
                Intent phoneIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                this.cordova.getActivity().getApplicationContext().startActivity(phoneIntent);
                this.callbackContext.success("opening");
            } catch (Exception e) {
                this.callbackContext.error("Open Failed");
            }
        }
        return false;
    }

    private void checkCallPermission() {
        if (permissionCounter >= 1) {
            // PhoneAccount currentPhoneAccount = tm.getPhoneAccount(handle);
            // if (currentPhoneAccount.isEnabled()) {
            //     if (pendingAction == "receiveCall") {
            //         this.receiveCall();
            //     } else if (pendingAction == "sendCall") {
            //         this.sendCall();
            //     }
            // } else {
            //     if (permissionCounter == 2) {
            //         // Intent phoneIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
            //         // phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            //         // this.cordova.getActivity().getApplicationContext().startActivity(phoneIntent);
            //     } else {
            //         this.callbackContext
            //                 .error("You need to accept phone account permissions in order to send and receive calls");
            //     }
            // }
        }
        permissionCounter--;
    }

    private void receiveCall() {
        // Bundle callInfo = new Bundle();
        // callInfo.putString("from", from);
        // tm.addNewIncomingCall(handle, callInfo);
        // permissionCounter = 0;
        this.callbackContext.success("Incoming call successful");
    }

    private void sendCall() {
        // Uri uri = Uri.fromParts("tel", to, null);
        // Bundle callInfoBundle = new Bundle();
        // callInfoBundle.putString("to", to);
        // Bundle callInfo = new Bundle();
        // callInfo.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callInfoBundle);
        // callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        // callInfo.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, true);
        // tm.placeCall(uri, callInfo);
        // permissionCounter = 0;
        this.callbackContext.success("Outgoing call successful");
    }

    private void mute() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(true);
    }

    private void unmute() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(false);
    }

    private void speakerOn() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
    }

    private void speakerOff() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
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
        } catch (Exception e) {
            this.callbackContext.error("Call Failed");
        }
        this.callbackContext.success("Call Successful");
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
        throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext
                    .sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "CALL_PHONE Permission Denied"));
                return;
            }
        }
        switch (requestCode) {
            case CALL_PHONE_REQ_CODE:
                this.sendCall();
                break;
            case REAL_PHONE_CALL:
                this.callNumber();
                break;
        }
    }

    private void addProperty(JSONObject obj, String key, Object value) {
        try {
            if (value == null) {
                obj.put(key, JSONObject.NULL);
            } else {
                obj.put(key, value);
            }
        } catch (JSONException ignored) {
            //Believe exception only occurs when adding duplicate keys, so just ignore it
        }
    }

}
