import React from 'react';

// Generic Error Boundary to isolate runtime errors in lazily loaded pages like JarDetails
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    // eslint-disable-next-line no-console
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="bg-white border border-red-200 rounded-lg p-6 text-center">
          <i data-lucide="alert-triangle" className="w-8 h-8 text-red-400 mx-auto mb-3" />
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Failed to render component</h3>
          <p className="text-xs text-gray-600 mb-4 break-all">{this.state.error && (this.state.error.message || String(this.state.error))}</p>
          <button onClick={this.handleRetry} className="px-3 py-1.5 text-xs rounded bg-blue-600 text-white hover:bg-blue-700">Retry</button>
        </div>
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
