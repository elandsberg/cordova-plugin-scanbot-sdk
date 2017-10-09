//
//  SBSDKOCRResult+JSON.h
//  ScanbotCordovaDemo
//
//  Created by Andrew Petrus on 02.03.17.
//
//

#import <ScanbotSDK/ScanbotSDK.h>

@interface SBSDKOCRResult (JSON)

- (NSDictionary *)dictionaryRepresentation;

@end

@interface SBSDKOCRResultBlock (JSON)

- (NSDictionary *)dictionaryRepresentation;

@end
