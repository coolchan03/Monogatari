import { defineConfig } from 'vitest/config';
import preact from '@preact/preset-vite';
import { VitePWA } from 'vite-plugin-pwa';

// GitHub Pages serves the site below /<repo>/, Electron loads it from file://, so the base is configurable.
const base = process.env.VITE_BASE ?? './';

export default defineConfig({
	base,
	plugins: [
		preact(),
		VitePWA({
			registerType: 'autoUpdate',
			injectRegister: 'auto',
			includeAssets: ['favicon.png', 'icons/apple-touch-icon.png'],
			manifest: {
				name: 'Monogatari',
				short_name: 'Monogatari',
				description: 'Manga, novels and anime — a world of stories in one place',
				theme_color: '#1976D2',
				background_color: '#101418',
				display: 'standalone',
				orientation: 'any',
				start_url: './',
				scope: './',
				categories: ['entertainment', 'books'],
				icons: [
					{ src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
					{ src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
					{ src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
				],
			},
			workbox: {
				globPatterns: ['**/*.{js,css,html,png,svg,webmanifest}'],
				navigateFallback: 'index.html',
				maximumFileSizeToCacheInBytes: 6 * 1024 * 1024,
			},
		}),
	],
	server: { fs: { allow: ['..'] } },
	build: { target: 'es2022', chunkSizeWarningLimit: 1200 },
	test: { environment: 'jsdom', include: ['src/**/*.test.ts'] },
});
