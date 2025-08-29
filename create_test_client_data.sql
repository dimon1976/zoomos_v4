-- SQL скрипт для создания тестового клиента TEST! и данных для проверки нормализации

-- Создаем клиента TEST! если его нет
INSERT INTO clients (name, created_at, updated_at, is_active)
VALUES ('TEST!', NOW(), NOW(), true)
ON CONFLICT (name) DO UPDATE SET 
    updated_at = NOW(),
    is_active = true;

-- Получаем ID клиента TEST!
-- В реальном запросе нужно будет заменить client_id на актуальный ID

-- Создаем шаблон экспорта с нормализацией для клиента TEST!
INSERT INTO export_templates (client_id, name, description, created_at, updated_at) 
SELECT c.id, 'Тест нормализации', 'Шаблон для тестирования нормализации объемов и брендов', NOW(), NOW()
FROM clients c WHERE c.name = 'TEST!';

-- Добавляем поля в шаблон экспорта с настройками нормализации
INSERT INTO export_template_fields (template_id, entity_field_name, export_column_name, field_order, is_included, normalization_type, normalization_rule)
SELECT t.id, 'brand', 'Бренд', 1, true, 'BRAND', null
FROM export_templates t 
JOIN clients c ON t.client_id = c.id 
WHERE c.name = 'TEST!' AND t.name = 'Тест нормализации';

INSERT INTO export_template_fields (template_id, entity_field_name, export_column_name, field_order, is_included, normalization_type, normalization_rule)
SELECT t.id, 'volume', 'Объем', 2, true, 'VOLUME', null
FROM export_templates t 
JOIN clients c ON t.client_id = c.id 
WHERE c.name = 'TEST!' AND t.name = 'Тест нормализации';

INSERT INTO export_template_fields (template_id, entity_field_name, export_column_name, field_order, is_included, normalization_type, normalization_rule)
SELECT t.id, 'price', 'Цена', 3, true, null, null
FROM export_templates t 
JOIN clients c ON t.client_id = c.id 
WHERE c.name = 'TEST!' AND t.name = 'Тест нормализации';

-- Создаем тестовые данные для экспорта (имитируем импортированные данные)
-- Примечание: в реальной БД эти данные должны быть в динамической таблице с именем клиента
-- Здесь мы создаем условный пример структуры

CREATE TABLE IF NOT EXISTS test_client_data (
    id SERIAL PRIMARY KEY,
    brand VARCHAR(255),
    volume VARCHAR(100),
    price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Вставляем тестовые данные с различными вариантами записи брендов и объемов
INSERT INTO test_client_data (brand, volume, price) VALUES
-- Тестовые бренды
('The Macallan', '0.7л', 12000.00),
('MACALLAN', '0,7 л.', 11500.00),
('Macallan, Edition №5', '0.7', 15000.00),
('the glenfiddich', '1л', 8000.00),
('jameson irish whiskey', '0,5 л.', 3500.00),
('JAMESON', '500мл', 3200.00),
('Chivas Regal', '0.75л', 4500.00),
('chivas regal blended scotch', '750мл', 4300.00),
('The Glenlivet', '1.0л', 9500.00),
('GLENLIVET 12 YEARS OLD', '1л', 9200.00);

-- Комментарий для пользователя:
-- После выполнения этого скрипта у вас будет:
-- 1. Клиент TEST! в базе данных
-- 2. Шаблон экспорта "Тест нормализации" с полями brand и volume, настроенными на нормализацию
-- 3. Тестовые данные в таблице test_client_data для проверки работы нормализации

-- Для проверки нормализации нужно будет:
-- 1. Выполнить экспорт с использованием созданного шаблона
-- 2. Проверить что бренды нормализуются (The Macallan -> Macallan, jameson irish whiskey -> Jameson и т.д.)
-- 3. Проверить что объемы нормализуются (0.7л -> 0.7, 500мл -> 500 и т.д.)