//
//  SharedImageStorage.m
//  ScanbotCordovaDemo
//
//  Created by Andrew Petrus on 15.02.17.
//
//

#import "ImageUtils.h"
#import "LoggingUtils.h"

@implementation ImageUtils

+ (NSString *)tempDirectoryPath {
    NSString *docsPath = [[ImageUtils appDocumentsDirectory] path];
    return [NSString stringWithFormat:@"%@/%@", docsPath, sbsdkTmpFolder];
}

+ (void)recreateTempDirectoryIfNeeded {
    NSString *sbsdkTempPath = [ImageUtils tempDirectoryPath];
    if (![[NSFileManager defaultManager] fileExistsAtPath:sbsdkTempPath]) {
        [[NSFileManager defaultManager] createDirectoryAtPath:sbsdkTempPath
                                  withIntermediateDirectories:NO
                                                   attributes:nil
                                                        error:nil];
    }
}

+ (NSURL *)appDocumentsDirectory {
    return [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
}

+ (NSString *)generateTemporaryFileName:(NSString *)extension {
    NSUUID *UUID = [NSUUID UUID];
    NSString *stringUUID = [UUID UUIDString];
    return [NSString stringWithFormat:@"%@.%@", stringUUID, extension];
}

+ (NSString *)generateTemporaryDocumentsFilePath:(NSString *)extension {
    NSString *sbsdkTempPath = [ImageUtils tempDirectoryPath];
    return [NSString stringWithFormat:@"%@/%@", sbsdkTempPath, [self generateTemporaryFileName:extension]];
}

+ (UIImage *)loadImage:(NSString *)imageFilePath {
    if ([ImageUtils imageFileExists:imageFilePath]) {
        UIImage *image = [UIImage imageWithData:[NSData dataWithContentsOfFile:imageFilePath]];
        if (!image) {
            image = [UIImage imageWithData:[NSData dataWithContentsOfURL:[NSURL URLWithString:imageFilePath]]];
        }
        return image;
    } else {
        SBSDKLog(@"Image loading failed. File %@ does not exist.", imageFilePath);
        return nil;
    }
}

+ (BOOL)saveImage:(NSString *)imageFilePath image:(UIImage *)image quality:(CGFloat)quality {
    NSData *imageData = UIImageJPEGRepresentation(image, quality);
    return [imageData writeToFile:imageFilePath atomically:YES];
}

+ (NSError *)removeAllFilesFromTemporaryDocumentsDirectory {
    NSString *sbsdkTempPath = [ImageUtils tempDirectoryPath];
    NSFileManager *fileManager = [NSFileManager new];
    NSDirectoryEnumerator *enumerator = [fileManager enumeratorAtPath:sbsdkTempPath];
    NSError *err = nil;
    BOOL result;
    
    NSString *fileName;
    while (fileName = [enumerator nextObject]) {
        result = [fileManager removeItemAtPath:[sbsdkTempPath stringByAppendingPathComponent:fileName] error:&err];
        if (!result && err) {
            SBSDKLog(@"Cleanup encountered an error: %@", err);
            break;
        }
    }
    return err;
}

+ (BOOL)imageFileExists:(NSString *)imageFileUri {
    return [[NSFileManager defaultManager] fileExistsAtPath:[NSURL URLWithString:imageFileUri].path];
}

@end
