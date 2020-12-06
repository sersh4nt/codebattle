package com.codenjoy.dojo.tetris.client.ai;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2016 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import com.codenjoy.dojo.client.WebSocketRunner;
import com.codenjoy.dojo.services.Dice;
import com.codenjoy.dojo.services.Direction;
import com.codenjoy.dojo.client.AbstractJsonSolver;
import com.codenjoy.dojo.services.Point;
import com.codenjoy.dojo.services.RandomDice;
import com.codenjoy.dojo.tetris.client.Board;
import com.codenjoy.dojo.tetris.model.*;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.IntStream;

import static com.codenjoy.dojo.services.PointImpl.pt;

public class AISolver extends AbstractJsonSolver<Board> {

    private static final double LANDING_HEIGHT_FACTOR = -12.63;
    private static final double ERODED_CELLS_FACTOR = 6.60;
    private static final double ROW_TRANSITIONS_FACTOR = -9.22;
    private static final double COL_TRANSITIONS_FACTOR = -19.77;
    private static final double HOLES_FACTOR = -13.08;
    private static final double WELLS_FACTOR = -10.49;
    private static final double HOLES_DEPTH_FACTOR = -1.61;
    private static final double ROWS_WITH_HOLES_FACTOR = -24.04;
    private Dice dice;
    private int size;

    public AISolver(Dice dice) {
        this.dice = dice;
    }

    @Override
    public String getAnswer(Board board) {
        String glassString = board.getGlass().getLayersString().get(0);
        size = board.getGlass().size();
        Glass glass = new GlassImpl(size, size, () -> 0);
        int currentLevel = board.getCurrentLevel();

        Elements current = board.getCurrentFigureType();
        if (current == null) {
            return "";
        }
        Figure figure = Type.getByIndex(current.index()).create();

        Point point = board.getCurrentFigurePoint();

        Level level = new LevelImpl(glassString);
        List<Plot> plots = level.plots();

        removeCurrentFigure(glass, figure, point, plots);

        Tetris.setPlots(glass, plots);

        if(currentLevel == 1) {
            List<Combination> combos = getPointToDrop(size, glass, figure, false);

            Combination combo = findBest(combos);
            return getCombo(combo, point);
        } else {
            if(board.getCurrentFigureType() == Elements.BLUE && glass.getHasFourLines()) {
                Combination combo = new Combination(0, pt(17, 2));
                return getCombo(combo, point);
            } else {
                List<Combination> combos = getPointToDrop(size, glass, figure, true);
                Combination combo = findBest(combos);
                return getCombo(combo, point);
            }
        }
    }

    private String getCombo(Combination combo, Point point) {
        if (combo == null) {
            System.out.println(); // не должно случиться
        }

        assert combo != null;
        Point to = combo.getPoint();

        int dx = to.getX() - point.getX();
        Direction direction;
        if (dx > 0) {
            direction = Direction.RIGHT;
        } else if (dx < 0) {
            direction = Direction.LEFT;
        } else {
            direction = null;
        }

        final String[] result = {""};
        if (direction != null) {
            IntStream.rangeClosed(1, Math.abs(dx))
                    .forEach(i -> result[0] += (((result[0].length() > 0) ? "," : "") + direction.toString()));
        }
        String rotate = (combo.getRotate() == 0) ? "" : String.format("ACT(%s)", combo.getRotate());
        String comma = (StringUtils.isEmpty(rotate) || StringUtils.isEmpty(result[0])) ? "" : ",";
        String part = rotate + comma + result[0];
        String comma2 = (StringUtils.isEmpty(part)) ? "" : ",";
        return part + comma2 + "DOWN";
    }

    private Combination findBest(List<Combination> combos) {
        return Collections.max(combos, compareCombos());
    }

    private Comparator<? super Combination> compareCombos() {
        return (o1, o2) -> Double.compare(getPenalty(o1), getPenalty(o2));
    }

    private double getPenalty(Combination o1) {
        return o1.getLandingHeight() * LANDING_HEIGHT_FACTOR +
                o1.getErodedCells() * ERODED_CELLS_FACTOR +
                o1.getRowTransitions() * ROW_TRANSITIONS_FACTOR +
                o1.getColTransitions() * COL_TRANSITIONS_FACTOR +
                o1.getHoles() * HOLES_FACTOR +
                o1.getWells() * WELLS_FACTOR +
                o1.getHoleDepth() * HOLES_DEPTH_FACTOR +
                o1.getRowsWithHoles() * ROWS_WITH_HOLES_FACTOR;
    }

    private void removeCurrentFigure(Glass glass, Figure figure, Point point, List<Plot> plots) {
        glass.figureAt(figure, point.getX(), point.getY());
        List<Plot> toRemove = glass.currentFigure();
        plots.removeAll(toRemove);
        glass.figureAt(null, 0, 0);
    }

    static class Combination {
        private Point point;
        private int rotate;
        private int landingHeight = 0;
        private int erodedCells = 0;
        private int rowTransitions = 0;
        private int colTransitions = 0;
        private int holes = 0;
        private int wells = 0;
        private int holeDepth = 0;
        private int rowsWithHoles = 0;

        public Combination(int rotate, Point point) {
            this.rotate = rotate;
            this.point = point;
        }

        public void setScore(int landingHeight,
                             int erodedCells,
                             int rowTransitions,
                             int colTransitions,
                             int holes,
                             int wells,
                             int holeDepth,
                             int rowsWithHoles) {
            this.landingHeight = landingHeight;
            this.erodedCells = erodedCells;
            this.rowTransitions = rowTransitions;
            this.colTransitions = colTransitions;
            this.holes = holes;
            this.wells = wells;
            this.holeDepth = holeDepth;
            this.rowsWithHoles = rowsWithHoles;
        }

        public Point getPoint() { return point; }

        public int getRotate() { return rotate; }

        public int getLandingHeight() { return landingHeight; }

        public int getErodedCells() { return erodedCells; }

        public int getRowTransitions() { return rowTransitions; }

        public int getColTransitions() { return colTransitions; }

        public int getHoles() { return holes; }

        public int getWells() { return wells; }

        public int getHoleDepth() { return holeDepth; }

        public int getRowsWithHoles() { return rowsWithHoles; }
    }

    private List<Combination> getPointToDrop(int size, Glass glass, Figure figure, boolean lastCol) {
        List<Combination> result = new LinkedList<>();
        for (int r = 0; r <= 3; r++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (glass.accept(figure, x, y, lastCol)) {
                        Glass clone = glass.clone();
                        clone.drop(figure, x, y);
                        Combination combo = new Combination(r, pt(x, y));
                        calculateScore(clone, combo);
                        result.add(combo);
                    }
                }
            }
            figure.rotate(1);
        }
        return result;
    }

    private void calculateScore(Glass glass, Combination combo) {
        List<Plot> dropped = glass.dropped();
        boolean[][] occupied = new boolean[size][size];
        for (int x = 0; x < size; x++) {
            occupied[x] = new boolean[size];
            for (int y = 0; y < size; y++) {
                occupied[x][y] = false;
            }
        }
        dropped.forEach(point -> occupied[point.getX()][point.getY()] = true);
        int landingHeight = glass.getLandingHeight();
        int erodedCells = glass.getErodedCells();
        int rowTransitions = 0;
        int colTransitions = 0;
        int holes = 0;
        int wells = 0;
        int holeDepth = 0;
        int rowsWithHoles = 0;

        int[] colHeight = new int[size];
        boolean[] colHasHole = new boolean[size];

        //finding wells
        for(int col = 1; col < size - 1; ++col) {
            boolean start = false;
            int startHeight = 0;
            for(int y = size - 1; y >=0; --y) {
                if(occupied[col-1][y] && !occupied[col][y] && occupied[col + 1][y]) {
                    if(!start) {
                        start = true;
                        startHeight = y;
                    }
                }
                else if (start) {
                    start = false;
                    int depth = startHeight - y;
                    wells += depth * (depth + 1) / 2;
                }
            }
        }

        //finding holes and holes depth
        for(int col = 0; col < size; ++col) {
            boolean start = false;
            for(int y = size - 1; y >= 0; --y) {
                if(occupied[col][y] && !start) {
                    start = true;
                    colHeight[col] = y;
                }
                else if (!occupied[col][y] && y < colHeight[col]) {
                    holeDepth += colHeight[col] - y;
                    holes++;
                    colHasHole[col] = true;
                }
            }
        }

        //finding row transitions
        for(int row = 0; row < size; ++row) {
            for(int x = 1; x < size; ++x) {
                if(occupied[x - 1][row] != occupied[x][row])
                    rowTransitions++;
            }
        }
        //finding col transitions
        for(int col = 0; col < size; ++col) {
            if (colHasHole[col])
                rowsWithHoles++;
            for(int y = 1; y < size; ++y) {
                if(occupied[col][y - 1] != occupied[col][y])
                    colTransitions++;
            }
        }

        combo.setScore(landingHeight,
                erodedCells,
                rowTransitions,
                colTransitions,
                holes,
                wells,
                holeDepth,
                rowsWithHoles
        );
    }

    public static void main(String[] args) {
        WebSocketRunner.runClient(
                "http://codebattle2020.westeurope.cloudapp.azure.com/codenjoy-contest/board/player/eryb8br89cdnbsnxc6r9?code=2029116705810868315",
                new AISolver(new RandomDice()),
                new Board());
    }
}