import {Config} from '@remotion/cli/config';

Config.setVideoImageFormat('jpeg');
Config.setJpegQuality(92);
Config.setOverwriteOutput(true);
Config.setConcurrency(6);

// material-color-utilities is an ES module whose imports omit ".js": let webpack resolve them.
Config.overrideWebpackConfig((config) => ({
  ...config,
  module: {
    ...config.module,
    rules: [...(config.module?.rules ?? []), {test: /\.m?js$/, resolve: {fullySpecified: false}}],
  },
}));
