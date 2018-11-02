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
    public final float[] height;
    public final float[] water;
    public final int size;

    public boolean dirty = true;

    private final PointList points = new PointList();

    public Data(int size)
    {
        this.size = size;
        this.height = new float[size*size];
        this.water = new float[size*size];
        this.dirty = true;
    }

    public void save(OutputStream out) throws IOException {
        writeInt(size,out);
        writeArray( height,out );
        writeArray( water,out );
    }

    public static Data read(InputStream in) throws IOException
    {
        final int size = readInt(in);
        final Data result = new Data(size);
        final float[] height = readFloatArray(in);
        System.arraycopy( height,0,result.height,0,size*size );

        final float[] floatArray = readFloatArray(in);
        System.arraycopy( floatArray,0,result.water,0,size*size );

        result.dirty = true;
        return result;
    }

    public void clear() {
        Arrays.fill( height,(byte) 0);
        Arrays.fill( water,0);
        dirty = true;
    }

    private final class RandomGen
    {
        public final Random rnd;
        private float range;

        private RandomGen(long seed,float range)
        {
            this.rnd = new Random(seed);
            this.range = range;
        }

        public void setRange(float range)
        {
            this.range = range;
        }

        private float rndValue()
        {
            return -range + 2 * range*rnd.nextFloat();
        }
    }

    public void initWater(int minHeight,float amount)
    {
        for ( int i = 0 ; i < size*size; i++)
        {
            float h = height[i];
            water[i] = h > minHeight ? water[i]+amount: 0;
        }
        dirty = true;
    }

    private float trueHeight(int x,int y) {
        return height(x,y)+water(x,y);
    }

    public void flow() {

        dirty = true;

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
                        fastSetWater(x,y, newValue < EPSILON ? 0 : newValue );
                        points.forEach( fraction, (px,py,data) ->
                        {
                            final float newW = water(px,py)+data;
                            fastSetWater(px,py,newW < EPSILON ? 0 : newW );
                        });
                    }
                }
            }
        }
    }

    public void clearWater() {
        dirty = true;
        Arrays.fill(water,(byte)0);
    }

    public float water(int x,int y) {
        return this.water[y*size+x];
    }

    public Data initHeights(long seed, float randomRange) {

        final RandomGen rnd = new RandomGen(seed,randomRange);

        Arrays.fill(height,0f);

        final float[] tmp = new float[4];
        for ( int i = 0 ; i < tmp.length ; i++ )
        {
            tmp[i] = 255*rnd.rnd.nextFloat();
        }
        fastSetHeight(0,0, tmp[0] );
        fastSetHeight(size-1,0, tmp[1] );
        fastSetHeight(0,size-1, tmp[2] );
        fastSetHeight(size-1,size-1, tmp[3] );

        float range = randomRange;

        for ( int i = 1 ; i < 4 ; i++ )
        {
            rnd.setRange( range );
            mdp(rnd);
            range /= 2.0f;
        }

        // normalize
        float min = 10000000;
        float max = -10000000;
        for ( int i =0, len= size*size ; i < len; i++) {
            float v = height[i];
            if ( v < min ) {
                min = v;
            }
            if ( v > max ) {
                max = v;
            }
        }
        float scale = 255f/(max-min);
        for ( int i =0, len= size*size ; i < len; i++)
        {
            float v = height[i];
            height[i] = (v-min)*scale;
        }
        return this;
    }

    public void smooth() {

        dirty = true;

        final float[] copy = Arrays.copyOf( this.height, this.height.length );
        for ( int iz = 1 ; iz < size-1; iz++)
        {
            for ( int ix = 1 ; ix < size-1; ix++) {
                float h1 = height(ix-1,iz-1 );
                float h2 = height( ix ,iz-1 );
                float h3 = height(ix+ 1,iz-1 );

                float h4 = height(ix-1,iz );
                float h6 = height(ix+ 1,iz );

                float h7 = height(ix-1,iz+1 );
                float h8 = height( ix ,iz+1 );
                float h9 = height(ix+ 1,iz+1 );

                float avg = (h1+h2+h3+h4+h6+h7+h8+h9)/8f;
                copy[ ix + iz*size ] = avg;
            }
        }
        System.arraycopy( copy, 0, this.height,0, copy.length );
    }

    private static float clamp(float v) {
        if ( v < 0 ) {
            return 0;
        }
        if ( v > 255 ) {
            return 255;
        }
        return v;
    }

    private Data mdp(RandomGen rnd)
    {
        dirty = true;

        int stepSize = size;

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
                    final float topLeft = height( x, y );
                    final float topRight = height( x + sm1, y );
                    final float bottomLeft = height( x, y + sm1 );
                    final float bottomRight = height( x + sm1, y + sm1 );
                    final float centerValue =
                    clamp(
                            rnd.rndValue() + ( topLeft + topRight + bottomLeft + bottomRight) / 4
                    );
                    final int cx = x + stepSize/2;
                    final int cy = y + stepSize/2;
                    fastSetHeight(cx, cy, centerValue);
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
                    final float topCenter = height( x+stepSize/2 , (y-stepSize/2) );
                    final float bottomCenter = height( x+stepSize/2 , (y+stepSize+stepSize/2) );
                    final float leftCenter = height( x-stepSize/2 , (y+stepSize/2) );
                    final float rightCenter = height( x+stepSize+stepSize/2 , (y+stepSize/2) );

                    final float topLeft = height( x, y );
                    final float topRight = height( x + sm1, y );
                    final float bottomLeft = height( x, y + sm1 );
                    final float bottomRight = height( x + sm1, y + sm1 );

                    final int cx = x + stepSize/2;
                    final int cy = y + stepSize/2;
                    final float center = height(cx,cy);

                    float newValue;
                    // top-center
                    newValue = clamp( rnd.rndValue() + (topLeft + topRight + topCenter + center)/4 );
                    fastSetHeight( cx, y, newValue );

                    // bottom-center
                    newValue = clamp( rnd.rndValue() + (bottomLeft + bottomRight + bottomCenter + center) / 4 );
                    fastSetHeight( cx,y+sm1, newValue );

                    // left-center
                    newValue = clamp( rnd.rndValue() + (topLeft + bottomLeft + leftCenter + center)/4 );
                    fastSetHeight( x, cy, newValue );

                    // right-center
                    newValue = clamp( rnd.rndValue() + (topRight+bottomRight+rightCenter + center)/4);
                    fastSetHeight( x+sm1,cy,newValue );
                }
            }

            // half step size
            stepSize >>>= 1;
        }
        return this;
    }

    public float height(int x,int y) {
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
            return this.height[ (ry%size) * size + (rx%size)];
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

    private void fastSetWater(int x,int y,float value)
    {
        this.water[ y*size + x ] = value;
    }

    public void setWater(int x,int y,float value)
    {
        dirty = true;
        fastSetWater(x,y,value);
    }

    public void incHeight(int x,int y,int increment) {
        fastSetHeight(x,y,height( x,y ) + increment );
    }

    public void incWater(int x,int y,float increment) {
        setWater(x,y,water( x,y ) + increment );
    }

    private void fastSetHeight(int x, int y, float value)
    {
        while ( x < 0 ) {  x += size; }
        while ( y < 0 ) {  y += size; }
        this.height[ (y%size)*size + (x%size) ] = value;
    }

    public void setHeight(int x, int y, int value)
    {
        dirty = true;
        fastSetHeight( x,y,value );
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