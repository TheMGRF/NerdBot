package net.hypixel.nerdbot.generator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MinecraftHead {
    private final BufferedImage image, skin;
    private final Graphics2D g2d;

    /***
     * Creates a MinecraftHead renderer
     * @param targetSkin the skin which is meant to be created
     */
    public MinecraftHead(BufferedImage targetSkin) {
        int width = (int) Math.round(17 * HeadTransforms.xDistanceHat);
        int height = (int) Math.round(17 * HeadTransforms.squareHatDistance);

        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.g2d = this.image.createGraphics();
        this.skin = targetSkin;
    }

    /***
     * Paints a single square on the image given the lower left point
     * @param x starting x location to draw
     * @param y starting y location to draw
     * @param side the direction the square is facing
     */
    private void paintSquare(double x, double y, Side side) {
        int[] pointsX = new int[4];
        int[] pointsY = new int[4];
        double[] transforms = side.getTransforms();

        for (int i = 0; i < 4; i++) {
            pointsX[i] = (int) Math.round(x + transforms[i * 2]);
            pointsY[i] = (int) Math.round(y + transforms[i * 2 + 1]);
        }

        g2d.fillPolygon(pointsX, pointsY, 4);
    }

    /***
     * Draws an entire face of the skin onto the image
     * @param startingX starting x location to draw (the center vertex of the cube)
     * @param startingY starting y location to draw (the center vertex of the cube)
     * @param side the direction the square is facing
     * @param face which part of the head (top, bottom, left, right, etc)
     */
    private void drawFace(double startingX, double startingY, Side side, Face face) {
        int pixelTrackUp, newLineDisplacement;
        double squareDistance = side.getDistance();
        double xDistance = side.getXDistance();
        double yDistance = side.getYDistance();

        // applys transforms to the starting X/Y position to ensure it is in the correct place
        switch (side) {
            case RIGHT_SIDE, RIGHT_HAT_SIDE -> {
                pixelTrackUp = -1;
                newLineDisplacement = 0;
                startingY += squareDistance * 7;
            }
            case LEFT_SIDE, LEFT_HAT_SIDE -> {
                pixelTrackUp = 1;
                newLineDisplacement = 0;
                startingX -= xDistance * 8;
                startingY += yDistance * 6;
            }
            case TOP_SIDE, TOP_HAT_SIDE -> {
                pixelTrackUp = 1;
                newLineDisplacement = 1;
                startingX -= xDistance * 7;
                startingY -= yDistance + squareDistance * 3.99;
            }
            default -> {
                pixelTrackUp = 0;
                newLineDisplacement = 0;
            }
        }

        double defaultX = startingX;
        double defaultY = startingY;

        for (int y = face.getStartY() + 7; y >= face.getStartY(); y--) {
            for (int x = face.getStartX(); x < face.getStartX() + 8; x++) {
                g2d.setColor(new Color(skin.getRGB(x, y), true));
                paintSquare(startingX, startingY, side);
                startingX += xDistance;
                startingY += yDistance * pixelTrackUp;
            }

            defaultX += newLineDisplacement * xDistance;
            startingX = defaultX;
            defaultY -= (squareDistance - (newLineDisplacement * yDistance)) ;
            startingY = defaultY;
        }
    }

    /***
     * Renders the Minecraft Head
     * @return a rendered Minecraft Head
     */
    public MinecraftHead drawHead() {
        int startingX = this.image.getWidth() / 2;
        int startingY = this.image.getHeight() / 2;

        drawFace(startingX, startingY + HeadTransforms.squareHatDistance * 8, Side.TOP_HAT_SIDE, Face.HAT_BOTTOM);
        drawFace(startingX - HeadTransforms.xDistanceHat * 8, startingY - HeadTransforms.yDistanceHat * 8, Side.RIGHT_HAT_SIDE, Face.HAT_LEFT);
        drawFace(startingX + HeadTransforms.xDistanceHat * 8, startingY - HeadTransforms.yDistanceHat * 8, Side.LEFT_HAT_SIDE, Face.HAT_BACK);

        drawFace(startingX, startingY, Side.TOP_SIDE, Face.HEAD_TOP);
        drawFace(startingX, startingY, Side.RIGHT_SIDE,  Face.HEAD_RIGHT);
        drawFace(startingX, startingY, Side.LEFT_SIDE, Face.HEAD_FRONT);

        drawFace(startingX, startingY, Side.TOP_HAT_SIDE, Face.HAT_TOP);
        drawFace(startingX, startingY, Side.RIGHT_HAT_SIDE, Face.HAT_RIGHT);
        drawFace(startingX, startingY, Side.LEFT_HAT_SIDE, Face.HAT_FRONT);

        return this;
    }

    /***
     * Gets the generated image
     * @return the buffered image containing the head
     */
    public BufferedImage getImage() {
        return this.image;
    }

    /***
     * Saves the image to a file
     * @return a file which can be shared
     * @throws IOException If the file cannot be saved
     */
    public File toFile() throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(this.getImage(), "PNG", tempFile);
        return tempFile;
    }
}

/***
 * Standard distances between points on the isometric grid.
 */
class HeadTransforms {
    public static int squareDistance = 15;
    public static double xDistance = squareDistance * Math.cos(Math.toRadians(30));
    public static double yDistance = squareDistance * Math.sin(Math.toRadians(30));
    public static double squareHatDistance = squareDistance * 1.05;
    public static double xDistanceHat = squareHatDistance * Math.cos(Math.toRadians(30));
    public static double yDistanceHat = squareHatDistance * Math.sin(Math.toRadians(30));
}

/***
 * Describes the 4 points of the square which fit the isometric pattern
 */
enum Side {
    LEFT_SIDE(HeadTransforms.squareDistance, new double[] {0, 0, 0, HeadTransforms.squareDistance, HeadTransforms.xDistance, HeadTransforms.squareDistance + HeadTransforms.yDistance, HeadTransforms.xDistance, HeadTransforms.yDistance}, HeadTransforms.xDistance, HeadTransforms.yDistance),
    RIGHT_SIDE(HeadTransforms.squareDistance, new double[] {0, 0, 0, HeadTransforms.squareDistance, HeadTransforms.xDistance, HeadTransforms.squareDistance - HeadTransforms.yDistance, HeadTransforms.xDistance, -HeadTransforms.yDistance}, HeadTransforms.xDistance, HeadTransforms.yDistance),
    TOP_SIDE(HeadTransforms.squareDistance, new double[] {0, 0, -HeadTransforms.xDistance, HeadTransforms.yDistance, 0, HeadTransforms.squareDistance, HeadTransforms.xDistance, HeadTransforms.yDistance}, HeadTransforms.xDistance, HeadTransforms.yDistance),
    LEFT_HAT_SIDE(HeadTransforms.squareHatDistance, new double[] {0, 0, 0, HeadTransforms.squareHatDistance, HeadTransforms.xDistanceHat, HeadTransforms.squareHatDistance + HeadTransforms.yDistanceHat, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat}, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat),
    RIGHT_HAT_SIDE(HeadTransforms.squareHatDistance, new double[] {0, 0, 0, HeadTransforms.squareHatDistance, HeadTransforms.xDistanceHat, HeadTransforms.squareHatDistance - HeadTransforms.yDistanceHat, HeadTransforms.xDistanceHat, -HeadTransforms.yDistanceHat}, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat),
    TOP_HAT_SIDE(HeadTransforms.squareHatDistance, new double[] {0, 0, -HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat, 0, HeadTransforms.squareHatDistance, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat}, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat);
    private final double distance;
    private final double[] transforms;
    private final double xDistance;
    private final double yDistance;

    Side(double distance, double[] transforms, double xDistance, double yDistance) {
        this.distance = distance;
        this.transforms = transforms;
        this.xDistance = xDistance;
        this.yDistance = yDistance;
    }

    public double getDistance() {
        return distance;
    }
    public double[] getTransforms() {
        return this.transforms;
    }
    public double getXDistance() {
        return this.xDistance;
    }
    public double getYDistance() {
        return this.yDistance;
    }
}

/***
 * The X/Y coordinates for where the head is located in the skin image
 */
enum Face {
    HEAD_FRONT(8, 8), HEAD_BACK(24, 8), HEAD_LEFT(0, 8), HEAD_RIGHT(16, 8), HEAD_TOP(8, 0), HEAD_BOTTOM(16, 0),
    HAT_FRONT(40, 8), HAT_BACK(56, 8), HAT_LEFT(32, 8), HAT_RIGHT(48, 8), HAT_TOP(40, 0), HAT_BOTTOM(48, 0);

    private final int startX, startY;

    Face(int startX, int startY) {
        this.startX = startX;
        this.startY = startY;
    }

    public int getStartX() {
        return this.startX;
    }

    public int getStartY() {
        return this.startY;
    }
}