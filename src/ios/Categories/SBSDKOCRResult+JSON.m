//
//  SBSDKOCRResult+JSON.m
//  ScanbotCordovaDemo
//
//  Created by Andrew Petrus on 02.03.17.
//
//

#import "SBSDKOCRResult+JSON.h"
#import "SBSDKPageAnalyzerResult+JSON.h"

@implementation SBSDKOCRResult (JSON)

- (NSArray *)linesDictionaries {
    NSMutableArray *linesRecords = [NSMutableArray array];
    for (SBSDKOCRResultBlock *linesBlock in self.lines) {
        [linesRecords addObject:[linesBlock dictionaryRepresentation]];
    }
    return linesRecords;
}

- (NSArray *)paragraphsDictionaries {
    NSMutableArray *paragraphsRecords = [NSMutableArray array];
    for (SBSDKOCRResultBlock *paragraphBlock in self.paragraphs) {
        [paragraphsRecords addObject:[paragraphBlock dictionaryRepresentation]];
    }
    return paragraphsRecords;
}

- (NSArray *)wordsDictionaries {
    NSMutableArray *wordsRecords = [NSMutableArray array];
    for (SBSDKOCRResultBlock *wordsBlock in self.words) {
        [wordsRecords addObject:[wordsBlock dictionaryRepresentation]];
    }
    return wordsRecords;
}

- (NSDictionary *)dictionaryRepresentation {
    NSMutableDictionary *result = [NSMutableDictionary dictionary];
    result[@"recognizedText"] = self.recognizedText;
    result[@"paragraphs"] = [self paragraphsDictionaries];
    result[@"lines"] = [self linesDictionaries];
    result[@"words"] = [self wordsDictionaries];
    if (self.pageAnalyzerResult) {
        result[@"pageAnalyzerResult"] = [self.pageAnalyzerResult dictionaryRepresentation];
    }
    return result;
}

@end


@implementation SBSDKOCRResultBlock (JSON)

- (NSDictionary *)dictionaryFromCGRect:(CGRect)rect {
    return @{@"x":@(rect.origin.x),
             @"y":@(rect.origin.y),
             @"width":@(rect.size.width),
             @"height":@(rect.size.height)};
}

- (NSDictionary *)dictionaryRepresentation {
    return @{@"text":self.text,
             @"confidence":@(self.confidenceValue),
             @"boundingBox":[self dictionaryFromCGRect:self.boundingBox]};
}

@end
