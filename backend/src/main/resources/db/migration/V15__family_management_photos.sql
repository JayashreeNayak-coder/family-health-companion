-- Family management and photos. See docs/04-database-design.md section 3.
-- A photo is a small JPEG in the private document storage, under a random key. A family's name is
-- unique among the families each account belongs to; that spans memberships, so the service checks it.

ALTER TABLE family ADD COLUMN photo_key text;
ALTER TABLE family ADD COLUMN photo_updated_at timestamptz;
ALTER TABLE family ADD CONSTRAINT family_photo_key_uk UNIQUE (photo_key);
ALTER TABLE family ADD CONSTRAINT family_photo_ck CHECK ((photo_key IS NULL) = (photo_updated_at IS NULL));

ALTER TABLE health_profile ADD COLUMN photo_key text;
ALTER TABLE health_profile ADD COLUMN photo_updated_at timestamptz;
ALTER TABLE health_profile ADD CONSTRAINT health_profile_photo_key_uk UNIQUE (photo_key);
ALTER TABLE health_profile ADD CONSTRAINT health_profile_photo_ck CHECK ((photo_key IS NULL) = (photo_updated_at IS NULL));
