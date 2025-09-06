// Utility functions for JLib Inspector Dashboard

export const formatFileSize = (bytes) => {
    if (bytes === 0 || bytes === -1) return 'Unknown';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

export const formatRelativeTime = (dateString) => {
    if (!dateString) return 'Never';
    
    const now = new Date();
    const date = new Date(dateString);
    const diffInSeconds = Math.floor((now - date) / 1000);
    
    if (diffInSeconds < 60) return 'Just now';
    if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
    if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
    return `${Math.floor(diffInSeconds / 86400)}d ago`;
};

export const extractJarNameForSearch = (fileName) => {
    if (!fileName || fileName.endsWith('.class') || fileName === '') {
        return null;
    }
    
    // Skip system JARs (JRT modules)
    if (fileName.startsWith('jdk.') || fileName.startsWith('java.')) {
        return null;
    }
    
    // Remove .jar extension
    let name = fileName.replace(/\.jar$/, '');
    
    // Common version patterns to remove
    // Pattern 1: name-1.2.3 or name-1.2.3-SNAPSHOT
    name = name.replace(/-\d+(?:\.\d+)*(?:-[A-Z]+)?$/i, '');
    
    // Pattern 2: name-1.2.3.RELEASE or similar
    name = name.replace(/-\d+(?:\.\d+)*\.[A-Z]+$/i, '');
    
    // Pattern 3: name_1_2_3 (underscore versions)
    name = name.replace(/_\d+(?:_\d+)*$/, '');
    
    return name || null;
};

export const getMvnRepositoryUrl = (fileName) => {
    const searchName = extractJarNameForSearch(fileName);
    if (!searchName) return null;
    return `https://mvnrepository.com/search?q=${encodeURIComponent(searchName)}`;
};

export const copyToClipboard = async (text, callback) => {
    try {
        await navigator.clipboard.writeText(text);
        if (callback) callback(true);
        return true;
    } catch (err) {
        console.error('Failed to copy text: ', err);
        if (callback) callback(false);
        return false;
    }
};

export const initLucideIcons = () => {
    if (window.lucide) {
        window.lucide.createIcons();
    }
};
