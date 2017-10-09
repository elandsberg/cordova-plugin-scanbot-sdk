#ifdef _CORDOVA_TEST_TARGET
#import "CDVPlugin.h"
#else
#import <Cordova/CDVPlugin.h>
#endif

@interface ScanbotCameraPlugin : CDVPlugin

- (void)startCamera:(CDVInvokedUrlCommand *)command;
- (void)dismissCamera:(CDVInvokedUrlCommand *)command;

- (void)startCropping:(CDVInvokedUrlCommand *)command;

@end
