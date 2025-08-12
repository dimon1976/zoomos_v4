-- Удаляем лишние поля из таблицы clients
ALTER TABLE clients
    DROP COLUMN contact_email,
    DROP COLUMN contact_phone;