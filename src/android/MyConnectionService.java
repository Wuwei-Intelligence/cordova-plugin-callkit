package com.dmarc.cordovacall;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.os.Handler;
import android.net.Uri;
import java.util.ArrayList;
import android.util.Log;

//
import android.app.PendingIntent;
import android.content.ComponentName;
import org.apache.cordova.firebase.FirebasePlugin;

public class MyConnectionService extends ConnectionService {

    private static String TAG = "MyConnectionService";
    private static Connection conn;

    public static Connection getConnection() {
        return conn;
    }

    public static void deinitConnection() {
        conn = null;
    }

    @Override
    public Connection onCreateIncomingConnection(final PhoneAccountHandle connectionManagerPhoneAccount, final ConnectionRequest request) {
        String sessionid = request.getExtras().getString("android_voip_session_id");
        if (sessionid == null) {
            Connection connection = new Connection() {};
            connection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            connection.destroy();
            return connection;
        }
        final Connection connection = new Connection() {

            @Override
            public void onAnswer() { }

            @Override
            public void onReject() { }

            @Override
            public void onAbort() { }

            @Override
            public void onDisconnect() { }
        };
        connection.setAddress(Uri.parse(request.getExtras().getString("from")), TelecomManager.PRESENTATION_ALLOWED);
        Icon icon = CordovaCall.getIcon();
        if(icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence)"", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        conn = connection;

        connection.setAudioModeIsVoip(true);
        return connection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        final Connection connection = new Connection() {
            @Override
            public void onAnswer() { }

            @Override
            public void onReject() { }

            @Override
            public void onAbort() { }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                if (CordovaCall.getCordova() != null) {
                    ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                    for (final CallbackContext callbackContext : callbackContexts) {
                        CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                            public void run() {
                                PluginResult result = new PluginResult(PluginResult.Status.OK, "hangup event called successfully");
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            }
                        });
                    }
                }
            }

            @Override
            public void onStateChanged(int state) {
            //   if(state == Connection.STATE_DIALING) {
            //     final Handler handler = new Handler();
            //     handler.postDelayed(new Runnable() {
            //         @Override
            //         public void run() {
            //             Intent intent = new Intent(CordovaCall.getCordova().getActivity().getApplicationContext(), CordovaCall.getCordova().getActivity().getClass());
            //             intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            //             CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
            //         }
            //     }, 500);
            //   }
            }
        };
        connection.setAddress(Uri.parse(request.getExtras().getString("to")), TelecomManager.PRESENTATION_ALLOWED);
        Icon icon = CordovaCall.getIcon();
        if(icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence)"", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        connection.setDialing();
        conn = connection;
        connection.setAudioModeIsVoip(true);
        ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("sendCall");
        if(callbackContexts != null) {
            for (final CallbackContext callbackContext : callbackContexts) {
                CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                    public void run() {
                        PluginResult result = new PluginResult(PluginResult.Status.OK, "sendCall event called successfully");
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    }
                });
            }
        }
        return connection;
    }
}
