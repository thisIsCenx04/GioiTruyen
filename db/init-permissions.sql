CREATE DATABASE IF NOT EXISTS gioitruyen CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'gioitruyen'@'%' IDENTIFIED BY 'gioitruyen_local';
CREATE USER IF NOT EXISTS 'gioitruyen'@'127.0.0.1' IDENTIFIED BY 'gioitruyen_local';
CREATE USER IF NOT EXISTS 'gioitruyen'@'localhost' IDENTIFIED BY 'gioitruyen_local';
GRANT ALL PRIVILEGES ON gioitruyen.* TO 'gioitruyen'@'%';
GRANT ALL PRIVILEGES ON gioitruyen.* TO 'gioitruyen'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gioitruyen.* TO 'gioitruyen'@'localhost';
FLUSH PRIVILEGES;
