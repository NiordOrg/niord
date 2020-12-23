
CREATE DATABASE niord CHARACTER SET utf8 COLLATE utf8_general_ci;

CREATE USER 'niord'@'localhost' IDENTIFIED BY 'niord';
GRANT ALL PRIVILEGES ON *.* TO 'niord'@'localhost' WITH GRANT OPTION;
CREATE USER 'niord'@'%' IDENTIFIED BY 'niord';
GRANT ALL PRIVILEGES ON *.* TO 'niord'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;


CREATE DATABASE niordkc CHARACTER SET utf8 COLLATE utf8_general_ci;

CREATE USER 'niordkc'@'localhost' IDENTIFIED BY 'niordkc';
GRANT ALL PRIVILEGES ON *.* TO 'niordkc'@'localhost' WITH GRANT OPTION;
CREATE USER 'niordkc'@'%' IDENTIFIED BY 'niordkc';
GRANT ALL PRIVILEGES ON *.* TO 'niordkc'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;

