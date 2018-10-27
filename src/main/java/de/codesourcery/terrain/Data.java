package de.codesourcery.terrain;

import java.util.Arrays;
import java.util.Random;

public class Data
{
    public final byte[] height;
    public final float[] water;
    public final byte[] sediment;
    public final int size;

    public Data(int size)
    {
        this.size = size;
        this.height = new byte[size*size];
        this.water = new float[size*size];
        this.sediment = new byte[size*size];
    }

    private final class RandomGen
    {
        public final Random rnd;
        private final int range;

        private RandomGen(long seed,int range)
        {
            this.rnd = new Random(seed);
            this.range = range;
        }

        private int rndValue()
        {
            return rndValue(1.0f);
        }

        private int rndValue(float scale)
        {
            final float value = rnd.nextFloat()*range;
            return (int) (scale*value);
        }
    }

    public void initWater(int minHeight)
    {
        for ( int i = 0 ; i < size*size; i++)
        {
            int h = height[i] & 0xff;
            water[i] = h > minHeight ? 255 : 0;
        }
        Arrays.fill(sediment,(byte) 0);
    }

    public void flow()
    {
        final int[] gradients = new int[9];

        final float percMovedPerTick = 0.1f; // 0..1

        final float[] waterCopy = new float[ size*size ];

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                final float w = water( x, y );
                if ( w == 0 )
                {
                    continue;
                }

                final int h = height(x,y);
                // ok, there's water inside the current square,
                // calculate gradients
                final int minx = x == 0 ? 1 : -1;
                final int maxx = x == size - 1 ? -1 : 1;
                final int miny = y == 0 ? 1 : -1;
                final int maxy = y == size - 1 ? -1 : 1;
                Arrays.fill( gradients,0 );
                int gradCount = 0;
                for (int gradx = minx; gradx <= maxx; gradx++)
                {
                    for (int grady = miny; grady <= maxy; grady++)
                    {
                        if ( gradx != 0 && grady != 0 )
                        {
                            int rx = x + gradx;
                            int ry = y + grady;
                            int h2 = height(rx,ry);
                            final int grad = h - h2;
                            if ( grad >= 0 )
                            {
                                // ok, downstream
                                gradients[ (1+grady)*3 + 1 + gradx] = grad;
                                gradCount++;
                            }
                        }
                    }
                }
                if ( gradCount > 0 ) {
                    // distribute water from current grid location
                    // according to gradients
                    final float fraction = percMovedPerTick * w / gradCount;
                    for ( int ix = -1 ; ix < 2 ; ix++)
                    {
                        for ( int iy = -1 ; iy < 2 ; iy++)
                        {
                            if ( ix !=0 && iy !=0 ) {
                                int grad = gradients[ (1+iy) * 3 + 1 + ix];
                                if ( grad > 0 )
                                {
                                    final int idx = (y+iy)*size + (x+ix);
                                    float oldValue = water[idx];
                                    float newValue = oldValue + fraction;
                                    waterCopy[idx] = newValue < 0 ? 0 : newValue;
                                }
                            }
                        }
                    }
                    float oldValue = (percMovedPerTick * water[y*size+x]);
                    float newValue = waterCopy[y*size+x] - oldValue;
                    waterCopy[ y*size + x ] = newValue < 0 ? 0 : newValue;
                } else {
                    waterCopy[ y*size + x ] = w;
                }
            }
        }
        System.arraycopy( waterCopy, 0 , water, 0 , size*size );
    }

    public void clearWater() {
        Arrays.fill(water,(byte)0);
        Arrays.fill(sediment,(byte)0);
    }

    public float water(int x,int y) {
        return this.water[y*size+x];
    }

    public Data initHeights(long seed,float startScale,int range,float scaleReduction,boolean normalize)
    {
        final RandomGen rnd = new RandomGen(seed,range);

        Arrays.fill(height,(byte) 0);

        final byte[] tmp = new byte[4];
        for ( int i = 0 ; i < tmp.length ; i++ ) {
            tmp[i] = (byte) rnd.rndValue();
        }
        setHeight(0,0, tmp[0] );
        setHeight(size-1,0, tmp[1] );
        setHeight(0,size-1, tmp[2] );
        setHeight(size-1,size-1, tmp[3] );

        int stepSize = size;
        float scale = startScale;

        int min = 255;
        int max = 0;

        while (stepSize >=2)
        {
            final int sm1 = (stepSize&1) != 0 ? stepSize-1 : stepSize;

            /* Diamond step.
             *
             * The diamond step takes a square, finds the midpoint, and sets the midpoint
             * to the average of the four corners plus a random value in some range.
             * Imagine drawing lines from the four points to the midpoint,
             * for every square in the array: you would create a diamond pattern (hence the name!).
             */
            for (int y = 0; y <  size ; y += stepSize)
            {
                for (int x = 0; x < size ; x += stepSize)
                {
                    final int topLeft = height( x, y );
                    final int topRight = height( x + sm1, y );
                    final int bottomLeft = height( x, y + sm1 );
                    final int bottomRight = height( x + sm1, y + sm1 );
                    final int centerValue = rnd.rndValue(scale) + ( topLeft + topRight + bottomLeft + bottomRight) / 4;
                    final int cx = x + stepSize/2;
                    final int cy = y + stepSize/2;
                    setHeight(cx, cy, centerValue);
                    max = Math.max( max , centerValue );
                    min = Math.min( min, centerValue );
                }
            }

            /*
             * Square step.
             * The square step takes a diamond, finds the midpoint, and pulls
             * in the average of the values of the points forming the corners of
             * the diamond (plus a random value).
             * Again, imagine drawing the lines from the corners to the midpoint:
             * you’d create a square pattern.
             */
            for (int y = 0; y < size ; y += stepSize)
            {
                for (int x = 0; x < size ; x += stepSize)
                {
                    final int topCenter = height( x+stepSize/2 , (y-stepSize/2) );
                    final int bottomCenter = height( x+stepSize/2 , (y+stepSize+stepSize/2) );
                    final int leftCenter = height( x-stepSize/2 , (y+stepSize/2) );
                    final int rightCenter = height( x+stepSize+stepSize/2 , (y+stepSize/2) );

                    final int topLeft = height( x, y );
                    final int topRight = height( x + sm1, y );
                    final int bottomLeft = height( x, y + sm1 );
                    final int bottomRight = height( x + sm1, y + sm1 );

                    final int cx = x + stepSize/2;
                    final int cy = y + stepSize/2;
                    final int center = height(cx,cy);

                    int newValue;
                    // top-center
                    newValue = rnd.rndValue( scale ) + (topLeft + topRight + topCenter + center)/4;
                    setHeight( cx, y, newValue );
                    min = Math.min(min,newValue); max = Math.max(max,newValue);

                    // bottom-center
                    newValue = rnd.rndValue( scale ) + (bottomLeft + bottomRight + bottomCenter + center) / 4;
                    setHeight( cx,y+sm1, newValue );
                    min = Math.min(min,newValue); max = Math.max(max,newValue);

                    // left-center
                    newValue = rnd.rndValue( scale ) + (topLeft + bottomLeft + leftCenter + center)/4;
                    setHeight( x, cy, newValue );
                    min = Math.min(min,newValue); max = Math.max(max,newValue);

                    // right-center
                    newValue = rnd.rndValue(scale) + (topRight+bottomRight+rightCenter + center)/4;
                    setHeight( x+sm1,cy,newValue );
                    min = Math.min(min,newValue); max = Math.max(max,newValue);
                }
            }

            // half step size
            scale = scale*scaleReduction;
            stepSize >>>= 1;
        }

        if ( normalize ) {
            float factor = 255f/(max-min);
            for ( int i = 0 ; i < size*size ; i++ ) {
                float newValue = (height[i]-min)*factor;
                height[i] = (byte) newValue;
            }
        }
        return this;
    }

    public int height(int x,int y) {
        try
        {
            int rx = x;
            while ( rx < 0 ) {
                rx += size;
            }
            int ry = y;
            while ( ry < 0 ) {
                ry += size;
            }
            return this.height[ (ry%size) * size + (rx%size)] & 0xff;
        }
        catch(ArrayIndexOutOfBoundsException e) {
            System.out.flush();
            System.err.flush();
            System.err.println("x: "+x+",y: "+y);
            System.err.flush();
            throw e;
        }
    }

    public void setHeight(int x,int y,int value)
    {
        int rx = x;
        while ( rx < 0 ) {
            rx += size;
        }
        int ry = y;
        while ( ry < 0 ) {
            ry += size;
        }
        this.height[ (ry%size)*size + (rx%size) ] = (byte) (value > 255 ? 255 : value < 0 ? 0 : value );
    }
}
