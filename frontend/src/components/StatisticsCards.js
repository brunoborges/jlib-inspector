import React, { useEffect } from 'react';
import { initLucideIcons } from '../utils/helpers';

const StatisticsCards = ({ applications }) => {
    useEffect(() => {
        initLucideIcons();
    }, []);

    const stats = {
        totalApps: applications.length,
        totalJars: applications.reduce((sum, app) => sum + (app.jars ? app.jars.length : 0), 0),
        loadedJars: applications.reduce((sum, app) => 
            sum + (app.jars ? app.jars.filter(jar => jar.loaded).length : 0), 0),
        uniqueJars: new Set(
            applications.flatMap(app => 
                app.jars ? app.jars.map(jar => jar.fileName).filter(name => name) : []
            )
        ).size
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
            title: 'Total JARs',
            value: stats.totalJars,
            icon: 'package',
            gradient: 'from-green-50 to-green-100',
            iconBg: 'bg-green-500',
            textColor: 'text-green-700',
            valueColor: 'text-green-900'
        },
        {
            title: 'Loaded JARs',
            value: stats.loadedJars,
            icon: 'check-circle',
            gradient: 'from-yellow-50 to-yellow-100',
            iconBg: 'bg-yellow-500',
            textColor: 'text-yellow-700',
            valueColor: 'text-yellow-900'
        },
        {
            title: 'Unique JARs',
            value: stats.uniqueJars,
            icon: 'layers',
            gradient: 'from-purple-50 to-purple-100',
            iconBg: 'bg-purple-500',
            textColor: 'text-purple-700',
            valueColor: 'text-purple-900'
        }
    ];

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            {cards.map((card, index) => (
                <div key={index} className={`card p-6 bg-gradient-to-br ${card.gradient}`}>
                    <div className="flex items-center justify-between">
                        <div>
                            <p className={`text-sm font-medium ${card.textColor}`}>{card.title}</p>
                            <p className={`text-3xl font-bold ${card.valueColor}`}>{card.value}</p>
                        </div>
                        <div className={`w-12 h-12 ${card.iconBg} rounded-full flex items-center justify-center shadow-lg`}>
                            <i data-lucide={card.icon} className="w-6 h-6 text-white"></i>
                        </div>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default StatisticsCards;
