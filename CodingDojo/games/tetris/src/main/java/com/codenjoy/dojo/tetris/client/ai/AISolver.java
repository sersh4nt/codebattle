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

    private static final double LINES_FACTOR = 0.4106816860123958;
    private static final double HEIGHEST_COL_FACTOR = -0.08679520494876472;
    private static final double SUM_HEIGHT_FACTOR = -0.6152727732730796;
    private static final double RELATIVE_HEIGHT_FACTOR = 0.028340097577794654;
    private static final double HOLES_FACTOR = -0.17437734216356443;
    private static final double BUMPINESS_FACTOR = -0.021586109522043928;
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

        List<Combination> combos = getPointToDrop(size, glass, figure);

        Combination combo = findBest(combos);

        if (combo == null) {
            System.out.println(); // не должно случиться
        }

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
        return (o1, o2) -> Double.compare(o1.getScore(), o2.getScore());
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
        private int lines;
        private int maxHeight;
        private int sumHeight;
        private int relHeight;
        private int holes;
        private int bumpiness;

        private double score;

        public Combination(int rotate, Point point) {
            this.rotate = rotate;
            this.point = point;
        }

        public void setScore(int lines, int maxHeight, int sumHeight, int relHeight, int holes, int bumpiness) {
            this.lines = lines;
            this.maxHeight = maxHeight;
            this.sumHeight = sumHeight;
            this.relHeight = relHeight;
            this.holes = holes;
            this.bumpiness = bumpiness;

            this.score = lines * LINES_FACTOR + 
                maxHeight * HEIGHEST_COL_FACTOR +
                sumHeight * SUM_HEIGHT_FACTOR +
                relHeight * RELATIVE_HEIGHT_FACTOR +
                holes * HOLES_FACTOR +
                bumpiness * BUMPINESS_FACTOR;
        }

        public Point getPoint() { return point; }

        public int getRotate() { return rotate; }

        public double getScore() { return score; }

        public int getRelHeight() { return relHeight; }

        public int getBumpiness() { return bumpiness; }

        public int getHoles() { return holes; }

        public int getLines() { return lines; }

        public int getMaxHeight() { return maxHeight; }

        public int getSumHeight() { return sumHeight; }
    }

    private List<Combination> getPointToDrop(int size, Glass glass, Figure figure) {
        List<Combination> result = new LinkedList<>();
        for (int r = 0; r <= 2; r++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (glass.accept(figure, x, y)) {
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
        int holes = 0;
        int sumHeight = 0;
        int relHeight;
        int maxHeight;
        int lines = glass.getRemovedLines();
        int bumpiness = 0;

        int[] colHeight = new int[size];
        for(int col = 0; col < size; ++col) {
            boolean start = false;
            for(int y = size - 1; y >= 0; --y) {
                if (occupied[col][y] && !start) {
                    colHeight[col] = y + 1;
                    start = true;
                }
                if(!occupied[col][y] && y < colHeight[col])
                    holes++;
            }
            sumHeight += colHeight[col];
            if(col > 0)
                bumpiness += Math.abs(colHeight[col] - colHeight[col - 1]);
        }
        maxHeight = Arrays.stream(colHeight).max().getAsInt();
        relHeight = maxHeight - Arrays.stream(colHeight).min().getAsInt();

        combo.setScore(lines, maxHeight, sumHeight, relHeight, holes, bumpiness);
    }

    public static void main(String[] args) {
        WebSocketRunner.runClient(
                "http://codebattle2020.westeurope.cloudapp.azure.com/codenjoy-contest/board/player/n9ep28lbmznwqjeb4laf?code=8130214853929400052",
                new AISolver(new RandomDice()),
                new Board());
    }
}