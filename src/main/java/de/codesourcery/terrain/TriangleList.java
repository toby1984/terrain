package de.codesourcery.terrain;

import com.badlogic.gdx.math.Matrix4;

public class TriangleList
{
    public static final int COMPONENT_CNT = 3;

    // vertices
    public float vertices[]=new float[0];

    // triangle corner indices (clockwise)
    public int[] indices =new int[0];

    private int vertexPtr = 0;
    private int idxPtr = 0;

    private static float[] realloc(float[] array,int newLen) {
        final float[] tmp = new float[ newLen ];
        System.arraycopy( array,0,tmp,0,array.length );
        return tmp;
    }

    private static int[] realloc(int[] array,int newLen) {
        final int[] tmp = new int[ newLen ];
        System.arraycopy( array,0,tmp,0,array.length );
        return tmp;
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
        if ( idxPtr == indices.length ) 
        {
            final int triCount = triangleCount();
            final int newTriCount = triCount + triCount/2 + 1;
            indices = realloc( indices, newTriCount*3 );
        }
        final int idx = idxPtr;
        indices[idx ]=p0;
        indices[idx+1]=p1;
        indices[idx+2]=p2;
        idxPtr +=3;
    }

    public void clear() {
        vertexPtr = 0;
        idxPtr = 0;
    }

    public int vertexCount() {
        return vertexPtr / COMPONENT_CNT;
    }

    public int indexCount() {
        return idxPtr;
    }
    
    public int triangleCount() {
        return idxPtr/ 3;
    }
    
    public void assureVertices(int count) 
    {
        final int available = (vertices.length-vertexPtr)/ COMPONENT_CNT;
        final int needed = count - available;
        if ( needed > 0 ) 
        {
            vertices = realloc( vertices,
                    this.vertices.length+needed* COMPONENT_CNT );
        }
    }

    public void assureIndices(int count) 
    {
        final int available = this.indices.length - idxPtr;
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
        System.arraycopy( indices,0,destination.indices,0,idxPtr );
    }
    
    public void multiply(Matrix4 m) {
        Matrix4.mulVec( m.val,vertices,0,vertexCount(),COMPONENT_CNT );
    }

    public void project(Matrix4 m) {
        Matrix4.prj( m.val,vertices,0,vertexCount(),COMPONENT_CNT );
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
        final float xStart = -(size * squareSize / 2);
        final float zStart = -(size * squareSize / 2);
        int heightMapPtr = 0;
        int vertexPtr = this.vertexPtr;
        final float[] vertexArray = this.vertices;
        final byte[] heightMap = data.height;

        int iz=0;
        for ( float z = zStart ; iz < size; z+=squareSize,iz++)
        {
            int ix = 0;
            for ( float x = xStart ; ix < size; x+=squareSize,ix++)
            {
                final float height = heightMap[heightMapPtr++] & 0xff;
                vertexArray[vertexPtr  ] = x;
                vertexArray[vertexPtr+1] = height;
                vertexArray[vertexPtr+2] = z;
                vertexPtr += COMPONENT_CNT;
            }
        }
        this.vertexPtr = vertexPtr;
        
        // setup indices
        int p0Ptr = 0;
        int p1Ptr = COMPONENT_CNT;
        int p2Ptr = (size* COMPONENT_CNT) + COMPONENT_CNT;
        int p3Ptr = size* COMPONENT_CNT;

        int idxPtr = this.idxPtr;
        final int[] idxArray = this.indices;
        for ( iz = 0 ; iz < size-1; iz++)
        {
            int ix = 0;
            for ( ix = 0; ix < size-1; ix++)
            {
                // triangle #0
                idxArray[idxPtr ] = p0Ptr;
                idxArray[idxPtr+1] = p1Ptr;
                idxArray[idxPtr+2] = p2Ptr;

                // triangle #1
                idxArray[idxPtr+3] = p0Ptr;
                idxArray[idxPtr+4] = p2Ptr;
                idxArray[idxPtr+5] = p3Ptr;

                idxPtr += 6;

                p0Ptr += COMPONENT_CNT;
                p1Ptr += COMPONENT_CNT;
                p2Ptr += COMPONENT_CNT;
                p3Ptr += COMPONENT_CNT;
            }
        }
        this.idxPtr = idxPtr;
    }
}