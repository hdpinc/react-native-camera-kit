import React, { Component } from 'react';
import CameraKitCamera from './CameraKitCamera';
import { NativeModules} from 'react-native';

const NativeCameraModule = NativeModules.RNKitCameraModule;

export default class ZXCamera extends React.Component {


  static async stopScanning() {
    return await NativeCameraModule.setShouldScan(false);
  }

  static async startScanning() {
    return await NativeCameraModule.setShouldScan(true);
  }

  render() {
    return <CameraKitCamera {...this.props}/>
  }

}
