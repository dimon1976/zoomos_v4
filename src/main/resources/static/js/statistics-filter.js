// Используем существующие глобальные переменные из results.html
// currentWarningThreshold и currentCriticalThreshold уже определены

console.log('Statistics filter loaded');

document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM ready - filter initialization');
    // Подождем пока основной код выполнится
    setTimeout(function() {
        markTableRows();
    }, 100);
});

// Маркировка строк на основе существующих классов
function markTableRows() {
    const rows = document.querySelectorAll('.statistics-table tbody tr');
    let warningCount = 0;
    let criticalCount = 0;
    
    rows.forEach(row => {
        // Проверяем наличие ячеек с отклонениями
        const warningCells = row.querySelectorAll('.metric-increase-warning, .metric-decrease-warning');
        const criticalCells = row.querySelectorAll('.metric-increase-critical, .metric-decrease-critical');
        
        if (criticalCells.length > 0) {
            row.setAttribute('data-row-type', 'critical');
            criticalCount++;
        } else if (warningCells.length > 0) {
            row.setAttribute('data-row-type', 'warning');
            warningCount++;
        } else {
            row.setAttribute('data-row-type', 'normal');
        }
    });
    
    console.log(`Rows marked: ${warningCount} warnings, ${criticalCount} critical`);
}

// Обновляем маркировку при изменении порогов
window.markTableRowsAfterThresholdChange = function() {
    markTableRows();
};