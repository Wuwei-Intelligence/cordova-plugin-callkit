#import "AppDelegate.h"
#import "IonicDeeplinkPlugin.h"
#import "Intents/Intents.h"
#import <CallKit/CallKit.h>

static NSString *const DEEPLINK_PLUGIN_NAME = @"IonicDeeplinkPlugin";

@implementation AppDelegate (UnifiedContinueUserActivity)

- (BOOL)application:(UIApplication *)application continueUserActivity:(NSUserActivity *)userActivity restorationHandler:(void (^)(NSArray *restorableObjects))restorationHandler {
    
    // 首先檢查是否為 CallKit Intent
    if (userActivity.interaction) {
        INInteraction *interaction = userActivity.interaction;
        INIntent *intent = interaction.intent;
        BOOL isVideo = [intent isKindOfClass:[INStartVideoCallIntent class]];
        BOOL isAudio = [intent isKindOfClass:[INStartAudioCallIntent class]];
        
        if (isVideo || isAudio) {
            // 處理 CallKit Intent
            INPerson *contact;
            if(isVideo) {
                INStartVideoCallIntent *startCallIntent = (INStartVideoCallIntent *)intent;
                contact = startCallIntent.contacts.firstObject;
            } else {
                INStartAudioCallIntent *startCallIntent = (INStartAudioCallIntent *)intent;
                contact = startCallIntent.contacts.firstObject;
            }
            
            INPersonHandle *personHandle = contact.personHandle;
            NSString *callId = personHandle.value;

            if (callId) {
                NSString *callName = [[NSUserDefaults standardUserDefaults] stringForKey:callId];
                if(!callName) {
                    callName = callId;
                }
                NSDictionary *intentInfo = @{ @"callName" : callName, @"callId" : callId, @"isVideo" : isVideo?@YES:@NO};
                [[NSNotificationCenter defaultCenter] postNotificationName:@"RecentsCallNotification" object:intentInfo];
                return YES;
            }
        }
    }
    
    // 如果不是 CallKit Intent，則處理深度連結
    IonicDeeplinkPlugin *plugin = [self.viewController getCommandInstance:DEEPLINK_PLUGIN_NAME];
    if(plugin != nil) {
        BOOL handled = [plugin handleContinueUserActivity:userActivity];
        if (handled) {
            return YES;
        }
    }
    
    return NO;
}

@end