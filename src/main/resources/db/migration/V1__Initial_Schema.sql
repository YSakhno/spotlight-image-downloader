CREATE TABLE downloads (
    file_hash                   TEXT                            PRIMARY KEY,
    filename                    TEXT                            NOT NULL,
    category                    TEXT                            NOT NULL,
    image_name                  TEXT                            NOT NULL,
    title                       TEXT                            NOT NULL,
    description                 TEXT                            NOT NULL,
    download_time               TEXT                            NOT NULL,
    last_modified_time          TEXT                            NULL
);

-- Unique index for case-insensitive filename lookups
CREATE UNIQUE INDEX ux_downloads_filename_case_insensitive
    ON downloads(lower(filename));
