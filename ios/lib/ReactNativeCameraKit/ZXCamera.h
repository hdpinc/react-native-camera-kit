#import <UIKit/UIKit.h>

#if __has_include(<React/RCTBridge.h>)
#import <React/RCTConvert.h>
#else
#import "RCTConvert.h"
#endif

#import <ZXingObjC/ZXingObjC.h>

typedef void (^CallbackBlock)(BOOL success);

@interface ZXCamera : UIView <ZXCaptureDelegate>

- (void)setShouldScan:(BOOL)shouldScan callback:(CallbackBlock)block;

@end
