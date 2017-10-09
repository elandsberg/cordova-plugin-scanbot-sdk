#import "ScanbotSdkPlugin.h"
#import "ScanbotCameraViewController.h"

#import <Foundation/Foundation.h>

#ifdef _CORDOVA_TEST_TARGET
#import "CDVPlugin.h"
#import "CDVInvokedUrlCommand.h"
#else
#import <Cordova/CDVPlugin.h>
#endif

#import <ScanbotSDK/SBSDKScanbotSDK.h>
#import "HandyJSONParameters.h"
#import "ImageUtils.h"
#import "LoggingUtils.h"
#import "SharedConfiguration.h"

#import "SBSDKPolygon+JSON.h"
#import "SBSDKOCRResult+JSON.h"

@implementation ScanbotSdkPlugin

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

- (SBSDKImageFilterType)filterIdFromFilterNameString:(NSString *)filterName {
    if ([filterName isEqualToString:@"COLOR_ENHANCED"]) return SBSDKImageFilterTypeColor;
    if ([filterName isEqualToString:@"GRAYSCALE"]) return SBSDKImageFilterTypeGray;
    if ([filterName isEqualToString:@"BINARIZED"]) return SBSDKImageFilterTypeBinarized;
    return SBSDKImageFilterTypeNone;
}

- (void)initializeSdk:(CDVInvokedUrlCommand *)command {
    BOOL hasLicense = NO;
    HandyJSONParameters *params = [HandyJSONParameters JSONParametersWithCordovaCommand:command];
    if (params[@"licenseKey"] && ![params[@"licenseKey"] isEqualToString:@""]) {
        [ScanbotSDK setLicense: params[@"licenseKey"]];
        hasLicense = YES;
    }
    if (params[@"loggingEnabled"]) {
        BOOL loggingEnabled = [params[@"loggingEnabled"] boolValue];
        [SharedConfiguration defaultConfiguration].loggingEnabled = loggingEnabled;
        [ScanbotSDK setLoggingEnabled:loggingEnabled];
    }
    [ScanbotSDK setSharedApplication:[UIApplication sharedApplication]];
    [ImageUtils recreateTempDirectoryIfNeeded];
    
    NSString *callbackMessage = @"Scanbot SDK initialized.";
    if (!hasLicense) {
        callbackMessage = @"Trial mode activated. You can now test all features for 60 seconds.";
    }
    [SharedConfiguration defaultConfiguration].isSDKInitialized = YES;
    
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:callbackMessage];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)documentDetection:(CDVInvokedUrlCommand *)command {
    if (![self checkSDKInitializationWithCommand:command]) {
        return;
    }
    
    HandyJSONParameters *params = [HandyJSONParameters JSONParametersWithCordovaCommand:command];
    NSString *imageFilePath = params[@"imageFileUri"];

    SBSDKLog(@"Loading image from path: %@", imageFilePath);
    UIImage *image = [ImageUtils loadImage:imageFilePath];
    if (!image) {
        NSString *loadingErrorString = @"Document detection failed. Input image file does not exist.";
        CDVPluginResult *pluginResult = [self pluginResultError:loadingErrorString];
#ifdef _CORDOVA_TEST_TARGET
        command.testCaseCompletionBlock(pluginResult);
#else
        [self.commandDelegate sendPluginResult:pluginResult
                                    callbackId:command.callbackId];
#endif
        SBSDKLog(loadingErrorString);
        return;
    }
    
    SBSDKLog(@"Image loaded: %@", image);
    
    SBSDKLog(@"Creating SBSDKDocumentDetector instance ...");
    SBSDKDocumentDetector *detector = [SBSDKDocumentDetector new];
    SBSDKDocumentDetectorResult *result = [detector detectDocumentPolygonOnImage:image
                                                                visibleImageRect:CGRectZero
                                                                smoothingEnabled:YES
                                                      useLiveDetectionParameters:NO];

    NSString *outputImageFilePath = [ImageUtils generateTemporaryDocumentsFilePath:@"jpg"];
    NSURL *outputImageURL = [NSURL fileURLWithPath:outputImageFilePath];

    CGFloat compressionQuality = params.qualityValue;
    if (result.polygon != nil && (result.status == SBSDKDocumentDetectionStatusOK ||
                                  result.status == SBSDKDocumentDetectionStatusOK_SmallSize ||
                                  result.status == SBSDKDocumentDetectionStatusOK_BadAngles ||
                                  result.status == SBSDKDocumentDetectionStatusOK_BadAspectRatio)) {
        [SBSDKImageProcessor warpImage:image
                               polygon:result.polygon
                            completion:^(BOOL finished, NSError * _Nullable error, NSDictionary<NSString *,NSObject *> * _Nullable resultInfo) {
                                CDVPluginResult *pluginResult = nil;
                                if (finished && !error) {
                                    SBSDKLog(@"Got warpImage result");
                                    NSData *imageData = UIImageJPEGRepresentation((UIImage *)resultInfo[SBSDKResultInfoDestinationImageKey], compressionQuality);
                                    [imageData writeToFile:outputImageFilePath
                                                atomically:YES];
                                    NSDictionary *callbackResult = @{@"imageFileUri":outputImageURL.absoluteString,
                                                                     @"detectionResult":[result.polygon detectionStatusString],
                                                                     @"polygon":[result.polygon polygonPoints]};
                                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                 messageAsDictionary:callbackResult];
                                } else {
                                    pluginResult = [self pluginResultError:error.localizedDescription];
                                }
#ifdef _CORDOVA_TEST_TARGET
                                command.testCaseCompletionBlock(pluginResult);
#else
                                [self.commandDelegate sendPluginResult:pluginResult
                                                            callbackId:command.callbackId];
#endif
                            }];
    } else {
        NSString *detectionStatusString = [[SBSDKPolygon new] detectionStatusStringFromSBSDKStatus:SBSDKDocumentDetectionStatusError_NothingDetected];
        SBSDKLog(@"No acceptable polygon found. Result.status = %d", [result status]);
        NSDictionary *callbackResult = @{@"imageFileUri":[NSNull null],
                                         @"detectionResult":detectionStatusString,
                                         @"polygon":[NSArray array]};
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsDictionary:callbackResult];
#ifdef _CORDOVA_TEST_TARGET
        command.testCaseCompletionBlock(pluginResult);
#else
        [self.commandDelegate sendPluginResult:pluginResult
                                    callbackId:command.callbackId];
#endif
    }
}

- (void)applyImageFilter:(CDVInvokedUrlCommand *)command {
    if (![self checkSDKInitializationWithCommand:command]) {
        return;
    }
    
    HandyJSONParameters *params = [HandyJSONParameters JSONParametersWithCordovaCommand:command];
    NSString *imageFilePath = params[@"imageFileUri"];
    NSString *outputImageFilePath = [ImageUtils generateTemporaryDocumentsFilePath:@"jpg"];
    NSURL *inputImageFileURL = [NSURL URLWithString:imageFilePath];
    __block NSURL *outputImageFIleURL = [NSURL fileURLWithPath:outputImageFilePath];
    
    CGFloat compressionQuality = params.qualityValue;
    [SBSDKImageProcessor filterImage:[UIImage imageWithData:[NSData dataWithContentsOfURL:inputImageFileURL]]
                              filter:[self filterIdFromFilterNameString:params[@"imageFilter"]]
                          completion:^(BOOL finished, NSError * _Nullable error, NSDictionary<NSString *,NSObject *> * _Nullable resultInfo) {
                              if (finished && !error) {
                                  NSData *imageData = UIImageJPEGRepresentation((UIImage *)resultInfo[SBSDKResultInfoDestinationImageKey], compressionQuality);
                                  [imageData writeToFile:outputImageFilePath
                                              atomically:YES];
                                  SBSDKLog(@"Got filterImage result");
                                  CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                                messageAsDictionary:@{@"imageFileUri":outputImageFIleURL.absoluteString}];
#ifdef _CORDOVA_TEST_TARGET
                                  command.testCaseCompletionBlock(pluginResult);
#else
                                  [self.commandDelegate sendPluginResult:pluginResult
                                                              callbackId:command.callbackId];
#endif
                              } else {
                                  CDVPluginResult *pluginResult = [self pluginResultError:error.localizedDescription];
#ifdef _CORDOVA_TEST_TARGET
                                  command.testCaseCompletionBlock(pluginResult);
#else
                                  [self.commandDelegate sendPluginResult:pluginResult
                                                              callbackId:command.callbackId];
#endif
                              }
                          }];
}

- (void)createPdf:(CDVInvokedUrlCommand *)command {
    if (![self checkSDKInitializationWithCommand:command]) {
        return;
    }
    
    HandyJSONParameters *params = [HandyJSONParameters JSONParametersWithCordovaCommand:command];
    if (![params validateArrayParameter:@"images" withErrorMessage:@"At least one image must be present."]) {
        return;
    }
    
    NSArray<NSString *> *imageFilePaths = params[@"images"];
    
    NSIndexSet *indexSet = [NSIndexSet indexSetWithIndexesInRange:NSMakeRange(0, imageFilePaths.count)];
    SBSDKPDFRendererPageSize sdkPageSize = SBSDKPDFRendererPageSizeAuto;

    NSString *outputPdfFilePath = [ImageUtils generateTemporaryDocumentsFilePath:@"pdf"];
    SBSDKLog(@"PDF output file path: %@", outputPdfFilePath);
    NSURL *pdfOutputURL = [NSURL fileURLWithPath:outputPdfFilePath];

    [self.commandDelegate runInBackground:^{
        [SBSDKPDFRenderer renderImageStorage:[self imageStorageFromFilesList:imageFilePaths] // we render temp dedicated storage
                            copyImageStorage:NO
                                    indexSet:indexSet
                                withPageSize:sdkPageSize
                                      output:pdfOutputURL
                           completionHandler:^(BOOL finished, NSError *error, NSDictionary *resultInfo) {
                               dispatch_async(dispatch_get_main_queue(), ^{
                                   SBSDKLog(@"Got PDF renderer result");
                                   CDVPluginResult *pluginResult = nil;
                                   if (finished && !error) {
                                       pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                    messageAsDictionary:@{@"pdfFileUri":pdfOutputURL.absoluteString}];
                                   } else {
                                       pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                        messageAsString:error.localizedDescription];
                                   }
#ifdef _CORDOVA_TEST_TARGET
                                   command.testCaseCompletionBlock(pluginResult);
#else
                                   [self.commandDelegate sendPluginResult:pluginResult
                                                               callbackId:command.callbackId];
#endif
                               });
                           }];
    }];
}

- (void)cleanup:(CDVInvokedUrlCommand *)command {
    if (![self checkSDKInitializationWithCommand:command]) {
        return;
    }
    
    NSError *cleanupError = [ImageUtils removeAllFilesFromTemporaryDocumentsDirectory];
    CDVPluginResult *pluginResult = nil;
    if (!cleanupError) {
        pluginResult = [self pluginResultOk:@"Cleanup done."];
    } else {
        pluginResult = [self pluginResultError:cleanupError.localizedDescription];
    }
#ifdef _CORDOVA_TEST_TARGET
    command.testCaseCompletionBlock(pluginResult);
#else
    [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];
#endif
}

- (NSArray<NSString *> *)checkMissingLanguages:(NSArray<NSString *> *)languages {
    NSArray *installedLanguages = [SBSDKOpticalTextRecognizer installedLanguages];
    NSMutableArray<NSString *> *missingLanguges = [NSMutableArray array];
    for (NSString *languageCode in languages) {
        if (![installedLanguages containsObject:languageCode]) {
            [missingLanguges addObject:languageCode];
        }
    }
    return missingLanguges;
}

- (NSString *)missingLanguagesStringFromArray:(NSArray<NSString *> *)missingLanguagesArray {
    NSString *arrayRepresentation = @"";
    for (NSInteger index = 0; index < missingLanguagesArray.count; index++) {
        NSString *languageCode = missingLanguagesArray[index];
        arrayRepresentation = [arrayRepresentation stringByAppendingFormat:@"\"%@\"", languageCode];
        if (index < missingLanguagesArray.count - 1) {
            arrayRepresentation = [arrayRepresentation stringByAppendingString:@", "];
        }
    }
    return [NSString stringWithFormat:@"Missing OCR language files for languages: [%@]", arrayRepresentation];
}

- (void)performOcr:(CDVInvokedUrlCommand *)command {
    if (![self checkSDKInitializationWithCommand:command]) {
        return;
    }
    
    HandyJSONParameters *params = [HandyJSONParameters JSONParametersWithCordovaCommand:command];
    if (![params validateArrayParameter:@"images" withErrorMessage:@"At least one image must be present."] ||
        ![params validateArrayParameter:@"languages" withErrorMessage:@"At least one language must be specified."] ||
        ![params validateParameter:@"outputFormat"]) {
        return;
    }
    
    NSArray *languages = params[@"languages"];
    NSArray *missingLanguages = [self checkMissingLanguages:languages];
    if (missingLanguages.count > 0) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                          messageAsString:[self missingLanguagesStringFromArray:missingLanguages]];
#ifdef _CORDOVA_TEST_TARGET
        command.testCaseCompletionBlock(pluginResult);
#else
        [self.commandDelegate sendPluginResult:pluginResult
                                    callbackId:command.callbackId];
#endif
        return;
    }
    
    NSArray<NSString *> *imageFilePaths = params[@"images"];
    NSString *outputFormat = params[@"outputFormat"];
    NSURL *outputPDFURL = nil;
    
    if ([outputFormat isEqualToString:@"PDF_FILE"] || [outputFormat isEqualToString:@"FULL_OCR_RESULT"]) {
        outputPDFURL = [NSURL fileURLWithPath:[ImageUtils generateTemporaryDocumentsFilePath:@"pdf"]];
    }
    
    NSString *langString = languages.firstObject;
    for (NSInteger index = 1; index < languages.count; ++index) {
        langString = [NSString stringWithFormat:@"%@+%@", langString, languages[index]];
    }
    
    [self.commandDelegate runInBackground:^{
        [SBSDKOpticalTextRecognizer recognizeText:[self imageStorageFromFilesList:imageFilePaths]
                                 copyImageStorage:YES
                                         indexSet:nil
                                   languageString:langString
                                     pdfOutputURL:outputPDFURL
                                       completion:^(BOOL finished, NSError *error, NSDictionary *resultInfo) {
                                           SBSDKLog(@"Got OCR result");
                                           CDVPluginResult *pluginResult = nil;
                                           if (finished && !error) {
                                               SBSDKOCRResult *result = resultInfo[SBSDKResultInfoOCRResultsKey];
                                               NSMutableDictionary *resultDict = [NSMutableDictionary dictionary];
                                               if ([outputFormat isEqualToString:@"PLAIN_TEXT"]) {
                                                   resultDict[@"plainText"] = result.recognizedText;
                                               } else if ([outputFormat isEqualToString:@"PDF_FILE"]) {
                                                   resultDict[@"pdfFileUri"] = outputPDFURL.absoluteString;
                                               } else if ([outputFormat isEqualToString:@"FULL_OCR_RESULT"]) {
                                                   resultDict[@"plainText"] = result.recognizedText;
                                                   resultDict[@"pdfFileUri"] = outputPDFURL.absoluteString;
                                               }
                                               pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                            messageAsDictionary:resultDict];
                                           } else {
                                               pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                                                messageAsString:error.localizedDescription];
                                           }
#ifdef _CORDOVA_TEST_TARGET
                                           command.testCaseCompletionBlock(pluginResult);
#else
                                           [self.commandDelegate sendPluginResult:pluginResult
                                                                       callbackId:command.callbackId];
#endif
                                       }];
    }];
}

- (void)getOcrConfigs:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        NSMutableDictionary *resultDict = [NSMutableDictionary dictionary];
        
        NSURL *languageDataPathURL = [NSURL fileURLWithPath: [SBSDKOpticalTextRecognizer languageDataPath]];
        resultDict[@"languageDataPath"] = [languageDataPathURL absoluteString];
        resultDict[@"installedLanguages"] = [SBSDKOpticalTextRecognizer installedLanguages];
        
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                      messageAsDictionary:resultDict];
        [self.commandDelegate sendPluginResult:pluginResult
                                    callbackId:command.callbackId];
    }];
}

- (SBSDKImageStorage *)imageStorageFromFilesList:(NSArray <NSString *> *)imageFilePaths {
    SBSDKImageStorage *storage = [SBSDKImageStorage new];
    for (NSString *imageFilePath in imageFilePaths) {
        UIImage *image = [ImageUtils loadImage:imageFilePath];
        if (image) {
            [storage addImage:image];
        }
    }
    return storage;
}

- (CDVPluginResult*)pluginResultOk:(NSString*)resultData {
    return [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                             messageAsString:resultData];
}

- (CDVPluginResult*)pluginResultError:(NSString*)errorMessage {
    return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                             messageAsString:errorMessage];
}

@end
