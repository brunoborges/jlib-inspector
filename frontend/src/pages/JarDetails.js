import React, { useEffect, useState, useCallback, useRef } from 'react';
import { initLucideIcons, copyToClipboard, formatFileSize, formatRelativeTime, getMvnRepositoryUrl } from '../utils/helpers';

// New simplified Jar Details page: receives only a jarId and loads data itself.
// Props: jarId, origin, onBack, onOpenApp
const JarDetails = ({ jarId, origin = 'app', onBack, onOpenApp }) => {
  const [jar, setJar] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const abortRef = useRef(null);

  const fetchJar = useCallback(async (id) => {
    if (!id) return;
    if (abortRef.current) abortRef.current.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`/api/jars/${id}`, { signal: controller.signal });
      if (!res.ok) {
        if (res.status === 404) throw new Error('JAR not found');
        throw new Error(`Failed to load JAR (HTTP ${res.status})`);
      }
      const data = await res.json();
      setJar(data);
    } catch (e) {
      if (e.name !== 'AbortError') setError(e.message || 'Unknown error');
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => { fetchJar(jarId); }, [jarId, fetchJar]);
  useEffect(() => { initLucideIcons(); }, [jar]);
  useEffect(() => () => { if (abortRef.current) abortRef.current.abort(); }, []);

  const mvnUrl = jar ? getMvnRepositoryUrl(jar.fileName) : null;
  const backLabel = origin === 'unique' ? 'Back to JARs' : 'Back to Application';

  if (!jarId) {
    return (
      <div className="max-w-4xl mx-auto py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2" />
          {backLabel}
        </button>
        <div className="bg-white p-6 rounded shadow">No JAR selected.</div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2" />
          {backLabel}
        </button>
        <div className="bg-white rounded shadow p-8 text-center">
          <i data-lucide="loader" className="w-8 h-8 text-gray-400 animate-spin mx-auto mb-4" />
          <p className="text-sm text-gray-600">Loading JAR details...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-4xl mx-auto py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2" />
          {backLabel}
        </button>
        <div className="bg-white rounded shadow p-8 text-center">
          <i data-lucide="alert-triangle" className="w-8 h-8 text-red-400 mx-auto mb-4" />
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Failed to load JAR</h3>
          <p className="text-sm text-gray-600 mb-4 break-all">{error}</p>
          <button onClick={() => fetchJar(jarId)} className="px-3 py-2 text-sm rounded bg-blue-600 text-white hover:bg-blue-700">Retry</button>
        </div>
      </div>
    );
  }

  if (!jar) {
    return (
      <div className="max-w-4xl mx-auto py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2" />
          {backLabel}
        </button>
        <div className="bg-white p-6 rounded shadow">JAR data unavailable.</div>
      </div>
    );
  }

  const { fileName, path: jarPath = '', checksum, size, manifest = {}, applications = [], jarId: realJarId, firstSeen, lastAccessed } = jar;

  // Derive aggregate loaded status (any application reporting loaded=true)
  const anyLoaded = applications.some(a => a.loaded);

  const timeBlock = (label, ts) => (
    <div>
      <span className="font-semibold text-gray-600">{label}:</span>{' '}
      {ts ? (
        <span className="ml-1" title={ts}>{formatRelativeTime(ts)}</span>
      ) : (
        <span className="ml-1 text-gray-500">N/A</span>
      )}
    </div>
  );

  // Determine icon based on whether this is a JAR (gracefully handle missing filename)
  const isJar = (fileName || '').toLowerCase().endsWith('.jar');
  const iconName = isJar ? 'package' : 'layers';
  // Match roughly the text-2xl height (~24px). w-6 h-6 = 24px, providing consistent alignment.
  const iconClass = `${isJar ? 'text-green-500' : 'text-blue-500'} w-6 h-6 mr-2 flex-shrink-0`;

  return (
    <div className="max-w-5xl mx-auto py-6">
      <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
        <i data-lucide="arrow-left" className="w-4 h-4 mr-2" />
        {backLabel}
      </button>
      <div className="bg-white rounded-xl shadow p-6">
        <div className="flex items-start justify-between mb-4">
          <div className="pr-4">
            <h2 className="flex items-center text-2xl font-bold text-gray-900 break-all">
              <i data-lucide={iconName} className={iconClass} />
              <span>{fileName || realJarId || 'Unknown JAR'}</span>
            </h2>
            {jarPath && <p className="text-xs text-gray-500 break-all mt-1 font-mono" title="Reported path from server">{jarPath}</p>}
            <div className="mt-2 text-xs space-y-1">
              <div className="flex items-center">
                <span className="font-semibold text-gray-600 mr-1">JAR ID:</span>
                <code className="bg-gray-50 border px-2 py-1 rounded text-[10px] break-all font-mono flex-1">{realJarId || jarId}</code>
                <button onClick={() => copyToClipboard(realJarId || jarId)} className="ml-2 text-gray-400 hover:text-blue-600" title="Copy JAR ID"><i data-lucide="copy" className="w-3 h-3" /></button>
              </div>
              {size != null && (
                <div><span className="font-semibold text-gray-600">Size:</span> <span className="ml-1" title={size + ' bytes'}>{formatFileSize(size)}</span></div>
              )}
              {timeBlock('First Seen', firstSeen)}
              {timeBlock('Last Accessed', lastAccessed)}
              <div><span className="font-semibold text-gray-600">Applications:</span> <span className="ml-1">{applications.length}</span></div>
            </div>
          </div>
          <div className="text-right text-sm">
            <div className={`inline-flex items-center px-2 py-1 rounded text-xs font-medium ${anyLoaded ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-gray-50 text-gray-500 border border-gray-200'}`}>{anyLoaded ? 'Loaded in runtime' : 'Not Loaded'}</div>
            {mvnUrl && (
              <div className="mt-3">
                <a href={mvnUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center text-[10px] text-blue-600 hover:underline">
                  <i data-lucide="external-link" className="w-3 h-3 mr-1" />Maven Central Search
                </a>
              </div>
            )}
          </div>
        </div>

        {checksum && checksum !== '?' && (
          <div className="mb-4">
            <span className="text-xs font-semibold text-gray-600">SHA-256:</span>
            <div className="flex items-center mt-1">
              <code className="text-xs break-all bg-gray-50 p-2 rounded border flex-1">{checksum}</code>
              <button onClick={() => copyToClipboard(checksum)} className="ml-2 text-gray-400 hover:text-blue-600" title="Copy checksum">
                <i data-lucide="copy" className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}

        {/* Applications and Manifest sections stacked on single column */}
        <div className="grid md:grid-cols-1 gap-6">
          {/* Applications Section */}
          <div>
            <h3 className="text-sm font-semibold text-gray-800 mb-2 flex items-center"><i data-lucide="info" className="w-4 h-4 mr-1" /> Applications</h3>
            {applications.length === 0 && (
              <p className="text-xs text-gray-500">This JAR is not associated with any tracked application.</p>
            )}
            {applications.length > 0 && (
              <div className="overflow-auto border rounded-lg">
                <table className="min-w-full text-[11px]">
                  <thead className="bg-gray-50 text-gray-600">
                    <tr>
                      <th className="text-left font-medium px-2 py-1">App</th>
                      <th className="text-left font-medium px-2 py-1">Loaded</th>
                      <th className="text-left font-medium px-2 py-1">Path</th>
                      <th className="text-left font-medium px-2 py-1">Last Accessed</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {applications.map(a => (
                      <tr key={a.appId} className="hover:bg-gray-50 align-top">
                        <td className="px-2 py-1 space-y-1">
                          <div className="flex items-center space-x-1">
                            <a
                              href={`#/app/${a.appId}`}
                              onClick={(e) => { e.preventDefault(); onOpenApp && onOpenApp(a.appId); }}
                              className="inline-flex items-center px-1.5 py-0.5 rounded bg-blue-50 text-blue-700 hover:underline hover:bg-blue-100"
                            >
                              <i data-lucide="app-window" className="w-3 h-3 mr-1" />
                              {(a.appName || a.appId).substring(0, 24)}
                            </a>
                            <button onClick={() => copyToClipboard(a.appId)} className="text-gray-400 hover:text-blue-600" title="Copy App ID"><i data-lucide="copy" className="w-3 h-3" /></button>
                          </div>
                        </td>
                        <td className="px-2 py-1">
                          <span className={`inline-flex px-1.5 py-0.5 rounded border ${a.loaded ? 'bg-green-50 text-green-700 border-green-200' : 'bg-gray-50 text-gray-500 border-gray-200'}`}>{a.loaded ? 'Yes' : 'No'}</span>
                        </td>
                        <td className="px-2 py-1 max-w-xs">
                          <div className="font-mono break-all" title={a.path}>{a.path}</div>
                          <button onClick={() => copyToClipboard(a.path)} className="mt-1 text-gray-400 hover:text-blue-600" title="Copy Path"><i data-lucide="copy" className="w-3 h-3" /></button>
                        </td>
                        <td className="px-2 py-1" title={a.lastAccessed || ''}>{a.lastAccessed ? formatRelativeTime(a.lastAccessed) : 'N/A'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Manifest Section */}
          <div>
            <h3 className="text-sm font-semibold text-gray-800 mb-2 flex items-center"><i data-lucide="file-text" className="w-4 h-4 mr-1" /> Manifest</h3>
            {Object.keys(manifest).length === 0 && (
              <p className="text-xs text-gray-500">No manifest attributes captured.</p>
            )}
            {Object.keys(manifest).length > 0 && (
              <div className="border rounded-lg divide-y">
                {Object.entries(manifest).map(([k, v]) => (
                  <div key={k} className="px-3 py-2 flex items-start justify-between hover:bg-gray-50">
                    <span className="text-xs font-medium text-gray-600 mr-3 break-all w-40 flex-shrink-0">{k}</span>
                    <span className="text-xs text-gray-900 font-mono break-all flex-1">{v}</span>
                    <button onClick={() => copyToClipboard(v)} className="ml-2 text-gray-400 hover:text-blue-600 flex-shrink-0" title="Copy value">
                      <i data-lucide="copy" className="w-3 h-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default JarDetails;
