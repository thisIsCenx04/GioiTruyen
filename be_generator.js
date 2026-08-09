const fs = require('fs');
const path = require('path');

const sqlPath = path.join(__dirname, 'db', 'entity_real.sql');
const basePackage = 'com.storyplatform';
const outputDir = path.join(__dirname, 'be', 'src', 'main', 'java', 'com', 'storyplatform');

const sqlContent = fs.readFileSync(sqlPath, 'utf8');

const tableToDomain = {
    'users': 'auth',
    'auth_accounts': 'auth',
    'password_reset_tokens': 'auth',
    'user_profiles': 'auth',
    'user_settings': 'auth',

    'teams': 'teams',
    'team_members': 'teams',
    'team_follows': 'teams',

    'genres': 'catalog',
    'stories': 'catalog',
    'story_genres': 'catalog',
    'story_reviews': 'catalog',
    'chapters': 'catalog',
    'chapter_audios': 'catalog',

    'audio_listens': 'engagement',
    'story_views': 'engagement',
    'story_daily_stats': 'engagement',
    'library_items': 'engagement',
    'story_follows': 'engagement',

    'comments': 'community',
    'comment_likes': 'community',
    'community_rooms': 'community',
    'community_messages': 'community',

    'currencies': 'monetization',
    'wallets': 'monetization',
    'wallet_transactions': 'monetization',
    'payment_methods': 'monetization',
    'deposit_packages': 'monetization',
    'payments': 'monetization',
    'purchase_orders': 'monetization',
    'purchase_order_items': 'monetization',
    'chapter_unlocks': 'monetization',
    'donations': 'monetization',
    'team_ledger': 'monetization',

    'story_recommendations': 'gamification',
    'ranking_snapshots': 'gamification',
    'missions': 'gamification',
    'user_mission_progress': 'gamification',
    'referral_codes': 'gamification',
    'referrals': 'gamification',

    'reports': 'moderation',
    'admin_audit_logs': 'moderation',

    'notifications': 'system',
    'notification_preferences': 'system',
    'site_settings': 'system',
    'site_documents': 'system',
    'advertisements': 'system',
    'ad_events': 'system'
};

function toCamelCase(str) {
    str = str.replace(/^\(/, '').replace(/\)$/, '');
    return str.replace(/_([a-z])/g, (g) => g[1].toUpperCase());
}

function toSingular(str) {
    if (str.endsWith('ies')) return str.slice(0, -3) + 'y';
    if (str.endsWith('ses')) return str.slice(0, -2);
    if (str.endsWith('s') && !str.endsWith('ss') && !str.endsWith('us') && !str.endsWith('progress')) return str.slice(0, -1);
    return str;
}

function toPascalCase(str) {
    const camel = toCamelCase(toSingular(str));
    return camel.charAt(0).toUpperCase() + camel.slice(1);
}

function parseSqlTypeToJava(sqlType) {
    sqlType = sqlType.toUpperCase();
    if (sqlType.includes('UUID')) return 'UUID';
    if (sqlType.includes('VARCHAR') || sqlType.includes('TEXT')) return 'String';
    if (sqlType.includes('BIGINT')) return 'Long';
    if (sqlType.includes('INTEGER') || sqlType.includes('SMALLINT')) return 'Integer';
    if (sqlType.includes('BOOLEAN')) return 'Boolean';
    if (sqlType.includes('TIMESTAMPTZ')) return 'Instant';
    if (sqlType.includes('DATE')) return 'LocalDate';
    if (sqlType.includes('NUMERIC')) return 'java.math.BigDecimal';
    if (sqlType.includes('JSONB')) return 'String';
    return 'String';
}

const enumRegex = /CREATE TYPE (\w+) AS ENUM \((.*?)\);/gs;
let match;
const enums = {};
while ((match = enumRegex.exec(sqlContent)) !== null) {
    const enumName = match[1];
    const enumValues = match[2].split(',').map(v => v.replace(/'/g, '').trim());
    enums[enumName] = enumValues;
}

enums['team_ledger_type'] = ['DONATION', 'CHAPTER_UNLOCK', 'WITHDRAWAL', 'PLATFORM_FEE'];

const tableRegex = /CREATE TABLE (\w+) \(([\s\S]*?)\);/gs;
const tables = {};
while ((match = tableRegex.exec(sqlContent)) !== null) {
    const tableName = match[1];
    const tableBody = match[2];
    const columns = [];
    const seenCols = new Set();
    let pk = null;

    const lines = tableBody.split('\n');
    for (let line of lines) {
        line = line.trim();
        if (!line || line.startsWith('--') || line.startsWith('CONSTRAINT') || line.startsWith('UNIQUE') || line.startsWith('PRIMARY KEY') || line.startsWith('CHECK') || line.startsWith('FOREIGN')) continue;
        
        const parts = line.split(/\s+/);
        let colName = parts[0].replace(/,/g, '').replace(/^\(/, '').replace(/\)$/, '');
        let colType = parts[1] ? parts[1].replace(/,/g, '') : '';

        if (!colName || seenCols.has(colName)) continue;
        seenCols.add(colName);

        let javaType = null;
        if (enums[colType]) {
            javaType = toPascalCase(colType);
        } else if (colType) {
            javaType = parseSqlTypeToJava(colType);
        }

        if (javaType) {
            const isId = line.includes('PRIMARY KEY') || colName === 'id';
            columns.push({ name: colName, type: javaType, isId });
            if (isId) {
                pk = javaType;
            }
        }
    }
    
    tables[tableName] = { columns, pk: pk || 'UUID' };
}

function ensureDirSync(dir) {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
}

function getEnumDomain(enumName) {
    if (enumName.startsWith('user_') || enumName === 'auth_provider' || enumName === 'theme_mode') return 'auth';
    if (enumName.startsWith('team_')) return 'teams';
    if (enumName.startsWith('story_') || enumName === 'chapter_access_type' || enumName === 'chapter_status' || enumName === 'review_status') return 'catalog';
    if (enumName === 'generic_content_status') return 'community';
    if (enumName === 'report_target_type' || enumName === 'report_status') return 'moderation';
    if (enumName === 'currency_code' || enumName === 'wallet_transaction_type' || enumName === 'payment_method_type' || enumName === 'payment_status' || enumName === 'purchase_type' || enumName === 'team_ledger_type') return 'monetization';
    if (enumName === 'ranking_type' || enumName === 'ranking_period' || enumName === 'mission_type') return 'gamification';
    if (enumName === 'notification_type' || enumName === 'site_document_type' || enumName === 'advertisement_type' || enumName === 'advertisement_placement' || enumName === 'ad_event_type') return 'system';
    return 'shared';
}

Object.keys(enums).forEach(enumName => {
    const domain = getEnumDomain(enumName);
    const className = toPascalCase(enumName);
    const dir = path.join(outputDir, domain, 'domain');
    ensureDirSync(dir);
    
    const content = `package ${basePackage}.${domain}.domain;

public enum ${className} {
    ${enums[enumName].join(',\n    ')}
}
`;
    fs.writeFileSync(path.join(dir, className + '.java'), content);
});

Object.keys(tables).forEach(tableName => {
    const domain = tableToDomain[tableName] || 'shared';
    const className = toPascalCase(tableName);
    const table = tables[tableName];
    
    const domainDir = path.join(outputDir, domain, 'domain');
    ensureDirSync(domainDir);
    
    let imports = [
        'org.springframework.data.annotation.Id',
        'org.springframework.data.relational.core.mapping.Table'
    ];
    if (table.columns.some(c => c.type === 'UUID')) imports.push('java.util.UUID');
    if (table.columns.some(c => c.type === 'Instant')) imports.push('java.time.Instant');
    if (table.columns.some(c => c.type === 'LocalDate')) imports.push('java.time.LocalDate');

    table.columns.forEach(col => {
        Object.keys(enums).forEach(eName => {
            const enumClass = toPascalCase(eName);
            if (col.type === enumClass) {
                const enumDomain = getEnumDomain(eName);
                if (enumDomain !== domain) {
                    imports.push(`${basePackage}.${enumDomain}.domain.${enumClass}`);
                }
            }
        });
    });

    let entityContent = `package ${basePackage}.${domain}.domain;\n\n`;
    [...new Set(imports)].forEach(i => entityContent += `import ${i};\n`);
    entityContent += `\n@Table("${tableName}")\npublic class ${className} {\n`;

    table.columns.forEach(col => {
        if (col.isId) entityContent += `    @Id\n`;
        entityContent += `    private ${col.type} ${toCamelCase(col.name)};\n`;
    });

    entityContent += `\n    public ${className}() {}\n\n`;

    table.columns.forEach(col => {
        const field = toCamelCase(col.name);
        const methodSuffix = toPascalCase(col.name);
        entityContent += `    public ${col.type} get${methodSuffix}() { return ${field}; }\n`;
        entityContent += `    public void set${methodSuffix}(${col.type} ${field}) { this.${field} = ${field}; }\n\n`;
    });

    entityContent += `}\n`;
    
    fs.writeFileSync(path.join(domainDir, className + '.java'), entityContent);

    const infraDir = path.join(outputDir, domain, 'infrastructure');
    ensureDirSync(infraDir);
    
    const pkType = table.pk === 'UUID' ? 'UUID' : (table.pk === 'String' ? 'String' : 'UUID');
    let repoImports = [
        `${basePackage}.${domain}.domain.${className}`,
        'org.springframework.data.repository.CrudRepository',
        'org.springframework.stereotype.Repository'
    ];
    if (pkType === 'UUID') repoImports.push('java.util.UUID');

    let repoContent = `package ${basePackage}.${domain}.infrastructure;\n\n`;
    [...new Set(repoImports)].forEach(i => repoContent += `import ${i};\n`);
    repoContent += `\n@Repository\npublic interface ${className}Repository extends CrudRepository<${className}, ${pkType}> {\n`;
    if (className === 'User') {
        repoContent += `    java.util.Optional<User> findByEmail(String email);\n`;
        repoContent += `    java.util.Optional<User> findByUsername(String username);\n`;
    }
    repoContent += `}\n`;

    fs.writeFileSync(path.join(infraDir, className + 'Repository.java'), repoContent);
});

console.log('Backend code generation (Spring Data JDBC annotations) complete.');
