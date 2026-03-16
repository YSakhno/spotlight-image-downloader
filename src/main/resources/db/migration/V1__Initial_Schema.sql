CREATE TABLE downloads (
    id                          INTEGER                         PRIMARY KEY AUTOINCREMENT,
    filename                    TEXT                            NOT NULL,
    category                    TEXT                            NOT NULL,
    title                       TEXT                            NOT NULL,
    description                 TEXT                            NOT NULL,
    file_hash                   TEXT                            NOT NULL
);

-- Unique index for looking up files by the SHA256 hash of their binary data
CREATE UNIQUE INDEX ux_downloads_file_hash
    ON downloads(file_hash);

-- Unique index for case-insensitive filename lookups
CREATE UNIQUE INDEX ux_downloads_filename_case_insensitive
    ON downloads(lower(filename));
