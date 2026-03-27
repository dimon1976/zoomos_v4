-- Partial indexes для ускорения запросов на странице очистки справочника ШК.
-- Покрывают только некорректные записи, поэтому почти не занимают место.

-- Индекс для ШК в научной нотации (содержат 'E' или 'e')
CREATE INDEX IF NOT EXISTS idx_bh_products_scientific_notation
    ON bh_products(barcode)
    WHERE barcode ~ '[Ee]';

-- Индекс для NULL штрихкодов (для проверки сирот)
CREATE INDEX IF NOT EXISTS idx_bh_products_null_barcode
    ON bh_products(id)
    WHERE barcode IS NULL;

-- Индекс для коротких штрихкодов (< 6 символов)
CREATE INDEX IF NOT EXISTS idx_bh_products_short_barcode
    ON bh_products(barcode)
    WHERE barcode IS NOT NULL AND LENGTH(barcode) < 6;
