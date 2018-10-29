package de.codesourcery.terrain;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class TriangleList
{
    public static final int COMPONENT_CNT = 3;

    private static final boolean DEPTH_SORT = true;
    private static final boolean CULL_SURFACES = true;

    // vertices
    public float vertices[]=new float[0];

    // normal vectors
    public float normals[] = new float[0];

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
            this.normals = realloc(this.normals, newPointCnt * 3 );
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
            vertices = realloc( vertices, this.vertices.length+needed* COMPONENT_CNT );
            normals = realloc( normals, this.normals.length+needed*3);
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

    public void copyTo(TriangleList destination,boolean copyNormals)
    {
        destination.clear();
        destination.assureIndices( indexCount() );
        destination.assureVertices( vertexCount() );

        System.arraycopy( vertices,0,destination.vertices,0,vertexPtr );
        System.arraycopy( indices,0,destination.indices,0,idxPtr );
        if ( copyNormals )
        {
            System.arraycopy( normals, 0, destination.normals, 0, vertexCount() * 3 );
        }
        destination.vertexPtr = this.vertexPtr;
        destination.idxPtr = this.idxPtr;
    }
    
    public void multiply(Matrix4 m) {
        Matrix4.mulVec( m.val,vertices,0,vertexCount(),COMPONENT_CNT );
    }

    public void project(Matrix4 m) {
        Matrix4.prj( m.val,vertices,0,vertexCount(),COMPONENT_CNT );
    }

    private String readVertex(int vertexNo)
    {
        final int idx = vertexNo * COMPONENT_CNT;
        return new Vector3( vertices[idx], vertices[idx+1], vertices[idx+2]).toString();
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
        final byte[] heightMap = data.height;

        int iz=0;
        for ( float z = zStart ; iz < size; z+=squareSize,iz++)
        {
            float x = xStart;
            for ( int ix = 0; ix < size; x+=squareSize,ix++)
            {
                final float height = (heightMap[heightMapPtr++] & 0xff);
                vertexArray[vertexPtr  ] = x;
                vertexArray[vertexPtr+1] = 0.05f*height;
                vertexArray[vertexPtr+2] = z;
                vertexPtr += COMPONENT_CNT;
            }
        }
        this.vertexPtr = vertexPtr;
        
        // setup indices
        int idxPtr = this.idxPtr;
        final int[] idxArray = this.indices;
        for ( iz = 0 ; iz < size-1; iz++)
        {
            int p0Ptr = iz * size;
            int p1Ptr = p0Ptr + 1;
            int p2Ptr = p0Ptr + size + 1;
            int p3Ptr = p0Ptr + size;
            for (int ix = 0; ix < size-1; ix++)
            {
                // triangle #0
                idxArray[idxPtr  ] = p0Ptr;
                idxArray[idxPtr+1] = p1Ptr;
                idxArray[idxPtr+2] = p2Ptr;

//                System.out.println("Adding triangle: "+readVertex( p0Ptr )+" -> "+readVertex(p1Ptr)+" -> "+readVertex(p2Ptr));

                // triangle #1
                idxArray[idxPtr+3] = p0Ptr;
                idxArray[idxPtr+4] = p2Ptr;
                idxArray[idxPtr+5] = p3Ptr;
//                System.out.println("Adding triangle: "+readVertex( p0Ptr )+" -> "+readVertex(p2Ptr)+" -> "+readVertex(p3Ptr));

                idxPtr += 6;

                p0Ptr++;
                p1Ptr++;
                p2Ptr++;
                p3Ptr++;
            }
        }
        this.idxPtr = idxPtr;
//        System.out.println("setupMesh(): "+vertexCount()+" vertices, "+indexCount()+ " indices, "+triangleCount()+" triangles");
    }

    public interface IVisitor {

        void beforeFirstVisit();

        /**
         *
         * @param p0Idx start of first point in vertex array
         * @param p1Idx start of second point in vertex array
         * @param p2Idx start of third point in vertex array
         */
        void visit(int p0Idx,int p1Idx,int p2Idx);
    }

    public void setToCube(float size) {
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
    }

    private void addQuad(int p0,int p1,int p2,int p3)
    {
        addTriangle( p0,p1,p2 );
        addTriangle( p0,p2,p3 );
    }

    public void visitDepthSortedTriangles(float viewX,float viewY,float viewZ,IVisitor visitor)
    {
        final float[] distances = new float[ triangleCount() ];
        final int[] triangleNo = new int[ triangleCount() ];

        // calculate squared distance to midpoint of each triangle
        final Vector3 p0 = new Vector3();
        final Vector3 p1 = new Vector3();
        final Vector3 p2 = new Vector3();
        final Vector3 u = new Vector3();
        final Vector3 v = new Vector3();
        final Vector3 n = new Vector3();
        final Vector3 viewVec = new Vector3();
        final Vector3 avg = new Vector3();

        for ( int i = 0, cnt = triangleCount() ; i < cnt ; i++ )
        {
            triangleNo[i] = i;
            final int triIdx = i*3;

            final int offsetP0 = indices[triIdx] * TriangleList.COMPONENT_CNT;
            final int offsetP1 = indices[triIdx + 1] * TriangleList.COMPONENT_CNT;
            final int offsetP2 = indices[triIdx + 2] * TriangleList.COMPONENT_CNT;

//            System.out.println("Triangle "+indices[triIdx]+" -> "+indices[triIdx + 1]+" -> "+indices[triIdx + 2]);

            final float p0X = vertices[offsetP0];
            final float p0Y = vertices[offsetP0 + 1];
            final float p0Z = vertices[offsetP0 + 2];
            p0.set( p0X, p0Y, p0Z );

            final float p1X = vertices[offsetP1];
            final float p1Y = vertices[offsetP1 + 1];
            final float p1Z = vertices[offsetP1 + 2];
            p1.set( p1X, p1Y, p1Z );

            final float p2X = vertices[offsetP2];
            final float p2Y = vertices[offsetP2 + 1];
            final float p2Z = vertices[offsetP2 + 2];
            p2.set( p2X, p2Y, p2Z );

            // calculate mid-point
            avg.set(p0).add(p1).add(p2).scl(1f/3);
            distances[i] = avg.dst2( viewX,viewY,viewZ );

            // calculate normal vector
            u.set(p1).sub(p0);
            v.set(p2).sub(p0);
            n.set(v).crs( u ).nor(); // TODO: Normalization needed?

            normals[offsetP0]   = n.x;
            normals[offsetP0+1] = n.y;
            normals[offsetP0+2] = n.z;

            // calculate view vector
            viewVec.set(viewX,viewY,viewZ).sub(p0).nor();

            // calculate dot product between normal vector and view vector
            // to determine angle
            if ( ! CULL_SURFACES || n.dot( viewVec ) > 0 ) {
                triangleNo[i] = i;
            } else {
                triangleNo[i] = -1;
            }
        }

        // perform depth sort
        if ( DEPTH_SORT )
        {
            quickSort( 0, distances.length - 1, distances, triangleNo );
        }

        // start visiting triangles with the one farthest away
        visitor.beforeFirstVisit();
        for ( int i = triangleNo.length-1 ; i >= 0 ; i--)
        {
            final int tri = triangleNo[i];
            if ( tri >= 0 ) // only consider visible triangles
            {
                final int idxOffset = tri * 3;
                visitor.visit( indices[idxOffset]     * TriangleList.COMPONENT_CNT,
                               indices[idxOffset + 1] * TriangleList.COMPONENT_CNT,
                               indices[idxOffset + 2] * TriangleList.COMPONENT_CNT
                );
            }
        }
    }

    private static void quickSort(int l, int r, float[] distances, int[] triangles) {

        if (l < r)
        {
            final int q = partition(l, r,distances,triangles);
            quickSort(l, q,distances,triangles);
            quickSort(q + 1, r,distances,triangles);
        }
    }

    private static int partition(int l, int r,float[] distances,int[] triangles) {

        float pivot = distances[(l + r) / 2];
        int i = l - 1;
        int j = r + 1;
        while (true)
        {
            do {
                i++;
            } while (distances[i] < pivot);

            do {
                j--;
            } while (distances[j] > pivot);

            if (i < j)
            {
                final float tmp = distances[i];
                distances[i] = distances[j];
                distances[j] = tmp;

                final int tmpi = triangles[i];
                triangles[i] = triangles[j];
                triangles[j] = tmpi;
            } else {
                return j;
            }
        }
    }
}