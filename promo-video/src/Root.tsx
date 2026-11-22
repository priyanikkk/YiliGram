import React from 'react';
import {AbsoluteFill, Audio, Composition, continueRender, delayRender, Sequence, staticFile} from 'remotion';
import {CONFIG, SceneKey, sceneFrames, SCENE_ORDER, sceneStart, TEASER, TOTAL_FRAMES} from './config';
import {VOICEOVER} from './content';
import {FONT, roles} from './theme';
import {Subtitles} from './components/extra';
import {Intro} from './scenes/Intro';
import {Expressive} from './scenes/Expressive';
import {Customization} from './scenes/Customization';
import {Plugins} from './scenes/Plugins';
import {Details} from './scenes/Details';
import {Disclaimer, Download, OpenSource, Push} from './scenes/Outro';
import {UPDATE_FPS, UPDATE_FRAMES, UpdateTrack} from './update/Update';

// Roboto Flex (variable: weight 100–1000, width 25–151) for the kinetic type.
const fontHandle = delayRender('Roboto Flex');
const face = new FontFace(FONT, `url(${staticFile('fonts/RobotoFlex.ttf')}) format('truetype')`, {weight: '100 1000', stretch: '25% 151%'});
face.load().then((f) => {
  document.fonts.add(f);
  continueRender(fontHandle);
}).catch(() => continueRender(fontHandle));

const SCENES: Record<SceneKey, React.FC> = {
  intro: Intro, expressive: Expressive, customization: Customization, plugins: Plugins, details: Details,
  push: Push, openSource: OpenSource, download: Download, disclaimer: Disclaimer,
};

/** Subtitle colors per scene (light or dark backgrounds). */
const SUB_ROLES: Record<SceneKey, ReturnType<typeof roles>> = {
  intro: roles(undefined, true), expressive: roles(), customization: roles(), plugins: roles(undefined, true),
  details: roles(), push: roles(), openSource: roles(undefined, true), download: roles(undefined, true), disclaimer: roles(undefined, true),
};

/** UI sound cues: [scene, second in scene, sound]. */
const SFX: [SceneKey, number, 'poof' | 'pop' | 'click' | 'tick' | 'ding'][] = [
  ['intro', 0.0, 'poof'], ['intro', 0.37, 'poof'], ['intro', 0.73, 'poof'], ['intro', 1.1, 'poof'], ['intro', 1.47, 'poof'], ['intro', 1.9, 'pop'],
  ['expressive', 2.2, 'poof'], ['expressive', 4.4, 'poof'], ['expressive', 6.6, 'pop'],
  ['customization', 1.5, 'tick'], ['customization', 3.4, 'tick'], ['customization', 5.4, 'tick'], ['customization', 8.5, 'click'], ['customization', 11.5, 'click'],
  ['plugins', 8.2, 'poof'], ['details', 2.6, 'poof'], ['details', 3.8, 'pop'],
  ['push', 1.0, 'ding'], ['push', 3.2, 'pop'], ['openSource', 0.4, 'poof'], ['download', 0.3, 'pop'], ['download', 0.8, 'click'],
];

const Track: React.FC<{withAudio?: boolean}> = ({withAudio = true}) => (
  <AbsoluteFill style={{background: '#000'}}>
    {SCENE_ORDER.map((k) => {
      const Scene = SCENES[k];
      return (
        <Sequence key={k} from={sceneStart(k)} durationInFrames={sceneFrames(k)} name={k}>
          <Scene />
          <Subtitles lines={VOICEOVER[k] ?? []} r={SUB_ROLES[k]} sceneSeconds={CONFIG.scenes[k]} />
        </Sequence>
      );
    })}
    {withAudio && CONFIG.hasVoiceover ? <Audio src={staticFile('voiceover.wav')} /> : null}
    {withAudio && CONFIG.hasMusic ? <Audio src={staticFile(CONFIG.musicFile)} volume={CONFIG.musicVolume} /> : null}
    {withAudio && CONFIG.hasSfx ? SFX.map(([k, s, name], i) => (
      <Sequence key={i} from={sceneStart(k) + Math.round(s * CONFIG.fps)} durationInFrames={CONFIG.fps}>
        <Audio src={staticFile(`sfx/${name}.wav`)} volume={0.5} />
      </Sequence>
    )) : null}
  </AbsoluteFill>
);

/** 12 s teaser: pieces of three scenes. */
const Teaser: React.FC = () => {
  let at = 0;
  return (
    <AbsoluteFill style={{background: '#000'}}>
      {TEASER.parts.map((p, i) => {
        const len = Math.round(p.seconds * CONFIG.fps);
        const Scene = SCENES[p.scene];
        const el = (
          <Sequence key={i} from={at} durationInFrames={len}>
            <Sequence from={-Math.round(p.from * CONFIG.fps)}>
              <Scene />
            </Sequence>
          </Sequence>
        );
        at += len;
        return el;
      })}
      {CONFIG.hasMusic ? (
        <Audio src={staticFile(CONFIG.musicFile)} startFrom={Math.round(7 * CONFIG.fps)}
          volume={(f) => CONFIG.musicVolume * Math.min(1, f / 10, (Math.round(12 * CONFIG.fps) - f) / 30)} />
      ) : null}
    </AbsoluteFill>
  );
};

export const TEASER_FRAMES = TEASER.parts.reduce((n, p) => n + Math.round(p.seconds * CONFIG.fps), 0);

export const RemotionRoot: React.FC = () => (
  <>
    <Composition id="YiliGramVertical" component={Track} durationInFrames={TOTAL_FRAMES} fps={CONFIG.fps} width={1080} height={1920} />
    <Composition id="YiliGramHorizontal" component={Track} durationInFrames={TOTAL_FRAMES} fps={CONFIG.fps} width={1920} height={1080} />
    <Composition id="Update014Vertical" component={UpdateTrack} durationInFrames={UPDATE_FRAMES} fps={UPDATE_FPS} width={1080} height={1920} />
    <Composition id="Update014Horizontal" component={UpdateTrack} durationInFrames={UPDATE_FRAMES} fps={UPDATE_FPS} width={1920} height={1080} />
    <Composition id="YiliGramTeaser" component={Teaser} durationInFrames={TEASER_FRAMES} fps={CONFIG.fps} width={1080} height={1920} />
  </>
);
