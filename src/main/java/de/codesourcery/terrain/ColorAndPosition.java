package de.codesourcery.terrain;

public final class ColorAndPosition
{
    public final int color;
    public final float position;

    ColorAndPosition(int color, float position)
    {
        if ( position < 0.0f || position > 1.0f )
        {
            throw new IllegalArgumentException( "position must be 0...1" );
        }
        this.color = color;
        this.position = position;
    }

    public float r()
    {
        return ((color >> 16) & 0xff) / 255.0f;
    }

    public float g()
    {
        return ((color >> 8) & 0xff) / 255.0f;
    }

    public float b()
    {
        return (color & 0xff) / 255.0f;
    }
}
