import React from 'react';
import {
  requireNativeComponent,
  NativeModules,
} from 'react-native';

const NativeCamera = requireNativeComponent('ZXCamera', null);
const NativeCameraAction = NativeModules.ZXCameraManager;

export default class ZXCamera extends React.Component {

	static async stopScanning(saveToCameraRoll = true) {
    return await NativeCameraAction.setShouldScan(false);
  }

  static async startScanning() {
    return await NativeCameraAction.setShouldScan(true);
  }

  render() {
    return (
      <NativeCamera {...this.props} />
    );
  }
}
