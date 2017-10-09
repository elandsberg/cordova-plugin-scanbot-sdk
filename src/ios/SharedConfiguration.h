//
//  SharedConfiguration.h
//  ScanbotCordovaDemo
//
//  Created by Andrew Petrus on 20.02.17.
//
//

#import <Foundation/Foundation.h>

@interface SharedConfiguration : NSObject

@property (nonatomic) BOOL loggingEnabled;
@property (nonatomic) BOOL isSDKInitialized;

+ (instancetype)defaultConfiguration;

@end
