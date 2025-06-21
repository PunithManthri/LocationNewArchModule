module.exports = {
  project: {
    ios: {},
    android: {},
  },
  dependencies: {},
  codegenConfig: {
    name: 'LocationModule',
    jsSrcsDir: './src/NewArchitecture',
    libraryType: 'module',
    android: {
      enabled: true,
    },
    ios: {
      enabled: true,
    },
  },
}; 