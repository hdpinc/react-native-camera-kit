
import React from 'react';
import { requireNativeComponent } from 'react-native';

const NativeCamera = requireNativeComponent('ZXCamera', null);


export default class ZXCamera extends React.Component {
  render() {
    return (
      <NativeCamera />
    );
  }
}
