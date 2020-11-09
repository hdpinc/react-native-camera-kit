
#import "ZXCameraManager.h"
#import "ZXCamera.h"


@interface ZXCameraManager ()

@property (nonatomic, strong) ZXCamera *camera;

@end

@implementation ZXCameraManager

RCT_EXPORT_MODULE()

- (UIView *)view {
    self.camera = [ZXCamera new];
    return self.camera;
}

RCT_EXPORT_VIEW_PROPERTY(onReadCode, RCTDirectEventBlock)

RCT_EXPORT_METHOD(setShouldScan:(BOOL)shouldScan
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    
    [self.camera setShouldScan:shouldScan callback:^(BOOL success) {
        if (resolve) {
            resolve([NSNumber numberWithBool:success]);
        }
    }];
}


@end
