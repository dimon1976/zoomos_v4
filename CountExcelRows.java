import org.apache.poi.ss.usermodel.*;
import java.io.File;
import java.io.FileInputStream;

public class CountExcelRows {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java CountExcelRows <file.xlsx>");
            return;
        }

        File file = new File(args[0]);
        if (!file.exists()) {
            System.out.println("File not found: " + args[0]);
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum() + 1;  // +1 because getLastRowNum is 0-based

            System.out.println("File: " + args[0]);
            System.out.println("Total rows (including header): " + rowCount);
            System.out.println("Data rows (excluding header): " + (rowCount - 1));
        }
    }
}