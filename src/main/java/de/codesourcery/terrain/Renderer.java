package de.codesourcery.terrain;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController;
import com.badlogic.gdx.math.Vector3;

import java.awt.*;
import java.util.Arrays;

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

    public Renderer()
    {
        cameraController.setVelocity( 50 );
        camera.position.set( new Vector3(0.0f,-12.5f,-25.05f) );
        camera.direction.set( new Vector3(0.0f,0.0f,-1.0f) );
        camera.update(true);
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
//        mesh1.setToCube( 5f );
        mesh1.copyTo( mesh2,true);

        // transform mesh into view space for depth sorting
        mesh2.multiply( camera.view );

        mesh2.visitDepthSortedTriangles(
                camera.position.x,
                camera.position.y,
                camera.position.z,
                new TriangleList.IVisitor()
                {
                    private final int x[] = new int[3];
                    private final int y[] = new int[3];

                    private final Vector3 norm = new Vector3();

                    private float[] vertices;
                    private float[] normals;

                    @Override
                    public void beforeFirstVisit()
                    {
                        // depth sorting is done but
                        // we need coordinates in screen space
                        // for rendering
                        normals = copy( mesh2.normals );
                        vertices = copy( mesh2.vertices );
                        mesh1.copyTo(mesh2,false); // keep normals for later
                        project(mesh2);
                    }

                    private float[] copy(float[] input) {
                        return Arrays.copyOf(input,input.length);
                    }

                    @Override
                    public void visit(int p0Idx,int p1Idx,int p2Idx)
                    {
                        // get normal in view space
                        norm.set(
                                normals[ p0Idx ],
                                normals[ p0Idx+1],
                                normals[ p0Idx+2]
                        );

                        // add base point
                        norm.add( vertices[p0Idx],
                                  vertices[p0Idx+1],
                                  vertices[p0Idx+2]);

                        norm.scl(2);

                        // perspective projection
                        projectNoMul( norm );

                        x[0] = (int) mesh2.vertices[ p0Idx ];
                        x[1] = (int) mesh2.vertices[ p1Idx ];
                        x[2] = (int) mesh2.vertices[ p2Idx ];

                        y[0] = (int) ( camera.viewportHeight - mesh2.vertices[ p0Idx+1 ]);
                        y[1] = (int) ( camera.viewportHeight - mesh2.vertices[ p1Idx+1 ]);
                        y[2] = (int) ( camera.viewportHeight - mesh2.vertices[ p2Idx+1 ]);

//                        gfx.fillPolygon( x, y,3 );

                        gfx.setColor(Color.BLACK);
                        drawLine(gfx,mesh2.vertices, p0Idx, p1Idx );
                        drawLine(gfx,mesh2.vertices, p1Idx, p2Idx );
                        drawLine(gfx,mesh2.vertices, p2Idx, p0Idx );

                        gfx.setColor(Color.GREEN);
//                        gfx.drawLine( x[0], y[0], (int) norm.x, (int) (camera.viewportHeight - norm.y) );
                    }
                });
    }

    private Vector3 projectNoMul(Vector3 worldCoords)
    {
        worldCoords.prj( camera.projection );
        worldCoords.x = camera.viewportWidth * (worldCoords.x + 1) / 2 + viewportX;
        worldCoords.y = camera.viewportHeight * (worldCoords.y + 1) / 2 + viewportY;
        worldCoords.z = (worldCoords.z + 1) / 2;
        return worldCoords;
    }

    private void drawLine(Graphics2D g,float[] vertices, int offsetP0,int offsetP1)
    {
        final int x0 = (int) vertices[offsetP0];
        final int y0 = (int) (camera.viewportHeight - vertices[offsetP0 + 1]);
        final int x1 = (int) vertices[offsetP1];
        final int y1 = (int) (camera.viewportHeight - vertices[offsetP1 + 1]);

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
