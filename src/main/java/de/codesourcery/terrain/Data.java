package de.codesourcery.terrain;

import java.awt.Rectangle;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Data
{
    /**
     * Any water level below this value will be clamped to zero.
     */
    public static final float EPSILON = 0.0001f;

    protected enum CalcMode {
        JAVA,
        NATIVE,
        OPENCL
    }

    private static final CalcMode CALC_MODE = CalcMode.NATIVE;


    private static FloatMemoryPool memoryPool = new FloatMemoryPool();

    private final Rectangle[] slices;
    private final MyRunnable[] runnables;

    protected final class MyRunnable implements Runnable
    {
        public final Rectangle area;
        public final CyclicBarrier barrier;

        public MyRunnable(Rectangle area, CyclicBarrier barrier) {
            this.area = area;
            this.barrier = barrier;
        }

        public void run()
        {
            try {
                flow(area,size,height.array(),water.array());
            }
            finally
            {
                try
                {
                    barrier.await();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    private final int threadCount;
    public final FloatBuffer height;
    public final FloatBuffer water;
    private final int[][] offsets;
    public final int size;

    private final ThreadPoolExecutor threadPool;
    private final CyclicBarrier barrier;

    public boolean dirty = true;

    public Data(int size)
    {
        this.size = size;

        final int elemCount = size * size;

        this.height = FloatBuffer.allocate( elemCount );
        this.water = FloatBuffer.allocate( elemCount );

        final ArrayBlockingQueue workQueue =
                new ArrayBlockingQueue(10 );

        final ThreadFactory threadFactory = new ThreadFactory()
        {
            private final ThreadGroup tg = new ThreadGroup( Thread.currentThread().getThreadGroup(),"flow" );
            private final AtomicInteger threadId = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r)
            {
                final Thread t = new Thread(tg,r,"flow-"+threadId.incrementAndGet());
                t.setDaemon( true );
                return t;
            }
        };

        this.threadCount = Math.max(1,Runtime.getRuntime().availableProcessors()/2);
        System.out.println("Using "+threadCount+" threads");

        this.threadPool = new ThreadPoolExecutor( threadCount,
                threadCount,10, TimeUnit.SECONDS,workQueue,threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy() );

        slices = new Rectangle[threadCount];
        barrier = new CyclicBarrier(threadCount+1);

        for ( int i = 0 ; i < threadCount ; i++ ) {
            slices[i] = new Rectangle();
        }
        setupSlices( 1,1,size-2,size-2,slices);
        runnables = new MyRunnable[ slices.length ];
        for ( int i = 0 ; i < slices.length ; i++ ) {
            runnables[i] = new MyRunnable(slices[i], barrier );
        }

        this.dirty = true;

        /* Setup tables with relative
         * array offsets for neighbouring
         * cells based on x/y coordinates
         * of the center cell.
         *
         * +-+---------------------+-+
         * |1|          2          |3|
         * +-+---------------------+-+
         * | |                     | |
         * | |                     | |
         * |8|                     |4|
         * | |                     | |
         * +-+---------------------+-+
         * |7|           6         |5|
         * +-----------------------+-+
         */
        this.offsets = new int[9][];
        this.offsets[0] = new int[] {1,size+1,size};
        this.offsets[1] = new int[] {-1,1,size-1,size,size+1};
        this.offsets[2] = new int[] {-1,size-1,size};
        this.offsets[3] = new int[] {-size-1,-size,-1,size-1,size};
        this.offsets[4] = new int[] {-size-1,-size,-1};
        this.offsets[5] = new int[] {-size-1,-size,-size+1,-1,1};
        this.offsets[6] = new int[] {-size,-size+1,1};
        this.offsets[7] = new int[] {-size,-size+1,1,size+1,size};
        this.offsets[8] = new int[] {-size-1,-size,-size+1,-1,1,size-1,size,size+1};
    }

    private int[] getNeighbourOffsets(int x,int y) {
        if ( x == 0 )
        {
            // x == 0
            if ( y == 0 ) { return offsets[0]; }
            if ( y == size-1 ) { return offsets[6]; }
            return offsets[7];
        }
        if ( x == size-1 )
        {
            // x == size-1
            if ( y == 0 ) { return offsets[2]; }
            if ( y == size-1 ) { return offsets[4]; }
            return offsets[3];
        }
        // x > 0 && x < size-1
        if ( y == 0 ) {
            return offsets[1];
        }
        if ( y < size-1 ) {
            return offsets[8];
        }
        return offsets[5];
    }

    public void save(OutputStream out) throws IOException {
        writeInt(size,out);
        writeArray( height.array(),out );
        writeArray( water.array(),out );
    }

    public static Data read(InputStream in) throws IOException
    {
        final int size = readInt(in);
        final Data result = new Data(size);
        final float[] height = readFloatArray(in);
        System.arraycopy( height,0,result.height.array(),0,size*size );

        final float[] floatArray = readFloatArray(in);
        System.arraycopy( floatArray,0,result.water.array(),0,size*size );

        result.dirty = true;
        return result;
    }

    public void clear()
    {
        Arrays.fill( height.array(),(byte) 0);
        Arrays.fill( water.array(),0);
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
        height.rewind();
        water.rewind();
        for ( int i = 0 ; i < size*size; i++)
        {
            float h = height.get();
            water.put( h > minHeight ? water.get(i)+amount: 0);
        }
        dirty = true;
    }

    public void flow(int count)
    {
        // Using JNA + copying Java arrays using Memory.write()/read()
        // 1000 - flow() time: 55 ms (total: 51616 ms)

        // Using JNA + FloatBuffer
        // 1000 - flow() time: 16 ms (total: 14736 ms)

        // Using Java only with float[] array
        // 1000 - flow() time: 15 ms (total: 18191 ms)

        // Using Java only with FloatBuffer
        // 1000 - flow() time: 17 ms (total: 18121 ms

        switch( CALC_MODE )
        {
            case JAVA:
                for ( int i = 0 ; i < count ; i++)
                {
                    barrier.reset();
                    for (int threadNo = 0, runnablesLength = runnables.length; threadNo < runnablesLength; threadNo++)
                    {
                        threadPool.submit( runnables[threadNo] );
                    }
                    while( true )
                    {
                        try
                        {
                            barrier.await();
                            break;
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            case NATIVE:
                height.rewind();
                water.rewind();
                if ( count == 1 )
                {
                    FlowLibrary.INSTANCE.flow( size, height , water );
                } else {
                    FlowLibrary.INSTANCE.flowRepeat( size, height , water , count);
                }
                break;
            case OPENCL:
                // TODO: Implement me
                break;
        }
        dirty = true;
    }

    private static void setupSlices(int x0,int y0,int width,int height,Rectangle[] output)
    {
        final int slices = output.length;
        int sliceWidth = Math.max(1,width/slices);
        int additionalLastSliceWidth = width - slices*sliceWidth;

        int x=x0;
        int w = sliceWidth;
        for ( int i = 0 ; i < slices ; i++, x+= sliceWidth )
        {
            if ( (i+1) == slices ) {
                w += additionalLastSliceWidth;
            }
            output[i].setBounds( x,y0,w,height);
        }
    }

    private void flow(Rectangle rect, int trueSize, float[] height, float[] water)
    {
        // 1000 - flow() time: 15 ms (total: 17217 ms)
        // 1000 - flow() time: 16 ms (total: 16102 ms)
        // 1000 - flow() time: 14 ms (total: 15903 ms)
        // --
        // 1000 - flow() time: 14 ms (total: 15754 ms

        // relative offsets to direct neightbours of current cell
        final int[] relNeighbourOffsets = {-trueSize-1,-trueSize,-trueSize+1,-1,1,trueSize-1,trueSize,trueSize+1};

        // array holding list of direct
        // neighbours whose level (water+height) is
        // below the current node's level (water+height)
        // so water needs to be re-distributed there
        final int[] neighbours = new int[8];
        int ptr;
        // TODO: Code currently cheats and ignores the border area as
        // TODO: we'd need to do lots of additional comparisons to detect
        // TODO: those boundary cases (OR duplicate the loop and
        // TODO: deal with the first/last row/column separately)
        for (int y = rect.y, ymax = rect.y + rect.height; y < ymax ; y++)
        {
            ptr = y*trueSize+rect.x;
            for ( int x = rect.x, xmax = rect.x + rect.width ; x < xmax ; x++,ptr++ )
            {
                final float currentWater = water[ptr];
                if ( currentWater == 0 ) {
                    // no water in this cell
                    continue;
                }
                // true height (ground height + water height)
                final float currentHeight = currentWater + height[ptr];
                int pointCount = 0;
                float heightSum = 0;
                for (int relOffset : relNeighbourOffsets )
                {
                    final int offset = ptr + relOffset;
                    final float otherHeight = water[offset]+height[offset];
                    if ( otherHeight < currentHeight )
                    {
                        // ok, downstream
                        heightSum += otherHeight;
                        neighbours[pointCount++] = offset;
                    }
                }

                if ( pointCount > 0 )
                {
                    final float avgHeight = heightSum / pointCount;
                    final float h = currentHeight - avgHeight;
                    final float excessWater = Math.min(currentWater,h);

                    final float fraction = excessWater / pointCount;
                    final float newValue = currentWater - excessWater;
                    water[ptr] = newValue < EPSILON ? 0 : newValue;
                    for ( int i = pointCount-1 ; i >= 0 ; i-- )
                    {
                        final int offset = neighbours[i];
                        final float newW = water[offset]+fraction;
                        water[offset] = newW;
                    }
                }
            }
        }
    }

    public void clearWater() {
        dirty = true;
        Arrays.fill(water.array(),(float) 0);
    }

    public float height(int idx) {
        return height.get(idx);
    }

    public float water(int idx) {
        return water.get(idx);
    }

    public float water(int x,int y) {
        return water.get(y*size+x);
    }

    public Data initHeights(long seed, float randomRange) {

        final RandomGen rnd = new RandomGen(seed,randomRange);

        Arrays.fill(height.array(),0f);

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
            float v = height.get(i);
            if ( v < min ) {
                min = v;
            }
            if ( v > max ) {
                max = v;
            }
        }
        float scale = 255f/(max-min);
        height.rewind();
        for ( int i =0, len= size*size ; i < len; i++)
        {
            float v = height.get();
            height.put( i, (v-min)*scale );
        }
        return this;
    }

    public void smooth() {

        dirty = true;

        final float[] copy = Arrays.copyOf( this.height.array(), this.height.array().length );
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
        System.arraycopy( copy, 0, this.height.array(),0, copy.length );
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
            return height.get((ry%size) * size + (rx%size) );
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
        return getWaterSum(water.array());
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
        dirty = true;
        water.put(x+y*size,value);
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
        this.height.put( (y%size)*size + (x%size) , value);
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