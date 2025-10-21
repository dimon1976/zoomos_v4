-- Добавление новых полей в таблицу av_data
-- Включает поля конкурентов (brand, categories, productId, bar, oldPrice),
-- поля региона (country) и системный идентификатор (zmsId)

-- Новые поля конкурентов
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_brand VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_category1 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_category2 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_category3 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_category4 VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_product_id VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_bar VARCHAR(255);
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS competitor_old_price DECIMAL(10,2);

-- Новые поля региона
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS region_country VARCHAR(100);

-- Системные поля
ALTER TABLE av_data ADD COLUMN IF NOT EXISTS zms_id VARCHAR(100);

-- Комментарии для документации
COMMENT ON COLUMN av_data.competitor_brand IS 'Бренд товара конкурента';
COMMENT ON COLUMN av_data.competitor_category1 IS 'Категория 1 товара конкурента';
COMMENT ON COLUMN av_data.competitor_category2 IS 'Категория 2 товара конкурента';
COMMENT ON COLUMN av_data.competitor_category3 IS 'Категория 3 товара конкурента';
COMMENT ON COLUMN av_data.competitor_category4 IS 'Категория 4 товара конкурента';
COMMENT ON COLUMN av_data.competitor_product_id IS 'ID товара конкурента';
COMMENT ON COLUMN av_data.competitor_bar IS 'Штрих-код товара конкурента';
COMMENT ON COLUMN av_data.competitor_old_price IS 'Старая цена товара конкурента';
COMMENT ON COLUMN av_data.region_country IS 'Страна региона';
COMMENT ON COLUMN av_data.zms_id IS 'Системный идентификатор Zoomos';
