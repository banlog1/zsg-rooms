CREATE TABLE IF NOT EXISTS bank_seeds (
    revision TEXT NOT NULL,
    type TEXT NOT NULL,
    slot INTEGER NOT NULL CHECK(slot >= 0),
    seed TEXT NOT NULL,
    family TEXT NOT NULL,
    PRIMARY KEY (revision, type, slot),
    UNIQUE (revision, seed)
);
CREATE TABLE IF NOT EXISTS bank_types (
    revision TEXT NOT NULL,
    type TEXT NOT NULL,
    count INTEGER NOT NULL CHECK(count >= 0),
    PRIMARY KEY (revision, type)
);
CREATE TABLE IF NOT EXISTS bank_active (
    profile TEXT PRIMARY KEY,
    revision TEXT NOT NULL
);
