
#if __has_include(<React/RCTBridge.h>)
#import <React/UIView+React.h>
#import <React/RCTConvert.h>
#else
#import "UIView+React.h"
#import "RCTConvert.h"
#endif

#import "ZXCamera.h"



@interface ZXCamera () <ZXCaptureDelegate>
@property (nonatomic, strong) ZXCapture *capture;
@property (nonatomic) BOOL scanning;
@property (nonatomic) UIView *scanRectView;

@end

@implementation ZXCamera {
    CGAffineTransform _captureSizeTransform;
}

- (void)dealloc
{
    [self.capture.layer removeFromSuperlayer];
    self.capture.delegate = nil;
    [self.capture stop];
}

- (void)removeReactSubview:(UIView *)subview
{
    [subview removeFromSuperview];
    [self.capture.layer removeFromSuperlayer];
    self.capture.delegate = nil;
    [self.capture stop];
    [super removeReactSubview:subview];
}

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    
    self.capture = [[ZXCapture alloc] init];
    self.capture.sessionPreset = AVCaptureSessionPresetHigh;
    self.capture.camera = self.capture.back;
    self.capture.delegate = self;
    [self.layer addSublayer:self.capture.layer];
    
    self.scanning = NO;
    return self;
}

-(void)reactSetFrame:(CGRect)frame {
    [super reactSetFrame:frame];
    
#if TARGET_IPHONE_SIMULATOR
    return;
#endif
    
    self.frame = frame;
    self.capture.layer.frame = frame;
    
    CGFloat frameWidth = self.frame.size.width - 2 * 30;
    CGFloat frameHeight = frameWidth;
    self.scanRectView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, frameWidth, frameHeight)];
    self.scanRectView.center = self.center;
    self.scanRectView.backgroundColor = [UIColor redColor];
    [self addSubview:self.scanRectView];
    
    [self applyOrientation];
}

#pragma mark - Private
- (void)applyOrientation {
    UIInterfaceOrientation orientation = [[UIApplication sharedApplication] statusBarOrientation];
    float scanRectRotation;
    float captureRotation;
    
    switch (orientation) {
        case UIInterfaceOrientationPortrait:
            captureRotation = 0;
            scanRectRotation = 90;
            break;
        case UIInterfaceOrientationLandscapeLeft:
            captureRotation = 90;
            scanRectRotation = 180;
            break;
        case UIInterfaceOrientationLandscapeRight:
            captureRotation = 270;
            scanRectRotation = 0;
            break;
        case UIInterfaceOrientationPortraitUpsideDown:
            captureRotation = 180;
            scanRectRotation = 270;
            break;
        default:
            captureRotation = 0;
            scanRectRotation = 90;
            break;
    }
    self.capture.layer.frame = self.frame;
    CGAffineTransform transform = CGAffineTransformMakeRotation((CGFloat) (captureRotation / 180 * M_PI));
    [self.capture setTransform:transform];
    [self.capture setRotation:scanRectRotation];
    
    [self applyRectOfInterest:orientation];
}

- (void)applyRectOfInterest:(UIInterfaceOrientation)orientation {
    CGFloat scaleVideoX, scaleVideoY;
    CGFloat videoSizeX, videoSizeY;
    CGRect transformedVideoRect = self.scanRectView.frame;
    if([self.capture.sessionPreset isEqualToString:AVCaptureSessionPreset1920x1080]) {
        videoSizeX = 1080;
        videoSizeY = 1920;
    } else {
        videoSizeX = 720;
        videoSizeY = 1280;
    }
    if(UIInterfaceOrientationIsPortrait(orientation)) {
        scaleVideoX = self.capture.layer.frame.size.width / videoSizeX;
        scaleVideoY = self.capture.layer.frame.size.height / videoSizeY;
        
        // Convert CGPoint under portrait mode to map with orientation of image
        // because the image will be cropped before rotate
        // reference: https://github.com/TheLevelUp/ZXingObjC/issues/222
        CGFloat realX = transformedVideoRect.origin.y;
        CGFloat realY = self.capture.layer.frame.size.width - transformedVideoRect.size.width - transformedVideoRect.origin.x;
        CGFloat realWidth = transformedVideoRect.size.height;
        CGFloat realHeight = transformedVideoRect.size.width;
        transformedVideoRect = CGRectMake(realX, realY, realWidth, realHeight);
        
    } else {
        scaleVideoX = self.capture.layer.frame.size.width / videoSizeY;
        scaleVideoY = self.capture.layer.frame.size.height / videoSizeX;
    }
    
    _captureSizeTransform = CGAffineTransformMakeScale(1.0/scaleVideoX, 1.0/scaleVideoY);
    self.capture.scanRect = CGRectApplyAffineTransform(transformedVideoRect, _captureSizeTransform);
}

#pragma mark - ZXCaptureDelegate Methods

- (void)captureCameraIsReady:(ZXCapture *)capture {
    self.scanning = YES;
}

- (void)captureResult:(ZXCapture *)capture result:(ZXResult *)result {
    if (!self.scanning) return;
    if (!result) return;
    
    // We got a result.
    [self.capture stop];
    self.scanning = NO;
    
    // Display found barcode location
    CGAffineTransform inverse = CGAffineTransformInvert(_captureSizeTransform);
    NSMutableArray *points = [[NSMutableArray alloc] init];
    NSString *location = @"";
    for (ZXResultPoint *resultPoint in result.resultPoints) {
        CGPoint cgPoint = CGPointMake(resultPoint.x, resultPoint.y);
        CGPoint transformedPoint = CGPointApplyAffineTransform(cgPoint, inverse);
        transformedPoint = [self convertPoint:transformedPoint toView:self];
        NSValue* windowPointValue = [NSValue valueWithCGPoint:transformedPoint];
        location = [NSString stringWithFormat:@"%@ (%f, %f)", location, transformedPoint.x, transformedPoint.y];
        [points addObject:windowPointValue];
    }
    
    // Vibrate
    AudioServicesPlaySystemSound(kSystemSoundID_Vibrate);
    
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
        self.scanning = YES;
        [self.capture start];
    });
}

@end


