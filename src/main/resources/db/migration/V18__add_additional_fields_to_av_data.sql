-- Добавление дополнительных полей product_additional6-10 и competitor_additional3-10
-- для расширения возможностей хранения данных о товарах и конкурентах

-- Добавляем дополнительные поля продуктов (productAdditional6-10)
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS product_additional6 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS product_additional7 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS product_additional8 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS product_additional9 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS product_additional10 VARCHAR(255);

-- Добавляем дополнительные поля конкурентов (competitorAdditional3-10)
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_additional3 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_additional4 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_additional5 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_additional6 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_additional7 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_additional8 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_additional9 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_additional10 VARCHAR(255);

-- Комментарии для документации
COMMENT ON COLUMN av_data.product_additional6 IS 'Дополнительное поле товара 6';
COMMENT ON COLUMN av_data.product_additional7 IS 'Дополнительное поле товара 7';
COMMENT ON COLUMN av_data.product_additional8 IS 'Дополнительное поле товара 8';
COMMENT ON COLUMN av_data.product_additional9 IS 'Дополнительное поле товара 9';
COMMENT ON COLUMN av_data.product_additional10 IS 'Дополнительное поле товара 10';

COMMENT ON COLUMN av_data.competitor_additional3 IS 'Дополнительное поле конкурента 3';
COMMENT ON COLUMN av_data.competitor_additional4 IS 'Дополнительное поле конкурента 4';
COMMENT ON COLUMN av_data.competitor_additional5 IS 'Дополнительное поле конкурента 5';
COMMENT ON COLUMN av_data.competitor_additional6 IS 'Дополнительное поле конкурента 6';
COMMENT ON COLUMN av_data.competitor_additional7 IS 'Дополнительное поле конкурента 7';
COMMENT ON COLUMN av_data.competitor_additional8 IS 'Дополнительное поле конкурента 8';
COMMENT ON COLUMN av_data.competitor_additional9 IS 'Дополнительное поле конкурента 9';
COMMENT ON COLUMN av_data.competitor_additional10 IS 'Дополнительное поле конкурента 10';
