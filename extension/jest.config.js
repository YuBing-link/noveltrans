module.exports = {
  testEnvironment: 'jsdom',
  testMatch: ['**/__tests__/**/*.test.js'],
  coverageDirectory: 'coverage',
  collectCoverageFrom: [
    'src/**/*.js',
    '!src/lib/vendor/**',
    '!src/popup/**/*.js',
    '!**/__tests__/**',
  ],
  setupFiles: ['<rootDir>/__tests__/setup.js'],
}
