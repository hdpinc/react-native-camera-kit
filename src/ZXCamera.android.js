import React, { Component } from 'react';
import CameraKitCamera from './CameraKitCamera';

export default class ZXCamera extends React.Component {

  render() {
    	return <CameraKitCamera {...this.props}/>
  }

}
