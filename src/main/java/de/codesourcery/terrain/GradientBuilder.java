package de.codesourcery.terrain;

import java.util.ArrayList;
import java.util.List;

public final class GradientBuilder
{
    private final List<ColorAndPosition> colors = new ArrayList<>();

    public GradientBuilder addColor(java.awt.Color color, float position)
    {
        return addColor( color.getRGB(), position );
    }

    public GradientBuilder addColor(int color, float position)
    {
        colors.add( new ColorAndPosition( color, position ) );
        return this;
    }

    public int[] buildGradient(int steps)
    {
        final int[] result = new int[steps];
        colors.sort( (a, b) -> Float.compare( a.position, b.position ) );
        ColorAndPosition previous = colors.get( 0 );
        for (int i = 1, colorsSize = colors.size(); i < colorsSize; i++)
        {
            ColorAndPosition current = colors.get( i );
            int startIdx = (int) (steps * previous.position);
            int endIdx = (int) (steps * current.position);
            float delta = endIdx - startIdx;
            float dr = (current.r() - previous.r()) / delta;
            float dg = (current.g() - previous.g()) / delta;
            float db = (current.b() - previous.b()) / delta;

            float r = previous.r();
            float g = previous.g();
            float b = previous.b();
            for (int j = startIdx; j < endIdx; j++)
            {
                result[j] = 0xff000000 |
                        ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
                r = Math.max( 0, Math.min( r + dr, 255 ) );
                g = Math.max( 0, Math.min( g + dg, 255 ) );
                b = Math.max( 0, Math.min( b + db, 255 ) );
            }
            previous = current;
        }
        return result;
    }

}
