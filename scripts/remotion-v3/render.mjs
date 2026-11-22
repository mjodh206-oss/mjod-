import path from 'node:path';
import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
process.env.AWS_EXECUTION_ENV ||= 'AWS_Lambda_nodejs22.x';
const {default:chromium}=await import('@sparticuz/chromium');
const {bundle}=await import('@remotion/bundler');
const {renderMedia,renderStill,selectComposition}=await import('@remotion/renderer');

const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'../..');
const timelinePath=path.join(here,'timeline-v3.json');
if(!fs.existsSync(timelinePath)) throw new Error('Missing timeline-v3.json; run scripts/build_mary_celeste_v3_audio.py first.');
const timeline=JSON.parse(fs.readFileSync(timelinePath,'utf8'));
const serveUrl=await bundle({entryPoint:path.join(here,'src/index.jsx'),publicDir:path.join(here,'public'),webpackOverride:(config)=>config});
const browserExecutable=await chromium.executablePath();
const isShort=process.argv.includes('--short');
const isStill=process.argv.includes('--still');
const id=isShort?'MaryCelesteShortV3':'MaryCelesteV3';
const inputProps=isShort?{sectionFrames:timeline.shortSectionFrames,cues:timeline.shortCues}:{sectionFrames:timeline.sectionFrames,cues:timeline.cues};
const composition=await selectComposition({serveUrl,id,inputProps,browserExecutable,chromiumOptions:{enableMultiProcessOnLinux:false}});
const out=isShort?path.join(root,'Mary-Celeste-short-v3-silent.mp4'):path.join(root,'Mary-Celeste-main-v3-silent.mp4');
if(isStill){
  const frame=Number(process.env.FRAME||180);
  await renderStill({composition,serveUrl,output:path.join(here,'preview.jpg'),frame,inputProps,browserExecutable,chromiumOptions:{enableMultiProcessOnLinux:false},imageFormat:'jpeg'});
  console.log(`Rendered preview frame ${frame}`);
}else{
  let logged=0;
  await renderMedia({composition,serveUrl,codec:'h264',outputLocation:out,inputProps,browserExecutable,chromiumOptions:{enableMultiProcessOnLinux:false},concurrency:isShort?1:2,crf:18,x264Preset:'medium',pixelFormat:'yuv420p',audioCodec:'aac',muted:true,onProgress:({progress,renderedFrames})=>{const percent=Math.floor(progress*100);if(percent>=logged+5){logged=percent;console.log(`${percent}% (${renderedFrames}/${composition.durationInFrames})`);}},metadata:{title:isShort?'ماري سيليست — الشورت V3':'ماري سيليست — النسخة V3',comment:'Editorial motion graphics, Remotion / React; illustrative imagery, not archival footage.'}});
  console.log(`Rendered ${out} (${composition.durationInFrames} frames)`);
}
