package de.codesourcery.terrain;

import com.badlogic.gdx.math.DelaunayTriangulator;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

import java.util.Arrays;

public class TriangleList
{
    // position (x3)
    // normal (x3)
    // color unpacked (x4)
    public static final int COMPONENT_CNT = 10;

    private static final float HEIGHT_SCALE_FACTOR = 0.5f;

    // vertices
    public float[] vertices=new float[0];

    // triangle corner indices (clockwise)
    public short[] indices =new short[0];

    private int vertexPtr = 0;
    private int indexPtr = 0;

    public void compact() {

        if ( this.vertices.length > vertexPtr ) {
            this.vertices = realloc( this.vertices, vertexPtr );
        }
        if ( this.indices.length > indexPtr ) {
            this.indices = realloc( this.indices, indexPtr );
        }
    }

    private static float[] realloc(float[] array,int newLen) {
        final float[] tmp = new float[ newLen ];
        System.arraycopy( array,0,tmp,0,Math.min(array.length,newLen) );
        return tmp;
    }

    private static short[] realloc(short[] array,int newLen)
    {
        final short[] tmp = new short[ newLen ];
        System.arraycopy( array,0,tmp,0,Math.min(array.length,newLen) );
        return tmp;
    }

    private static boolean[] realloc(boolean[] array,int newLen)
    {
        final boolean[] tmp = new boolean[ newLen ];
        System.arraycopy( array,0,tmp,0,Math.min(array.length,newLen) );
        return tmp;
    }

    public int addVertex(Vector3 v)
    {
        final int vertexNo = vertexPtr/COMPONENT_CNT;
        addVertex( v.x,v.y,v.z );
        return vertexNo;
    }

    public void addVertex(float x, float y, float z,int color)
    {
        if ( vertexPtr == this.vertices.length )
        {
            final int pointCnt = vertexCount();
            final int newPointCnt = pointCnt + pointCnt/2 + 1;
            this.vertices = realloc(this.vertices,newPointCnt* COMPONENT_CNT );
        }
        final int idx = vertexPtr;
        this.vertices[idx] = x;
        this.vertices[idx+1] = y;
        this.vertices[idx+2] = z;
        // next 3 elements are normal coordinates
        this.vertices[idx+6] = ((color>>16) & 0xff)/255f; // r
        this.vertices[idx+7] = ((color>> 8) & 0xff)/255f; // g
        this.vertices[idx+8] = ((color    ) & 0xff)/255f; // b
        this.vertices[idx+9] = ((color>>24) & 0xff)/255f; // a

        vertexPtr += COMPONENT_CNT;
    }

    public void addVertex(float x, float y, float z)
    {
        addVertex(x,y,z,0xffff0000);
    }

    public void addTriangle(int p0,int p1,int p2)
    {
        if ( indexPtr == indices.length )
        {
            final int triCount = triangleCount();
            final int newTriCount = triCount + triCount/2 + 1;
            indices = realloc( indices, newTriCount*3 );
        }
        final int idx = indexPtr;
        indices[idx ]=(short) p0;
        indices[idx+1]=(short) p1;
        indices[idx+2]=(short) p2;
        indexPtr +=3;
    }

    public void clear() {
        vertexPtr = 0;
        indexPtr = 0;
    }

    public int vertexCount() {
        return vertexPtr / COMPONENT_CNT;
    }

    public int indexCount() {
        return indexPtr;
    }

    public int triangleCount() {
        return indexPtr / 3;
    }

    public void assureVertices(int count)
    {
        final int available = (vertices.length-vertexPtr)/ COMPONENT_CNT;
        final int needed = count - available;
        if ( needed > 0 )
        {
            vertices = realloc( vertices, this.vertices.length+needed* COMPONENT_CNT );
        }
    }

    public void assureIndices(int count)
    {
        final int available = this.indices.length - indexPtr;
        int needed = count-available;
        if ( needed > 0 ) {
            this.indices = realloc( this.indices,
                    this.indices.length + needed );
        }
    }

    public void copyTo(TriangleList destination)
    {
        destination.clear();
        destination.assureIndices( indexCount() );
        destination.assureVertices( vertexCount() );

        System.arraycopy( vertices,0,destination.vertices,0,vertexPtr );
        System.arraycopy( indices,0,destination.indices,0, indexPtr );

        destination.vertexPtr = this.vertexPtr;
        destination.indexPtr = this.indexPtr;
    }

    private static boolean[] toBooleanArray(String s,int size) {

        boolean[] result = new boolean[size*size];
        String[] rows = s.split("\n");
        for ( int iy = 0 ; iy < size ; iy++)
        {
            for (int ix = 0; ix < size; ix++)
            {
                switch ( rows[iy].charAt( ix ) )
                {
                    case '.':
                        result[iy * size + ix] = true;
                        break;
                    case '_':
                        result[iy * size + ix] = false;
                        break;
                    default:
                        throw new IllegalArgumentException( "Unhandled char: " + rows[iy].charAt( ix ) );
                }
            }
        }
        return result;
    }

    public static void main(String[] args)
    {
        final int size = 5;
        final float tileSize = 5f;

        Data data = new Data(size);
        final String s =
                        "___.__\n"+
                        "__..._\n"+
                        ".....\n"+
                        "._...\n"+
                        "___..\n";
        final boolean[] bool = toBooleanArray( s, size );
        print( bool, size);
        System.out.println("OUTLINE:");
        toOutline( bool, data );
        print( bool, size);
        final FloatArray array = new FloatArray();
        outlineToVertices( bool,array,data );
        System.out.println("Array size: "+array.size);
        Arrays.fill(bool,false);
        final float xOrigin = -tileSize*(data.size/2f);
        final float zOrigin = -tileSize*(data.size/2f);
        for ( int i = 0 ; i < array.size ; i+=2 )
        {
            int ix = (int) array.get( i );
            int iz = (int) array.get( i+1 );
            bool[ ix + iz*size ] = true;
        }
        System.out.println("Polygon:");
        print(bool,size);
    }

    public void setupWaterMesh(Data data,
                               float tileSize)
    {
        clear();

        final int size = data.size;

        final FloatArray pointArray = new FloatArray();

        final boolean[] visited = new boolean[ size*size ];

        final boolean[] tmpVisited = new boolean[ size*size ];

        final DelaunayTriangulator triangulator = new DelaunayTriangulator();

        int pointNo = 0;
        int vertexIdx = 0;
        for (int iz = 0 ; iz < size; iz++)
        {
            for (int ix = 0 ; ix < size ; ix++,pointNo++,vertexIdx += COMPONENT_CNT)
            {
                if ( ! visited[pointNo] &&
                        data.water[pointNo] != 0.0f)
                {
                    final float y = HEIGHT_SCALE_FACTOR * data.height[pointNo] +
                            HEIGHT_SCALE_FACTOR * data.water[pointNo];

                    // we found water,try to expand area as much as possible
                    Arrays.fill(tmpVisited,false);
                    floodFill(ix,iz,tmpVisited,data);

                    // merge all visited cells
                    // into visited[] array
                    for ( int i = 0,len=data.size*data.size;i<len;i++)
                    {
                        if ( tmpVisited[i] ) {
                            visited[i]=true;
                        }
                    }

                    // now turn expanded area into outline
                    toOutline(tmpVisited,data);

                    // Tesselate tmpVisited[] array into triangles
                    if ( outlineToVertices( tmpVisited,pointArray,data ) && pointArray.size > 2 )
                    {
                        final ShortArray shortArray = triangulator.computeTriangles( pointArray, false );
                        if ( shortArray.size >= 3  ) // got at least one triangle
                        {
                            final float xOrigin = -tileSize*data.size/2f;
                            final float zOrigin = -tileSize*data.size/2f;

                            for ( int i = 0, len = shortArray.size ; i < len ; i+=3 )
                            {
                                final int p0Idx = (int) shortArray.get(i);
                                float p0X = xOrigin + pointArray.get(p0Idx) * tileSize;
                                float p0Z = zOrigin + pointArray.get(p0Idx+1) * tileSize;
                                addVertex( p0X, p0Z, y, 0x80000080 ); // TODO: Careful - assumption here is that all 3 points are in one plane (=water is flag)

                                final int p1Idx = (int) shortArray.get(i+1);
                                float p1X = xOrigin + pointArray.get(p1Idx) * tileSize;
                                float p1Z = zOrigin + pointArray.get(p1Idx+1) * tileSize;
                                addVertex( p1X, p1Z, y, 0x80000080 ); // TODO: Careful - assumption here is that all 3 points are in one plane (=water is flag)

                                final int p2Idx = (int) shortArray.get(i+2);
                                float p2X = xOrigin + pointArray.get(p2Idx) * tileSize;
                                float p2Z = zOrigin + pointArray.get(p2Idx+1) * tileSize;
                                addVertex( p2X, p2Z, y, 0x80000080 ); // TODO: Careful - assumption here is that all 3 points are in one plane (=water is flag)
                                addTriangle( i,i+1,i+2 );
                            }
                        }
                    }
                }
            }
        }
    }

    private static void print(boolean[] data,int size)
    {
        for ( int iz = 0 ; iz < size ; iz++)
        {
            for ( int ix = 0 ; ix < size ; ix++ ) {
                if ( data[iz*size+ix] ) {
                    System.out.print(".");
                }
                else
                {
                    System.out.print( "_" );
                }
            }
            System.out.println();
        }
    }

    private static boolean outlineToVertices(boolean[] outline,FloatArray array,Data data)
    {
        array.clear();

        // sanity check that each row has at least 2 points
        // except for maybe the very first and very last line
        int max = data.size;
        final int[] pointsPerRow = new int[ max ];
        for ( int iz = 0 ; iz < max ; iz++)
        {
            int pointCount = 0;
            for ( int ix = 0 ; ix < max ; ix++ ) {
                if ( outline[ix + iz*max] ) {
                    pointCount++;
                }
            }
            pointsPerRow[iz] = pointCount;
        }
        boolean gotStart=false;
        boolean gotEnd=false;
        for ( int i = 0 ; i < max ; i++ ) {
            if ( pointsPerRow[i] > 0 )
            {
                if ( gotEnd ) {
//                    throw new RuntimeException("Illegal outline, has disconnected points ?");
                    System.err.println("Illegal outline, has disconnected points ?");
                    return false;
                }
                if ( ! gotStart ) {
                    gotStart = true;
                    continue;
                }
                if ( pointsPerRow[i] == 1 ) {
                    // only top or bottom row may have only one point
                    gotEnd = true;
                }
            } else {
                gotEnd = true;
            }
        }

        int iz = 0;
        // find first,left-most point on outline
        int firstX = -1;
        for ( ; iz < max ; iz++ )
        {
            firstX = findFirstPointFromLeft( outline, iz, data );
            if ( firstX != -1 )
            {
                array.add( firstX , iz );
                break;
            }
        }
        if ( firstX == -1 )
        {
            return true; // we found no point at all -> empty outline
        }
        // find next left-most point
        int previousX = firstX;
        int previousZ = iz;
        iz++;
        int dx = Integer.MAX_VALUE;
loop:
        for ( int tmpX=0 ; iz < max ; iz++ )
        {
            tmpX = findFirstPointFromLeft( outline, iz, data );
            if ( tmpX != -1 )
            {
                final int delta = previousX - tmpX;
                if ( dx == Integer.MAX_VALUE || delta == dx )
                {
                    previousX = tmpX;
                    previousZ = iz;
                    dx = delta;
                    continue;
                }
                array.add( previousX, previousZ);
                array.add( tmpX, iz);
                dx = delta;
                previousX = tmpX;
                previousZ = iz;
                continue;
            }
            array.add( previousX, previousZ);
            break;
        }

        // now go up and look for the right-most points
        iz--;
        firstX = -1;
        for ( ; iz >= 0 ; iz-- )
        {
            firstX = findFirstPointFromRight( outline, iz, data );
            if ( firstX != -1 )
            {
                array.add( firstX, iz );
                break;
            }
        }
        if ( firstX == -1 )
        {
            if ( array.size < 3 ) {
                array.clear();
            }
            return false; // invalid/empty outline
        }

        // find next right-most point
        previousX = firstX;
        previousZ = iz;
        iz--;
        dx = Integer.MAX_VALUE;
loop:
        for ( int tmpX = 0 ; iz >= 0 ; iz--)
        {
            tmpX = findFirstPointFromRight( outline, iz, data );
            if ( tmpX != -1 )
            {
                final int delta = previousX - tmpX;
                if ( dx == Integer.MAX_VALUE || delta == dx )
                {
                    dx = delta;
                    previousX = tmpX;
                    previousZ = iz;
                    continue;
                }
                array.add( previousX, previousZ);
                array.add( tmpX, iz);
                dx = delta;
                previousX = tmpX;
                previousZ = iz;
                continue;
            }
            array.add( previousX, previousZ);
            break;
        }
        return true;
    }

    private static int findFirstPointFromLeft(boolean[] outline,int iz,Data data)
    {
        int max=data.size;
        int ptr = iz*max;
        for ( int ix = 0; ix < max ; ix++,ptr++ ) {
            if ( outline[ptr] ) {
                return ix;
            }
        }
        return -1;
    }

    private static int findFirstPointFromRight(boolean[] outline,int iz,Data data)
    {
        int max=data.size;
        int ptr = iz*max + max -1 ;
        for ( int ix = max-1 ; ix >= 0; ix--,ptr-- ) {
            if ( outline[ptr] ) {
                System.out.println("right-most @ "+ix);
                return ix;
            }
        }
        return -1;
    }

    private static void toOutline(boolean[] tmpVisited,Data data)
    {
        final int max = data.size;
        for ( int iz = 1 ; iz < max ; iz++)
        {
            int offset=iz*max;
            int ix = 0;
            for (  ; ix < max ; ix++,offset++ )
            {
                if ( tmpVisited[offset] )
                {
                    ix++;
                    offset++;
                    while( (ix+1) < max && tmpVisited[offset] && tmpVisited[offset+1])
                    {
                        tmpVisited[offset]=false; // clear next
                        ix++;
                        offset++;
                    }
                }
            }
        }
    }

    private void floodFill(int ix, int iz, boolean[] tmpVisited, Data data)
    {
        final int offset = iz*data.size+ix;
        tmpVisited[offset] = true;

        final int minX = ix == 0 ? 0 : ix-1;
        final int minZ = iz == 0 ? 0 : iz-1;
        final int maxX = ix == data.size-1 ? 0 : ix+1;
        final int maxZ = iz == data.size-1 ? 0 : iz+1;
        for ( int dx = minX ; dx < maxX ; dx++ )
        {
            for ( int dz = minZ ; dz < maxZ ; dz++ )
            {
                if ( dx != 0 || dz != 0 ) {
                    int rx = ix+dx;
                    int rz = iz+dz;
                    int ptr = rz*data.size+rx;
                    if ( data.water[ptr] != 0 && ! tmpVisited[ptr] )
                    {
                        floodFill( rx,rz,tmpVisited,data );
                    }
                }
            }
        }
    }


    public void setupHeightMesh(Data data, float squareSize,int[] colorGradient)
    {
        clear();

        // pre-size arrays
        assureVertices( data.size*data.size );

        final int quadCount = (data.size-1)*(data.size-1);
        final int triangleCount = quadCount*2;
        assureIndices( triangleCount* COMPONENT_CNT );

        // setup vertices
        final int size = data.size;
        Float max = -1000000f;
        Float min =  1000000f;
        for ( int i = 0 ; i < size ; i++ ) {
            float h = data.height[i];
            if ( h > max ) {
                max = h;
            }
            if ( h < min ) {
                min = h;
            }
        }
        final float gradHeightScale = (max-min)/(colorGradient.length-1);
        final float xStart = -((size/2) * squareSize);
        final float zStart = -((size/2) * squareSize);

        int heightMapPtr = 0;
        int vertexPtr = this.vertexPtr;
        final float[] vertexArray = this.vertices;
        final float[] heightMap = data.height;

        int iz=0;
        for ( float z = zStart ; iz < size; z+=squareSize,iz++)
        {
            float x = xStart;
            for ( int ix = 0; ix < size; x+=squareSize,ix++)
            {
                final float height = heightMap[heightMapPtr++];
                final int color = colorGradient[ Math.max(0,(int) ((height - min)*gradHeightScale)) ];

                vertexArray[vertexPtr  ] = x;
                vertexArray[vertexPtr+1] = HEIGHT_SCALE_FACTOR*height;
//                vertexArray[vertexPtr+1] = (ix%2 == 0 || iz%2 == 0 ) ? 10:0;
                vertexArray[vertexPtr+2] = z;
                // normals: index 3..5
                // color unpacked
                vertexArray[vertexPtr+6] = ((color>>16) & 0xff)/255f; // r
                vertexArray[vertexPtr+7] = ((color>> 8) & 0xff)/255f; // g
                vertexArray[vertexPtr+8] = ((color    ) & 0xff)/255f; // b
                vertexArray[vertexPtr+9] = ((color>>24) & 0xff)/255f; // a
                vertexPtr += COMPONENT_CNT;
            }
        }
        this.vertexPtr = vertexPtr;

        // setup indices
        int idxPtr = this.indexPtr;
        final short[] idxArray = this.indices;
        for ( iz = 0 ; iz < size-1; iz++)
        {
            int p0Ptr = iz*size;
            int p1Ptr = p0Ptr + 1;
            int p2Ptr = p0Ptr + size + 1;
            int p3Ptr = p0Ptr + size;
            for (int ix = 0; ix < size-1; ix++)
            {
                // triangle #0
                idxArray[idxPtr  ] = (short) p0Ptr;
                idxArray[idxPtr+1] = (short) p1Ptr;
                idxArray[idxPtr+2] = (short) p2Ptr;

                // triangle #1
                idxArray[idxPtr+3] = (short) p0Ptr;
                idxArray[idxPtr+4] = (short) p2Ptr;
                idxArray[idxPtr+5] = (short) p3Ptr;

                idxPtr += 6;

                p0Ptr++;
                p1Ptr++;
                p2Ptr++;
                p3Ptr++;
            }
        }
        this.indexPtr = idxPtr;

        // calculate normals
        final Vector3 center=new Vector3();
        final Vector3 top=new Vector3();
        final Vector3 left=new Vector3();
        final Vector3 right=new Vector3();
        final Vector3 bottom=new Vector3();

        vertexPtr = 0;
        iz=0;
        for ( float z = zStart ; iz < size; iz++)
        {
            for ( int ix = 0; ix < size; ix++, vertexPtr+=COMPONENT_CNT)
            {
                center.set( vertexArray[vertexPtr], vertexArray[vertexPtr+1], vertexArray[vertexPtr+2] );
                if ( iz < size-1 )
                {
                    // not top row
                    if ( ix < size-1 ) {
                        // not right-most column, not top row: center,right,bottom
                        int idx = vertexPtr+COMPONENT_CNT;
                        right.set( vertexArray[idx], vertexArray[idx+1],vertexArray[idx+2] );
                        idx = vertexPtr + ( COMPONENT_CNT*size);
                        bottom.set( vertexArray[idx], vertexArray[idx+1],vertexArray[idx+2] );

                        n.set( calcNormal( center, right, bottom) );

                    }  else {
                        // rightmost column: center,left,bottom (3)
                        int idx = vertexPtr-COMPONENT_CNT;
                        left.set( vertexArray[idx], vertexArray[idx+1],vertexArray[idx+2] );
                        idx = vertexPtr + ( COMPONENT_CNT*size);
                        bottom.set( vertexArray[idx], vertexArray[idx+1],vertexArray[idx+2] );

                        n.set( calcNormal( center, left, bottom) );
                    }
                }
                else
                {
                    // bottom row
                    if ( ix < size-1 ) {
                        // leftmost column: center,right,top
                        int idx = vertexPtr+COMPONENT_CNT;
                        right.set( vertexArray[idx], vertexArray[idx+1],vertexArray[idx+2] );
                        idx = vertexPtr-size*COMPONENT_CNT;
                        top.set( vertexArray[idx], vertexArray[idx+1],vertexArray[idx+2] );
                        n.set( calcNormal( center, top, right) );
                    }  else {
                        // rightmost column: center,left,top
                        int idx = vertexPtr-COMPONENT_CNT;
                        left.set( vertexArray[idx], vertexArray[idx+1],vertexArray[idx+2] );
                        idx = vertexPtr-size*COMPONENT_CNT;
                        top.set( vertexArray[idx], vertexArray[idx+1],vertexArray[idx+2] );

                        n.set( calcNormal( center, top, left) );
                    }
                }
                vertexArray[vertexPtr+3] = n.x;
                vertexArray[vertexPtr+4] = n.y;
                vertexArray[vertexPtr+5] = n.z;
            }
        }
        // average normals
        vertexPtr = 0;
        iz=0;
        for ( float z = zStart ; iz < size; iz++)
        {
            for ( int ix = 0; ix < size; ix++, vertexPtr+=COMPONENT_CNT)
            {
                n.set( vertexArray[vertexPtr+3], vertexArray[vertexPtr+4], vertexArray[vertexPtr+5] );
                if ( iz < size-1 )
                {
                    // not top row
                    if ( ix < size-1 ) {
                        // not right-most column, not top row: center,right,bottom
                        int idx = vertexPtr+COMPONENT_CNT;
                        right.set( vertexArray[idx+3], vertexArray[idx+4],vertexArray[idx+5] );
                        idx = vertexPtr + ( COMPONENT_CNT*size);
                        bottom.set( vertexArray[idx+3], vertexArray[idx+4],vertexArray[idx+5] );

                        n.add( right ).add( bottom );

                    }  else {
                        // rightmost column: center,left,bottom (3)
                        int idx = vertexPtr-COMPONENT_CNT;
                        left.set( vertexArray[idx+3], vertexArray[idx+4],vertexArray[idx+5] );
                        idx = vertexPtr + ( COMPONENT_CNT*size);
                        bottom.set( vertexArray[idx+3], vertexArray[idx+4],vertexArray[idx+5] );

                        n.add(left).add(bottom);
                    }
                }
                else
                {
                    // bottom row
                    if ( ix < size-1 ) {
                        // leftmost column: center,right,top
                        int idx = vertexPtr+COMPONENT_CNT;
                        right.set( vertexArray[idx+3], vertexArray[idx+4],vertexArray[idx+5] );
                        idx = vertexPtr-size*COMPONENT_CNT;
                        top.set( vertexArray[idx+3], vertexArray[idx+4],vertexArray[idx+5] );
                        n.add(top).add( right );
                    }  else {
                        // rightmost column: center,left,top
                        int idx = vertexPtr-COMPONENT_CNT;
                        left.set( vertexArray[idx+3], vertexArray[idx+4],vertexArray[idx+5] );
                        idx = vertexPtr-size*COMPONENT_CNT;
                        top.set( vertexArray[idx+3], vertexArray[idx+4],vertexArray[idx+5] );
                        n.add( top ).add( left);
                    }
                }
                n.scl(1/3f).nor();
                vertexArray[vertexPtr+3] = n.x;
                vertexArray[vertexPtr+4] = n.y;
                vertexArray[vertexPtr+5] = n.z;
            }
        }
    }

    private final Vector3 u=new Vector3();
    private final Vector3 v=new Vector3();
    private final Vector3 n=new Vector3();

    private Vector3 calcNormal(Vector3 base,Vector3 left,Vector3 right)
    {
        u.set(left ).sub(base);
        v.set(right).sub(base);
        n.set(u).crs(v).nor();
        return n;
    }

    public void setToCube(float size)
    {
        clear();

        // back
        final Vector3 p0 = new Vector3(-size/2f,size/2 ,-size/2);
        final Vector3 p1 = new Vector3( size/2f,size/2 ,-size/2);
        final Vector3 p2 = new Vector3( size/2f,-size/2,-size/2);
        final Vector3 p3 = new Vector3(-size/2f,-size/2,-size/2);

        // front
        final Vector3 p4 = new Vector3(-size/2f,size/2 ,size/2);
        final Vector3 p5 = new Vector3( size/2f,size/2 ,size/2);
        final Vector3 p6 = new Vector3( size/2f,-size/2,size/2);
        final Vector3 p7 = new Vector3(-size/2f,-size/2,size/2);

        int p0i = addVertex( p0 );
        int p1i = addVertex( p1 );
        int p2i = addVertex( p2 );
        int p3i = addVertex( p3 );

        int p4i = addVertex( p4 );
        int p5i = addVertex( p5 );
        int p6i = addVertex( p6 );
        int p7i = addVertex( p7 );

        // front side
        addQuad(p4i,p5i,p6i,p7i);

        // left side
        addQuad(p0i,p4i,p7i,p3i);

        // right side
        addQuad(p5i,p1i,p2i,p6i);

        // top side
        addQuad(p0i,p1i,p5i,p4i);

        // bottom side
        addQuad(p7i,p6i,p2i,p3i);

        // back side
        addQuad(p1i,p0i,p3i,p2i);

        calculateNormals();
    }

    private void addQuad(int p0,int p1,int p2,int p3)
    {
        addTriangle( p0,p1,p2 );
        addTriangle( p0,p2,p3 );
    }

    private void calculateNormals()
    {
        // calculate squared distance to midpoint of each triangle
        final Vector3 p0 = new Vector3();
        final Vector3 p1 = new Vector3();
        final Vector3 p2 = new Vector3();
        final Vector3 u = new Vector3();
        final Vector3 v = new Vector3();
        final Vector3 n = new Vector3();

        for ( int i = 0, cnt = triangleCount() ; i < cnt ; i++ )
        {
            final int triIdx = i*3;

            final int offsetP0 = indices[triIdx] * TriangleList.COMPONENT_CNT;
            final int offsetP1 = indices[triIdx + 1] * TriangleList.COMPONENT_CNT;
            final int offsetP2 = indices[triIdx + 2] * TriangleList.COMPONENT_CNT;

            final float p0X = vertices[offsetP0];
            final float p0Y = vertices[offsetP0 + 1];
            final float p0Z = vertices[offsetP0 + 2];

            final float p1X = vertices[offsetP1];
            final float p1Y = vertices[offsetP1 + 1];
            final float p1Z = vertices[offsetP1 + 2];

            final float p2X = vertices[offsetP2];
            final float p2Y = vertices[offsetP2 + 1];
            final float p2Z = vertices[offsetP2 + 2];

            p0.set( p0X, p0Y, p0Z );
            p1.set( p1X, p1Y, p1Z );
            p2.set( p2X, p2Y, p2Z );

            u.set(p2).sub(p0);
            v.set(p1).sub(p0);
            n.set(u).crs(v).nor();

            vertices[offsetP0+3] = n.x;
            vertices[offsetP0+4] = n.y;
            vertices[offsetP0+5] = n.z;

            vertices[offsetP1+3] = n.x;
            vertices[offsetP1+4] = n.y;
            vertices[offsetP1+5] = n.z;

            vertices[offsetP2+3] = n.x;
            vertices[offsetP2+4] = n.y;
            vertices[offsetP2+5] = n.z;
        }
    }
}