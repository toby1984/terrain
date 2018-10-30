package de.codesourcery.terrain;

import com.badlogic.gdx.math.Vector3;

public class TriangleList
{
    public static final int COMPONENT_CNT = 10;

    // vertices
    public float vertices[]=new float[0];

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

    public int addVertex(Vector3 v)
    {
        final int vertexNo = vertexPtr/COMPONENT_CNT;
        addVertex( v.x,v.y,v.z );
        return vertexNo;
    }

    public void addVertex(float x, float y, float z)
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
        vertexPtr += COMPONENT_CNT;
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

    public void setupMesh(Data data, final float squareSize)
    {
        clear();

        // pre-size arrays
        assureVertices( data.size*data.size );

        final int quadCount = (data.size-1)*(data.size-1);
        final int triangleCount = quadCount*2;
        assureIndices( triangleCount* COMPONENT_CNT );

        // setup vertices
        final int size = data.size;
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
                vertexArray[vertexPtr  ] = x;
                vertexArray[vertexPtr+1] = 0.05f*height;
//                vertexArray[vertexPtr+1] = (ix%2 == 0 || iz%2 == 0 ) ? 10:0;
                vertexArray[vertexPtr+2] = z;
                // normals: index 3..5
                // color unpacked
                vertexArray[vertexPtr+6] = 0;
                vertexArray[vertexPtr+7] = 1;
                vertexArray[vertexPtr+8] = 0;
                vertexArray[vertexPtr+9] = 1; // a

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
        System.out.println( v+ " x "+u+" => normal: "+n);
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