//
//  SharedConfiguration.m
//  ScanbotCordovaDemo
//
//  Created by Andrew Petrus on 20.02.17.
//
//

#import "SharedConfiguration.h"

@implementation SharedConfiguration

+ (instancetype)defaultConfiguration {
    static SharedConfiguration *config = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        config = [self new];
    });
    return config;
}

@end
