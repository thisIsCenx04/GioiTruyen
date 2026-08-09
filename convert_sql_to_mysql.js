const fs = require('fs');
const path = require('path');

const srcSql = fs.readFileSync(path.join(__dirname, 'db', 'entity_real.sql'), 'utf8');

// 1. Extract Enums
const enumRegex = /CREATE TYPE (\w+) AS ENUM \((.*?)\);/gs;
let match;
const enums = {};
while ((match = enumRegex.exec(srcSql)) !== null) {
    const enumName = match[1];
    const enumValues = match[2].trim();
    enums[enumName] = `ENUM(${enumValues})`;
}

// Manually add missing enums
enums['team_ledger_type'] = `ENUM('DONATION', 'CHAPTER_UNLOCK', 'WITHDRAWAL', 'PLATFORM_FEE')`;

// 2. Process Tables
let mysqlSql = `-- ============================================================
-- Web Truyen - MySQL Database Schema (Auto Migration)
-- Generated from entity_real.sql
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;
`;

const tableRegex = /CREATE TABLE (\w+) \(([\s\S]*?)\);/gs;
while ((match = tableRegex.exec(srcSql)) !== null) {
    const tableName = match[1];
    let tableBody = match[2];

    const lines = tableBody.split('\n');
    const newLines = [];

    for (let line of lines) {
        let trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('--')) continue;

        // Convert Postgres types to MySQL types
        trimmed = trimmed.replace(/\bUUID\b/gi, 'VARCHAR(36)');
        trimmed = trimmed.replace(/\bTIMESTAMPTZ\b/gi, 'TIMESTAMP');
        trimmed = trimmed.replace(/\bJSONB\b/gi, 'JSON');
        trimmed = trimmed.replace(/\bgen_random_uuid\(\)/gi, '(UUID())');
        trimmed = trimmed.replace(/\bCURRENT_TIMESTAMP\b/gi, 'CURRENT_TIMESTAMP');

        // Convert custom enum types to MySQL inline ENUM(...)
        Object.keys(enums).forEach(eName => {
            const regex = new RegExp(`\\b${eName}\\b`, 'g');
            trimmed = trimmed.replace(regex, enums[eName]);
        });

        newLines.push('    ' + trimmed);
    }

    mysqlSql += `\nDROP TABLE IF EXISTS \`${tableName}\`;\n`;
    mysqlSql += `CREATE TABLE \`${tableName}\` (\n`;
    mysqlSql += newLines.join('\n');
    mysqlSql += `\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;\n`;
}

mysqlSql += `\nSET FOREIGN_KEY_CHECKS = 1;\n`;

const migrationDir = path.join(__dirname, 'be', 'src', 'main', 'resources', 'db', 'migration');
if (!fs.existsSync(migrationDir)) {
    fs.mkdirSync(migrationDir, { recursive: true });
}

// Clean old files in migration dir
const oldFiles = fs.readdirSync(migrationDir);
oldFiles.forEach(file => {
    fs.unlinkSync(path.join(migrationDir, file));
});

// Write V1__init_schema.sql
fs.writeFileSync(path.join(migrationDir, 'V1__init_schema.sql'), mysqlSql);

// Write V2__seed_data.sql
const seedSql = `-- ============================================================
-- Web Truyen - Initial Seed Data (V2)
-- ============================================================

-- Seed Default Admin User (Password: Admin@123)
INSERT INTO \`users\` (\`id\`, \`email\`, \`username\`, \`password_hash\`, \`role\`, \`status\`, \`created_at\`, \`updated_at\`)
VALUES ('00000000-0000-0000-0000-000000000001', 'admin@gioitruyen.com', 'admin', '$2a$10$76/nK8p3eBwW8mH.zS2W/.x4/tK9r7X1h5gK4wW.J5xH9X.J5xH9X', 'ADMIN', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE \`updated_at\` = NOW();

-- Seed Default Genres
INSERT INTO \`genres\` (\`id\`, \`name\`, \`slug\`, \`description\`)
VALUES 
('10000000-0000-0000-0000-000000000001', 'Tên Tiên Hạp', 'tien-hiep', 'Truyện tiên hiệp tu chân'),
('10000000-0000-0000-0000-000000000002', 'Huyền Huyễn', 'huyen-huyen', 'Truyện huyền huyễn thế giới mới'),
('10000000-0000-0000-0000-000000000003', 'Đô Thị', 'do-thi', 'Truyện đô thị hiện đại'),
('10000000-0000-0000-0000-000000000004', 'Võ Học', 'vo-hac', 'Truyện kiếm hiệp võ lâm')
ON DUPLICATE KEY UPDATE \`name\` = VALUES(\`name\`);

-- Seed Default Currencies
INSERT INTO \`currencies\` (\`id\`, \`code\`, \`name\`, \`symbol\`, \`is_active\`, \`created_at\`)
VALUES 
('20000000-0000-0000-0000-000000000001', 'COIN', 'Xu Truyện', 'Xu', TRUE, NOW())
ON DUPLICATE KEY UPDATE \`name\` = VALUES(\`name\`);

-- Seed Default Deposit Packages
INSERT INTO \`deposit_packages\` (\`id\`, \`name\`, \`price_vnd\`, \`coin_amount\`, \`bonus_coin\`, \`is_active\`)
VALUES 
('30000000-0000-0000-0000-000000000001', 'Gói Nạp Khởi Đầu', 10000, 100, 10, TRUE),
('30000000-0000-0000-0000-000000000002', 'Gói Nạp Phổ Thông', 50000, 500, 70, TRUE),
('30000000-0000-0000-0000-000000000003', 'Gói Nạp Cao Cấp', 100000, 1000, 200, TRUE)
ON DUPLICATE KEY UPDATE \`name\` = VALUES(\`name\`);
`;

fs.writeFileSync(path.join(migrationDir, 'V2__seed_data.sql'), seedSql);

console.log('Flyway migration files created successfully.');
