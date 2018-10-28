package de.codesourcery.terrain;

import com.badlogic.gdx.graphics.PerspectiveCamera;

import java.awt.Graphics2D;

public class Renderer
{
    private static final float TILE_SIZE = 5f;

    public float viewportX=0;
    public float viewportY=0;
    public float viewportWidth=640;
    public float viewportHeight=320;

    public final PerspectiveCamera camera = new PerspectiveCamera();

    private final TriangleList mesh1 = new TriangleList();
    private final TriangleList mesh2 = new TriangleList();

    private Data data;

    public Renderer() {
    }

    public void setData(Data d) {
        this.data = d;
        mesh1.setupMesh( d, TILE_SIZE );
    }

    public void render(Graphics2D gfx)
    {
        if ( data == null) {
            return;
        }

        mesh1.copyTo( mesh2 );
        project(mesh2);

        final int[] indices = mesh2.indices;
        final float[] vertices = mesh2.vertices;
        final int idxCnt = mesh2.indexCount();
        for ( int idxPtr = 0 ; idxPtr < idxCnt ; idxPtr+=3)
        {
            int p0 = indices[idxPtr];
            int p1 = indices[idxPtr+1];
            int p2 = indices[idxPtr+2];

            final int offsetP0 = p0 * TriangleList.COMPONENT_CNT;
            final int offsetP1 = p1 * TriangleList.COMPONENT_CNT;
            final int offsetP2 = p2 * TriangleList.COMPONENT_CNT;

            drawLine(gfx,vertices,offsetP0,offsetP1);
            drawLine(gfx,vertices,offsetP1,offsetP2);
            drawLine(gfx,vertices,offsetP2,offsetP0);
        }
    }

    private static void drawLine(Graphics2D g,float[] vertices, int offsetP0,int offsetP1) {
        g.drawLine((int) vertices[offsetP0],(int) vertices[offsetP0+1],
                   (int) vertices[offsetP1],(int) vertices[offsetP0+1]);
    }

    private void project(TriangleList mesh)
    {
        mesh.project( camera.combined );

        final float[] vertices = mesh.vertices;
        final int vertexCnt = mesh.vertexCount();
        int vertexPtr = 0;
        for ( int i = 0 ; i < vertexCnt ; i++,vertexPtr+= TriangleList.COMPONENT_CNT )
        {
            vertices[vertexPtr  ] = viewportWidth * (vertices[vertexPtr  ] + 1) / 2 + viewportX;
            vertices[vertexPtr+1] = viewportHeight * (vertices[vertexPtr+1] + 1) / 2 + viewportY;
            vertices[vertexPtr+2] = (vertices[vertexPtr+2] + 1) / 2;
        }
    }
}
