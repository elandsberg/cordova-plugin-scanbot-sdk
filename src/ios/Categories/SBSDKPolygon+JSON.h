//
//  SBSDKPolygon+JSON.h
//  ScanbotCordovaDemo
//
//  Created by Andrew Petrus on 20.02.17.
//
//

#import <ScanbotSDK/ScanbotSDK.h>

@interface SBSDKPolygon (JSON)

- (NSArray *)polygonPoints;
- (NSString *)detectionStatusString;
- (NSString *)detectionStatusStringFromSBSDKStatus:(SBSDKDocumentDetectionStatus)status;

@end
