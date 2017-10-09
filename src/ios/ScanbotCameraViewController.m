#import "ScanbotCameraViewController.h"
#import "ImageUtils.h"
#import "HandyJSONParameters.h"
#import "LoggingUtils.h"
#import "UIColor+JSON.h"

@interface ScanbotCameraViewController ()

@property (nonatomic, strong) UIButton *closeButton;
@property (nonatomic, strong) UIButton *flashButton;
@property (nonatomic, strong) UIButton *autoSnapButton;

@property (nonatomic, strong) NSString *originalImageFileUri;

@end

@implementation ScanbotCameraViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.originalImageFileUri = @"";
    if (self.imageCompressionQuality <= 0 || self.imageCompressionQuality > 1.0) {
        self.imageCompressionQuality = 0.95;
    }
    
    if (!self.strokeColor) {
        self.strokeColor = [UIColor colorFromHexString:@"#ff80cbc4"];
    }
    
    // Create the SBSDKScannerViewController.
    // We want it to be embedded into self.
    // As we do not want automatic image storage we pass nil here for the image storage.
    self.scannerViewController = [[SBSDKScannerViewController alloc] initWithParentViewController:self imageStorage:nil];
    SBSDKLog(@"ScanbotCameraViewController: SBSDKScannerViewController created");
    SBSDKLog(@"ScanbotCameraViewController: image quality = %f", self.imageCompressionQuality);
    SBSDKLog(@"ScanbotCameraViewController: edgeColor = %@", self.strokeColor);
    SBSDKLog(@"ScanbotCameraViewController: autoSnappingEnabled = %@", self.autoSnappingEnabled ? @"true" : @"false");
    SBSDKLog(@"ScanbotCameraViewController: autoSnappingSensitivity = %f", self.autoSnappingSensitivity);
    
    self.scannerViewController.delegate = self;
    self.scannerViewController.imageScale = (CGFloat)(1.0f / (CGFloat)self.sampleSize);
    self.scannerViewController.autoCaptureSensitivity = self.autoSnappingSensitivity;
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self placeCloseButton];
    [self placeFlashButton];
    [self placeAutoSnapButton];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(updateFlashButtonStatus)
                                                 name:UIApplicationWillEnterForegroundNotification
                                               object:nil];
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    self.viewAppeared = NO;
    
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:UIApplicationWillEnterForegroundNotification
                                                  object:nil];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    self.viewAppeared = YES;
}

- (BOOL)shouldAutorotate {
    return NO;
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    return UIInterfaceOrientationMaskPortrait;
}

- (UIStatusBarStyle)preferredStatusBarStyle {
    return UIStatusBarStyleLightContent;
}

#pragma mark - Custom UI modifications

- (void)placeCloseButton {
    CGRect buttonFrame = CGRectMake(30, 40, 20, 20);
    if (!self.closeButton) {
        self.closeButton = [[UIButton alloc] initWithFrame:buttonFrame];
        [self.closeButton setImage:[UIImage imageNamed:@"ui_action_close"]
                          forState:UIControlStateNormal];
        [self.closeButton addTarget:self
                             action:@selector(closeButtonTapped:)
                   forControlEvents:UIControlEventTouchUpInside];
    } else {
        [self.closeButton setFrame:buttonFrame];
    }
    [self.view addSubview:self.closeButton];
    [self.view bringSubviewToFront:self.closeButton];
}

- (void)placeAutoSnapButton {
    CGSize screenSize = [UIScreen mainScreen].bounds.size;
    CGRect buttonFrame = CGRectMake(50, screenSize.height - 80, 40, 40);
    if (!self.autoSnapButton) {
        self.autoSnapButton = [[UIButton alloc] initWithFrame:buttonFrame];
        [self.autoSnapButton setImage:[UIImage imageNamed:@"ui_autosnap_on"]
                             forState:UIControlStateSelected];
        [self.autoSnapButton setImage:[UIImage imageNamed:@"ui_autosnap_off"]
                             forState:UIControlStateNormal];
        [self.autoSnapButton addTarget:self
                                action:@selector(autoSnapButtonTapped:)
                      forControlEvents:UIControlEventTouchUpInside];
    } else {
        [self.autoSnapButton setFrame:buttonFrame];
    }
    [self.view addSubview:self.autoSnapButton];
    [self.view bringSubviewToFront:self.autoSnapButton];
    
    [self.autoSnapButton setSelected:self.autoSnappingEnabled];
    if (self.autoSnappingEnabled) {
        self.scannerViewController.shutterMode = SBSDKShutterModeSmart;
    } else {
        self.scannerViewController.shutterMode = SBSDKShutterModeAlwaysManual;
    }
}

- (void)placeFlashButton {
    CGSize screenSize = [UIScreen mainScreen].bounds.size;
    CGRect buttonFrame = CGRectMake(screenSize.width - 80, screenSize.height - 80, 40, 40);
    if (!self.flashButton) {
        self.flashButton = [[UIButton alloc] initWithFrame:buttonFrame];
        [self.flashButton setImage:[UIImage imageNamed:@"ui_flash_on"]
                          forState:UIControlStateSelected];
        [self.flashButton setImage:[UIImage imageNamed:@"ui_flash_off"]
                          forState:UIControlStateNormal];
        [self.flashButton addTarget:self
                             action:@selector(flashButtonTapped:)
                   forControlEvents:UIControlEventTouchUpInside];
        [self.flashButton setSelected:self.scannerViewController.cameraSession.isTorchLightEnabled];
    } else {
        [self.flashButton setFrame:buttonFrame];
    }
    [self.view addSubview:self.flashButton];
    [self.view bringSubviewToFront:self.flashButton];
}

- (void)closeButtonTapped:(id)sender {
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)flashButtonTapped:(id)sender {
    self.scannerViewController.cameraSession.torchLightEnabled = !self.scannerViewController.cameraSession.isTorchLightEnabled;
    [self.flashButton setSelected:self.scannerViewController.cameraSession.isTorchLightEnabled];
}

- (void)updateFlashButtonStatus {
    if (self.flashButton) {
        [self.flashButton setSelected:self.scannerViewController.cameraSession.isTorchLightEnabled];
    }
}

- (void)reenableAutosnapButton {
    self.autoSnapButton.userInteractionEnabled = YES;
}

- (void)autoSnapButtonTapped:(id)sender {
    self.autoSnappingEnabled = !self.autoSnappingEnabled;
    if (self.autoSnapButton) {
        [self.autoSnapButton setSelected:self.autoSnappingEnabled];
    }
    
    if (self.autoSnappingEnabled) {
        self.scannerViewController.shutterMode = SBSDKShutterModeSmart;
    } else {
        self.scannerViewController.shutterMode = SBSDKShutterModeAlwaysManual;
    }
    
    // avoid autosnap button tapping while shutter button is still animating it's state
    self.autoSnapButton.userInteractionEnabled = NO;
    [self performSelector:@selector(reenableAutosnapButton)
               withObject:nil
               afterDelay:0.5f];
}

#pragma mark - SBSDKScannerViewControllerDelegate

- (void)scannerController:(SBSDKScannerViewController *)controller didFailCapturingImage:(NSError *)error {
    if (self.commandDelegate && self.command) {
        [self dismissViewControllerAnimated:YES completion:^{
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.localizedDescription];
            [self.commandDelegate sendPluginResult:pluginResult
                                        callbackId:self.command.callbackId];
        }];
    }
}

- (BOOL)scannerControllerShouldAnalyseVideoFrame:(SBSDKScannerViewController *)controller {
    // We want to only process video frames when self is visible on screen and front most view controller
    return self.viewAppeared && self.presentedViewController == nil && self.autoSnappingEnabled;
}

- (void)scannerController:(SBSDKScannerViewController *)controller
  didCaptureDocumentImage:(UIImage *)documentImage {
    // Here we get the perspective corrected and cropped document image after the shutter was (auto)released.
    if (self.commandDelegate && self.command) {
        NSString *imageUri = [ImageUtils generateTemporaryDocumentsFilePath:@"jpg"];
        NSURL *imageUrl = [NSURL fileURLWithPath:imageUri];
        NSData *imageData = UIImageJPEGRepresentation(documentImage, self.imageCompressionQuality);
        BOOL result = [imageData writeToFile:imageUri
                                  atomically:YES];
        CDVPluginResult *pluginResult = nil;
        if (result) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                         messageAsDictionary:@{@"imageFileUri":imageUrl.absoluteString,
                                                               @"originalImageFileUri":self.originalImageFileUri}];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                             messageAsString:@"Error writing image file."];
        }
        [self dismissViewControllerAnimated:YES completion:^{
            [self.commandDelegate sendPluginResult:pluginResult
                                        callbackId:self.command.callbackId];
        }];
    }
}

- (void)scannerController:(SBSDKScannerViewController *)controller
          didCaptureImage:(nonnull UIImage *)image {
    NSString *filePathString = [ImageUtils generateTemporaryDocumentsFilePath:@"jpg"];
    self.originalImageFileUri = [NSURL fileURLWithPath:filePathString].absoluteString;
    [UIImageJPEGRepresentation(image, self.imageCompressionQuality) writeToFile:filePathString
                                                                     atomically:YES];
}

- (void)scannerController:(SBSDKScannerViewController *)controller
         didDetectPolygon:(SBSDKPolygon *)polygon
               withStatus:(SBSDKDocumentDetectionStatus)status {
    // Everytime the document detector finishes detection it calls this delegate method.
}

- (NSString *)scannerController:(SBSDKScannerViewController *)controller
localizedTextForDetectionStatus:(SBSDKDocumentDetectionStatus)status {
    if (!self.autoSnappingEnabled) {
        return nil;
    }
    
    NSString *okStatus = controller.autoShutterEnabled ?
    @"Don't move. Capturing document..." :
    @"Press shutter button now!";
    
    NSDictionary *localizedStatuses = @{
                                        @"autosnapping_hint_do_not_move":okStatus,
                                        @"autosnapping_hint_move_closer":@"Move closer.",
                                        @"autosnapping_hint_bad_angles":@"Turn your device to\nhave a more rectangular outline.",
                                        @"autosnapping_hint_nothing_detected":@"Searching for document...",
                                        @"autosnapping_hint_too_noisy":@"Background too noisy!\nSearching for document...",
                                        @"autosnapping_hint_too_dark":@"Poor light!\nSearching for document..."
                                        };
    
    if (self.command) {
        HandyJSONParameters *params = [HandyJSONParameters JSONParametersWithCordovaCommand:self.command];
        if (params[@"textResBundle"]) {
            localizedStatuses = params[@"textResBundle"];
        }
    }
    
    switch (status) {
        case SBSDKDocumentDetectionStatusOK:
            return localizedStatuses[@"autosnapping_hint_do_not_move"];
        case SBSDKDocumentDetectionStatusOK_SmallSize:
            return localizedStatuses[@"autosnapping_hint_move_closer"];
        case SBSDKDocumentDetectionStatusOK_BadAngles:
            return localizedStatuses[@"autosnapping_hint_bad_angles"];
        case SBSDKDocumentDetectionStatusError_NothingDetected:
            return localizedStatuses[@"autosnapping_hint_nothing_detected"];
        case SBSDKDocumentDetectionStatusError_Noise:
            return localizedStatuses[@"autosnapping_hint_too_noisy"];
        case SBSDKDocumentDetectionStatusError_Brightness:
            return localizedStatuses[@"autosnapping_hint_too_dark"];
        default:
            return nil;
    }
}

- (UIColor *)scannerController:(SBSDKScannerViewController *)controller
polygonColorForDetectionStatus:(SBSDKDocumentDetectionStatus)status {
    return self.strokeColor;
}
@end
