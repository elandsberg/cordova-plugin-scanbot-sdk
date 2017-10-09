#import "ScanbotCameraPlugin.h"
#import "ScanbotCameraViewController.h"

#import <Foundation/Foundation.h>

#ifdef _CORDOVA_TEST_TARGET
#import "CDVPlugin.h"
#import "CDVInvokedUrlCommand.h"
#else
#import <Cordova/CDVPlugin.h>
#endif

#import <ScanbotSDK/SBSDKScanbotSDK.h>

#import "LoggingUtils.h"
#import "HandyJSONParameters.h"

#import "ImageUtils.h"
#import "SBSDKPolygon+JSON.h"
#import "UIColor+JSON.h"

static NSString *const defauleEdgeColor = @"#ff80cbc4";

@interface ScanbotCameraPlugin () <SBSDKCropViewControllerDelegate>

@property (strong, nonatomic) CDVInvokedUrlCommand *cropVCCommand;
@property (nonatomic) CGFloat croppingOutputQuality;

@end

@implementation ScanbotCameraPlugin

- (BOOL)checkSDKInitializationWithCommand:(CDVInvokedUrlCommand *)command {
    if (![SharedConfiguration defaultConfiguration].isSDKInitialized) {
        NSString *errorMessage = @"Scanbot SDK is not initialized. Please call the ScanbotSdk.initializeSdk() function first.";
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                          messageAsString:errorMessage];
        [self.commandDelegate sendPluginResult:pluginResult
                                    callbackId:command.callbackId];
        return NO;
    }
    return YES;
}

- (UIViewController *)topMostController {
    UIViewController *topController = [UIApplication sharedApplication].keyWindow.rootViewController;
    while (topController.presentedViewController) {
        topController = topController.presentedViewController;
    }
    return topController;
}

- (void)startCamera:(CDVInvokedUrlCommand *)command {
    if (![self checkSDKInitializationWithCommand:command]) {
        return;
    }
    
    SBSDKLog(@"ScanbotCameraPlugin: startCamera ...");
    [self.commandDelegate runInBackground:^{
        SBSDKLog(@"Creating ScanbotCameraViewController instance ...");
        ScanbotCameraViewController *scanbotCameraViewController = [ScanbotCameraViewController new];
        scanbotCameraViewController.command = command;
        scanbotCameraViewController.commandDelegate = self.commandDelegate;
        
        HandyJSONParameters *params = [HandyJSONParameters JSONParametersWithCordovaCommand:command];
        
        NSString *const edgeColorKey = @"edgeColor";
        UIColor *edgeColor = [UIColor colorFromHexString:defauleEdgeColor];
        if (params[edgeColorKey] && ![params[edgeColorKey] isEqualToString:@""]) {
            edgeColor = [UIColor colorFromHexString:params[edgeColorKey]];
        }
        scanbotCameraViewController.strokeColor = edgeColor;
        scanbotCameraViewController.imageCompressionQuality = params.qualityValue;
        scanbotCameraViewController.autoSnappingEnabled = [params boolParameterValueForKey:@"autoSnappingEnabled"
                                                                              defaultValue:YES];
        scanbotCameraViewController.autoSnappingSensitivity = params.autoSnappingSensitivityValue;
        scanbotCameraViewController.sampleSize = params.sampleSizeValue;
        
        SBSDKLog(@"Starting scanbot camera....");
        dispatch_async(dispatch_get_main_queue(), ^{
            [[self topMostController] presentViewController:scanbotCameraViewController
                                                   animated:YES
                                                 completion:nil];
        });
    }];
}

- (void)dismissCamera:(CDVInvokedUrlCommand *)command {
    if (![self checkSDKInitializationWithCommand:command]) {
        return;
    }
    
    UIViewController *topmostVC = [self topMostController];
    if ([topmostVC isKindOfClass:[ScanbotCameraViewController class]]) {
        [topmostVC dismissViewControllerAnimated:YES completion:nil];
    }
}

- (void)startCropping:(CDVInvokedUrlCommand *)command {
    if (![self checkSDKInitializationWithCommand:command]) {
        return;
    }
    
    SBSDKLog(@"ScanbotCameraPlugin: startCropping ...");
    [self.commandDelegate runInBackground:^{
        HandyJSONParameters *params = [HandyJSONParameters JSONParametersWithCordovaCommand:command];
        self.croppingOutputQuality = params.qualityValue;

        NSString *imageFileUri = params[@"imageFileUri"];
        NSURL *inputImageURL = [NSURL URLWithString:imageFileUri];
        SBSDKLog(@"inputImageURL: %@", inputImageURL);
        
        UIImage *image = [ImageUtils loadImage:inputImageURL.absoluteString];
        SBSDKCropViewController *cropController = [[SBSDKCropViewController alloc] init];
        cropController.image = image;
        cropController.delegate = self;
        
        NSString *const edgeColorKey = @"edgeColor";
        UIColor *edgeColor = [UIColor colorFromHexString:defauleEdgeColor];
        if (params[edgeColorKey] && ![params[edgeColorKey] isEqualToString:@""]) {
            edgeColor = [UIColor colorFromHexString:params[edgeColorKey]];
        }
        cropController.edgeColor = edgeColor;
        cropController.magneticEdgeColor = edgeColor;
        
        self.cropVCCommand = command;
        
        SBSDKLog(@"Starting SBSDKCropViewController...");
        dispatch_async(dispatch_get_main_queue(), ^{
            UINavigationController *navigationController = [[UINavigationController alloc] initWithRootViewController:cropController];
            navigationController.navigationBar.barStyle = UIBarStyleBlack;
            navigationController.navigationBar.tintColor = [UIColor whiteColor];
            [[self topMostController] presentViewController:navigationController
                                                   animated:YES
                                                 completion:nil];
        });
    }];
}

#pragma mark - SBSDKCropViewController delegate

- (void)cropViewController:(SBSDKCropViewController *)cropViewController
didApplyChangesWithPolygon:(SBSDKPolygon *)polygon
              croppedImage:(UIImage *)croppedImage {
    SBSDKLog(@"Got warpImage result");
    NSString *outputImageFilePath = [ImageUtils generateTemporaryDocumentsFilePath:@"jpg"];
    NSURL *outputImageURL = [NSURL fileURLWithPath:outputImageFilePath];
    if ([ImageUtils saveImage:outputImageFilePath image:croppedImage quality:self.croppingOutputQuality]) {
        NSDictionary *callbackResult = @{@"imageFileUri":outputImageURL.absoluteString,
                                         @"polygon":[polygon polygonPoints]};
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsDictionary:callbackResult];
        [self.commandDelegate sendPluginResult:pluginResult
                                    callbackId:self.cropVCCommand.callbackId];
    } else {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                 messageAsString:@"Save image failed."]
                                    callbackId:self.cropVCCommand.callbackId];
    }
    [[self topMostController] dismissViewControllerAnimated:YES
                                                 completion:nil];
    
}

- (void)cropViewControllerDidCancelChanges:(SBSDKCropViewController *)cropViewController {
    [[self topMostController] dismissViewControllerAnimated:YES completion:nil];
}

- (UIImage *)cancelButtonImageForCropViewController:(SBSDKCropViewController *)cropViewController {
    return [UIImage imageNamed:@"ui_action_close"];
}

- (UIImage *)applyButtonImageForCropViewController:(SBSDKCropViewController *)cropViewController {
    return [UIImage imageNamed:@"ui_action_checkmark"];
}

@end
