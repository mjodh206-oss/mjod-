import React from 'react';
import {Composition, registerRoot} from 'remotion';
import {EpisodeV3, ShortV3} from './Root.jsx';

const Root=()=> <>
  <Composition id="MaryCelesteV3" component={EpisodeV3} durationInFrames={1} fps={24} width={1280} height={720} defaultProps={{sectionFrames:[1,1,1,1,1,1],cues:[]}} calculateMetadata={({props})=>({durationInFrames:props.sectionFrames.reduce((a,b)=>a+b,0)})}/>
  <Composition id="MaryCelesteShortV3" component={ShortV3} durationInFrames={1} fps={24} width={720} height={1280} defaultProps={{sectionFrames:[1,1,1],cues:[]}} calculateMetadata={({props})=>({durationInFrames:props.sectionFrames.reduce((a,b)=>a+b,0)})}/>
</>;
registerRoot(Root);
