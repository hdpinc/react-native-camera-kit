
#if __has_include(<React/RCTBridge.h>)
#import <React/UIView+React.h>
#import <React/RCTConvert.h>
#else
#import "UIView+React.h"
#import "RCTConvert.h"
#endif

#import "ZXCamera.h"

@interface ZXCamera ()
@property (nonatomic, strong) ZXCapture *capture;
@property (nonatomic) BOOL scanning;
@property (nonatomic) UIView *scanRectView;
@property (nonatomic, strong) RCTDirectEventBlock onReadCode;
@property (nonatomic,) NSInteger heightForScannerFrame;
@property (nonatomic,) NSInteger scannerFrameOffset;
@property (nonatomic) BOOL shouldScan;
@end

@implementation ZXCamera

- (void)dealloc
{
    [self.capture.layer removeFromSuperlayer];
    [self.scanRectView removeFromSuperview];
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

- (void)removeFromSuperview
{
    [self.capture.layer removeFromSuperlayer];
    [self.scanRectView removeFromSuperview];
    self.capture.delegate = nil;
    [self.capture stop];
    [super removeFromSuperview];
}

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    
    self.capture = [[ZXCapture alloc] init];
    self.capture.sessionPreset = AVCaptureSessionPreset1920x1080;
    self.capture.camera = self.capture.back;
    self.capture.delegate = self;
    
    self.scanning = NO;
    self.shouldScan = YES;
    return self;
}

- (void)setShouldScan:(BOOL)shouldScan callback:(CallbackBlock)block {
    self.shouldScan = shouldScan;
    if(shouldScan) {
        self.scanning = YES;
        [self.capture start];
    }else{
        self.scanning = NO;
        [self.capture stop];
    }
    
    if (block) {
        block(YES);
    }
}

-(void)reactSetFrame:(CGRect)frame {
    [super reactSetFrame:frame];
    
#if TARGET_IPHONE_SIMULATOR
    return;
#endif
    
    self.frame = frame;
    
    // プレビュー描画
    self.capture.layer.frame = self.frame;
    [self.layer addSublayer:self.capture.layer];
    
    // ガイド描画
    CGFloat frameWidth = self.heightForScannerFrame;
    CGFloat frameHeight = frameWidth;
    self.scanRectView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, frameWidth, frameHeight)];
    self.scanRectView.center = self.center;
    self.scanRectView.layer.borderColor = [UIColor whiteColor].CGColor;
    self.scanRectView.layer.borderWidth = 3.0f;
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
    CGAffineTransform transform = CGAffineTransformMakeRotation((CGFloat) (captureRotation / 180 * M_PI));
    [self.capture setTransform:transform];
    [self.capture setRotation:scanRectRotation];
    [self applyRectOfInterest:orientation];
}

- (void)applyRectOfInterest:(UIInterfaceOrientation)orientation {
    CGFloat scaleVideoX, scaleVideoY ,scaleVideo;
    CGFloat videoSizeX, videoSizeY;
    
    CGRect transformedVideoRect = CGRectMake(0, 0, self.scanRectView.frame.size.width + self.scannerFrameOffset, self.scanRectView.frame.size.height + self.scannerFrameOffset);
    
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
    } else {
        scaleVideoX = self.capture.layer.frame.size.width / videoSizeY;
        scaleVideoY = self.capture.layer.frame.size.height / videoSizeX;
    }
    
    scaleVideo = scaleVideoX < scaleVideoY ?scaleVideoX : scaleVideoY;
    CGAffineTransform _captureSizeTransform = CGAffineTransformMakeScale(1.0/scaleVideo, 1.0/scaleVideo);
    CGSize realSize = CGSizeApplyAffineTransform(transformedVideoRect.size, _captureSizeTransform);
    // プレビューレイヤーはAVLayerVideoGravityResizeAspectFillで貼り付けられているので実際の映像の中央を読み取る
    CGPoint realPoint = CGPointMake((videoSizeY-realSize.width)*0.5, (videoSizeX-realSize.height)*0.5);
    self.capture.scanRect = CGRectMake(realPoint.x, realPoint.y, realSize.width, realSize.height);
    
    [self.capture start];
}

#pragma mark - ZXCaptureDelegate Methods

- (void)captureCameraIsReady:(ZXCapture *)capture {
    self.scanning = YES;
}

- (void)captureResult:(ZXCapture *)capture result:(ZXResult *)result{
    if (!self.scanning) return;
    if (!self.shouldScan) return;
    if(result.barcodeFormat != kBarcodeFormatQRCode) return;
    
    [self.capture stop];
    self.scanning = NO;
    
    NSMutableDictionary *metaData = result.resultMetadata;
    NSNumber *sequenceValue = [metaData objectForKey:@(kResultMetadataTypeStructuredAppendSequence)];
    if(sequenceValue == nil) sequenceValue = [NSNumber numberWithInt:0];
    NSNumber *parity = [metaData objectForKey:@(kResultMetadataTypeStructuredAppendParity)];
    if(parity == nil) parity = [NSNumber numberWithInt:0];
    
    NSString *bit = [self convertBinary:[sequenceValue intValue]];
    NSString *padding = [@"" stringByPaddingToLength:8 - (bit.length) withString:@"0" startingAtIndex:0];
    NSString *sequence =  [ padding stringByAppendingString:bit];
    // 読み取ったQRコードの番号 4bit + トータルのQRコード数 4bit
    int count = [self convertDecimal:[sequence substringToIndex:4]];
    int total = [self convertDecimal:[sequence substringWithRange:NSMakeRange(4,4)]];
    
    // ０からカウントされるのでRNには+1した状態でコールバックする
    NSDictionary *resultDictionary = @{@"codeStringValue":result.text,
                                       @"parity":parity,
                                       @"total":[NSNumber numberWithInt: total + 1],
                                       @"count":[NSNumber numberWithInt: count + 1]
    };
    if (self.onReadCode){
        self.onReadCode(resultDictionary);
    }
    
    self.scanning = YES;
    [self.capture start];
}

- (NSString *) convertBinary:(int) decimal {
    NSString *binary = @"";
    while (decimal > 0) {
        NSString * count = [NSString stringWithFormat:@"%d", decimal % 2];
        binary =  [ count stringByAppendingString:binary];
        decimal = decimal / 2;
    }
    return binary;
}

- (int) convertDecimal:(NSString *) binary {
    int decimal = 0;
    for (int i=0; i<binary.length; i++) {
        int tmp = [[binary substringWithRange:NSMakeRange(i,1)] intValue] * pow(2,binary.length - i - 1 );
        decimal = decimal + tmp;
    }
    return decimal;
}

@end


