-- Illust Data
CREATE TABLE IF NOT EXISTS users
(
    `uid`     INTEGER      NOT NULL,
    `name`    NVARCHAR(15) NOT NULL COLLATE LATIN1_100_CI_AI_UTF8,
    `account` VARCHAR(32)  NOT NULL COLLATE LATIN1_100_BIN,
    PRIMARY KEY (`uid`)
);
CREATE TABLE IF NOT EXISTS artworks
(
    `pid`             INTEGER      NOT NULL,
    `uid`             INTEGER      NOT NULL,
    `title`           NVARCHAR(32) NOT NULL,
    `caption`         TEXT         NOT NULL,
    `create_at`       INTEGER      NOT NULL,
    -- page_count max 200
    `page_count`      SMALLINT     NOT NULL,
    -- sanity_level 0 2 4 6 7
    `sanity_level`    TINYINT      NOT NULL,
    `type`            TINYINT      NOT NULL,
    `width`           SMALLINT     NOT NULL,
    `height`          SMALLINT     NOT NULL,
    `total_bookmarks` INTEGER      NOT NULL DEFAULT 0,
    `total_comments`  INTEGER      NOT NULL DEFAULT 0,
    `total_view`      INTEGER      NOT NULL DEFAULT 0,
    `age`             TINYINT      NOT NULL DEFAULT 0,
    `is_ero`          BOOLEAN      NOT NULL DEFAULT FALSE,
    `deleted`         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`pid`),
    FOREIGN KEY (`uid`) REFERENCES users (`uid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS tags
(
    `pid`             INTEGER      NOT NULL,
    `name`            NVARCHAR(30) NOT NULL COLLATE LATIN1_100_BIN,
    `translated_name` TEXT COLLATE LATIN1_100_BIN,
    PRIMARY KEY (`pid`, `name`),
    FOREIGN KEY (`pid`) REFERENCES artworks (`pid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS tag_name ON tags (`name`);
CREATE INDEX IF NOT EXISTS tag_translated_name ON tags (`translated_name`);
CREATE TABLE IF NOT EXISTS files
(
    `pid`   INTEGER      NOT NULL,
    `index` TINYINT      NOT NULL,
    `md5`   CHAR(32)     NOT NULL COLLATE LATIN1_100_CI_AI,
    `url`   VARCHAR(255) NOT NULL COLLATE LATIN1_100_CI_AI,
    -- file size max 32MB
    `size`  INTEGER      NOT NULL,
    PRIMARY KEY (`pid`, `index`),
    FOREIGN KEY (`pid`) REFERENCES artworks (`pid`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS file_md5 ON files (`md5`);

-- User Data
CREATE TABLE IF NOT EXISTS statistic_ero
(
    `sender`    BIGINT  NOT NULL,
    `group`     INTEGER,
    `pid`       INTEGER NOT NULL,
    `timestamp` INTEGER NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS statistic_tag
(
    `sender`    BIGINT       NOT NULL,
    `group`     INTEGER,
    `pid`       INTEGER,
    `tag`       NVARCHAR(30) NOT NULL COLLATE LATIN1_100_CI_AI_UTF8,
    `timestamp` INTEGER      NOT NULL,
    PRIMARY KEY (`sender`, `timestamp`)
);
CREATE TABLE IF NOT EXISTS statistic_search
(
    `md5`        CHAR(32)      NOT NULL COLLATE LATIN1_100_CI_AI,
    `similarity` NUMERIC(6, 4) NOT NULL,
    `pid`        INTEGER       NOT NULL,
    `title`      NVARCHAR(64)  NOT NULL,
    `uid`        INTEGER       NOT NULL,
    `name`       NVARCHAR(15)  NOT NULL,
    PRIMARY KEY (`md5`)
);
CREATE TABLE IF NOT EXISTS statistic_alias
(
    `name` NVARCHAR(15) NOT NULL COLLATE LATIN1_100_CI_AI_UTF8,
    `uid`  INTEGER      NOT NULL,
    PRIMARY KEY (`name`)
);
CREATE TABLE IF NOT EXISTS statistic_task
(
    `task`      VARCHAR(64) NOT NULL COLLATE LATIN1_100_CI_AI,
    `pid`       INTEGER     NOT NULL,
    `timestamp` INTEGER     NOT NULL,
    PRIMARY KEY (`task`, `pid`)
);