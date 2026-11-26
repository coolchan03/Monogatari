import { render } from 'preact';
import { App } from './ui/App';
import { applyTheme } from './core/settings';
import './styles.css';

applyTheme();
render(<App />, document.getElementById('app')!);
