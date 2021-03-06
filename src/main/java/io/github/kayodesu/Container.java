package io.github.kayodesu;

import io.github.kayodesu.block.Block;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * @author Yo Ka
 */
public class Container extends Canvas {

    private GraphicsContext gc;

    // 画布中每个小方块的状态
    public enum CellStat {
        EMPTY,     // 空
        MOVING,    // 正在移动的痕迹
        SOLIDIFY   // 已经固定存在的小方块
    }

    public int cellSideLen;
    public int gapBetweenCells;

    public static class Cell {
        public CellStat stat = CellStat.EMPTY;
        public Color color = Color.BLACK;
    }

    // 画布状态表示, 零点在左上角
    private Cell[][] cellMatrix;
    private int columnsCount, rowsCount;

    public int getColumnsCount() {
        return columnsCount;
    }

    public int getRowsCount() {
        return rowsCount;
    }

    public Container(int width, int height, int cellSideLen, int gapBetweenCells, int columnsCount, int rowsCount) {
        super(width, height);

        this.cellSideLen = cellSideLen;
        this.gapBetweenCells = gapBetweenCells;
        this.columnsCount = columnsCount;
        this.rowsCount = rowsCount;
        cellMatrix = new Cell[columnsCount][rowsCount];
        gc = getGraphicsContext2D();

        for(int x = 0; x < columnsCount; x++)
            for(int y = 0; y < rowsCount; y++)
                cellMatrix[x][y] = new Cell();
    }

    public Cell[][] getCellMatrix() {
        return cellMatrix;
    }

    private boolean full = false;

    private Block danglingBlock;
    public int blockLeft, blockTop; // danglingBlock 的坐标

    public ConflictType setDanglingBlock(int left, int top, Block block) {
        ConflictType type = testBoundAndConflict(left, top, block);
        if (type == ConflictType.NONE_CONFLICT) {
            this.blockLeft = left;
            this.blockTop = top;
            danglingBlock = block;
        }
        return type;
    }

    public Block getDanglingBlock() {
        return danglingBlock;
    }

    public boolean isFull() {
        return full;
    }

    public boolean moveLeft() {
        assert danglingBlock != null;

        ConflictType type = testBoundAndConflict(blockLeft - 1, blockTop, danglingBlock);
        if (type == ConflictType.NONE_CONFLICT) {
            blockLeft--;
            draw();
            return true;
        }
        return false;
    }

    public boolean moveRight() {
        assert danglingBlock != null;

        ConflictType type = testBoundAndConflict(blockLeft + 1, blockTop, danglingBlock);
        if (type == ConflictType.NONE_CONFLICT) {
            blockLeft++;
            draw();
            return true;
        }
        return false;
    }

    public boolean transform() {
        assert danglingBlock != null;

        danglingBlock.switchToNextStat();
        ConflictType type = testBoundAndConflict(blockLeft, blockTop, danglingBlock);
        if (type == ConflictType.NONE_CONFLICT) {
            draw();
            return true;
        }
        danglingBlock.switchToPrevStat();
        return false;
    }

    public boolean moveDown() {
        assert danglingBlock != null;

        ConflictType type = testBoundAndConflict(blockLeft, blockTop + 1, danglingBlock);
        if (type == ConflictType.NONE_CONFLICT) {
            blockTop++;
            draw();
            return true;
        }
        return false;
    }

    public boolean tryMoveDown() {
        assert danglingBlock != null;

        ConflictType type = testBoundAndConflict(blockLeft, blockTop + 1, danglingBlock);
        if (type == ConflictType.NONE_CONFLICT) {
            blockTop++;
            return true;
        }
        return false;
    }

    /**
     * 消除满行。
     * 先将panel的所有未满行复制到一个临时的数组中，
     * 在将此临时数组复制的panel中
     * @return 移除的行数
     */
    private int removeFullLines() {
        int notFullLineCount = 0;

        CellStat[][] tmp = new CellStat[columnsCount][rowsCount];
        for (int x = 0; x < columnsCount; x++)
            for (int y = 0; y < rowsCount; y++)
                tmp[x][y] = CellStat.EMPTY;

        int j = rowsCount - 1;

        for (int y = rowsCount - 1; y >= 0; y--) {
            for (int x = 0; x < columnsCount; x++) {
                if (cellMatrix[x][y].stat == CellStat.EMPTY) {
                    // 发现一未满行，将此行复制的tmp数组的对应位置
                    for (int i = 0, t = 0; i < columnsCount; i++, t++) {
                        tmp[i][j] = cellMatrix[t][y].stat;
                    }
                    j--;
                    notFullLineCount++;
                    break;
                }
            }
        }

        for (int x = 0; x < columnsCount; x++) {
            for (int y = 0; y < rowsCount; y++) {
                cellMatrix[x][y].stat = tmp[x][y];
            }
        }

        return rowsCount - notFullLineCount;
    }

    public int merger() {
        assert danglingBlock != null;
        assert testBoundAndConflict(blockLeft, blockTop, danglingBlock) == ConflictType.NONE_CONFLICT;

        for(int x = 0; x < Block.SIDE_LEN; x++) {
            for(int y = 0; y < Block.SIDE_LEN; y++) {
                if(danglingBlock.getData()[x][y]) {
                    // 上方屏幕外图形的不合并
                    if (blockTop + y < 0) {
                        full = true;
                    } else {
                        cellMatrix[blockLeft + x][blockTop + y].stat = CellStat.SOLIDIFY;
                        cellMatrix[blockLeft + x][blockTop + y].color = danglingBlock.color;
                    }
                }
            }
        }

        int removedLinesCount = removeFullLines();
        if (removedLinesCount > 0 && blockTop < 0) {
            blockTop += removedLinesCount; // 下移 removedLinesCount 行
            if (blockTop > 0) {
                full = false;

                for(int x = 0; x < Block.SIDE_LEN; x++)
                    for(int y = 0; y < removedLinesCount; y++)
                        if(danglingBlock.getData()[x][y]) {
                            cellMatrix[blockLeft + x][blockTop + y].stat = CellStat.SOLIDIFY;
                            cellMatrix[blockLeft + x][blockTop + y].color = danglingBlock.color;
                        }
            }
        }

        danglingBlock = null;
        draw();
        return removedLinesCount;
    }

    public void pasteDanglingBlock() {
        assert danglingBlock != null;
        assert testBoundAndConflict(blockLeft, blockTop, danglingBlock) == ConflictType.NONE_CONFLICT;

        for(int x = 0; x < Block.SIDE_LEN; x++) {
            for(int y = 0; y < Block.SIDE_LEN; y++) {
                // 上方屏幕外图形的不合并
                if(danglingBlock.getData()[x][y] && blockTop + y >= 0)
                    cellMatrix[blockLeft + x][blockTop + y].stat = CellStat.MOVING;
            }
        }
    }

    public void unPasteDanglingBlock() {
        for(int x = 0; x < columnsCount; x++) {
            for(int y = 0; y < rowsCount; y++) {
                if(cellMatrix[x][y].stat == CellStat.MOVING)
                    cellMatrix[x][y].stat = CellStat.EMPTY;
            }
        }
    }

    public enum ConflictType {
        NONE_CONFLICT,  // 无冲突
        CONFLICT,       // 冲突
        OUT_OF_LEFT_BOUND,  // 左越界
        OUT_OF_RIGHT_BOUND,
        OUT_OF_BOTTOM_BOUND,
    }

    /**
     * 检测是否冲突
     * @param left 面板中的x坐标
     * @param top  面板中的y坐标
     * @param block 需要检测的小方块
     * @return 是否冲突
     */
    public ConflictType testBoundAndConflict(int left, int top, Block block) {
        boolean[][] data = block.getData();

        for (int x = 0; x < Block.SIDE_LEN; x++) {
            for (int y = 0; y < Block.SIDE_LEN; y++) {
                if (data[x][y]) {
                    int i = left + x;
                    int j = top + y;

                    if(i < 0)
                        return ConflictType.OUT_OF_LEFT_BOUND;
                    if(i >= columnsCount)
                        return ConflictType.OUT_OF_RIGHT_BOUND;
                    if(j >= rowsCount)
                        return ConflictType.OUT_OF_BOTTOM_BOUND;

                    // 小方块从顶部刚出来时是可以显示不全的，所以（j<0）不算越界
                    if(j >= 0 && cellMatrix[i][j].stat != CellStat.EMPTY) {
                        return ConflictType.CONFLICT;
                    }
                }
            }
        }

        return ConflictType.NONE_CONFLICT;
    }

    public void draw() {
        // 将更新界面的工作交给 FX application thread 执行
        Platform.runLater(() -> {
            gc.setFill(Color.BLACK);

            for (int x = 0; x < columnsCount; x++) {
                for (int y = 0; y < rowsCount; y++) {
                    double x0 = x * cellSideLen + x * gapBetweenCells;
                    double y0 = y * cellSideLen + y * gapBetweenCells;

                    if ((danglingBlock != null)
                            && (blockLeft <= x) && (x < blockLeft + Block.SIDE_LEN)
                            && (blockTop <= y) && (y < blockTop + Block.SIDE_LEN)
                            && (danglingBlock.getData()[x - blockLeft][y - blockTop])) {
                        gc.setFill(danglingBlock.color);
                    } else {
                        if (cellMatrix[x][y].stat == CellStat.EMPTY) {
                            gc.clearRect(x0, y0, cellSideLen, cellSideLen);
                            continue;
                        } else if (cellMatrix[x][y].stat == CellStat.SOLIDIFY) {
                            gc.setFill(cellMatrix[x][y].color);
                        }
                    }

                    gc.fillRect(x0, y0, cellSideLen, cellSideLen);
                }
            }
        });
    }

    /**
     * 设置面板显示"over"
     */
    public void overPattern() {
        for (int y = rowsCount - 1; y >= 0; y--) {
            for (int x = 0; x < columnsCount; x++) {
                cellMatrix[x][y].stat = CellStat.EMPTY;
            }
        }

        // "O"
        cellMatrix[1][3].stat = CellStat.SOLIDIFY;
        cellMatrix[1][4].stat = CellStat.SOLIDIFY;
        cellMatrix[1][5].stat = CellStat.SOLIDIFY;
        cellMatrix[1][6].stat = CellStat.SOLIDIFY;
        cellMatrix[1][7].stat = CellStat.SOLIDIFY;

        cellMatrix[2][3].stat = CellStat.SOLIDIFY;
        cellMatrix[2][7].stat = CellStat.SOLIDIFY;

        cellMatrix[3][3].stat = CellStat.SOLIDIFY;
        cellMatrix[3][4].stat = CellStat.SOLIDIFY;
        cellMatrix[3][5].stat = CellStat.SOLIDIFY;
        cellMatrix[3][6].stat = CellStat.SOLIDIFY;
        cellMatrix[3][7].stat = CellStat.SOLIDIFY;

        // "V"
        cellMatrix[5][3].stat = CellStat.SOLIDIFY;
        cellMatrix[5][4].stat = CellStat.SOLIDIFY;
        cellMatrix[5][5].stat = CellStat.SOLIDIFY;
        cellMatrix[5][6].stat = CellStat.SOLIDIFY;

        cellMatrix[6][7].stat = CellStat.SOLIDIFY;

        cellMatrix[7][3].stat = CellStat.SOLIDIFY;
        cellMatrix[7][4].stat = CellStat.SOLIDIFY;
        cellMatrix[7][5].stat = CellStat.SOLIDIFY;
        cellMatrix[7][6].stat = CellStat.SOLIDIFY;

        // "E"
        cellMatrix[1][9].stat = CellStat.SOLIDIFY;
        cellMatrix[1][10].stat = CellStat.SOLIDIFY;
        cellMatrix[1][11].stat = CellStat.SOLIDIFY;
        cellMatrix[1][12].stat = CellStat.SOLIDIFY;
        cellMatrix[1][13].stat = CellStat.SOLIDIFY;

        cellMatrix[2][9].stat = CellStat.SOLIDIFY;
        cellMatrix[3][9].stat = CellStat.SOLIDIFY;

        cellMatrix[2][11].stat = CellStat.SOLIDIFY;
        cellMatrix[3][11].stat = CellStat.SOLIDIFY;

        cellMatrix[2][13].stat = CellStat.SOLIDIFY;
        cellMatrix[3][13].stat = CellStat.SOLIDIFY;

        // "R"
        cellMatrix[5][9].stat = CellStat.SOLIDIFY;
        cellMatrix[5][10].stat = CellStat.SOLIDIFY;
        cellMatrix[5][11].stat = CellStat.SOLIDIFY;
        cellMatrix[5][12].stat = CellStat.SOLIDIFY;
        cellMatrix[5][13].stat = CellStat.SOLIDIFY;

        cellMatrix[6][9].stat = CellStat.SOLIDIFY;
        cellMatrix[7][9].stat = CellStat.SOLIDIFY;

        cellMatrix[7][10].stat = CellStat.SOLIDIFY;
        cellMatrix[7][11].stat = CellStat.SOLIDIFY;

        cellMatrix[6][11].stat = CellStat.SOLIDIFY;

        cellMatrix[6][12].stat = CellStat.SOLIDIFY;
        cellMatrix[7][13].stat = CellStat.SOLIDIFY;

        draw();
    }

}
