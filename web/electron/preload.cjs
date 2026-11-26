'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('monogatariDesktop', {
	request: (req) => ipcRenderer.invoke('http:request', req),
	setImageHeaders: (url, headers) => ipcRenderer.invoke('media:headers', url, headers),
	info: () => ipcRenderer.invoke('app:info'),
	version: 'desktop',
});
