#import <ScanbotSDK/SBSDKScanbotSDK.h>

#ifdef _CORDOVA_TEST_TARGET
#import "CDVPlugin.h"
#import "CDVInvokedUrlCommand.h"
#else
#import <Cordova/CDVPlugin.h>
#endif

@interface ScanbotCameraViewController : UIViewController
@end

@interface ScanbotCameraViewController() <SBSDKScannerViewControllerDelegate>

@property (strong, nonatomic) UIColor *strokeColor;
@property (strong, nonatomic) SBSDKScannerViewController *scannerViewController;
@property (strong, nonatomic) CDVInvokedUrlCommand *command;
@property (weak, nonatomic) id<CDVCommandDelegate> commandDelegate;
@property (assign, nonatomic) BOOL viewAppeared;
@property (nonatomic) CGFloat imageCompressionQuality;
@property (nonatomic) BOOL autoSnappingEnabled;
@property (nonatomic) NSInteger sampleSize;
@property (nonatomic) CGFloat autoSnappingSensitivity;

@end
