import React, { useState, useEffect } from 'react';
import { formatFileSize, getMvnRepositoryUrl, copyToClipboard, initLucideIcons } from '../utils/helpers';

const JarItem = ({ jar, isCompact = false, isUniqueJar = false, onOpenApp, appNameById, onOpenJar }) => {
    const [copyStatus, setCopyStatus] = useState(null);
    
    useEffect(() => {
        initLucideIcons();
    }, [copyStatus]);

    const isJar = jar.fileName.endsWith('.jar');
    const iconClass = !isJar ? 'w-4 h-4 text-blue-500' : 'w-4 h-4 text-green-500';
    const iconName = !isJar ? 'layers' : 'package';
    const mvnUrl = getMvnRepositoryUrl(jar.fileName);

    const handleCopyChecksum = async (e) => {
        e.stopPropagation();
        const success = await copyToClipboard(jar.checksum);
        setCopyStatus(success ? 'success' : 'error');
        setTimeout(() => setCopyStatus(null), 2000);
    };

    return (
        <div className="jar-modal-item p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer" onClick={() => { if (onOpenJar && jar.jarId) onOpenJar(jar.jarId); }}>
            <div className="flex items-start justify-between">
                <div className="flex items-start space-x-3 flex-1">
                    <i data-lucide={iconName} className={`${iconClass} mt-1 flex-shrink-0`}></i>
                    <div className="flex-1 min-w-0">
                        <h5 className="text-sm font-medium text-gray-900 truncate">
                            {jar.fileName || 'Unknown JAR'}
                        </h5>
                        <p className="text-xs text-gray-500 break-all mb-1">{jar.path}</p>
                        {jar.checksum && jar.checksum !== '?' && (
                            <div className="flex items-center justify-between bg-gray-50 p-2 rounded border mb-2">
                                <div className="flex-1 min-w-0">
                                    <span className="text-xs text-gray-600 font-medium">SHA-256:</span>
                                    <span className="text-xs text-gray-800 font-mono ml-1 break-all">{jar.checksum}</span>
                                </div>
                                <button 
                                    onClick={handleCopyChecksum}
                                    className={`ml-2 p-1 rounded transition-colors flex-shrink-0 ${
                                        copyStatus === 'success' ? 'text-green-600 bg-green-100' :
                                        copyStatus === 'error' ? 'text-red-600 bg-red-100' :
                                        'text-gray-400 hover:text-gray-600 hover:bg-gray-200'
                                    }`}
                                    title={copyStatus === 'success' ? 'Copied!' : copyStatus === 'error' ? 'Failed to copy' : 'Copy checksum'}
                                >
                                    <i data-lucide={
                                        copyStatus === 'success' ? 'check' :
                                        copyStatus === 'error' ? 'x' : 'copy'
                                    } className="w-3 h-3"></i>
                                </button>
                            </div>
                        )}
                        {mvnUrl && (
                            <a 
                                href={mvnUrl} 
                                target="_blank" 
                                rel="noopener noreferrer" 
                                className="inline-flex items-center text-xs text-blue-600 hover:text-blue-800 hover:underline"
                            >
                                <i data-lucide="external-link" className="w-3 h-3 mr-1"></i>
                                Search on mvnrepository.com
                            </a>
                        )}
                    </div>
                </div>
                <div className="text-right flex-shrink-0 ml-3">
                    <p className="text-xs text-gray-600">{formatFileSize(jar.size)}</p>
                    <p className={`text-xs ${jar.loaded ? 'text-green-600' : 'text-gray-400'} font-medium`}>
                        {jar.loaded ? 'Loaded' : 'Not loaded'}
                    </p>
                </div>
            </div>
        </div>
    );
};

export default JarItem;
