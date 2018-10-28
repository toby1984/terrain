package de.codesourcery.terrain;

import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector3;

public class TriangleList
{
    private static final int COMPONENTS_PER_VERTEX = 3;

    // vertices
    private float vertices[]=new float[0];

    // triangle corner indices (clockwise)
    private int[] corners =new int[0];

    private int pointPtr = 0;
    private int idxPtr = 0;

    public interface IVisitor {
        public void visit(int x,int y,float data);
    }

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

    public void add(float x,float y,float z)
    {
        if ( pointPtr == this.vertices.length )
        {
            final int pointCnt = this.vertices.length/3;
            final int newPointCnt = pointCnt + pointCnt/2 + 1;
            this.vertices = realloc(this.vertices,newPointCnt);
        }
        int idx = pointPtr;
        this.vertices[idx++] = x;
        this.vertices[idx++] = y;
        this.vertices[idx++] = z;
        pointPtr = idx;
    }

    public void clear() {
        pointPtr = 0;
        idxPtr = 0;
    }

    public int pointCount() {
        return pointPtr/3;
    }

    public int indexCount() {
        return idxPtr;
    }

    public void setup(Data data,float squareSize)
    {
        clear();

        Matrix4 m;
        
        // setup points
        final int size = data.size;
        for ( int x = 0 ; x < size; x++) {
            for ( int y = 0 ; y < size; y++)
            {

            }
        }

    }

    public void forEach(float data,IVisitor v) {

        final int len = pointPtr;
        for ( int i = 0 ; i < len ; i++ ) {
            v.visit(x[i],y[i],data);
        }
    }
}