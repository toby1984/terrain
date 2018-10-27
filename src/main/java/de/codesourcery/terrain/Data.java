package de.codesourcery.terrain;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

public class Data
{
    public static final float EPSILON = 0.0001f;
    public final byte[] height;
    public final float[] water;
    public final int size;

    public void save(OutputStream out) throws IOException {
        writeInt(size,out);
        writeArray( height,out );
        writeArray( water,out );
    }

    public static Data read(InputStream in) throws IOException {
        final int size = readInt(in);
        final Data result = new Data(size);
        byte[] byteArray = readByteArray(in);
        System.arraycopy( byteArray,0,result.height,0,size*size );

        float[] floatArray = readFloatArray(in);
        System.arraycopy( floatArray,0,result.water,0,size*size );

        return result;
    }

    public Data(int size)
    {
        this.size = size;
        this.height = new byte[size*size];
        this.water = new float[size*size];
    }

    public void clear() {
        Arrays.fill( height,(byte) 0);
        Arrays.fill( water,0);
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

    public void initWater(int minHeight,float amount)
    {
        for ( int i = 0 ; i < size*size; i++)
        {
            int h = height[i] & 0xff;
            water[i] = h > minHeight ? water[i]+amount: 0;
        }
    }

    private float trueHeight(int x,int y) {
        return height(x,y)+water(x,y);
    }

    private final PointList points = new PointList();

    public void flow() {

        for ( int x = 0 ; x < size ; x++ )
        {
            for (int y = 0; y < size; y++)
            {
                final float currentWater = water(x,y);
                if ( currentWater == 0 ) {
                    continue;
                }
                points.clear();
                final float currentHeight = trueHeight( x, y );
                float heightSum = 0;
                for (int gradx = -1; gradx <= 1; gradx++)
                {
                    for (int grady = -1; grady <= 1; grady++)
                    {
                        if ( gradx != 0 || grady != 0 )
                        {
                            final int rx = x + gradx;
                            final int ry = y + grady;
                            if ( rx >= 0 && ry >= 0 && rx < size && ry < size )
                            {
                                float otherHeight = trueHeight( rx, ry );
                                if ( otherHeight < currentHeight )
                                {
                                    // ok, downstream
                                    heightSum += otherHeight;
                                    points.add( rx, ry );
                                }
                            }
                        }
                    }
                }

                if ( points.size() > 0 )
                {
                    final float avgHeight = heightSum / points.size();
                    final float h = currentHeight - avgHeight;
                    final float excessWater = Math.min( currentWater, h );

                    if ( excessWater > 0 )
                    {
                        final float fraction = excessWater / (points.size()+1);
                        float newValue = water(x,y) - fraction*points.size();
                        setWater(x,y, newValue < EPSILON ? 0 : newValue );
                        points.forEach( fraction, (px,py,data) ->
                        {
                            final float newW = water(px,py)+data;
                            setWater(px,py,newW < EPSILON ? 0 : newW );
                        });
                    }
                }
            }
        }
    }

    public void clearWater() {
        Arrays.fill(water,(byte)0);
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

    public float getWaterSum() {
        return getWaterSum(water);
    }

    private float getWaterSum(float[] water) {
        float sum = 0f;
        for ( int i = 0 ; i < size*size ; i++ ) {
            sum += water[i];
        }
        return sum;
    }

    public void setWater(int x,int y,float value)
    {
        this.water[ y*size + x ] = value;
    }

    public void incHeight(int x,int y,int increment) {
        setHeight(x,y,height( x,y ) + increment );
    }

    public void incWater(int x,int y,float increment) {
        setWater(x,y,water( x,y ) + increment );
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

    private static void writeArray(byte[] array,OutputStream out) throws IOException
    {
        writeInt(array.length,out);
        for ( byte value : array ) {
            out.write( value);
        }
    }

    private static void writeArray(float[] array,OutputStream out) throws IOException
    {
        writeInt(array.length,out);
        for ( float value : array ) {
            writeFloat(value,out);
        }
    }

    private static byte[] readByteArray(InputStream in) throws IOException
    {
        final int len = readInt(in);
        final byte[] result = new byte[ len ];
        for ( int i = 0 ; i < len ; i++ ) {
            final int tmp = in.read();
            if ( tmp == -1 ) {
                throw new EOFException( "Premature end of input" );
            }
            result[i] = (byte) tmp;
        }
        return result;
    }

    private static float[] readFloatArray(InputStream in) throws IOException
    {
        final int len = readInt(in);
        final float[] result = new float[ len ];
        for ( int i = 0 ; i < len ; i++ ) {
            result[i] = readFloat(in);
        }
        return result;
    }

    private static void writeFloat(float f,OutputStream out) throws IOException {
        writeInt(Float.floatToIntBits( f ), out );
    }

    private static float readFloat(InputStream in) throws IOException
    {
        return Float.intBitsToFloat( readInt(in) );
    }

    private static void writeInt(int v, OutputStream out) throws IOException {
        out.write( (v >> 24) & 0xff );
        out.write( (v >> 16) & 0xff );
        out.write( (v >>  8) & 0xff );
        out.write( (v      ) & 0xff );
    }

    private static int readInt(InputStream in) throws IOException {

        int value = 0;
        for ( int i = 0 ; i < 4 ; i++)
        {
            value <<= 8;
            final int tmp = in.read();
            if ( tmp == -1 )
            {
                throw new EOFException( "Premature end of file" );
            }
            value |= (tmp & 0xff);
        }
        return value;
    }
}