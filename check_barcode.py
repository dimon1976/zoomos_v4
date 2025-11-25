#!/usr/bin/env python3
import openpyxl

# Открываем файл
wb = openpyxl.load_workbook(r'd:\Job\Книга11.xlsx', read_only=True)
ws = wb.active

# Ищем штрихкод 4603347224946 в колонках B (исходные) и C (справочные)
target = '4603347224946'
found_in_col_b = []
found_in_col_c = []

for idx, row in enumerate(ws.iter_rows(min_row=2, max_row=110437, values_only=True), start=2):
    # Колонка B (index 1) - исходные штрихкоды
    if row[1]:
        barcodes_b = str(row[1]).split(',')
        for bc in barcodes_b:
            if bc.strip() == target:
                found_in_col_b.append((idx, row[0], row[1]))

    # Колонка C (index 2) - справочные штрихкоды
    if row[2]:
        barcodes_c = str(row[2]).split(',')
        for bc in barcodes_c:
            if bc.strip() == target:
                found_in_col_c.append((idx, row[0], row[2], row[3]))

print(f"Поиск штрихкода: {target}")
print(f"\nНайдено в колонке B (исходные): {len(found_in_col_b)}")
for row_num, id_val, bc_val in found_in_col_b[:5]:
    print(f"  Строка {row_num}: ID={id_val}, Штрихкоды={bc_val[:100]}")

print(f"\nНайдено в колонке C (справочные): {len(found_in_col_c)}")
for row_num, id_val, bc_val, url in found_in_col_c[:5]:
    print(f"  Строка {row_num}: ID={id_val}, Штрихкоды={bc_val[:100]}, URL={url[:80] if url else 'None'}")

wb.close()
