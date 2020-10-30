
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

@end
