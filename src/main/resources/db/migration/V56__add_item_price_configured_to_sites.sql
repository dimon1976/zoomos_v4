-- V56: добавить поле item_price_configured в zoomos_sites
-- Признак что сайт настроен в Zoomos: поле ITEM_PRICE заполнено на странице /shops-parser/{site}/settings
-- null = ещё не проверялось, false = не настроен, true = настроен

ALTER TABLE zoomos_sites
    ADD COLUMN IF NOT EXISTS item_price_configured BOOLEAN DEFAULT NULL;
