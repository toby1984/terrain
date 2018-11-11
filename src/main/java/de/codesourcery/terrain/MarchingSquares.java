package de.codesourcery.terrain;

import com.badlogic.gdx.math.Vector3;

public class MarchingSquares
{
    private final Vector3 p0=new Vector3();
    private final Vector3 p1=new Vector3();
    private final Vector3 p2=new Vector3();
    private final Vector3 p3=new Vector3();

    public void process(Data data,
                        float tileSize,
                        boolean[] outline,
                        TriangleList meshBuilder,
                        float height)
    {
        final int waterColor = 0xff0000ff;

        final int size = data.size;
        float origin = -tileSize*size/2f;

        for ( int z = 0 ; z < size-1 ; z++)
        {
            float z0 = origin + z*tileSize;
            float z1 = origin + (z+1)*tileSize;
            int ptr = z*size;
            for ( int x = 0 ; x < size-1 ; x++, ptr++)
            {
                if ( outline[ptr] )
                {
                    float x0 = origin + x * tileSize;
                    float x1 = origin + (x + 1) * tileSize;

                    p0.set( x0, height, z0 );
                    p1.set( x1, height, z0 );
                    p2.set( x1, height, z1 );
                    p3.set( x0, height, z1 );

                    int idx0 = meshBuilder.addVertex( p0, waterColor );
                    int idx1 = meshBuilder.addVertex( p1, waterColor );
                    int idx2 = meshBuilder.addVertex( p2, waterColor );
                    int idx3 = meshBuilder.addVertex( p3, waterColor );

                    meshBuilder.addQuad( idx0, idx1, idx2, idx3 );
                }
            }
        }
    }
}