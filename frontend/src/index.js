import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

// Create root and render the app
const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<App />);

// Hot Module Replacement for development
if (module.hot) {
    module.hot.accept();
}
