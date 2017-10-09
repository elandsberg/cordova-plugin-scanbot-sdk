//
//  LoggingUtils.h
//  ScanbotCordovaDemo
//
//  Created by Andrew Petrus on 20.02.17.
//
//

#ifndef LoggingUtils_h
#define LoggingUtils_h

#import <ScanbotSDK/SBSDKScanbotSDK.h>
#import "SharedConfiguration.h"

static void SBSDKLog(NSString *format, ...) {
    if ([SharedConfiguration defaultConfiguration].loggingEnabled) {
        va_list argumentList;
        va_start(argumentList, format);
        NSMutableString *message = [[NSMutableString alloc] initWithFormat:format arguments:argumentList];
        NSLogv(message, argumentList);
        va_end(argumentList);
    }
}

#endif /* LoggingUtils_h */
