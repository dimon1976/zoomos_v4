ALTER TABLE av_data
    ALTER COLUMN competitor_price TYPE DOUBLE PRECISION USING NULLIF(competitor_price, '')::double precision,
    ALTER COLUMN competitor_promotional_price TYPE DOUBLE PRECISION USING NULLIF(competitor_promotional_price, '')::double precision,
    ALTER COLUMN competitor_additional_price TYPE DOUBLE PRECISION USING NULLIF(competitor_additional_price, '')::double precision;