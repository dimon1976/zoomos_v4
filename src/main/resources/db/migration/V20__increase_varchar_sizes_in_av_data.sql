-- Увеличение размера VARCHAR полей в таблице av_data для поддержки длинных значений
-- Некоторые поля могут содержать длинные строки (особенно description, commentary, product_name)

-- Увеличиваем критические поля до TEXT (неограниченный размер)
ALTER TABLE av_data ALTER COLUMN product_description TYPE TEXT;
ALTER TABLE av_data ALTER COLUMN product_url TYPE TEXT;
ALTER TABLE av_data ALTER COLUMN competitor_commentary TYPE TEXT;
ALTER TABLE av_data ALTER COLUMN competitor_product_name TYPE TEXT;
ALTER TABLE av_data ALTER COLUMN competitor_url TYPE TEXT;
ALTER TABLE av_data ALTER COLUMN competitor_web_cache_url TYPE TEXT;

-- Увеличиваем дополнительные поля до VARCHAR(1000)
ALTER TABLE av_data ALTER COLUMN product_additional1 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional2 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional3 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional4 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional5 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional6 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional7 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional8 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional9 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN product_additional10 TYPE VARCHAR(1000);

ALTER TABLE av_data ALTER COLUMN competitor_additional TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional2 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional3 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional4 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional5 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional6 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional7 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional8 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional9 TYPE VARCHAR(1000);
ALTER TABLE av_data ALTER COLUMN competitor_additional10 TYPE VARCHAR(1000);

-- Комментарии
COMMENT ON COLUMN av_data.product_description IS 'Описание товара (TEXT для поддержки длинных описаний)';
COMMENT ON COLUMN av_data.product_url IS 'URL товара (TEXT для поддержки длинных URL)';
COMMENT ON COLUMN av_data.competitor_commentary IS 'Комментарий о товаре конкурента (TEXT для поддержки длинных комментариев)';
COMMENT ON COLUMN av_data.competitor_product_name IS 'Название товара конкурента (TEXT для поддержки длинных названий)';
COMMENT ON COLUMN av_data.competitor_url IS 'URL товара конкурента (TEXT для поддержки длинных URL)';
COMMENT ON COLUMN av_data.competitor_web_cache_url IS 'URL кэша товара конкурента (TEXT для поддержки длинных URL)';
