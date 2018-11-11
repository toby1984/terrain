package de.codesourcery.terrain;

import com.badlogic.gdx.math.Vector3;

import java.nio.FloatBuffer;

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
                        float height,
                        float squareSize)
    {
        final int waterColor = 0xff0000ff;

        final int size = data.size;
        float origin = -tileSize*size/2f;

        final float half = squareSize / 2f;
        for ( int z = 0 ; z < size-2 ; z++)
        {
            float z0 = origin + z*tileSize;
            float z1 = origin + (z+1)*tileSize;
            int ptr = z*size;
            for ( int x = 0 ; x < size-2 ; x++, ptr++)
            {
                float x0 = origin + x*tileSize;
                float x1 = origin + (x+1)*tileSize;

                final boolean isIn0 = outline[ ptr ];
                final boolean isIn1 = outline[ ptr + 1 ];
                final boolean isIn2 = outline[ ptr + size + 1 ];
                final boolean isIn3 = outline[ ptr + size ];
                final int index = (isIn3 ? 8 : 0) | (isIn2 ? 4 : 0) | (isIn1 ? 2 : 0) | (isIn0 ? 1: 0);
//                System.out.println( x+","+z+" ("+x0+","+z0+") => index: "+index);
                switch(index)
                {
                    case 0: // trivial: fully outside
                        continue;
                    case 1:
                        p0.set(x0,height,z0);
                        p1.set(x0+half,height,z0);
                        p2.set(x0,height,z0+ half );

                        int idx0 = meshBuilder.addVertex( p0, waterColor );
                        int idx1 = meshBuilder.addVertex( p1, waterColor );
                        int idx2 = meshBuilder.addVertex( p2, waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 2:
                        p0.set(x0+half,height,z0);
                        p1.set(x1,height,z0);
                        p2.set(x1,height,z0+ half );

                        idx0 = meshBuilder.addVertex( p0, waterColor );
                        idx1 = meshBuilder.addVertex( p1, waterColor );
                        idx2 = meshBuilder.addVertex( p2, waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 3:
                        p0.set(x0,height,z0);
                        p1.set(x1,height,z0);
                        p2.set(x1,height,z0+ half );
                        p3.set(x0,height,z0+ half );

                        idx0 = meshBuilder.addVertex( p0, waterColor );
                        idx1 = meshBuilder.addVertex( p1, waterColor );
                        idx2 = meshBuilder.addVertex( p2, waterColor );
                        int idx3 = meshBuilder.addVertex( p3, waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    case 4:
                        p0.set(x0+ half,height,z1);
                        p1.set(x1,height,z0+ half );
                        p2.set(x1,height,z1);

                        idx0 = meshBuilder.addVertex( p0 , waterColor );
                        idx1 = meshBuilder.addVertex( p1 , waterColor );
                        idx2 = meshBuilder.addVertex( p2 , waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 5:
                        p0.set(x0,height,z0);
                        p1.set(x0+ half,height,z0);
                        p2.set(x0,height,z0+ half );

                        idx0 = meshBuilder.addVertex( p0, waterColor );
                        idx1 = meshBuilder.addVertex( p1, waterColor );
                        idx2 = meshBuilder.addVertex( p2, waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);

                        p0.set(x0+ half,height,z1);
                        p1.set(x1,height,z0+ half );
                        p2.set(x1,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 6:
                        p0.set(x0+ half,height,z0);
                        p1.set(x1,height,z0);
                        p2.set(x1,height,z1);
                        p3.set(x0+ half,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );
                        idx3 = meshBuilder.addVertex( p3,waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    case 7:
                        p0.set(x0,height,z0);
                        p1.set(x1,height,z0);
                        p2.set(x1,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor);
                        idx1 = meshBuilder.addVertex( p1,waterColor);
                        idx2 = meshBuilder.addVertex( p2,waterColor);

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 8:
                        p0.set(x0,height,z0+ half );
                        p1.set(x0+ half,height,z1);
                        p2.set(x0,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 9:
                        p0.set(x0,height,z0);
                        p1.set(x0+ half,height,z0);
                        p2.set(x0+ half,height,z1);
                        p3.set(x0,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );
                        idx3 = meshBuilder.addVertex( p3,waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    case 10:
                        p0.set(x0,height,z0);
                        p1.set(x0+ half,height,z1);
                        p2.set(x0,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);

                        p0.set(x0+ half,height,z0);
                        p1.set(x1,height,z0);
                        p2.set(x1,height,z0+ half );

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 11:
                        p0.set(x0,height,z0);
                        p1.set(x1,height,z0);
                        p2.set(x0,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 12:
                        p0.set(x0,height,z0+ half );
                        p1.set(x1,height,z0+ half );
                        p2.set(x1,height,z1);
                        p3.set(x0,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );
                        idx3 = meshBuilder.addVertex( p3,waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    case 13:
                        p0.set(x0,height,z0);
                        p1.set(x1,height,z1);
                        p2.set(x0,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 14:
                        p0.set(x1,height,z0);
                        p1.set(x1,height,z1);
                        p2.set(x0,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 15: // trivial: fully inside
                        // get height from any of the vertices
                        // (since it's water we assume a flat surface => all vertices have the same
                        // height above 'sea level'
                        p0.set(x0,height,z0);
                        p1.set(x1,height,z0);
                        p2.set(x1,height,z1);
                        p3.set(x0,height,z1);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );
                        idx3 = meshBuilder.addVertex( p3,waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached");
                }
            }
        }
    }

    private void addQuad(TriangleList meshBuilder,float halfTileSize,int waterColor)
    {
        int idx0 = meshBuilder.addVertex( p0,waterColor );
        int idx1 = meshBuilder.addVertex( p1,waterColor );
        int idx2 = meshBuilder.addVertex( p2,waterColor );
        int idx3 = meshBuilder.addVertex( p3,waterColor );

        meshBuilder.addQuad(idx0, idx1, idx2, idx3);


    }
}