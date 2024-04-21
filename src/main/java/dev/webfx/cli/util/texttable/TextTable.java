package dev.webfx.cli.util.texttable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bruno Salmon
 */
public final class TextTable {

    private static final String NEW_ROW = "NEW_ROW";
    private static final String ROW_SEPARATOR = "ROW_SEPARATOR";

    private final List<String> cells = new ArrayList<>();
    private final List<Integer> colWidths = new ArrayList<>(); // The column widths (each being the max cell size in that column)
    //private int rowIndex; // the index of the row we are filling
    private int colIndex = -1; // the index of the last column filled on the current row

    public TextTable addCell(String cell) {
        cells.add(cell);
        int cellWidth = cell.length();
        colIndex++;
        if (colIndex == colWidths.size())
            colWidths.add(cellWidth);
        else if (cellWidth > colWidths.get(colIndex))
            colWidths.set(colIndex, cellWidth);
        return this;
    }

    public TextTable addCell(Object cell) {
        return addCell(cell.toString());
    }

    public TextTable newRow() {
        cells.add(NEW_ROW);
        //rowIndex++;
        colIndex = -1;
        return this;
    }

    public TextTable addRowSeparator() {
        newRow();
        cells.add(ROW_SEPARATOR);
        return newRow();
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        int colIndex = 0;
        // Top border
        formatRowSeparator(sb, true, false);
        sb.append('\n');
        for (String cell : cells) {
            if (cell == NEW_ROW) {
                sb.append('\n');
                colIndex = 0;
            } else if (cell == ROW_SEPARATOR) {
                formatRowSeparator(sb, false, false);
                colIndex = colWidths.size();
            } else {
                boolean leftAlignment = colIndex == 0;
                int colWidth = colWidths.get(colIndex++);
                String spaces = " ".repeat(colWidth - cell.length());
                if (leftAlignment)
                    cell += spaces; // left alignment
                else
                    cell = spaces + cell; // right alignment
                sb.append("┃ ").append(cell).append(" ");
                if (colIndex == colWidths.size()) {
                    sb.append("┃"); // right border
                }
            }
        }
        // Bottom border
        sb.append('\n');
        formatRowSeparator(sb, false, true);
        return sb.toString();
    }

    private void formatRowSeparator(StringBuilder sb, boolean top, boolean bottom) {
        char left   = top ? '┏' : bottom ? '┗' : '┣';
        char middle = top ? '┳' : bottom ? '┻' : '╋';
        char right  = top ? '┓' : bottom ? '┛' : '┫';
        boolean[] first = {true};
        colWidths.forEach(width -> {
            sb.append(first[0] ? left : middle).append("━".repeat(width + 2));
            first[0] = false;
        });
        sb.append(right);
    }

}
