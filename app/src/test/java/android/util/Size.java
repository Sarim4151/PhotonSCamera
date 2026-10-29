package android.util;

/** JVM test implementation of Android's immutable dimensions; no device API calls. */
public final class Size {
    private final int width;
    private final int height;

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof Size)) return false;
        Size size = (Size) other;
        return width == size.width && height == size.height;
    }

    @Override public int hashCode() { return height ^ Integer.rotateLeft(width, 16); }
    @Override public String toString() { return width + "x" + height; }
}
