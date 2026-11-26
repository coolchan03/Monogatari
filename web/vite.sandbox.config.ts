import { defineConfig } from 'vite';
import { resolve } from 'node:path';

// Builds the extension sandbox runtime into one self-contained IIFE string that the app
// injects into a sandboxed iframe (see src/core/sandbox-host.ts).
export default defineConfig({
	build: {
		outDir: 'src/generated',
		emptyOutDir: false,
		copyPublicDir: false,
		minify: true,
		target: 'es2020',
		lib: {
			entry: resolve(__dirname, 'src/sandbox/entry.ts'),
			name: 'MonogatariSandbox',
			formats: ['iife'],
			fileName: () => 'sandbox.iife.js',
		},
	},
	server: { fs: { allow: ['..'] } },
});
