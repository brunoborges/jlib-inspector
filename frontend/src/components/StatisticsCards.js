import React, { useEffect } from 'react';
import { initLucideIcons } from '../utils/helpers';

const StatisticsCards = ({ applications, onUniqueJarsClick }) => {
    useEffect(() => {
        initLucideIcons();
    }, []);

    // Build a map of unique jars and whether they are active (loaded in any app)
    const uniqueJarMap = new Map();
    applications.forEach(app => {
        (app.jars || []).forEach(jar => {
            const key = jar.fileName || jar.path?.split('/').pop();
            if (!key) return;
            const prev = uniqueJarMap.get(key) || { active: false };
            uniqueJarMap.set(key, { active: prev.active || !!jar.loaded });
        });
    });

    const activeCount = Array.from(uniqueJarMap.values()).filter(v => v.active).length;
    const stats = {
        totalApps: applications.length,
        jars: uniqueJarMap.size,
        activeJars: activeCount,
        inactiveJars: uniqueJarMap.size - activeCount,
    };

    const cards = [
        {
            title: 'Total Applications',
            value: stats.totalApps,
            icon: 'server',
            gradient: 'from-blue-50 to-blue-100',
            iconBg: 'bg-blue-500',
            textColor: 'text-blue-700',
            valueColor: 'text-blue-900'
        },
        {
            title: 'Active JARs',
            value: stats.activeJars,
            icon: 'check-circle',
            gradient: 'from-green-50 to-green-100',
            iconBg: 'bg-green-500',
            textColor: 'text-green-700',
            valueColor: 'text-green-900',
            clickable: true,
            filter: 'active'
        },
        {
            title: 'Inactive JARs',
            value: stats.inactiveJars,
            icon: 'slash',
            gradient: 'from-gray-50 to-gray-100',
            iconBg: 'bg-gray-500',
            textColor: 'text-gray-700',
            valueColor: 'text-gray-900',
            clickable: true,
            filter: 'inactive'
        },
        {
            title: 'JARs',
            value: stats.jars,
            icon: 'layers',
            gradient: 'from-purple-50 to-purple-100',
            iconBg: 'bg-purple-500',
            textColor: 'text-purple-700',
            valueColor: 'text-purple-900',
            clickable: true,
            filter: 'all'
        }
    ];

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            {cards.map((card, index) => (
                <div 
                    key={index} 
                    className={`card p-6 bg-gradient-to-br ${card.gradient} ${
                        card.clickable 
                            ? 'cursor-pointer hover:scale-105 hover:shadow-xl transition-all duration-200 border-2 border-transparent hover:border-purple-300' 
                            : ''
                    }`}
                    onClick={card.clickable ? () => onUniqueJarsClick(card.filter) : undefined}
                    title={card.clickable ? 'Click to view detailed list of JARs' : undefined}
                >
                    <div className="flex items-center justify-between">
                        <div className="flex-1">
                            <div className="flex items-center gap-2">
                                <p className={`text-sm font-medium ${card.textColor}`}>{card.title}</p>
                                {card.clickable && (
                                    <i data-lucide="external-link" className="w-3 h-3 text-purple-600 opacity-70"></i>
                                )}
                            </div>
                            <p className={`text-3xl font-bold ${card.valueColor}`}>{card.value}</p>
                            {card.clickable && (
                                <p className="text-xs text-purple-600 mt-1 opacity-80">Click to explore →</p>
                            )}
                        </div>
                        <div className={`w-12 h-12 ${card.iconBg} rounded-full flex items-center justify-center shadow-lg ${
                            card.title === 'Unique JARs' ? 'hover:shadow-2xl transition-shadow duration-200' : ''
                        }`}>
                            <i data-lucide={card.icon} className="w-6 h-6 text-white"></i>
                        </div>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default StatisticsCards;
