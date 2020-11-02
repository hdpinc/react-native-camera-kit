#import <UIKit/UIKit.h>

#if __has_include(<React/RCTBridge.h>)
#import <React/RCTConvert.h>
#else
#import "RCTConvert.h"
#endif

#import <ZXingObjC/ZXingObjC.h>

@interface ZXCamera : UIView <ZXCaptureDelegate>

@end
