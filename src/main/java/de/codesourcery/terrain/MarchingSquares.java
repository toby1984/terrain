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
                        float squareSize)
    {
        System.out.println("INPUT:");
        TriangleList.print(outline,data.size);

        final int waterColor = 0xff0000ff;

        final int size = data.size;
        float origin = -tileSize*size/2f;

        final float half = squareSize / 2f;
        for ( int z = 0 ; z < size-2 ; z++)
        {
            float z0 = origin + z*squareSize;
            int ptr = z*size;
            for ( int x = 0 ; x < size-2 ; x++, ptr++)
            {
                float x0 = origin + x*squareSize;
                final boolean isIn0 = outline[ z*size + x ];
                final boolean isIn1 = outline[ z*size + x ];
                final boolean isIn2 = outline[ z*size + x ];
                final boolean isIn3 = outline[ z*size + x ];
//                final boolean isIn1 = outline[ z*size + x + 1 ];
//                final boolean isIn2 = outline[ (z+1)*size + x +1  ];
//                final boolean isIn3 = outline[ (z+1)*size + x ];
                final int index = (isIn3 ? 8 : 0) | (isIn2 ? 4 : 0) | (isIn1 ? 2 : 0) | (isIn0 ? 1: 0);
                System.out.println( x+","+z+" => index: "+index);
                switch(index)
                {
                    case 0: // trivial: fully outside
                        continue;
                    case 1:
                        float height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));
                        p0.set(x0,height,z0);
                        p1.set(x0+half,height,z0);
                        p2.set(x0,height,z0+ half );

                        int idx0 = meshBuilder.addVertex( p0, waterColor );
                        int idx1 = meshBuilder.addVertex( p1, waterColor );
                        int idx2 = meshBuilder.addVertex( p2, waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 2:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr+1)+data.height.get(ptr+1));
                        p0.set(x0+half,height,z0);
                        p1.set(x0+squareSize,height,z0);
                        p2.set(x0+squareSize,height,z0+ half );

                        idx0 = meshBuilder.addVertex( p0, waterColor );
                        idx1 = meshBuilder.addVertex( p1, waterColor );
                        idx2 = meshBuilder.addVertex( p2, waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 3:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));
                        p0.set(x0,height,z0);
                        p1.set(x0+squareSize,height,z0);
                        p2.set(x0+squareSize,height,z0+ half );
                        p3.set(x0,height,z0+ half );

                        idx0 = meshBuilder.addVertex( p0, waterColor );
                        idx1 = meshBuilder.addVertex( p1, waterColor );
                        idx2 = meshBuilder.addVertex( p2, waterColor );
                        int idx3 = meshBuilder.addVertex( p3, waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    case 4:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr+size+1)+data.height.get(ptr+size+1));
                        p0.set(x0+ half,height,z0+squareSize);
                        p1.set(x0+squareSize,height,z0+ half );
                        p2.set(x0+squareSize,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0 , waterColor );
                        idx1 = meshBuilder.addVertex( p1 , waterColor );
                        idx2 = meshBuilder.addVertex( p2 , waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 5:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));

                        p0.set(x0,height,z0);
                        p1.set(x0+ half,height,z0);
                        p2.set(x0,height,z0+ half );

                        idx0 = meshBuilder.addVertex( p0, waterColor );
                        idx1 = meshBuilder.addVertex( p1, waterColor );
                        idx2 = meshBuilder.addVertex( p2, waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);

                        float height2 = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr+size+1)+data.height.get(ptr+size+1));
                        p0.set(x0+ half,height2,z0+squareSize);
                        p1.set(x0+squareSize,height2,z0+ half );
                        p2.set(x0+squareSize,height2,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 6:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr+1)+data.height.get(ptr+1));
                        p0.set(x0+ half,height,z0);
                        p1.set(x0+squareSize,height,z0);
                        p2.set(x0+squareSize,height,z0+squareSize);
                        p3.set(x0+ half,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );
                        idx3 = meshBuilder.addVertex( p3,waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    case 7:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));
                        p0.set(x0,height,z0);
                        p1.set(x0+squareSize,height,z0);
                        p2.set(x0+squareSize,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor);
                        idx1 = meshBuilder.addVertex( p1,waterColor);
                        idx2 = meshBuilder.addVertex( p2,waterColor);

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 8:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr+size)+data.height.get(ptr+size));
                        p0.set(x0,height,z0+ half );
                        p1.set(x0+ half,height,z0+squareSize);
                        p2.set(x0,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 9:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));
                        p0.set(x0,height,z0);
                        p1.set(x0+ half,height,z0);
                        p2.set(x0+ half,height,z0+squareSize);
                        p3.set(x0,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );
                        idx3 = meshBuilder.addVertex( p3,waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    case 10:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));
                        p0.set(x0,height,z0);
                        p1.set(x0+ half,height,z0+squareSize);
                        p2.set(x0,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);

                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr+1)+data.height.get(ptr+1));
                        p0.set(x0+ half,height,z0);
                        p1.set(x0+squareSize,height,z0);
                        p2.set(x0+squareSize,height,z0+ half );

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 11:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));
                        p0.set(x0,height,z0);
                        p1.set(x0+squareSize,height,z0);
                        p2.set(x0,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 12:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr+size)+data.height.get(ptr+size));
                        p0.set(x0,height,z0+ half );
                        p1.set(x0+squareSize,height,z0+ half );
                        p2.set(x0+squareSize,height,z0+squareSize);
                        p3.set(x0,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );
                        idx3 = meshBuilder.addVertex( p3,waterColor );

                        meshBuilder.addQuad(idx0, idx1, idx2, idx3);
                        break;
                    case 13:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));
                        p0.set(x0,height,z0);
                        p1.set(x0+squareSize,height,z0+squareSize);
                        p2.set(x0,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 14:
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr+1)+data.height.get(ptr+1));
                        p0.set(x0+squareSize,height,z0);
                        p1.set(x0+squareSize,height,z0+squareSize);
                        p2.set(x0,height,z0+squareSize);

                        idx0 = meshBuilder.addVertex( p0,waterColor );
                        idx1 = meshBuilder.addVertex( p1,waterColor );
                        idx2 = meshBuilder.addVertex( p2,waterColor );

                        meshBuilder.addTriangle(idx0,idx1,idx2);
                        break;
                    case 15: // trivial: fully inside
                        // get height from any of the vertices
                        // (since it's water we assume a flat surface => all vertices have the same
                        // height above 'sea level'
                        height = TriangleList.HEIGHT_SCALE_FACTOR*(data.water.get(ptr)+data.height.get(ptr));
                        p0.set(x0,height,z0);
                        p1.set(x0+squareSize,height,z0);
                        p2.set(x0+squareSize,height,z0+squareSize);
                        p3.set(x0,height,z0+squareSize);

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
}