#ifdef _CORDOVA_TEST_TARGET
#import "CDVPlugin.h"
#import "CDVInvokedUrlCommand.h"
#else
#import <Cordova/CDVPlugin.h>
#endif

@interface ScanbotSdkPlugin : CDVPlugin

- (void)initializeSdk:(CDVInvokedUrlCommand*)command;
- (void)documentDetection:(CDVInvokedUrlCommand*)command;
- (void)applyImageFilter:(CDVInvokedUrlCommand*)command;
- (void)createPdf:(CDVInvokedUrlCommand*)command;
- (void)performOcr:(CDVInvokedUrlCommand *)command;
- (void)getOcrConfigs:(CDVInvokedUrlCommand *)command;
- (void)cleanup:(CDVInvokedUrlCommand*)command;

@end
