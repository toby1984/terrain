package de.codesourcery.terrain;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController;

import java.awt.Graphics2D;

public class Renderer
{
    private static final float TILE_SIZE = 5f;

    public final float viewportX=0;
    public final float viewportY=0;

    public final PerspectiveCamera camera = new PerspectiveCamera();

    public final FirstPersonCameraController cameraController =
            new FirstPersonCameraController( camera );

    private final TriangleList mesh1 = new TriangleList();
    private final TriangleList mesh2 = new TriangleList();

    private Data data;

    static {
        System.loadLibrary( "gdx64" );
    }

    public Renderer() {
        cameraController.setVelocity( 50 );
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
//        System.out.println("rendering(): "+ mesh2.vertexCount()+" vertices, "+mesh2.indexCount()+" indices, "+mesh2.triangleCount()+" triangles");
        project(mesh2);

        final int[] indices = mesh2.indices;
        final float[] vertices = mesh2.vertices;
        final int idxCnt = mesh2.indexCount();
        for ( int idxPtr = 0 ; idxPtr < idxCnt ; idxPtr+=3)
        {
            int p0 = indices[idxPtr];
            int p1 = indices[idxPtr+1];
            int p2 = indices[idxPtr+2];

//            System.out.println("triangle #"+idxPtr+": "+p0+" -> "+p1+" -> "+p2);

            final int offsetP0 = p0 * TriangleList.COMPONENT_CNT;
            final int offsetP1 = p1 * TriangleList.COMPONENT_CNT;
            final int offsetP2 = p2 * TriangleList.COMPONENT_CNT;

            drawLine(gfx,vertices,offsetP0,offsetP1);
            drawLine(gfx,vertices,offsetP1,offsetP2);
            drawLine(gfx,vertices,offsetP2,offsetP0);
        }
    }

    private void drawLine(Graphics2D g,float[] vertices, int offsetP0,int offsetP1) {
        final int x0 = (int) vertices[offsetP0];
        final int y0 = (int) (camera.viewportHeight - vertices[offsetP0 + 1]);
        final int x1 = (int) vertices[offsetP1];
        final int y1 = (int) (camera.viewportHeight - vertices[offsetP1 + 1]);
//        System.out.println("drawLine(): ("+x0+","+y0+") -> ("+x1+","+y1+")");
        if ( x0 >= 0 && x1 >= 0 && y0 >= 0 && y1 >= 0 &&
                y0 <= camera.viewportHeight && y1 <= camera.viewportHeight &&
                x0 <= camera.viewportWidth && x1 <= camera.viewportWidth ){
            g.drawLine( x0, y0, x1, y1 );
        }
    }

    private void project(TriangleList mesh)
    {
        mesh.project( camera.combined );

        final float[] vertices = mesh.vertices;
        final int vertexCnt = mesh.vertexCount();
        int vertexPtr = 0;
        for ( int i = 0 ; i < vertexCnt ; i++,vertexPtr+= TriangleList.COMPONENT_CNT )
        {
            vertices[vertexPtr  ] = camera.viewportWidth * (vertices[vertexPtr  ] + 1) / 2 + viewportX;
            vertices[vertexPtr+1] = camera.viewportHeight * (vertices[vertexPtr+1] + 1) / 2 + viewportY;
            vertices[vertexPtr+2] = (vertices[vertexPtr+2] + 1) / 2;
        }
    }

    public void zoomOut() {
        camera.position.z += 10;
        camera.update();
    }

    public void zoomIn() {
        camera.position.z -= 10;
        camera.update();
    }
}
